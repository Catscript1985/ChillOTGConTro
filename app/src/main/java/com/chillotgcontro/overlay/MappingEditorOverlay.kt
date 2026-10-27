package com.chillotgcontro.overlay

import android.app.Service
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.chillotgcontro.profiles.KeyMapping
import com.chillotgcontro.profiles.MappingProfile
import com.chillotgcontro.adb.AdbInputMappingService
import kotlin.math.abs

/** Lightweight on-game editor. Key presses are captured by the user-paired Wireless ADB monitor. */
class MappingEditorOverlay(private val service: Service, private val onClose: () -> Unit) {
    private val wm = service.getSystemService(Service.WINDOW_SERVICE) as WindowManager
    private val runtime by lazy { service.getSharedPreferences("runtime", Service.MODE_PRIVATE) }
    private var root: FrameLayout? = null
    private var markerLayer: FrameLayout? = null
    private var statusText: TextView? = null
    private var selectedIndex: Int? = null
    private var waitingForPosition = false
    private var previousMappingEnabled = false
    private var profile = "Mặc định"
    private var mappings = emptyList<KeyMapping>()
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            val state = runtime.getString("editor_state", "idle") ?: "idle"
            statusText?.text = when (state) {
                "await_new_key" -> "Đã chọn vị trí · nhấn phím OTG để thêm"
                "await_edit_key" -> "Đang đổi phím · nhấn phím OTG mới"
                else -> runtime.getString("editor_result", "Chạm + Thêm nút, rồi đặt vị trí trên game")
            }
            val latest = MappingProfile.load(service, profile)
            if (latest != mappings) {
                mappings = latest
                drawMarkers()
            }
            handler.postDelayed(this, 350)
        }
    }

    fun show(mode: String = "edit") {
        if (root != null) return
        profile = service.getSharedPreferences("profiles", Service.MODE_PRIVATE).getString("selected", "Mặc định") ?: "Mặc định"
        mappings = MappingProfile.load(service, profile)
        previousMappingEnabled = runtime.getBoolean("mapping_enabled", false)
        runtime.edit().putBoolean("mapping_enabled", false).putString("editor_state", "idle")
            .putString("editor_result", if (mode == "add") "Chạm + Thêm nút, rồi chạm vị trí cần gán" else "Chạm một nút để chọn · kéo nút để đổi vị trí").apply()

        val frame = FrameLayout(service).apply { setBackgroundColor(0x26000000) }
        val tapLayer = View(service).apply {
            setOnTouchListener { _, event ->
                if (!waitingForPosition) return@setOnTouchListener false
                if (event.action == MotionEvent.ACTION_UP) {
                    val current = root ?: return@setOnTouchListener true
                    val x = (event.x / current.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val y = (event.y / current.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    waitingForPosition = false
                    runtime.edit().putFloat("editor_x", x).putFloat("editor_y", y)
                        .putString("editor_state", "await_new_key").putString("editor_result", "").apply()
                    statusText?.text = "Đã chọn vị trí · nhấn phím OTG để thêm"
                }
                true
            }
        }
        frame.addView(tapLayer, FrameLayout.LayoutParams(-1, -1))
        val markers = FrameLayout(service)
        markerLayer = markers
        frame.addView(markers, FrameLayout.LayoutParams(-1, -1))

        val bar = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(0xEE13232B.toInt(), dp(18))
            elevation = dp(8).toFloat()
        }
        val title = TextView(service).apply {
            text = "CHILLOTG  ·  ${if (mode == "add") "THÊM NÚT" else "SỬA NÚT"}"
            setTextColor(Color.WHITE); textSize = 15f; setPadding(dp(4), dp(2), dp(4), dp(6))
        }
        bar.addView(title)
        statusText = TextView(service).apply { setTextColor(0xFF55D6BE.toInt()); textSize = 12f; text = "Đang tải…"; setPadding(dp(4), 0, dp(4), dp(6)) }
        bar.addView(statusText)
        val row = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(actionButton("+ Thêm nút", action = {
            waitingForPosition = true
            selectedIndex = null
            runtime.edit().putString("editor_state", "idle").putString("editor_result", "Chạm vị trí nút trên game").apply()
            statusText?.text = "Chạm vị trí nút trên game"
        }, weight = 1f))
        row.addView(actionButton("Đổi phím", action = {
            val index = selectedIndex
            if (index == null) Toast.makeText(service, "Chạm vào nút muốn sửa trước", Toast.LENGTH_SHORT).show()
            else runtime.edit().putInt("editor_index", index).putString("editor_state", "await_edit_key").putString("editor_result", "").apply()
        }, weight = 1f))
        row.addView(actionButton("Xóa", action = {
            val index = selectedIndex
            if (index == null || index !in mappings.indices) Toast.makeText(service, "Chạm vào nút muốn xóa trước", Toast.LENGTH_SHORT).show()
            else {
                mappings = mappings.toMutableList().also { it.removeAt(index) }
                MappingProfile.save(service, mappings, profile)
                selectedIndex = null
                runtime.edit().putString("editor_result", "Đã xóa nút").apply()
                drawMarkers()
            }
        }, weight = 0.8f))
        row.addView(actionButton("Xong", action = { close() }, weight = 0.8f))
        bar.addView(row)
        val barLp = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        barLp.setMargins(dp(10), dp(30), dp(10), 0)
        frame.addView(bar, barLp)

        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(-1, -1, type, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        root = frame
        runCatching { wm.addView(frame, params) }.onFailure {
            root = null
            Toast.makeText(service, "Không mở được editor nổi; kiểm tra quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show()
        }
        frame.post { drawMarkers() }
        handler.post(refresh)
    }

    private fun drawMarkers() {
        val layer = markerLayer ?: return
        if (layer.width <= 0 || layer.height <= 0) { layer.post { drawMarkers() }; return }
        layer.removeAllViews()
        mappings.forEachIndexed { index, mapping ->
            val size = dp(48)
            val marker = TextView(service).apply {
                text = mapping.label
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = rounded(if (selectedIndex == index) 0xFFFEA44A.toInt() else 0xFF168C80.toInt(), size / 2)
                elevation = dp(6).toFloat()
            }
            val lp = FrameLayout.LayoutParams(size, size)
            lp.leftMargin = (mapping.xRatio * layer.width).toInt() - size / 2
            lp.topMargin = (mapping.yRatio * layer.height).toInt() - size / 2
            layer.addView(marker, lp)
            var downX = 0f; var downY = 0f; var startLeft = 0; var startTop = 0; var moved = false
            marker.setOnTouchListener { _, event ->
                val currentLp = marker.layoutParams as FrameLayout.LayoutParams
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startLeft = currentLp.leftMargin; startTop = currentLp.topMargin; moved = false; true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX; val dy = event.rawY - downY
                        if (abs(dx) + abs(dy) > dp(4)) moved = true
                        if (moved) {
                            currentLp.leftMargin = (startLeft + dx.toInt()).coerceIn(0, (layer.width - size).coerceAtLeast(0))
                            currentLp.topMargin = (startTop + dy.toInt()).coerceIn(0, (layer.height - size).coerceAtLeast(0))
                            marker.layoutParams = currentLp
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (moved) {
                            val next = mappings.toMutableList()
                            next[index] = mapping.copy(xRatio = ((currentLp.leftMargin + size / 2f) / layer.width).coerceIn(0f, 1f), yRatio = ((currentLp.topMargin + size / 2f) / layer.height).coerceIn(0f, 1f))
                            mappings = next
                            MappingProfile.save(service, mappings, profile)
                        } else {
                            selectedIndex = index
                            statusText?.text = "Đã chọn ${mapping.label} · kéo để di chuyển"
                            drawMarkers()
                        }
                        true
                    }
                    else -> true
                }
            }
        }
    }

    private fun actionButton(label: String, action: () -> Unit, weight: Float) = Button(service).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        minHeight = dp(42)
        setPadding(dp(4), 0, dp(4), 0)
        layoutParams = LinearLayout.LayoutParams(0, dp(44), weight).apply { setMargins(dp(2), 0, dp(2), 0) }
        setOnClickListener { action() }
    }

    private fun close() {
        val view = root ?: return
        handler.removeCallbacks(refresh)
        runtime.edit().putString("editor_state", "idle").putBoolean("mapping_enabled", previousMappingEnabled)
            .putBoolean("adb_mode", previousMappingEnabled && service.getSharedPreferences("adb_config", Service.MODE_PRIVATE).getBoolean("connected", false)).apply()
        runCatching { wm.removeView(view) }
        if (!previousMappingEnabled && service.getSharedPreferences("adb_config", Service.MODE_PRIVATE).getBoolean("connected", false)) {
            AdbInputMappingService.stop(service)
        }
        root = null; markerLayer = null; statusText = null; selectedIndex = null; waitingForPosition = false
        onClose()
    }

    fun hide() { if (root != null) close() }
    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
}
