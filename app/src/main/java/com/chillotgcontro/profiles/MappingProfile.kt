package com.chillotgcontro.profiles

import android.content.Context
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

data class KeyMapping(val keyCode: Int, val xRatio: Float, val yRatio: Float, val label: String)

/** Lưu các điểm ánh xạ tương đối theo kích thước màn hình, tránh phụ thuộc độ phân giải cụ thể. */
object MappingProfile {
    private const val PREF = "mapping_profile"
    private const val KEY_MAPS = "maps"

    fun load(context: Context): List<KeyMapping> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_MAPS, null)
        if (raw == null) return defaults()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> arr.getJSONObject(i).let {
                KeyMapping(it.getInt("key"), it.getDouble("x").toFloat(), it.getDouble("y").toFloat(), it.getString("label"))
            } }
        }.getOrElse { defaults() }
    }

    fun save(context: Context, mappings: List<KeyMapping>) {
        val arr = JSONArray()
        mappings.forEach { m -> arr.put(JSONObject().put("key", m.keyCode).put("x", m.xRatio.coerceIn(0f, 1f)).put("y", m.yRatio.coerceIn(0f, 1f)).put("label", m.label)) }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY_MAPS, arr.toString()).apply()
    }

    fun defaults() = listOf(
        KeyMapping(KeyEvent.KEYCODE_W, .50f, .78f, "W"), KeyMapping(KeyEvent.KEYCODE_A, .42f, .85f, "A"),
        KeyMapping(KeyEvent.KEYCODE_S, .50f, .90f, "S"), KeyMapping(KeyEvent.KEYCODE_D, .58f, .85f, "D"),
        KeyMapping(KeyEvent.KEYCODE_SPACE, .88f, .82f, "SPACE")
    )
}
