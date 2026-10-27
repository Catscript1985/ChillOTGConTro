package com.chillotgcontro.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.chillotgcontro.profiles.KeyMapping
import com.chillotgcontro.profiles.MappingProfile

/** Receives hardware key events and sends user-configured taps through Android Accessibility. */
class KeyMappingService : AccessibilityService() {
    private var screenW = 1
    private var screenH = 1

    override fun onServiceConnected() {
        updateScreenSize()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        val runtime = getSharedPreferences("runtime", MODE_PRIVATE)
        val editState = runtime.getString("editor_state", "idle") ?: "idle"
        if (editState == "await_new_key" || editState == "await_edit_key") return captureEditorKey(event, editState)
        if (!runtime.getBoolean("mapping_enabled", false)) return false
        updateScreenSize()
        val profile = getSharedPreferences("profiles", MODE_PRIVATE).getString("selected", "Mặc định") ?: "Mặc định"
        val mapping = MappingProfile.load(this, profile).firstOrNull { it.keyCode == event.keyCode } ?: return false
        tap(mapping.xRatio * screenW, mapping.yRatio * screenH)
        return true
    }

    private fun captureEditorKey(event: KeyEvent, editState: String): Boolean {
        val forbidden = setOf(KeyEvent.KEYCODE_UNKNOWN, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_POWER)
        if (event.keyCode in forbidden || event.isSystem) return false
        val profile = getSharedPreferences("profiles", MODE_PRIVATE).getString("selected", "Mặc định") ?: "Mặc định"
        val list = MappingProfile.load(this, profile).toMutableList()
        val label = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
        if (editState == "await_new_key") {
            val runtime = getSharedPreferences("runtime", MODE_PRIVATE)
            val x = runtime.getFloat("editor_x", 0.5f)
            val y = runtime.getFloat("editor_y", 0.5f)
            list.add(KeyMapping(event.keyCode, x, y, label))
        } else {
            val index = getSharedPreferences("runtime", MODE_PRIVATE).getInt("editor_index", -1)
            if (index !in list.indices) return false
            list[index] = list[index].copy(keyCode = event.keyCode, label = label)
        }
        MappingProfile.save(this, list, profile)
        getSharedPreferences("runtime", MODE_PRIVATE).edit()
            .putString("editor_state", "idle")
            .putString("editor_result", "Đã gán phím $label")
            .apply()
        Toast.makeText(this, "Đã gán phím $label", Toast.LENGTH_SHORT).show()
        return true
    }

    private fun updateScreenSize() {
        screenW = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        screenH = resources.displayMetrics.heightPixels.coerceAtLeast(1)
    }

    private fun tap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < 24) return
        val path = Path().apply { moveTo(x.coerceIn(1f, screenW.toFloat()), y.coerceIn(1f, screenH.toFloat())) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 55)).build()
        dispatchGesture(gesture, null, null)
    }
}
