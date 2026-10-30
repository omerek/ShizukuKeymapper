package io.github.omerek.keymapper.input

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class EvdevDevice(val path: String, val name: String, val isKeyboard: Boolean, val isMouse: Boolean) {

    fun toJson(): JSONObject = JSONObject()
        .put("path", path).put("name", name).put("keyboard", isKeyboard).put("mouse", isMouse)

    companion object {
        /** /dev/input/event* düğümlerini tarar, klavye ve mouse olanları döndürür. Servis sürecinde çalışır. */
        fun scan(): List<EvdevDevice> {
            val nodes = File("/dev/input").listFiles { f -> f.name.startsWith("event") } ?: return emptyList()
            return nodes.sortedBy { it.name }.mapNotNull { probe(it.absolutePath) }
        }

        private fun probe(path: String): EvdevDevice? {
            val fd = EvdevNative.open(path)
            if (fd < 0) return null
            try {
                val name = EvdevNative.name(fd) ?: path
                val hasKey = EvdevNative.hasEventType(fd, LinuxKeys.EV_KEY)
                val keyboard = hasKey &&
                    EvdevNative.hasCode(fd, LinuxKeys.EV_KEY, LinuxKeys.KEY_A) &&
                    EvdevNative.hasCode(fd, LinuxKeys.EV_KEY, LinuxKeys.KEY_Z) &&
                    EvdevNative.hasCode(fd, LinuxKeys.EV_KEY, LinuxKeys.KEY_SPACE)
                val mouse = hasKey &&
                    EvdevNative.hasEventType(fd, LinuxKeys.EV_REL) &&
                    EvdevNative.hasCode(fd, LinuxKeys.EV_REL, LinuxKeys.REL_X) &&
                    EvdevNative.hasCode(fd, LinuxKeys.EV_REL, LinuxKeys.REL_Y) &&
                    EvdevNative.hasCode(fd, LinuxKeys.EV_KEY, LinuxKeys.BTN_LEFT)
                return if (keyboard || mouse) EvdevDevice(path, name, keyboard, mouse) else null
            } finally {
                EvdevNative.close(fd)
            }
        }

        fun listToJson(list: List<EvdevDevice>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(json: String?): List<EvdevDevice> {
            if (json.isNullOrBlank()) return emptyList()
            val arr = JSONArray(json)
            return (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                EvdevDevice(o.getString("path"), o.getString("name"), o.getBoolean("keyboard"), o.getBoolean("mouse"))
            }
        }
    }
}
