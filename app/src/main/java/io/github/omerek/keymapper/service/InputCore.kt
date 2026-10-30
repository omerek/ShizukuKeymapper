package io.github.omerek.keymapper.service

import android.os.RemoteException
import android.util.Log
import io.github.omerek.keymapper.IInputListener
import io.github.omerek.keymapper.input.EvdevDevice
import io.github.omerek.keymapper.input.EvdevNative
import io.github.omerek.keymapper.input.LinuxKeys
import io.github.omerek.keymapper.model.KeyMapping
import io.github.omerek.keymapper.model.MouseLookConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Servis tarafındaki girdi çekirdeği: klavyeleri bulur, okur, eşleme açıkken EVIOCGRAB ile yakalar
 * ve tuş olaylarını TouchInjector üzerinden dokunuşa çevirir.
 */
class InputCore(private val injector: TouchInjector) {

    @Volatile var listener: IInputListener? = null
    @Volatile var toggleKey: Int = LinuxKeys.KEY_F12
    @Volatile private var mappings: Map<Int, KeyMapping> = emptyMap()

    @Volatile var enabled = false
        private set

    /** Editör açıkken: klavye yakalanır ve tuşlar yalnızca bildirilir, dokunuşa çevrilmez. */
    @Volatile var capture = false
        private set

    private val shouldGrab get() = enabled || capture

    /** Mouse yalnızca kullanılıyorsa yakalanır; yoksa eşleme açıkken de normal imleç olarak çalışır. */
    private fun wantGrab(device: EvdevDevice): Boolean {
        if (!shouldGrab) return false
        if (device.isKeyboard || capture) return true
        val mouseUsed = mouseLook.config?.enabled == true ||
            mappings.keys.any { it in LinuxKeys.BTN_LEFT..LinuxKeys.BTN_TASK }
        return mouseUsed
    }

    @Volatile var devices: List<EvdevDevice> = emptyList()
        private set

    private val mouseLook = MouseLook(injector)

    private val readers = ConcurrentHashMap<String, DeviceReader>()
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        Thread({
            while (running) {
                try {
                    rescan()
                } catch (e: Exception) {
                    Log.e(TAG, "Cihaz taraması başarısız", e)
                }
                Thread.sleep(SCAN_INTERVAL_MS)
            }
        }, "evdev-scanner").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        readers.values.forEach { it.stop() }
        readers.clear()
        injector.releaseAll()
    }

    fun setMappings(list: List<KeyMapping>) {
        mappings = list.associateBy { it.code }
        updateGrab()
    }

    fun setMouseLook(config: MouseLookConfig?) {
        mouseLook.release()
        mouseLook.config = config
        updateGrab()
    }

    @Synchronized
    fun setEnabled(on: Boolean) {
        if (enabled == on) return
        enabled = on
        if (!on) {
            mouseLook.release()
            injector.releaseAll()
        }
        updateGrab()
        notifyListener { it.onMappingEnabledChanged(on) }
    }

    @Synchronized
    fun setCapture(on: Boolean) {
        if (capture == on) return
        capture = on
        updateGrab()
    }

    @Synchronized
    private fun updateGrab() {
        readers.values.forEach { it.setGrab(wantGrab(it.device)) }
    }

    private fun rescan() {
        val found = EvdevDevice.scan()
        // Yeni klavye ve mouse'lar için okuyucu başlat
        for (dev in found) {
            if (readers.containsKey(dev.path)) continue
            val fd = EvdevNative.open(dev.path)
            if (fd < 0) {
                Log.w(TAG, "${dev.path} açılamadı: errno ${-fd}")
                continue
            }
            val reader = DeviceReader(dev, fd)
            readers[dev.path] = reader
            if (wantGrab(dev)) reader.setGrab(true)
            reader.start()
        }
        if (found != devices) {
            devices = found
            val json = EvdevDevice.listToJson(found)
            notifyListener { it.onDevicesChanged(json) }
        }
    }

    private fun onEvent(type: Int, code: Int, value: Int) {
        if (type != LinuxKeys.EV_KEY || value == 2) return // 2 = otomatik tekrar
        val down = value == 1
        if (code == toggleKey) {
            if (down) setEnabled(!enabled)
            return
        }
        notifyListener { it.onKey(code, down) }
        if (!enabled) return
        if (down) {
            val m = mappings[code] ?: return
            injector.down(code, m.x, m.y)
        } else {
            // Eşleme basılıyken silinmiş olsa bile parmağı kaldır
            injector.up(code)
        }
    }

    private fun onMouseMove(dx: Int, dy: Int) {
        if (enabled) mouseLook.move(dx, dy)
    }

    private inline fun notifyListener(block: (IInputListener) -> Unit) {
        val l = listener ?: return
        try {
            block(l)
        } catch (e: RemoteException) {
            listener = null
        }
    }

    private inner class DeviceReader(val device: EvdevDevice, private val fd: Int) {
        @Volatile private var alive = true
        private val thread = Thread(::loop, "evdev-${device.path.substringAfterLast('/')}").apply { isDaemon = true }

        fun start() = thread.start()

        fun stop() {
            alive = false
        }

        @Volatile private var grabbed = false

        @Synchronized
        fun setGrab(on: Boolean) {
            if (grabbed == on) return
            if (EvdevNative.grab(fd, on)) grabbed = on
            else Log.w(TAG, "EVIOCGRAB($on) başarısız: ${device.name}")
        }

        private fun loop() {
            val buf = IntArray(64 * 3)
            // REL_X/REL_Y ayrı gelir; SYN_REPORT'ta tek hareket olarak işlenir
            var dx = 0
            var dy = 0
            val timeout = if (device.isMouse) MOUSE_POLL_TIMEOUT_MS else POLL_TIMEOUT_MS
            Log.i(TAG, "Okuma başladı: ${device.name} (${device.path})")
            while (alive) {
                val n = EvdevNative.read(fd, buf, timeout)
                if (n < 0) {
                    Log.i(TAG, "Cihaz kapandı: ${device.name} (errno ${-n})")
                    break
                }
                for (i in 0 until n) {
                    val type = buf[i * 3]
                    val code = buf[i * 3 + 1]
                    val value = buf[i * 3 + 2]
                    when (type) {
                        LinuxKeys.EV_REL -> when (code) {
                            LinuxKeys.REL_X -> dx += value
                            LinuxKeys.REL_Y -> dy += value
                        }
                        LinuxKeys.EV_SYN -> if (dx != 0 || dy != 0) {
                            onMouseMove(dx, dy)
                            dx = 0
                            dy = 0
                        }
                        else -> onEvent(type, code, value)
                    }
                }
                if (device.isMouse) mouseLook.tick()
            }
            EvdevNative.close(fd) // kapatmak yakalamayı da bırakır
            readers.remove(device.path, this)
        }
    }

    companion object {
        private const val TAG = "KeymapperInput"
        private const val SCAN_INTERVAL_MS = 2000L
        private const val POLL_TIMEOUT_MS = 250
        private const val MOUSE_POLL_TIMEOUT_MS = 100
    }
}
