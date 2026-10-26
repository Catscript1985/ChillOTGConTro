package com.chillotgcontro.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.chillotgcontro.profiles.MappingProfile

/** Lọc phím phần cứng và gửi thao tác chạm qua AccessibilityService.
 * Android/game có thể hạn chế lọc phím hoặc cử chỉ; giữ phím liên tục và camera mouse phụ thuộc game/thiết bị.
 */
class KeyMappingService : AccessibilityService() {
    private var enabled = false
    private var cameraMode = false
    private var screenW = 1
    private var screenH = 1

    override fun onServiceConnected() {
        enabled = getSharedPreferences("runtime", MODE_PRIVATE).getBoolean("mapping_enabled", false)
        screenW = resources.displayMetrics.widthPixels.coerceAtLeast(1)
        screenH = resources.displayMetrics.heightPixels.coerceAtLeast(1)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        enabled = getSharedPreferences("runtime", MODE_PRIVATE).getBoolean("mapping_enabled", false)
        if (!enabled || event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) return false
        if (event.keyCode == KeyEvent.KEYCODE_F8) {
            cameraMode = !cameraMode
            return true
        }
        if (cameraMode) return false
        val mapping = MappingProfile.load(this).firstOrNull { it.keyCode == event.keyCode } ?: return false
        tap(mapping.xRatio * screenW, mapping.yRatio * screenH)
        return true
    }

    private fun tap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < 24) return
        val path = Path().apply { moveTo(x.coerceIn(1f, screenW.toFloat()), y.coerceIn(1f, screenH.toFloat())) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 55)).build()
        dispatchGesture(gesture, null, null)
    }
}
