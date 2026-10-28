package com.chillotgcontro.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.chillotgcontro.MainActivity
import com.chillotgcontro.adb.AdbInputMappingService

class FloatingBubbleService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private var editor: MappingEditorOverlay? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotice()
        if (Settings.canDrawOverlays(this)) showBubble() else stopSelf()
    }

    private fun startForegroundNotice() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("chillotg_overlay", "Điều khiển nổi", NotificationManager.IMPORTANCE_LOW))
        val notification = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "chillotg_overlay") else Notification.Builder(this)
        notification.setContentTitle("ChillOTGConTro đang sẵn sàng").setContentText("Chạm nút nổi để thêm hoặc sửa phím").setSmallIcon(android.R.drawable.ic_media_play)
        startForeground(42, notification.build())
    }

    private fun showBubble() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = dp(48)
        val tv = TextView(this).apply {
            text = "G"; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF20B5A2.toInt(), 0xFF08756B.toInt())).apply { shape = GradientDrawable.OVAL }
            elevation = dp(8).toFloat()
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(size, size, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = dp(18); y = dp(180) }
        var sx = 0f; var sy = 0f; var startX = 0; var startY = 0
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; startX = params.x; startY = params.y; true }
                MotionEvent.ACTION_MOVE -> { params.x = startX - (e.rawX - sx).toInt(); params.y = startY + (e.rawY - sy).toInt(); wm.updateViewLayout(tv, params); true }
                MotionEvent.ACTION_UP -> { if (kotlin.math.abs(e.rawX - sx) < dp(10) && kotlin.math.abs(e.rawY - sy) < dp(10)) showPanel(); true }
                else -> true
            }
        }
        bubble = tv
        runCatching { wm.addView(tv, params) }.onFailure { Toast.makeText(this, "Không thể hiện nút nổi: hãy cấp quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show(); stopSelf() }
    }

    private fun showPanel() {
        panel?.let { runCatching { wm.removeView(it) }; panel = null; return }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            background = GradientDrawable().apply { setColor(0xF2182730.toInt()); cornerRadius = dp(22).toFloat(); setStroke(dp(1), 0xFF55D6BE.toInt()) }
            elevation = dp(12).toFloat()
        }
        box.addView(TextView(this).apply { text = "CHILLOTG  ·  BẢN ĐỒ PHÍM"; textSize = 15f; setTextColor(0xFF55D6BE.toInt()); setPadding(dp(8), dp(6), dp(8), dp(10)); setTypeface(null, android.graphics.Typeface.BOLD) })
        box.addView(TextView(this).apply { text = "Chọn một thao tác để chỉnh điều khiển game"; textSize = 12f; setTextColor(0xFFD6E1E5.toInt()); setPadding(dp(8), 0, dp(8), dp(8)) })
        box.addView(panelButton("＋  Thêm nút phím") { openEditor("add") })
        box.addView(panelButton("✎  Sửa / kéo nút hiện có") { openEditor("edit") })
        box.addView(panelButton("⏻  Bật / tắt ánh xạ") {
            val prefs = getSharedPreferences("runtime", MODE_PRIVATE)
            val next = !prefs.getBoolean("mapping_enabled", false)
            val adbReady = getSharedPreferences("adb_config", MODE_PRIVATE).getBoolean("connected", false)
            if (next && adbReady) {
                prefs.edit().putBoolean("mapping_enabled", true).putBoolean("adb_mode", true).apply()
                AdbInputMappingService.start(this)
                Toast.makeText(this, "Ánh xạ Wi‑Fi ADB đã bật", Toast.LENGTH_SHORT).show()
                showPanel()
            } else if (next) {
                Toast.makeText(this, "Ghép Wi‑Fi ADB trong Cài đặt trước khi bật ánh xạ", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                dismissPanel()
            } else {
                prefs.edit().putBoolean("mapping_enabled", false).putBoolean("adb_mode", false).apply()
                AdbInputMappingService.stop(this)
                Toast.makeText(this, if (next) "Ánh xạ đã bật" else "Ánh xạ đã tắt", Toast.LENGTH_SHORT).show()
                showPanel()
            }
        })
        box.addView(panelButton("⚙  Cài đặt ChillOTG") { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); dismissPanel() })
        box.addView(panelButton("▤  Lưu / tải keymap") {
            startActivity(Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_KEYMAP_LIBRARY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            dismissPanel()
        })
        box.addView(panelButton("Ẩn bong bóng nổi") {
            dismissPanel()
            Toast.makeText(this, "Đã tắt bong bóng · bật lại trong ChillOTG", Toast.LENGTH_SHORT).show()
            stopSelf()
        })
        box.addView(panelButton("Đóng menu") { dismissPanel() })
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams((resources.displayMetrics.widthPixels * .88f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        panel = box
        runCatching { wm.addView(box, params) }.onFailure { panel = null }
    }

    private fun openEditor(mode: String) {
        dismissPanel()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Cần cấp quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val adbReady = getSharedPreferences("adb_config", MODE_PRIVATE).getBoolean("connected", false)
        if (adbReady) {
            getSharedPreferences("runtime", MODE_PRIVATE).edit().putBoolean("adb_mode", true).apply()
            AdbInputMappingService.start(this)
        } else {
            Toast.makeText(this, "Ghép Wi‑Fi ADB trong Cài đặt để nhận phím trong editor", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        editor = MappingEditorOverlay(this) { editor = null }
        editor?.show(mode)
    }

    private fun panelButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply { setColor(0xFF263C46.toInt()); cornerRadius = dp(13).toFloat() }
        setPadding(dp(8), dp(4), dp(8), dp(4))
        val h = dp(48)
        layoutParams = LinearLayout.LayoutParams(-1, h).apply { setMargins(dp(2), dp(3), dp(2), dp(3)) }
        setOnClickListener { action() }
    }

    private fun dismissPanel() { panel?.let { runCatching { wm.removeView(it) } }; panel = null }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() {
        editor?.hide(); editor = null
        if (::wm.isInitialized) listOfNotNull(panel, bubble).forEach { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
