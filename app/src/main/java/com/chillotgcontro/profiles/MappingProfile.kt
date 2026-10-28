package com.chillotgcontro.profiles

import android.content.Context
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class KeyMapping(
    val keyCode: Int,
    val xRatio: Float,
    val yRatio: Float,
    val label: String,
    val controlType: String = "tap",
    val groupId: String = "",
    val direction: String = "",
    val mouseButton: String = "left"
)

data class KeymapPreset(
    val id: String,
    val name: String,
    val sourceProfile: String,
    val savedAt: Long,
    val mappings: List<KeyMapping>
)

/** Per-game mappings plus reusable named keymap snapshots. */
object MappingProfile {
    private const val PREF = "mapping_profile_"
    private const val KEY_MAPS = "maps"
    private const val LIBRARY_PREFS = "keymap_library"
    private const val PRESETS_KEY = "presets"
    private const val FORMAT_VERSION = 1

    private fun prefsName(profile: String) = PREF + profile.lowercase().replace(Regex("[^a-z0-9_-]"), "_")

    fun load(context: Context, profile: String = "Mặc định"): List<KeyMapping> {
        val raw = context.getSharedPreferences(prefsName(profile), Context.MODE_PRIVATE).getString(KEY_MAPS, null)
        if (raw == null) return defaults()
        return runCatching { decodeMappings(JSONArray(raw)) }.getOrElse { defaults() }
    }

    fun save(context: Context, mappings: List<KeyMapping>, profile: String = "Mặc định", synchronous: Boolean = false) {
        val editor = context.getSharedPreferences(prefsName(profile), Context.MODE_PRIVATE).edit().putString(KEY_MAPS, encodeMappings(mappings).toString())
        if (synchronous) editor.commit() else editor.apply()
    }

    fun createJoystick(xRatio: Float, yRatio: Float): List<KeyMapping> {
        val group = UUID.randomUUID().toString()
        val x = xRatio.coerceIn(0f, 1f)
        val y = yRatio.coerceIn(0f, 1f)
        return listOf(
            KeyMapping(KeyEvent.KEYCODE_W, x, y, "W", "joystick", group, "up"),
            KeyMapping(KeyEvent.KEYCODE_A, x, y, "A", "joystick", group, "left"),
            KeyMapping(KeyEvent.KEYCODE_S, x, y, "S", "joystick", group, "down"),
            KeyMapping(KeyEvent.KEYCODE_D, x, y, "D", "joystick", group, "right")
        )
    }

    fun createMouseButton(xRatio: Float, yRatio: Float, button: String = "left") = KeyMapping(
        mouseButtonKeyCode(button), xRatio.coerceIn(0f, 1f), yRatio.coerceIn(0f, 1f), mouseButtonLabel(button),
        controlType = "mouse_button", mouseButton = button
    )

    fun mouseButtonKeyCode(button: String) = when (button) {
        "right" -> KeyEvent.KEYCODE_BUTTON_2
        "middle" -> KeyEvent.KEYCODE_BUTTON_3
        else -> KeyEvent.KEYCODE_BUTTON_1
    }

    fun mouseButtonLabel(button: String) = when (button) {
        "right" -> "Chuột phải"
        "middle" -> "Chuột giữa"
        else -> "Chuột trái"
    }

    fun mouseButtonShortLabel(button: String) = when (button) {
        "right" -> "R"
        "middle" -> "M"
        else -> "L"
    }

    fun exportJson(profile: String, mappings: List<KeyMapping>): String = JSONObject()
        .put("app", "ChillOTGConTro")
        .put("formatVersion", FORMAT_VERSION)
        .put("profile", profile)
        .put("mappings", encodeMappings(mappings))
        .toString(2)

    /** Accepts this app's versioned object or the legacy bare JSON array. */
    fun parseJson(raw: String): List<KeyMapping>? = runCatching {
        val json = raw.trim()
        val array = if (json.startsWith("[")) JSONArray(json) else {
            val root = JSONObject(json)
            require(root.optString("app", "ChillOTGConTro") == "ChillOTGConTro") { "Tệp này không phải keymap ChillOTGConTro." }
            require(root.optInt("formatVersion", FORMAT_VERSION) <= FORMAT_VERSION) { "Keymap được tạo từ phiên bản mới hơn." }
            root.getJSONArray("mappings")
        }
        decodeMappings(array)
    }.getOrNull()

    fun importJson(context: Context, raw: String, profile: String): List<KeyMapping>? {
        val mappings = parseJson(raw) ?: return null
        save(context, mappings, profile, synchronous = true)
        return mappings
    }

