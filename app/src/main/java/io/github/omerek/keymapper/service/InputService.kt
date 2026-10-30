package io.github.omerek.keymapper.service

import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.InputEvent
import io.github.omerek.keymapper.IInputService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method
import java.util.concurrent.Executors
import kotlin.system.exitProcess

/**
 * Shizuku tarafından ayrı bir süreçte, shell (uid 2000) yetkisiyle çalıştırılan servis.
 * Uygulamanın kendi süreci değildir; Context ve uygulama durumu burada yoktur.
 */
class InputService : IInputService.Stub() {

    private val injector: TouchInjector
    private val gestureThread = Executors.newSingleThreadExecutor()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
        val (target, method) = resolveInjectMethod()
        injector = TouchInjector { event ->
            try {
                method.invoke(target, event, INJECT_MODE_ASYNC) as Boolean
            } catch (e: Exception) {
                Log.e(TAG, "injectInputEvent başarısız", e)
                false
            }
        }
        Log.i(TAG, "InputService başladı, uid=${Process.myUid()}")
    }

    override fun destroy() {
        injector.releaseAll()
        exitProcess(0)
    }

    override fun exit() = destroy()

    override fun getUid(): Int = Process.myUid()

    override fun touchDown(slot: Int, x: Float, y: Float) = injector.down(slot, x, y)

    override fun touchMove(slot: Int, x: Float, y: Float) = injector.move(slot, x, y)

    override fun touchUp(slot: Int) = injector.up(slot)

    override fun releaseAll() = injector.releaseAll()

    override fun tap(x: Float, y: Float, durationMs: Long) {
        gestureThread.execute {
            injector.down(SLOT_TAP, x, y)
            SystemClock.sleep(durationMs.coerceIn(10, 5000))
            injector.up(SLOT_TAP)
        }
    }

    override fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long) {
        gestureThread.execute {
            val duration = durationMs.coerceIn(50, 5000)
            val steps = (duration / 16).toInt().coerceAtLeast(2)
            injector.down(SLOT_SWIPE, x1, y1)
            for (i in 1..steps) {
                SystemClock.sleep(duration / steps)
                val t = i.toFloat() / steps
                injector.move(SLOT_SWIPE, x1 + (x2 - x1) * t, y1 + (y2 - y1) * t)
            }
            injector.up(SLOT_SWIPE)
        }
    }

    /** Android 14+ InputManagerGlobal, daha eskilerde InputManager.getInstance(). */
    private fun resolveInjectMethod(): Pair<Any, Method> {
        val cls = try {
            Class.forName("android.hardware.input.InputManagerGlobal")
        } catch (e: ClassNotFoundException) {
            Class.forName("android.hardware.input.InputManager")
        }
        val instance = cls.getDeclaredMethod("getInstance").invoke(null)!!
        val method = cls.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
        return instance to method
    }

    companion object {
        private const val TAG = "KeymapperInput"

        // InputManager.INJECT_INPUT_EVENT_MODE_ASYNC
        private const val INJECT_MODE_ASYNC = 0

        // Uygulamanın kullanmayacağı ayrılmış slotlar
        const val SLOT_TAP = -1
        const val SLOT_SWIPE = -2
    }
}
