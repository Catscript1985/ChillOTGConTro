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
    private var mouseSettingsOverlay: View? = null
    private var selectedIndex: Int? = null
    private var waitingForPosition = false
    private var pendingComponentType = "tap"
    private var previousMappingEnabled = false
    private var editorScale = .85f
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
        editorScale = runtime.getFloat("editor_scale", .85f).coerceIn(.60f, 1.15f)
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
                    } else if (pendingComponentType == "mouse_button") {
                        val used = mappings.filter { it.controlType == "mouse_button" }.map { it.mouseButton }.toSet()
                        val button = listOf("left", "right", "middle").firstOrNull { it !in used }
                        if (button == null) {
                            Toast.makeText(service, "Đã dùng hết ba nút chuột trái/phải/giữa; xóa một nút trước.", Toast.LENGTH_LONG).show()
                            statusText?.text = "Mỗi nút chuột chỉ gán một vị trí · xóa một nút để thêm tiếp"
                        } else {
                            mappings = mappings + MappingProfile.createMouseButton(x, y, button)
                            MappingProfile.save(service, mappings, profile)
                            selectedIndex = mappings.lastIndex
                            runtime.edit().putString("editor_state", "idle").putString("editor_result", "Đã thêm ${MappingProfile.mouseButtonLabel(button)} · chạm nút rồi chọn bánh răng để cấu hình").apply()
                            statusText?.text = "Đã thêm ${MappingProfile.mouseButtonLabel(button)} · chọn nút để mở ⚙"
                            drawMarkers()
                        }
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
            setTextColor(Color.WHITE); textSize = 13f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(4), dp(2), dp(4), dp(4))
        }
        val titleRow = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        val scaleText = TextView(service).apply { setTextColor(0xFF55D6BE.toInt()); textSize = 10f; gravity = Gravity.CENTER; text = "${(editorScale * 100).toInt()}%" }
        fun applyScale() {
            bar.pivotX = bar.width / 2f
            bar.pivotY = 0f
            bar.scaleX = editorScale
            bar.scaleY = editorScale
            scaleText.text = "${(editorScale * 100).toInt()}%"
        }
        titleRow.addView(toolbarIconButton("−") {
            editorScale = (editorScale - .10f).coerceIn(.60f, 1.15f)
            runtime.edit().putFloat("editor_scale", editorScale).apply()
            applyScale()
        })
        titleRow.addView(scaleText, LinearLayout.LayoutParams(dp(40), dp(32)))
        titleRow.addView(toolbarIconButton("+") {
            editorScale = (editorScale + .10f).coerceIn(.60f, 1.15f)
            runtime.edit().putFloat("editor_scale", editorScale).apply()
            applyScale()
        })
        val content = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
        var expanded = true
        lateinit var collapseButton: TextView
        collapseButton = toolbarIconButton("▾") {
            expanded = !expanded
            content.visibility = if (expanded) View.VISIBLE else View.GONE
            collapseButton.text = if (expanded) "▾" else "▸"
        }
        titleRow.addView(collapseButton)
        bar.addView(titleRow)
        statusText = TextView(service).apply { setTextColor(0xFF55D6BE.toInt()); textSize = 11f; text = "Đang tải…"; setPadding(dp(4), 0, dp(4), dp(4)) }
        content.addView(statusText)

        val topRow = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(actionButton("Thoát", action = { close() }, weight = .7f))
        topRow.addView(actionButton("+ Nút chạm", action = { beginPlace("tap") }, weight = .95f))
        topRow.addView(actionButton("Nút chuột", action = { beginPlace("mouse_button") }, weight = .9f))
        topRow.addView(actionButton("Joystick WASD", action = { beginPlace("joystick") }, weight = 1.25f))
        topRow.addView(actionButton("Lưu", action = { saveCurrent() }, weight = .7f))
        content.addView(topRow)

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
        content.addView(editRow)

        val bottomRow = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        bottomRow.addView(actionButton("Chia sẻ", action = { shareCurrent() }, weight = 1f))
        bottomRow.addView(actionButton("Nhập keymap", action = { openKeymapLibrary() }, weight = 1f))
        content.addView(bottomRow)
        bar.addView(content)

        val barLp = FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        barLp.setMargins(dp(10), dp(28), dp(10), 0)
        frame.addView(bar, barLp)

        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(-1, -1, type, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START }
        root = frame
        runCatching { wm.addView(frame, params) }.onFailure {
            root = null
            Toast.makeText(service, "Không mở được editor nổi; kiểm tra quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show()
        }
        frame.post { applyScale(); drawMarkers() }
        handler.post(refresh)
    }

    private fun beginPlace(type: String) {
        waitingForPosition = true
        pendingComponentType = type
        selectedIndex = null
        val message = when (type) {
            "joystick" -> "Chạm tâm joystick trên màn hình game"
            "mouse_button" -> "Chạm vị trí nút chuột trên màn hình game"
            else -> "Chạm vị trí nút trên game"
        }
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
            if (mapping.controlType == "mouse_button") {
                drawMouseButtonMarker(layer, index, mapping)
                return@forEachIndexed
            }
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

    private fun drawMouseButtonMarker(layer: FrameLayout, index: Int, mapping: KeyMapping) {
        val width = dp(62)
        val height = dp(50)
        val marker = FrameLayout(service)
        val label = TextView(service).apply {
            text = MappingProfile.mouseButtonShortLabel(mapping.mouseButton)
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(if (selectedIndex == index) 0xFFFEA44A.toInt() else 0xFF168C80.toInt(), dp(15))
            elevation = dp(6).toFloat()
        }
        marker.addView(label, FrameLayout.LayoutParams(-1, -1))
        if (selectedIndex == index) {
            val gear = TextView(service).apply {
                text = "⚙"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = rounded(0xFF263C46.toInt(), dp(12))
                elevation = dp(8).toFloat()
                setOnClickListener { openMouseButtonSettings(index) }
            }
            marker.addView(gear, FrameLayout.LayoutParams(dp(25), dp(25), Gravity.TOP or Gravity.END))
        }

        val lp = FrameLayout.LayoutParams(width, height)
        lp.leftMargin = (mapping.xRatio * layer.width).toInt() - width / 2
        lp.topMargin = (mapping.yRatio * layer.height).toInt() - height / 2
        layer.addView(marker, lp)
        var downX = 0f; var downY = 0f; var startLeft = 0; var startTop = 0; var moved = false
        label.setOnTouchListener { _, event ->
            val currentLp = marker.layoutParams as FrameLayout.LayoutParams
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startLeft = currentLp.leftMargin; startTop = currentLp.topMargin; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (abs(dx) + abs(dy) > dp(4)) moved = true
                    if (moved) {
                        currentLp.leftMargin = (startLeft + dx.toInt()).coerceIn(0, (layer.width - width).coerceAtLeast(0))
                        currentLp.topMargin = (startTop + dy.toInt()).coerceIn(0, (layer.height - height).coerceAtLeast(0))
                        marker.layoutParams = currentLp
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val next = mappings.toMutableList()
                        next[index] = mapping.copy(
                            xRatio = ((currentLp.leftMargin + width / 2f) / layer.width).coerceIn(0f, 1f),
                            yRatio = ((currentLp.topMargin + height / 2f) / layer.height).coerceIn(0f, 1f)
                        )
                        mappings = next
                        MappingProfile.save(service, mappings, profile)
                    } else {
                        selectedIndex = index
                        statusText?.text = "Đã chọn ${mapping.label} · chạm ⚙ để cấu hình"
                        drawMarkers()
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun openMouseButtonSettings(index: Int) {
        val host = root ?: return
        val mapping = mappings.getOrNull(index)?.takeIf { it.controlType == "mouse_button" } ?: return
        mouseSettingsOverlay?.let { runCatching { host.removeView(it) } }
        var choice = mapping.mouseButton
        val options = linkedMapOf<String, TextView>()
        val scrim = FrameLayout(service).apply {
            setBackgroundColor(0x99000000.toInt())
        }
        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = rounded(0xFF13232B.toInt(), dp(20))
            elevation = dp(16).toFloat()
            setOnClickListener { }
        }
        card.addView(TextView(service).apply {
            text = "Cấu hình nút chuột"
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        card.addView(TextView(service).apply {
            text = "Chọn nút vật lý sẽ phát chạm tại vị trí này."
            textSize = 12f
            setTextColor(0xFFD6E1E5.toInt())
            setPadding(0, dp(5), 0, dp(10))
        })
        val labels = linkedMapOf("left" to "Chuột trái (L)", "right" to "Chuột phải (R)", "middle" to "Chuột giữa (M)")
        fun refreshOptions() {
            options.forEach { (id, view) ->
                view.text = (if (id == choice) "✓  " else "    ") + labels.getValue(id)
                view.setTextColor(if (id == choice) 0xFF55D6BE.toInt() else Color.WHITE)
                view.background = rounded(if (id == choice) 0xFF263F45.toInt() else 0xFF26343D.toInt(), dp(12))
            }
        }
        labels.forEach { (id, label) ->
            val option = TextView(service).apply {
                text = label
                textSize = 14f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                layoutParams = LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(4) }
                setOnClickListener { choice = id; refreshOptions() }
            }
            options[id] = option
            card.addView(option)
        }
        refreshOptions()
        val saveButton = TextView(service).apply {
            text = "Lưu"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(0xFF08756B.toInt(), dp(12))
            layoutParams = LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(12) }
            setOnClickListener {
                val duplicate = mappings.indices.any { it != index && mappings[it].controlType == "mouse_button" && mappings[it].mouseButton == choice }
                if (duplicate) {
                    Toast.makeText(service, "Nút ${MappingProfile.mouseButtonLabel(choice)} đã được gán ở vị trí khác.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val updated = mappings.toMutableList()
                updated[index] = mapping.copy(
                    keyCode = MappingProfile.mouseButtonKeyCode(choice),
                    label = MappingProfile.mouseButtonLabel(choice),
                    mouseButton = choice
                )
                mappings = updated
                MappingProfile.save(service, mappings, profile, synchronous = true)
                host.removeView(scrim)
                mouseSettingsOverlay = null
                selectedIndex = index
                statusText?.text = "Đã lưu ${MappingProfile.mouseButtonLabel(choice)} vào keymap"
                drawMarkers()
            }
        }
        card.addView(saveButton)
        val cancelButton = TextView(service).apply {
            text = "Hủy"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFFD6E1E5.toInt())
            layoutParams = LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(4) }
            setOnClickListener { host.removeView(scrim); mouseSettingsOverlay = null }
        }
        card.addView(cancelButton)
        scrim.setOnClickListener { host.removeView(scrim); mouseSettingsOverlay = null }
        scrim.addView(card, FrameLayout.LayoutParams(dp(310), -2, Gravity.CENTER))
        host.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        mouseSettingsOverlay = scrim
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

    private fun toolbarIconButton(label: String, action: () -> Unit) = TextView(service).apply {
        text = label
        textSize = 16f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = rounded(0xFF263C46.toInt(), dp(10))
        layoutParams = LinearLayout.LayoutParams(dp(34), dp(32)).apply { setMargins(dp(2), 0, dp(2), dp(2)) }
        setOnClickListener { action() }
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
        val adbConnected = service.getSharedPreferences("adb_config", Service.MODE_PRIVATE).getBoolean("connected", false)
        val shizukuMode = runtime.getBoolean("shizuku_mode", false)
        runtime.edit().putString("editor_state", "idle").putBoolean("mapping_enabled", previousMappingEnabled)
            .putBoolean("adb_mode", previousMappingEnabled && adbConnected && !shizukuMode)
            .putBoolean("shizuku_mode", previousMappingEnabled && shizukuMode).apply()
        runCatching { wm.removeView(view) }
        if (!previousMappingEnabled && (adbConnected || shizukuMode)) {
            AdbInputMappingService.stop(service)
        }
        root = null; markerLayer = null; statusText = null; mouseSettingsOverlay = null; selectedIndex = null; waitingForPosition = false
        onClose()
    }

    fun hide() { if (root != null) close() }
    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }
}
