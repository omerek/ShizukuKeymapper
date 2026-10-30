package io.github.omerek.keymapper.service

import android.os.SystemClock
import io.github.omerek.keymapper.model.MouseLookConfig
import kotlin.math.hypot

/** Mouse hareketini tek bir parmakla kamera sürüklemesine çevirir. */
class MouseLook(private val injector: TouchInjector) {

    @Volatile var config: MouseLookConfig? = null

    private var active = false
    private var x = 0f
    private var y = 0f
    private var lastMove = 0L

    @Synchronized
    fun move(dx: Int, dy: Int) {
        val c = config?.takeIf { it.enabled } ?: return
        if (!active) {
            if (!injector.down(SLOT, c.cx, c.cy)) return
            x = c.cx
            y = c.cy
            active = true
        }
        x += dx * c.sensitivity
        y += dy * c.sensitivity
        if (hypot(x - c.cx, y - c.cy) > c.radius) {
            // Alanın kenarına gelindi: parmağı kaldır, merkeze yeniden koy ve hareketi oradan sürdür
            injector.up(SLOT)
            injector.down(SLOT, c.cx, c.cy)
            x = c.cx + dx * c.sensitivity
            y = c.cy + dy * c.sensitivity
        }
        // Parmak başka bir yerden kaldırıldıysa (ör. eşleme kapatıldı) bir sonraki harekette yeniden başla
        if (!injector.move(SLOT, x, y)) active = false
        lastMove = SystemClock.uptimeMillis()
    }

    /** Mouse bir süre durunca parmağı kaldır; bir sonraki hareket merkezden başlar. */
    @Synchronized
    fun tick() {
        if (active && SystemClock.uptimeMillis() - lastMove > IDLE_RELEASE_MS) release()
    }

    @Synchronized
    fun release() {
        if (!active) return
        injector.up(SLOT)
        active = false
    }

    companion object {
        const val SLOT = -3
        private const val IDLE_RELEASE_MS = 300L
    }
}
