package io.github.omerek.keymapper.model

import android.content.Context
import android.content.SharedPreferences
import io.github.omerek.keymapper.shizuku.ShizukuManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Geçici tek profil deposu (Adım 6'da uygulamaya özel JSON profillere dönüşecek). */
object MappingRepository {

    private const val PREFS = "mappings"
    private const val KEY = "default"

    private lateinit var prefs: SharedPreferences
    private val _mappings = MutableStateFlow<List<KeyMapping>>(emptyList())
    val mappings: StateFlow<List<KeyMapping>> = _mappings.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _mappings.value = runCatching { KeyMapping.listFromJson(prefs.getString(KEY, null)) }.getOrDefault(emptyList())
    }

    /** Aynı tuşa ait eski eşlemenin yerine geçer. */
    fun upsert(mapping: KeyMapping) = save(_mappings.value.filter { it.code != mapping.code } + mapping)

    fun replaceAll(list: List<KeyMapping>) = save(list.distinctBy { it.code })

    fun remove(code: Int) = save(_mappings.value.filter { it.code != code })

    fun json(): String = KeyMapping.listToJson(_mappings.value)

    private fun save(list: List<KeyMapping>) {
        _mappings.value = list
        val json = KeyMapping.listToJson(list)
        prefs.edit().putString(KEY, json).apply()
        ShizukuManager.pushMappings(json)
    }
}