    fun savePreset(context: Context, name: String, sourceProfile: String, mappings: List<KeyMapping>): Boolean {
        val cleanName = name.trim().take(48)
        if (cleanName.isBlank() || mappings.size > 100) return false
        val prefs = context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE)
        val current = runCatching { JSONArray(prefs.getString(PRESETS_KEY, "[]") ?: "[]") }.getOrElse { JSONArray() }
        val result = JSONArray()
        var replaced = false
        for (index in 0 until current.length()) {
            val item = current.optJSONObject(index) ?: continue
            if (item.optString("name").equals(cleanName, ignoreCase = true)) {
                if (!replaced) {
                    result.put(presetJson(cleanName, sourceProfile, mappings))
                    replaced = true
                }
            } else result.put(item)
        }
        if (!replaced) result.put(presetJson(cleanName, sourceProfile, mappings))
        return prefs.edit().putString(PRESETS_KEY, result.toString()).commit()
    }

    fun presets(context: Context): List<KeymapPreset> {
        val raw = context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE).getString(PRESETS_KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val mappings = item.optJSONArray("mappings")?.let(::decodeMappings) ?: return@mapNotNull null
                KeymapPreset(
                    item.optString("id"), item.optString("name"), item.optString("sourceProfile"),
                    item.optLong("savedAt"), mappings
                )
            }.sortedByDescending { it.savedAt }
        }.getOrDefault(emptyList())
    }

    fun applyPreset(context: Context, presetId: String, destinationProfile: String): List<KeyMapping>? {
        val preset = presets(context).firstOrNull { it.id == presetId } ?: return null
        save(context, preset.mappings, destinationProfile, synchronous = true)
        return preset.mappings
    }

    fun deletePreset(context: Context, presetId: String) {
        val prefs = context.getSharedPreferences(LIBRARY_PREFS, Context.MODE_PRIVATE)
        val current = presets(context).filterNot { it.id == presetId }
        val array = JSONArray()
        current.forEach { array.put(presetJson(it.name, it.sourceProfile, it.mappings, it.id, it.savedAt)) }
        prefs.edit().putString(PRESETS_KEY, array.toString()).apply()
    }

    private fun presetJson(name: String, sourceProfile: String, mappings: List<KeyMapping>, id: String = UUID.randomUUID().toString(), savedAt: Long = System.currentTimeMillis()) =
        JSONObject().put("id", id).put("name", name).put("sourceProfile", sourceProfile).put("savedAt", savedAt).put("mappings", encodeMappings(mappings))

    private fun encodeMappings(mappings: List<KeyMapping>): JSONArray {
        val array = JSONArray()
        mappings.take(100).forEach { mapping ->
            array.put(JSONObject()
                .put("key", mapping.keyCode)
                .put("x", mapping.xRatio.coerceIn(0f, 1f))
                .put("y", mapping.yRatio.coerceIn(0f, 1f))
                .put("label", mapping.label.take(16))
                .put("type", mapping.controlType)
                .put("group", mapping.groupId)
                .put("direction", mapping.direction)
                .put("mouseButton", mapping.mouseButton))
        }
        return array
    }

    private fun decodeMappings(array: JSONArray): List<KeyMapping> {
        require(array.length() <= 100) { "Keymap có quá nhiều nút." }
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val key = item.getInt("key")
            require(key in 0..300) { "Mã phím không hợp lệ." }
            val x = item.getDouble("x").toFloat()
            val y = item.getDouble("y").toFloat()
            require(x.isFinite() && y.isFinite()) { "Tọa độ keymap không hợp lệ." }
            val rawType = item.optString("type", "tap")
            val type = when (rawType) {
                "joystick" -> "joystick"
                "mouse_button" -> "mouse_button"
                else -> "tap"
            }
            val direction = item.optString("direction", "")
            require(type != "joystick" || direction in setOf("up", "down", "left", "right")) { "Hướng joystick không hợp lệ." }
            val mouseButton = item.optString("mouseButton", "left").takeIf { it in setOf("left", "right", "middle") } ?: "left"
            KeyMapping(
                key, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), item.optString("label", "KEY").take(16),
                type, item.optString("group", ""), direction, mouseButton
            )
        }
    }

    fun defaults() = listOf(
        KeyMapping(KeyEvent.KEYCODE_W, .50f, .78f, "W"), KeyMapping(KeyEvent.KEYCODE_A, .42f, .85f, "A"),
        KeyMapping(KeyEvent.KEYCODE_S, .50f, .90f, "S"), KeyMapping(KeyEvent.KEYCODE_D, .58f, .85f, "D"),
        KeyMapping(KeyEvent.KEYCODE_SPACE, .88f, .82f, "SPACE")
    )
}
