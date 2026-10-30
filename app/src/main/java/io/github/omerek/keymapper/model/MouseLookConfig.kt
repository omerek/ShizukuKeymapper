package io.github.omerek.keymapper.model

import org.json.JSONObject

/**
 * Mouse ile kamera bakışı: hareket, (cx, cy) merkezli ve radius yarıçaplı alanda sürüklemeye çevrilir.
 * Parmak alanın dışına çıkınca kaldırılıp merkeze yeniden konur. Koordinatlar ekran pikseli.
 */
data class MouseLookConfig(
    val enabled: Boolean,
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val sensitivity: Float,
) {
    fun toJson(): String = JSONObject()
        .put("enabled", enabled)
        .put("cx", cx.toDouble())
        .put("cy", cy.toDouble())
        .put("radius", radius.toDouble())
        .put("sensitivity", sensitivity.toDouble())
        .toString()

    companion object {
        fun fromJson(json: String?): MouseLookConfig? {
            if (json.isNullOrBlank()) return null
            val o = JSONObject(json)
            return MouseLookConfig(
                enabled = o.optBoolean("enabled", true),
                cx = o.getDouble("cx").toFloat(),
                cy = o.getDouble("cy").toFloat(),
                radius = o.getDouble("radius").toFloat(),
                sensitivity = o.optDouble("sensitivity", 1.0).toFloat(),
            )
        }

        /** PUBG benzeri oyunlar için varsayılan: ekranın sağ-orta bölgesi. */
        fun default(screenW: Int, screenH: Int) = MouseLookConfig(
            enabled = true,
            cx = screenW * 0.7f,
            cy = screenH * 0.45f,
            radius = minOf(screenW, screenH) * 0.18f,
            sensitivity = 1f,
        )
    }
}
