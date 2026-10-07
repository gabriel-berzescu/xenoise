package com.xenoise.data

import android.content.Context
import android.util.Log
import com.xenoise.model.Noise
import com.xenoise.model.Presets
import com.xenoise.model.Slopes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The user's saved noises plus small settings, stored in SharedPreferences.
 * Call from the main thread.
 */
class NoiseLibrary(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _customNoises = MutableStateFlow(loadCustomNoises())
    val customNoises: StateFlow<List<Noise>> = _customNoises.asStateFlow()

    var selectedId: String
        get() = prefs.getString(KEY_SELECTED, null) ?: Presets.Pink.id
        set(value) = prefs.edit().putString(KEY_SELECTED, value).apply()

    var volume: Float
        get() = prefs.getFloat(KEY_VOLUME, DEFAULT_VOLUME)
        set(value) = prefs.edit().putFloat(KEY_VOLUME, value.coerceIn(0f, 1f)).apply()

    fun find(id: String?): Noise? =
        Presets.byId(id) ?: _customNoises.value.firstOrNull { it.id == id }

    /** Adds the noise, or replaces the saved one with the same id. */
    fun save(noise: Noise) {
        val clean = noise.copy(
            name = noise.name.trim().ifEmpty { nextDefaultName() },
            slope = Slopes.normalize(noise.slope),
            isPreset = false,
        )
        val list = _customNoises.value.toMutableList()
        val index = list.indexOfFirst { it.id == clean.id }
        if (index >= 0) list[index] = clean else list.add(clean)
        update(list)
    }

    fun delete(id: String) {
        update(_customNoises.value.filterNot { it.id == id })
    }

    /** "Custom 1", "Custom 2", ... skipping names that are already taken. */
    fun nextDefaultName(): String {
        val taken = _customNoises.value.map { it.name }.toSet()
        var n = 1
        while ("Custom $n" in taken) n++
        return "Custom $n"
    }

    private fun update(list: List<Noise>) {
        _customNoises.value = list
        val json = JSONArray()
        list.forEach { noise ->
            json.put(
                JSONObject()
                    .put("id", noise.id)
                    .put("name", noise.name)
                    .put("slope", noise.slope.toDouble())
            )
        }
        prefs.edit().putString(KEY_CUSTOM, json.toString()).apply()
    }

    private fun loadCustomNoises(): List<Noise> {
        val raw = prefs.getString(KEY_CUSTOM, null) ?: return emptyList()
        return try {
            val json = JSONArray(raw)
            List(json.length()) { i ->
                val item = json.getJSONObject(i)
                Noise(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    slope = Slopes.normalize(item.getDouble("slope").toFloat()),
                )
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Ignoring unreadable saved noises", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "NoiseLibrary"
        private const val PREFS_NAME = "xenoise"
        private const val KEY_CUSTOM = "custom_noises"
        private const val KEY_SELECTED = "selected_id"
        private const val KEY_VOLUME = "volume"
        const val DEFAULT_VOLUME = 0.7f
    }
}
