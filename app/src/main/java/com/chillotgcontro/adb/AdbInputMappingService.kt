package com.chillotgcontro.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.view.KeyEvent
import android.view.WindowManager
import androidx.core.app.NotificationManagerCompat
import com.chillotgcontro.profiles.KeyMapping
import com.chillotgcontro.profiles.MappingProfile
import io.github.muntashirakon.adb.AdbStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/** User-started ADB shell bridge. Some OEM builds restrict getevent; errors are reported instead of hidden. */
class AdbInputMappingService : Service() {
    private val running = AtomicBoolean(false)
    @Volatile private var eventStream: AdbStream? = null
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, notification("Đang khởi động Wireless ADB…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMapping()
            stopSelf()
            return START_NOT_STICKY
        }
        if (running.compareAndSet(false, true)) {
            getSharedPreferences("adb_config", MODE_PRIVATE).edit().putBoolean("mapping_running", true).apply()
            worker = Thread({ runMonitor() }, "ChillOTG-AdbInput").apply { isDaemon = true; start() }
        }
        return START_STICKY
    }

    private fun runMonitor() {
        try {
            val prefs = getSharedPreferences("adb_config", MODE_PRIVATE)
            val adb = ChillAdbConnectionManager.get(this)
            if (!adb.isConnected) {
                val connected = adb.connectTls(this, 20_000L)
                if (!connected && !adb.isConnected) throw IllegalStateException("Không tự tìm thấy dịch vụ Wireless ADB. Bật Wireless debugging và ghép đôi lại.")
            }
            prefs.edit().putBoolean("connected", true).putString("last_status", "ADB đang theo dõi phím OTG").apply()
            updateNotification("ADB đã kết nối · đang chờ phím OTG")
            val stream = adb.openStream("shell:getevent -lt")
            eventStream = stream
            BufferedReader(InputStreamReader(stream.openInputStream(), StandardCharsets.UTF_8)).use { reader ->
                while (running.get()) {
                    val line = reader.readLine() ?: break
                    handleEventLine(line)
                }
            }
            if (running.get()) throw IllegalStateException("Lệnh getevent đã dừng. Thiết bị có thể chặn quyền đọc input event.")
        } catch (t: Throwable) {
            val detail = (t.message ?: t.javaClass.simpleName).take(130)
            getSharedPreferences("runtime", MODE_PRIVATE).edit().putBoolean("mapping_enabled", false).putBoolean("adb_mode", false).apply()
            getSharedPreferences("adb_config", MODE_PRIVATE).edit()
                .putBoolean("connected", false)
                .putBoolean("mapping_running", false)
                .putString("last_status", "ADB input: $detail")
                .apply()
            updateNotification("ADB input không khả dụng: $detail")
        } finally {
            running.set(false)
            getSharedPreferences("adb_config", MODE_PRIVATE).edit().putBoolean("mapping_running", false).apply()
            runCatching { eventStream?.close() }
            eventStream = null
        }
    }

    private fun handleEventLine(line: String) {
        val match = EVENT_PATTERN.matcher(line)
        if (!match.find()) return
        val action = match.group(2) ?: return
        if (action != "DOWN" && action != "REPEAT") return
        val keyCode = linuxNameToAndroid(match.group(1) ?: return) ?: return
        val runtime = getSharedPreferences("runtime", MODE_PRIVATE)
        val state = runtime.getString("editor_state", "idle") ?: "idle"
        val profile = getSharedPreferences("profiles", MODE_PRIVATE).getString("selected", "Mặc định") ?: "Mặc định"
        val list = MappingProfile.load(this, profile).toMutableList()
        if (state == "await_new_key" || state == "await_edit_key") {
            if (action != "DOWN") return
            if (keyCode in FORBIDDEN_KEYS) return
            val label = KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
            if (state == "await_new_key") {
                list.add(KeyMapping(keyCode, runtime.getFloat("editor_x", .5f), runtime.getFloat("editor_y", .5f), label))
            } else {
                val index = runtime.getInt("editor_index", -1)
                if (index !in list.indices) return
                list[index] = list[index].copy(keyCode = keyCode, label = label)
            }
            MappingProfile.save(this, list, profile)
            runtime.edit().putString("editor_state", "idle").putString("editor_result", "Đã gán phím $label qua Wi‑Fi ADB").apply()
            return
        }
        if (!runtime.getBoolean("mapping_enabled", false)) return
        val mapping = list.lastOrNull { it.keyCode == keyCode } ?: return
        val (width, height) = screenSize()
        val centerX = (mapping.xRatio * width).toInt().coerceIn(1, width)
        val centerY = (mapping.yRatio * height).toInt().coerceIn(1, height)
        if (mapping.controlType == "joystick") {
            val radius = (44f * resources.displayMetrics.density).toInt().coerceAtLeast(24)
            val (dx, dy) = when (mapping.direction) {
                "up" -> 0 to -radius
                "down" -> 0 to radius
                "left" -> -radius to 0
                "right" -> radius to 0
                else -> return
            }
            injectSwipe(centerX, centerY, (centerX + dx).coerceIn(1, width), (centerY + dy).coerceIn(1, height), 280)
        } else if (action == "DOWN") {
            injectTap(centerX, centerY)
        }
    }

    private fun injectTap(x: Int, y: Int) {
        val adb = ChillAdbConnectionManager.get(this)
        val stream = adb.openStream("shell:input tap $x $y")
        stream.use { input ->
            val buffer = ByteArray(256)
            val source = input.openInputStream()
            while (source.read(buffer) >= 0) { if (!running.get()) break }
        }
    }

    private fun injectSwipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Int) {
        val adb = ChillAdbConnectionManager.get(this)
        val stream = adb.openStream("shell:input swipe $startX $startY $endX $endY $durationMs")
        stream.use { input ->
            val buffer = ByteArray(256)
            val source = input.openInputStream()
            while (source.read(buffer) >= 0) { if (!running.get()) break }
        }
    }

    private fun screenSize(): Pair<Int, Int> {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val point = Point()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealSize(point)
        return point.x.coerceAtLeast(1) to point.y.coerceAtLeast(1)
    }

    private fun linuxNameToAndroid(name: String): Int? {
        if (name.startsWith("KEY_") && name.length == 5 && name.last() in 'A'..'Z') return KeyEvent.KEYCODE_A + (name.last() - 'A')
        if (name.startsWith("KEY_") && name.length == 5 && name.last() in '0'..'9') {
            return if (name.last() == '0') KeyEvent.KEYCODE_0 else KeyEvent.KEYCODE_1 + (name.last() - '1')
        }
        if (name.startsWith("KEY_F") && name.removePrefix("KEY_F").toIntOrNull() in 1..12) return KeyEvent.KEYCODE_F1 + (name.removePrefix("KEY_F").toInt() - 1)
        return when (name) {
            "KEY_SPACE" -> KeyEvent.KEYCODE_SPACE
            "KEY_ENTER", "KEY_KPENTER" -> KeyEvent.KEYCODE_ENTER
            "KEY_ESC" -> KeyEvent.KEYCODE_ESCAPE
            "KEY_TAB" -> KeyEvent.KEYCODE_TAB
            "KEY_BACKSPACE" -> KeyEvent.KEYCODE_DEL
            "KEY_LEFT", "KEY_LEFTARROW" -> KeyEvent.KEYCODE_DPAD_LEFT
            "KEY_RIGHT", "KEY_RIGHTARROW" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "KEY_UP", "KEY_UPARROW" -> KeyEvent.KEYCODE_DPAD_UP
            "KEY_DOWN", "KEY_DOWNARROW" -> KeyEvent.KEYCODE_DPAD_DOWN
            "KEY_LEFTSHIFT" -> KeyEvent.KEYCODE_SHIFT_LEFT
            "KEY_RIGHTSHIFT" -> KeyEvent.KEYCODE_SHIFT_RIGHT
            "KEY_LEFTCTRL" -> KeyEvent.KEYCODE_CTRL_LEFT
            "KEY_RIGHTCTRL" -> KeyEvent.KEYCODE_CTRL_RIGHT
            "KEY_LEFTALT" -> KeyEvent.KEYCODE_ALT_LEFT
            "KEY_RIGHTALT" -> KeyEvent.KEYCODE_ALT_RIGHT
            "BTN_SOUTH", "BTN_A" -> KeyEvent.KEYCODE_BUTTON_A
            "BTN_EAST", "BTN_B" -> KeyEvent.KEYCODE_BUTTON_B
            "BTN_NORTH", "BTN_X" -> KeyEvent.KEYCODE_BUTTON_X
            "BTN_WEST", "BTN_Y" -> KeyEvent.KEYCODE_BUTTON_Y
            "BTN_TL" -> KeyEvent.KEYCODE_BUTTON_L1
            "BTN_TR" -> KeyEvent.KEYCODE_BUTTON_R1
            "BTN_SELECT" -> KeyEvent.KEYCODE_BUTTON_SELECT
            "BTN_START" -> KeyEvent.KEYCODE_BUTTON_START
            "BTN_THUMBL" -> KeyEvent.KEYCODE_BUTTON_THUMBL
            "BTN_THUMBR" -> KeyEvent.KEYCODE_BUTTON_THUMBR
            "BTN_DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
            "BTN_DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
            "BTN_DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
            "BTN_DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
            else -> null
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Ánh xạ phím Wi‑Fi ADB", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(message: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("ChillOTG · ánh xạ Wi‑Fi ADB")
            .setContentText(message)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        try { NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification(message)) } catch (_: SecurityException) { }
    }

    private fun stopMapping() {
        running.set(false)
        runCatching { eventStream?.close() }
        worker?.interrupt()
        worker = null
        getSharedPreferences("adb_config", MODE_PRIVATE).edit().putBoolean("mapping_running", false).apply()
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
    }

    override fun onDestroy() {
        stopMapping()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.chillotgcontro.action.STOP_ADB_MAPPING"
        private const val CHANNEL_ID = "chillotg_adb_mapper"
        private const val NOTIFICATION_ID = 744
        private val EVENT_PATTERN = Pattern.compile("\\b(KEY_[A-Z0-9_]+|BTN_[A-Z0-9_]+)\\s+(DOWN|UP|REPEAT)\\b")
        private val FORBIDDEN_KEYS = setOf(KeyEvent.KEYCODE_UNKNOWN, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN)

        fun start(context: Context) {
            val intent = Intent(context, AdbInputMappingService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AdbInputMappingService::class.java))
        }
    }
}
