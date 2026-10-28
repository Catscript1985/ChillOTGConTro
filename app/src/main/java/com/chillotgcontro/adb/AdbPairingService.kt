package com.chillotgcontro.adb

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** Runs the user-triggered Wireless ADB discovery and pairing flow in a visible foreground service. */
class AdbPairingService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AdbPairingNotifications.ACTION_DISCOVER -> {
                startForeground(
                    AdbPairingNotifications.PROGRESS_ID,
                    AdbPairingNotifications.progressNotification(this, "Đang tìm Wireless debugging trên điện thoại này")
                )
                AdbPairingNotifications.discoverPairingAndPrompt(this) { finishService(startId) }
            }
            AdbPairingNotifications.ACTION_PAIR -> {
                startForeground(
                    AdbPairingNotifications.PROGRESS_ID,
                    AdbPairingNotifications.progressNotification(this, "Đang xác thực mã và tự tìm kết nối")
                )
                val host = intent.getStringExtra(AdbPairingNotifications.EXTRA_HOST).orEmpty()
                val pairPort = intent.getIntExtra(AdbPairingNotifications.EXTRA_PAIR_PORT, 0)
                val code = intent.getStringExtra(AdbPairingNotifications.EXTRA_CODE).orEmpty()
                AdbPairingNotifications.runPairing(this, host, pairPort, code) { finishService(startId) }
            }
            AdbPairingNotifications.ACTION_RECONNECT -> {
                startForeground(
                    AdbPairingNotifications.PROGRESS_ID,
                    AdbPairingNotifications.progressNotification(this, "Đang kết nối lại bằng khóa ADB đã lưu")
                )
                AdbPairingNotifications.reconnect(this) { finishService(startId) }
            }
            else -> {
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun finishService(startId: Int) {
        mainHandler.post {
            AdbPairingNotifications.cancelProgress(this)
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
            stopSelfResult(startId)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
