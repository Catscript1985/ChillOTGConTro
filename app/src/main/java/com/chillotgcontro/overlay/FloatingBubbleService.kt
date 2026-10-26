package com.chillotgcontro.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
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
import com.chillotgcontro.R

class FloatingBubbleService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotice()
        if (Settings.canDrawOverlays(this)) showBubble()
    }

    private fun startForegroundNotice() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("chillotg_overlay", "Điều khiển nổi", NotificationManager.IMPORTANCE_LOW))
        val notification = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "chillotg_overlay") else Notification.Builder(this)
        notification.setContentTitle("ChillOTGConTro đang sẵn sàng").setContentText("Chạm nút nổi để mở điều khiển").setSmallIcon(android.R.drawable.ic_media_play)
        startForeground(42, notification.build())
    }

    private fun showBubble() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val tv = TextView(this).apply {
            text = "OTG"; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(16, 127, 115)); elevation = 10f
            val p = (42 * resources.displayMetrics.density).toInt(); layoutParams = LinearLayout.LayoutParams(p, p)
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams((48 * resources.displayMetrics.density).toInt(), (48 * resources.displayMetrics.density).toInt(), type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.END; x = 18; y = 180 }
        var sx = 0f; var sy = 0f; var startX = 0; var startY = 0
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; startX = params.x; startY = params.y; true }
                MotionEvent.ACTION_MOVE -> { params.x = startX - (e.rawX - sx).toInt(); params.y = startY + (e.rawY - sy).toInt(); wm.updateViewLayout(tv, params); true }
                MotionEvent.ACTION_UP -> { if (kotlin.math.abs(e.rawX-sx) < 10 && kotlin.math.abs(e.rawY-sy) < 10) showPanel(); true }
                else -> true
            }
        }
        bubble = tv
        runCatching { wm.addView(tv, params) }.onFailure { Toast.makeText(this, "Không thể hiện nút nổi: hãy cấp quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show(); stopSelf() }
    }

    private fun showPanel() {
        panel?.let { runCatching { wm.removeView(it) }; panel = null; return }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 14, 20, 14); setBackgroundColor(Color.rgb(24, 36, 44)) }
        box.addView(TextView(this).apply { text = "ChillOTG · Điều khiển"; textSize = 17f; setTextColor(Color.WHITE); setPadding(6, 4, 6, 8) })
        box.addView(Button(this).apply { text = "Bật / Tắt ánh xạ phím"; setOnClickListener {
            val prefs = getSharedPreferences("runtime", MODE_PRIVATE); val next = !prefs.getBoolean("mapping_enabled", false); prefs.edit().putBoolean("mapping_enabled", next).apply()
            Toast.makeText(this@FloatingBubbleService, if (next) "Ánh xạ đã bật" else "Ánh xạ đã tắt", Toast.LENGTH_SHORT).show(); showPanel()
        } })
        box.addView(Button(this).apply { text = "Cài đặt / cấu hình"; setOnClickListener { startActivity(Intent(this@FloatingBubbleService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); showPanel() } })
        box.addView(Button(this).apply { text = "Đóng"; setOnClickListener { showPanel() } })
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        panel = box; runCatching { wm.addView(box, params) }.onFailure { panel = null }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() { listOfNotNull(panel, bubble).forEach { runCatching { wm.removeView(it) } }; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
