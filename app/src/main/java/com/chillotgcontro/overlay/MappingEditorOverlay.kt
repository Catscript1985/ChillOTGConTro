package com.chillotgcontro.overlay

import android.app.Service
import android.content.Intent
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
import com.chillotgcontro.MainActivity
import com.chillotgcontro.adb.AdbInputMappingService
import com.chillotgcontro.profiles.KeyMapping
import com.chillotgcontro.profiles.MappingProfile
import kotlin.math.abs

/** In-game editor for tap buttons and a four-direction virtual joystick. */
class MappingEditorOverlay(private val service: Service, private val onClose: () -> Unit) {
    private val wm = service.getSystemService(Service.WINDOW_SERVICE) as WindowManager
    private val runtime by lazy { service.getSharedPreferences("runtime", Service.MODE_PRIVATE) }
    private var root: FrameLayout? = null
    private var markerLayer: FrameLayout? = null
    private var statusText: TextView? = null
    private var selectedIndex: Int? = null
    private var waitingForPosition = false
    private var pendingComponentType = "tap"
    private var previousMappingEnabled = false
    private var profile = "Mặc định"
    private var mappings = emptyList<KeyMapping>()
    private val handler = Handler(Looper.getMainLooper())
    private val refresh = object : Runnable {
        override fun run() {
            val state = runtime.getString("editor_state", "idle") ?: "idle"
            statusText?.text = when (state) {
                "await_new_key" -> "Đã chọn vị trí · nhấn phím OTG để thêm nút"
                "await_edit_key" -> "Đang đổi phím · nhấn phím OTG mới"
                else -> runtime.getString("editor_result", "Thêm thành phần, đặt vị trí rồi Lưu keymap")
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
            .putString("editor_result", if (mode == "add") "Chọn thành phần rồi chạm vị trí cần đặt" else "Chạm nút để chọn · kéo joystick/nút để di chuyển").apply()

        val frame = FrameLayout(service).apply { setBackgroundColor(0x26000000) }
        val tapLayer = View(service).apply {
            setOnTouchListener { _, event ->
                if (!waitingForPosition) return@setOnTouchListener false
                if (event.action == MotionEvent.ACTION_UP) {
                    val current = root ?: return@setOnTouchListener true
                    val x = (event.x / current.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    val y = (event.y / current.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                    waitingForPosition = false
                    if (pendingComponentType == "joystick") {
                        val newJoystick = MappingProfile.createJoystick(x, y)
                        val movementKeys = setOf(android.view.KeyEvent.KEYCODE_W, android.view.KeyEvent.KEYCODE_A, android.view.KeyEvent.KEYCODE_S, android.view.KeyEvent.KEYCODE_D)
                        mappings = mappings.filterNot { it.controlType == "joystick" || (it.controlType == "tap" && it.keyCode in movementKeys) } + newJoystick
                        MappingProfile.save(service, mappings, profile)
                        selectedIndex = mappings.lastIndex - 3
                        runtime.edit().putString("editor_state", "idle").putString("editor_result", "Đã thêm joystick WASD · chọn hướng để đổi phím").apply()
                        statusText?.text = "Joystick đã thêm · kéo vòng tròn để đặt vị trí"
                        drawMarkers()
                    } else {
                        runtime.edit().putFloat("editor_x", x).putFloat("editor_y", y)
                            .putString("editor_state", "await_new_key").putString("editor_result", "").apply()
                        statusText?.text = "Đã chọn vị trí · nhấn phím OTG để thêm"
                    }
                    pendingComponentType = "tap"
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
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(0xEE13232B.toInt(), dp(18))
            elevation = dp(8).toFloat()
        }
        val title = TextView(service).apply {
            text = "CHILLOTG  ·  ${profile.takeLast(24)}"
            setTextColor(Color.WHITE); textSize = 13f; setPadding(dp(4), dp(2), dp(4), dp(4))
        }
        bar.addView(title)
        statusText = TextView(service).apply { setTextColor(0xFF55D6BE.toInt()); textSize = 11f; text = "Đang tải…"; setPadding(dp(4), 0, dp(4), dp(4)) }
        bar.addView(statusText)

        val topRow = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(actionButton("Thoát", action = { close() }, weight = .7f))
        topRow.addView(actionButton("+ Nút chạm", action = { beginPlace("tap") }, weight = 1f))
        topRow.addView(actionButton("Joystick WASD", action = { beginPlace("joystick") }, weight = 1.35f))
        topRow.addView(actionButton("Lưu", action = { saveCurrent() }, weight = .7f))
        bar.addView(topRow)

        val editRow = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        editRow.addView(actionButton("Đổi phím", action = {
            val index = selectedIndex
            if (index == null || index !in mappings.indices) Toast.makeText(service, "Chạm nút hoặc hướng joystick muốn đổi trước", Toast.LENGTH_SHORT).show()
            else runtime.edit().putInt("editor_index", index).putString("editor_state", "await_edit_key").putString("editor_result", "").apply()
        }, weight = 1f))
        editRow.addView(actionButton("Xóa", action = {
            val index = selectedIndex
            if (index == null || index !in mappings.indices) Toast.makeText(service, "Chạm nút hoặc hướng joystick muốn xóa trước", Toast.LENGTH_SHORT).show()
            else {
                mappings = mappings.toMutableList().also { it.removeAt(index) }
                MappingProfile.save(service, mappings, profile)
                selectedIndex = null
                runtime.edit().putString("editor_result", "Đã xóa thành phần").apply()
                drawMarkers()
            }
        }, weight = 1f))
        bar.addView(editRow)

        val bottomRow = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        bottomRow.addView(actionButton("Chia sẻ", action = { shareCurrent() }, weight = 1f))
        bottomRow.addView(actionButton("Nhập keymap", action = { openKeymapLibrary() }, weight = 1f))
        bar.addView(bottomRow)

        val barLp = FrameLayout.LayoutParams(-1, -2, Gravity.TOP)
        barLp.setMargins(dp(10), dp(28), dp(10), 0)
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

    private fun beginPlace(type: String) {
        waitingForPosition = true
        pendingComponentType = type
        selectedIndex = null
        val message = if (type == "joystick") "Chạm tâm joystick trên màn hình game" else "Chạm vị trí nút trên game"
        runtime.edit().putString("editor_state", "idle").putString("editor_result", message).apply()
        statusText?.text = message
    }

    private fun saveCurrent() {
        MappingProfile.save(service, mappings, profile, synchronous = true)
        runtime.edit().putString("editor_result", "Đã lưu keymap cho game này").apply()
        statusText?.text = "Đã lưu keymap cho game này"
        Toast.makeText(service, "Đã lưu keymap · ${profile.takeLast(28)}", Toast.LENGTH_SHORT).show()
    }

    private fun shareCurrent() {
        saveCurrent()
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "ChillOTG keymap · $profile")
            putExtra(Intent.EXTRA_TEXT, MappingProfile.exportJson(profile, mappings))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { service.startActivity(Intent.createChooser(share, "Chia sẻ keymap").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(service, "Không mở được menu chia sẻ", Toast.LENGTH_SHORT).show() }
    }

    private fun openKeymapLibrary() {
        saveCurrent()
        runCatching {
            service.startActivity(Intent(service, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_KEYMAP_LIBRARY, true)
                .putExtra(MainActivity.EXTRA_IMPORT_KEYMAP_NOW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }.onFailure { Toast.makeText(service, "Không mở được thư viện keymap", Toast.LENGTH_SHORT).show() }
    }

    private fun drawMarkers() {
        val layer = markerLayer ?: return
        if (layer.width <= 0 || layer.height <= 0) { layer.post { drawMarkers() }; return }
        layer.removeAllViews()
        val joystickIndices = mappings.indices.filter { mappings[it].controlType == "joystick" }.toSet()
        val joystickGroups = mappings.indices.filter { mappings[it].controlType == "joystick" }
            .groupBy { mappings[it].groupId.ifBlank { "missing-${mappings[it].keyCode}-${it}" } }
        joystickGroups.values.forEach { indices -> drawJoystick(layer, indices) }
        mappings.forEachIndexed { index, mapping ->
            if (index in joystickIndices) return@forEachIndexed
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

    private fun drawJoystick(layer: FrameLayout, indices: List<Int>) {
        if (indices.isEmpty()) return
        val first = mappings[indices.first()]
        val size = dp(132)
        val selected = selectedIndex?.let { it in indices } == true
        val circle = FrameLayout(service).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x55233454)
                setStroke(dp(if (selected) 3 else 2), if (selected) 0xFFFFB24A.toInt() else Color.WHITE)
            }
            elevation = dp(7).toFloat()
        }
        fun addDirection(direction: String, symbol: String, positionGravity: Int) {
            val index = indices.firstOrNull { mappings[it].direction == direction } ?: return
            val item = mappings[index]
            val label = TextView(service).apply {
                text = "$symbol\n${item.label}"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(if (selectedIndex == index) 0xFFFFC46A.toInt() else Color.WHITE)
                background = rounded(if (selectedIndex == index) 0xAA6C4A25.toInt() else 0x66566BD7, dp(18))
                elevation = dp(3).toFloat()
            }
            val lp = FrameLayout.LayoutParams(dp(40), dp(40), positionGravity)
            when (positionGravity) {
                Gravity.TOP or Gravity.CENTER_HORIZONTAL -> lp.topMargin = dp(5)
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL -> lp.bottomMargin = dp(5)
                Gravity.CENTER_VERTICAL or Gravity.START -> lp.leftMargin = dp(5)
                Gravity.CENTER_VERTICAL or Gravity.END -> lp.rightMargin = dp(5)
            }
            circle.addView(label, lp)
        }
        addDirection("up", "↑", Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        addDirection("down", "↓", Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        addDirection("left", "←", Gravity.CENTER_VERTICAL or Gravity.START)
        addDirection("right", "→", Gravity.CENTER_VERTICAL or Gravity.END)
        circle.addView(View(service).apply { background = rounded(0xFF6577F3.toInt(), dp(9)) }, FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER))

        val lp = FrameLayout.LayoutParams(size, size)
        lp.leftMargin = (first.xRatio * layer.width).toInt() - size / 2
        lp.topMargin = (first.yRatio * layer.height).toInt() - size / 2
        layer.addView(circle, lp)
        var downX = 0f; var downY = 0f; var startLeft = 0; var startTop = 0; var moved = false
        circle.setOnTouchListener { _, event ->
            val currentLp = circle.layoutParams as FrameLayout.LayoutParams
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startLeft = currentLp.leftMargin; startTop = currentLp.topMargin; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (abs(dx) + abs(dy) > dp(4)) moved = true
                    if (moved) {
                        currentLp.leftMargin = (startLeft + dx.toInt()).coerceIn(0, (layer.width - size).coerceAtLeast(0))
                        currentLp.topMargin = (startTop + dy.toInt()).coerceIn(0, (layer.height - size).coerceAtLeast(0))
                        circle.layoutParams = currentLp
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val x = ((currentLp.leftMargin + size / 2f) / layer.width).coerceIn(0f, 1f)
                        val y = ((currentLp.topMargin + size / 2f) / layer.height).coerceIn(0f, 1f)
                        mappings = mappings.mapIndexed { index, item -> if (index in indices) item.copy(xRatio = x, yRatio = y) else item }
                        MappingProfile.save(service, mappings, profile)
                    } else {
                        val dx = event.x - size / 2f; val dy = event.y - size / 2f
                        val direction = if (abs(dx) > abs(dy)) if (dx < 0) "left" else "right" else if (dy < 0) "up" else "down"
                        selectedIndex = indices.firstOrNull { mappings[it].direction == direction } ?: indices.first()
                        statusText?.text = "Đã chọn hướng $direction · Đổi phím để gán lại"
                        drawMarkers()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun actionButton(label: String, action: () -> Unit, weight: Float) = Button(service).apply {
        text = label
        isAllCaps = false
        textSize = 11f
        minHeight = dp(38)
        setPadding(dp(3), 0, dp(3), 0)
        layoutParams = LinearLayout.LayoutParams(0, dp(40), weight).apply { setMargins(dp(2), 0, dp(2), dp(2)) }
        setOnClickListener { action() }
    }

    private fun close() {
        val view = root ?: return
        handler.removeCallbacks(refresh)
        MappingProfile.save(service, mappings, profile, synchronous = true)
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
