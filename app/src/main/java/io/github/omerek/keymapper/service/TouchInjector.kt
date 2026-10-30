package io.github.omerek.keymapper.service

import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent

/**
 * Çoklu dokunuş durumunu tutar ve tutarlı MotionEvent dizileri üretir.
 *
 * Her slot (ör. bir tuş) ilk DOWN'da boştaki en küçük pointer id'yi alır ve UP'a kadar korur.
 * İlk parmak ACTION_DOWN, sonrakiler ACTION_POINTER_DOWN; son parmak ACTION_UP ile biter.
 */
class TouchInjector(private val inject: (InputEvent) -> Boolean) {

    private class Pointer(val id: Int, var x: Float, var y: Float)

    // Ekleme sırası korunur; olaydaki pointer index'i bu sıradır.
    private val active = LinkedHashMap<Int, Pointer>()
    private var downTime = 0L

    @Synchronized
    fun down(slot: Int, x: Float, y: Float): Boolean {
        if (active.containsKey(slot)) return move(slot, x, y)
        val id = freePointerId() ?: return false
        val now = SystemClock.uptimeMillis()
        if (active.isEmpty()) downTime = now
        active[slot] = Pointer(id, x, y)
        val action = if (active.size == 1) {
            MotionEvent.ACTION_DOWN
        } else {
            MotionEvent.ACTION_POINTER_DOWN or (indexOf(slot) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        }
        return send(action, now)
    }

    @Synchronized
    fun move(slot: Int, x: Float, y: Float): Boolean {
        val p = active[slot] ?: return false
        p.x = x
        p.y = y
        return send(MotionEvent.ACTION_MOVE, SystemClock.uptimeMillis())
    }

    @Synchronized
    fun up(slot: Int): Boolean {
        if (!active.containsKey(slot)) return false
        val action = if (active.size == 1) {
            MotionEvent.ACTION_UP
        } else {
            MotionEvent.ACTION_POINTER_UP or (indexOf(slot) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        }
        // UP olayı, kalkan parmağı da içermelidir; sonra listeden çıkarılır.
        val ok = send(action, SystemClock.uptimeMillis())
        active.remove(slot)
        return ok
    }

    @Synchronized
    fun releaseAll() {
        active.keys.toList().forEach { up(it) }
    }

    private fun indexOf(slot: Int): Int = active.keys.indexOf(slot)

    private fun freePointerId(): Int? {
        val used = active.values.map { it.id }.toSet()
        return (0 until MAX_POINTERS).firstOrNull { it !in used }
    }

    private fun send(action: Int, eventTime: Long): Boolean {
        val count = active.size
        val props = arrayOfNulls<MotionEvent.PointerProperties>(count)
        val coords = arrayOfNulls<MotionEvent.PointerCoords>(count)
        active.values.forEachIndexed { i, p ->
            props[i] = MotionEvent.PointerProperties().apply {
                id = p.id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
            coords[i] = MotionEvent.PointerCoords().apply {
                x = p.x
                y = p.y
                pressure = 1f
                size = 1f
            }
        }
        val event = MotionEvent.obtain(
            downTime, eventTime, action, count, props, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
        )
        return try {
            inject(event)
        } finally {
            event.recycle()
        }
    }

    companion object {
        const val MAX_POINTERS = 10
    }
}
