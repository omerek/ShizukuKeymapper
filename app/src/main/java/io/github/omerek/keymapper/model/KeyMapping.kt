package io.github.omerek.keymapper.model

import org.json.JSONArray
import org.json.JSONObject

/** Bir tuş (Linux key code) → ekran noktası (piksel). Adım 8'de oransal koordinata geçecek. */
data class KeyMapping(val code: Int, val x: Float, val y: Float) {

    fun toJson(): JSONObject = JSONObject().put("code", code).put("x", x.toDouble()).put("y", y.toDouble())

    companion object {
        fun fromJson(o: JSONObject) = KeyMapping(o.getInt("code"), o.getDouble("x").toFloat(), o.getDouble("y").toFloat())

        fun listToJson(list: List<KeyMapping>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun listFromJson(json: String?): List<KeyMapping> {
            if (json.isNullOrBlank()) return emptyList()
            val arr = JSONArray(json)
            return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }
    }
}
