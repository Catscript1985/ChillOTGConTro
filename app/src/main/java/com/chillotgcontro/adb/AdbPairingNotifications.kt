package com.chillotgcontro.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.chillotgcontro.MainActivity
import java.util.concurrent.Executors

object AdbPairingNotifications {
    const val ACTION_PAIR = "com.chillotgcontro.action.PAIR_WIFI_ADB"
    const val EXTRA_CODE = "pairing_code"
    private const val CHANNEL_ID = "chillotg_adb_pair"
    private const val NOTIFICATION_ID = 742
    private const val RESULT_ID = 743
    private val executor = Executors.newSingleThreadExecutor()

    fun sendPairPrompt(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Ghép nối Wi‑Fi ADB", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Nhập mã ghép nối Wireless debugging do Android hiển thị"
            })
        }
        val prefs = context.getSharedPreferences("adb_config", Context.MODE_PRIVATE)
        val host = prefs.getString("host", "")?.takeIf { it.isNotBlank() } ?: "IP chưa nhập"
        val pairPort = prefs.getInt("pair_port", 0)
        val connectPort = prefs.getInt("connect_port", 0)
        val intent = Intent(context, AdbPairingReceiver::class.java).setAction(ACTION_PAIR).apply {
            putExtra("host", host)
            putExtra("pair_port", pairPort)
            putExtra("connect_port", connectPort)
        }
        val mutability = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val replyIntent = PendingIntent.getBroadcast(context, 742, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutability)
        val openIntent = PendingIntent.getActivity(
            context, 743, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val input = RemoteInput.Builder(EXTRA_CODE).setLabel("Mã ghép đôi 6 chữ số").setAllowFreeFormInput(true).build()
        val action = Notification.Action.Builder(android.R.drawable.ic_menu_send, "Nhập mã", replyIntent)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .build()
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL_ID) else Notification.Builder(context)
        val notification = builder
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Nhập mã Wi‑Fi ADB")
            .setContentText("IP $host · cổng ghép đôi $pairPort · cổng ADB $connectPort")
            .setStyle(Notification.BigTextStyle().bigText("Mở Developer options → Wireless debugging → Pair device with pairing code. Nhập mã 6 số ở nút bên dưới. Mã chỉ dùng để ghép đôi trên thiết bị này."))
            .setContentIntent(openIntent)
            .setAutoCancel(false)
            .addAction(action)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            postResult(context, "Chưa được phép gửi thông báo. Hãy bật quyền Thông báo cho ChillOTG rồi thử lại.", false)
        }
    }

    fun postResult(context: Context, message: String, success: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Ghép nối Wi‑Fi ADB", NotificationManager.IMPORTANCE_DEFAULT))
        val openIntent = PendingIntent.getActivity(
            context, RESULT_ID, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL_ID) else Notification.Builder(context)
        try {
            NotificationManagerCompat.from(context).notify(RESULT_ID, builder
                .setSmallIcon(if (success) android.R.drawable.stat_sys_upload_done else android.R.drawable.stat_notify_error)
                .setContentTitle(if (success) "ChillOTG · ADB sẵn sàng" else "ChillOTG · Chưa kết nối")
                .setContentText(message)
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .build())
        } catch (_: SecurityException) {
            // The app UI remains the fallback when notification permission is denied.
        }
    }

    fun runPairing(context: Context, host: String, pairPort: Int, connectPort: Int, code: String, onComplete: () -> Unit = {}) {
        val app = context.applicationContext
        executor.execute {
            try {
                require(host.matches(Regex("[A-Za-z0-9.:-]{1,100}"))) { "Địa chỉ IP/host không hợp lệ." }
                require(pairPort in 1..65535 && connectPort in 1..65535) { "Cổng phải nằm trong khoảng 1–65535." }
                require(code.matches(Regex("\\d{6}"))) { "Mã ghép đôi phải có 6 chữ số." }
                val adb = ChillAdbConnectionManager.get(app)
                val paired = adb.pair(host, pairPort, code)
                require(paired) { "Android không chấp nhận mã ghép đôi. Hãy tạo mã mới rồi thử lại." }
                val connected = try {
                    adb.connect(host, connectPort) || adb.isConnected
                } catch (t: Throwable) {
                    false
                }
                app.getSharedPreferences("adb_config", Context.MODE_PRIVATE).edit()
                    .putString("host", host)
                    .putInt("pair_port", pairPort)
                    .putInt("connect_port", connectPort)
                    .putBoolean("paired", true)
                    .putBoolean("connected", connected)
                    .putString("last_status", if (connected) "Đã ghép đôi và kết nối" else "Đã ghép đôi; chưa kết nối cổng ADB")
                    .apply()
                postResult(app, if (connected) "Đã ghép đôi và kết nối Wireless debugging." else "Ghép đôi xong. Kiểm tra lại cổng ADB trên trang Wireless debugging.", connected)
            } catch (t: Throwable) {
                app.getSharedPreferences("adb_config", Context.MODE_PRIVATE).edit()
                    .putBoolean("connected", false)
                    .putString("last_status", t.message ?: "Không ghép nối được")
                    .apply()
                postResult(app, t.message ?: "Không ghép nối được. Kiểm tra Wi‑Fi, mã và cổng.", false)
            } finally {
                onComplete()
            }
        }
    }

    fun disconnect(context: Context) {
        val app = context.applicationContext
        executor.execute {
            try {
                ChillAdbConnectionManager.get(app).disconnect()
                app.getSharedPreferences("adb_config", Context.MODE_PRIVATE).edit()
                    .putBoolean("connected", false)
                    .putBoolean("mapping_running", false)
                    .putString("last_status", "Đã ngắt phiên ADB trong ChillOTG")
                    .apply()
                postResult(app, "Đã đóng kết nối ADB của ChillOTG. Quyền ghép đôi vẫn được lưu trên Android.", true)
            } catch (t: Throwable) {
                postResult(app, t.message ?: "Không thể ngắt kết nối ADB.", false)
            }
        }
    }
}

class AdbPairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AdbPairingNotifications.ACTION_PAIR) return
        val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(AdbPairingNotifications.EXTRA_CODE)?.toString()?.trim().orEmpty()
        val host = intent.getStringExtra("host").orEmpty()
        val pairPort = intent.getIntExtra("pair_port", 0)
        val connectPort = intent.getIntExtra("connect_port", 0)
        val pending = goAsync()
        AdbPairingNotifications.runPairing(context, host, pairPort, connectPort, code) { pending.finish() }
        try { NotificationManagerCompat.from(context).cancel(742) } catch (_: SecurityException) { }
    }
}
