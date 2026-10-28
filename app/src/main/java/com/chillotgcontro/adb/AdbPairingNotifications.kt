package com.chillotgcontro.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.chillotgcontro.MainActivity
import io.github.muntashirakon.adb.android.AdbMdns
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object AdbPairingNotifications {
    const val ACTION_PAIR = "com.chillotgcontro.action.PAIR_WIFI_ADB"
    const val ACTION_DISCOVER = "com.chillotgcontro.action.DISCOVER_WIFI_ADB"
    const val EXTRA_CODE = "pairing_code"
    const val EXTRA_HOST = "pairing_host"
    const val EXTRA_PAIR_PORT = "pairing_port"
    const val EXTRA_CODE_FROM_REMOTE_INPUT = "remote_pairing_code"
    const val PROGRESS_ID = 745
    private const val CHANNEL_ID = "chillotg_adb_pair"
    private const val NOTIFICATION_ID = 742
    private const val RESULT_ID = 743
    private const val DISCOVERY_TIMEOUT_SECONDS = 120L
    private val executor = Executors.newSingleThreadExecutor()
    private val discoveryActive = AtomicBoolean(false)

    /** Starts mDNS first; when this phone exposes its pairing endpoint, show the inline-code notification. */
    fun startDiscovery(context: Context) {
        val intent = Intent(context, AdbPairingService::class.java).setAction(ACTION_DISCOVER)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }

    fun discoverPairingAndPrompt(context: Context, onComplete: () -> Unit = {}) {
        val app = context.applicationContext
        if (!discoveryActive.compareAndSet(false, true)) {
            postResult(app, "Đang tìm dịch vụ ghép đôi. Mở Wireless debugging → Pair device with pairing code.", false)
            onComplete()
            return
        }
        val latch = CountDownLatch(1)
        val foundHost = AtomicReference<String?>(null)
        val foundPort = AtomicInteger(0)
        val mdns = AdbMdns(app, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { address: InetAddress?, port: Int ->
            val host = address?.hostAddress
            if (!host.isNullOrBlank() && port in 1..65535 && foundHost.compareAndSet(null, host)) {
                foundPort.set(port)
                latch.countDown()
            }
        }
        try {
            mdns.start()
        } catch (t: Throwable) {
            discoveryActive.set(false)
            postResult(app, "Không bắt đầu được tự dò Wireless ADB: ${t.message ?: "hãy kiểm tra quyền thiết bị ở gần"}", false)
            onComplete()
            return
        }
        executor.execute {
            try {
                if (latch.await(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    val host = foundHost.get()
                    val port = foundPort.get()
                    if (!host.isNullOrBlank() && port in 1..65535) {
                        sendPairPrompt(app, host, port)
                    } else {
                        postResult(app, "Không lấy được thông tin ghép đôi. Hãy mở lại Pair device with pairing code rồi thử lại.", false)
                    }
                } else {
                    postResult(app, "Chưa tìm thấy dịch vụ ghép đôi. Bật Wireless debugging rồi mở Pair device with pairing code trên chính điện thoại này.", false)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                postResult(app, "Đã dừng tìm dịch vụ ghép đôi.", false)
            } finally {
                runCatching { mdns.stop() }
                discoveryActive.set(false)
                onComplete()
            }
        }
    }

    private fun sendPairPrompt(context: Context, host: String, pairPort: Int) {
        ensureChannel(context, NotificationManager.IMPORTANCE_HIGH)
        val intent = Intent(context, AdbPairingReceiver::class.java).setAction(ACTION_PAIR).apply {
            putExtra(EXTRA_HOST, host)
            putExtra(EXTRA_PAIR_PORT, pairPort)
        }
        val mutability = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val replyIntent = PendingIntent.getBroadcast(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutability)
        val openIntent = PendingIntent.getActivity(
            context, RESULT_ID, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val input = RemoteInput.Builder(EXTRA_CODE_FROM_REMOTE_INPUT)
            .setLabel("Mã 6 số trên màn hình Wireless debugging")
            .setAllowFreeFormInput(true)
            .build()
        val action = Notification.Action.Builder(android.R.drawable.ic_menu_send, "Nhập mã ghép đôi", replyIntent)
            .addRemoteInput(input)
            .setAllowGeneratedReplies(false)
            .build()
        val notification = notificationBuilder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("ChillOTG tìm thấy mã ghép đôi Wi‑Fi ADB")
            .setContentText("Nhập mã 6 số đang hiển thị trong Wireless debugging")
            .setStyle(Notification.BigTextStyle().bigText("Không cần nhập IP hoặc cổng. Nhập mã 6 số trên màn hình Pair device with pairing code của chính điện thoại này."))
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

    fun progressNotification(context: Context, message: String): Notification {
        ensureChannel(context, NotificationManager.IMPORTANCE_LOW)
        return notificationBuilder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("ChillOTG đang ghép nối Wireless ADB")
            .setContentText(message)
            .setOngoing(true)
            .build()
    }

    fun cancelProgress(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(PROGRESS_ID) }
    }

    fun postResult(context: Context, message: String, success: Boolean) {
        ensureChannel(context, NotificationManager.IMPORTANCE_DEFAULT)
        val openIntent = PendingIntent.getActivity(
            context, RESULT_ID, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        try {
            NotificationManagerCompat.from(context).notify(RESULT_ID, notificationBuilder(context, CHANNEL_ID)
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

    fun runPairing(context: Context, host: String, pairPort: Int, code: String, onComplete: () -> Unit = {}) {
        val app = context.applicationContext
        executor.execute {
            try {
                require(host.matches(Regex("[A-Fa-f0-9:.%]{2,100}"))) { "Không nhận diện được địa chỉ của điện thoại." }
                require(pairPort in 1..65535) { "Không tìm thấy cổng ghép đôi." }
                require(code.matches(Regex("\\d{6}"))) { "Mã ghép đôi phải có đúng 6 chữ số." }
                val adb = ChillAdbConnectionManager.get(app)
                require(adb.pair(host, pairPort, code)) { "Android không chấp nhận mã. Tạo mã mới rồi thử lại." }
                val connected = try {
                    adb.connectTls(app, 20_000L) || adb.isConnected
                } catch (_: Throwable) {
                    adb.isConnected
                }
                app.getSharedPreferences("adb_config", Context.MODE_PRIVATE).edit()
                    .putBoolean("paired", true)
                    .putBoolean("connected", connected)
                    .putString("last_status", if (connected) "Đã ghép đôi và kết nối tự động" else "Đã ghép đôi; chưa tìm thấy dịch vụ kết nối TLS")
                    .apply()
                postResult(app, if (connected) "Đã ghép đôi và tự kết nối Wireless debugging." else "Đã ghép đôi. Giữ Wireless debugging bật và thử kết nối lại.", connected)
            } catch (t: Throwable) {
                app.getSharedPreferences("adb_config", Context.MODE_PRIVATE).edit()
                    .putBoolean("connected", false)
                    .putString("last_status", t.message ?: "Không ghép nối được")
                    .apply()
                postResult(app, t.message ?: "Không ghép nối được. Kiểm tra mã và Wireless debugging.", false)
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

    private fun ensureChannel(context: Context, importance: Int) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Ghép nối Wi‑Fi ADB", importance).apply {
                description = "Nhập mã ghép đôi Wireless debugging do Android hiển thị"
            })
        }
    }

    private fun notificationBuilder(context: Context, channelId: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, channelId) else Notification.Builder(context)
}

class AdbPairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AdbPairingNotifications.ACTION_PAIR) return
        val code = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(AdbPairingNotifications.EXTRA_CODE_FROM_REMOTE_INPUT)?.toString()?.trim().orEmpty()
        val serviceIntent = Intent(context, AdbPairingService::class.java).apply {
            action = AdbPairingNotifications.ACTION_PAIR
            putExtra(AdbPairingNotifications.EXTRA_HOST, intent.getStringExtra(AdbPairingNotifications.EXTRA_HOST).orEmpty())
            putExtra(AdbPairingNotifications.EXTRA_PAIR_PORT, intent.getIntExtra(AdbPairingNotifications.EXTRA_PAIR_PORT, 0))
            putExtra(AdbPairingNotifications.EXTRA_CODE, code)
        }
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
            NotificationManagerCompat.from(context).cancel(742)
        } catch (t: Throwable) {
            AdbPairingNotifications.postResult(context, t.message ?: "Không khởi động được ghép nối ADB.", false)
        }
    }
}
