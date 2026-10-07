package io.github.ar13x3.airpoint.core

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ThemeMode { System, Light, Dark }

data class UserSettings(
    /** 1..100. Multiplied by [SENSITIVITY_GAIN] to turn S Pen deltas into pixels. */
    val sensitivity: Float = 25f,
    /** 1..100, higher is smoother (more easing on the PC). */
    val smoothness: Float = 68f,
    val doublePressCenters: Boolean = true,
    /** Pushing the cursor past a screen edge scrolls in that direction. */
    val edgeScroll: Boolean = true,
    val theme: ThemeMode = ThemeMode.System,
    val onboarded: Boolean = false,
    val lastPcId: String? = null,
) {
    /** PC easing: fraction of remaining distance moved per 5 ms tick (1 = instant). */
    val smoothingAlpha: Float get() = 1f - (smoothness - 1f) / 99f * (1f - 0.15f)

    companion object {
        const val SENSITIVITY_GAIN = 10f
    }
}

private val Context.store by preferencesDataStore("airpoint")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val sensitivity = floatPreferencesKey("sensitivity")
        val smoothness = floatPreferencesKey("smoothness")
        val doublePress = booleanPreferencesKey("double_press_centers")
        val edgeScroll = booleanPreferencesKey("edge_scroll")
        val theme = stringPreferencesKey("theme")
        val onboarded = booleanPreferencesKey("onboarded")
        val lastPc = stringPreferencesKey("last_pc")
        val pairedPcs = stringPreferencesKey("paired_pcs")
        val deviceId = stringPreferencesKey("device_id")
    }

    val settings: Flow<UserSettings> = context.store.data.map { p ->
        UserSettings(
            sensitivity = p[Keys.sensitivity] ?: 25f,
            smoothness = p[Keys.smoothness] ?: 68f,
            doublePressCenters = p[Keys.doublePress] ?: true,
            edgeScroll = p[Keys.edgeScroll] ?: true,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            onboarded = p[Keys.onboarded] ?: false,
            lastPcId = p[Keys.lastPc],
        )
    }.distinctUntilChanged()

    val pairedPcs: Flow<List<PairedPc>> = context.store.data
        .map { decode(it[Keys.pairedPcs]) }
        .distinctUntilChanged()

    suspend fun setSensitivity(v: Float) = edit { it[Keys.sensitivity] = v }
    suspend fun setSmoothness(v: Float) = edit { it[Keys.smoothness] = v }
    suspend fun setDoublePressCenters(v: Boolean) = edit { it[Keys.doublePress] = v }
    suspend fun setEdgeScroll(v: Boolean) = edit { it[Keys.edgeScroll] = v }
    suspend fun setTheme(v: ThemeMode) = edit { it[Keys.theme] = v.name }
    suspend fun setOnboarded() = edit { it[Keys.onboarded] = true }

    suspend fun current(): UserSettings = settings.first()
    suspend fun paired(): List<PairedPc> = pairedPcs.first()

    suspend fun savePaired(pc: PairedPc) = edit { p ->
        val list = decode(p[Keys.pairedPcs]).filterNot { it.id == pc.id } + pc
        p[Keys.pairedPcs] = encode(list)
        p[Keys.lastPc] = pc.id
    }

    /** Remember where a PC was last seen and that it was just used. */
    suspend fun touch(id: String, host: String, port: Int, name: String?) = edit { p ->
        val list = decode(p[Keys.pairedPcs]).map {
            if (it.id == id) it.copy(host = host, port = port, name = name ?: it.name, lastUsed = System.currentTimeMillis()) else it
        }
        p[Keys.pairedPcs] = encode(list)
        p[Keys.lastPc] = id
    }

    suspend fun forget(id: String) = edit { p ->
        p[Keys.pairedPcs] = encode(decode(p[Keys.pairedPcs]).filterNot { it.id == id })
        if (p[Keys.lastPc] == id) p.remove(Keys.lastPc)
    }

    /** A stable random id for this phone, sent so a PC can recognise re-pairing. */
    suspend fun deviceId(): String {
        context.store.data.first()[Keys.deviceId]?.let { return it }
        val id = UUID.randomUUID().toString()
        edit { it[Keys.deviceId] = id }
        return id
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.store.edit { block(it) }
    }

    private fun decode(raw: String?): List<PairedPc> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PairedPc(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    host = o.getString("host"),
                    port = o.getInt("port"),
                    token = o.getString("token"),
                    lastUsed = o.optLong("lastUsed"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(list: List<PairedPc>): String = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("id", it.id).put("name", it.name).put("host", it.host).put("port", it.port)
                .put("token", it.token).put("lastUsed", it.lastUsed))
        }
    }.toString()
}
