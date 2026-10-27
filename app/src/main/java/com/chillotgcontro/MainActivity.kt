package com.chillotgcontro

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent as AndroidKeyEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chillotgcontro.connection.InstalledGame
import com.chillotgcontro.connection.InstalledGames
import com.chillotgcontro.connection.UsbDevices
import com.chillotgcontro.overlay.FloatingBubbleService
import com.chillotgcontro.profiles.KeyMapping
import com.chillotgcontro.profiles.MappingProfile

private val Ink = Color(0xFF101820)
private val Teal = Color(0xFF55D6BE)
private val CardBg = Color(0xFF1A2A32)

private object KeyCaptureBridge {
    var onKeyCaptured: ((Int) -> Boolean)? = null
}

class MainActivity : ComponentActivity() {
    override fun dispatchKeyEvent(event: AndroidKeyEvent): Boolean {
        if (event.action == AndroidKeyEvent.ACTION_DOWN && event.repeatCount == 0 && KeyCaptureBridge.onKeyCaptured?.invoke(event.keyCode) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Teal, background = Ink, surface = CardBg)) { ChillHome() } }
    }
}

@Composable
private fun ChillHome() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("profiles", Context.MODE_PRIVATE) }
    var devices by remember { mutableStateOf(UsbDevices.list(context)) }
    var otgStatus by remember { mutableStateOf(if (devices.isEmpty()) "Chưa kết nối thiết bị USB HID" else "Đã thấy thiết bị USB HID") }
    var games by remember { mutableStateOf(InstalledGames.list(context)) }
    var selectedPackage by remember { mutableStateOf(prefs.getString("selected_game", "") ?: "") }
    var profile by remember { mutableStateOf(prefs.getString("selected", "Mặc định") ?: "Mặc định") }
    var mappings by remember { mutableStateOf(MappingProfile.load(context, profile)) }
    var enabled by remember { mutableStateOf(context.getSharedPreferences("runtime", Context.MODE_PRIVATE).getBoolean("mapping_enabled", false)) }
    var showGamePicker by remember { mutableStateOf(false) }
    var showAdbGuide by remember { mutableStateOf(false) }
    var keyCaptureIndex by remember { mutableStateOf<Int?>(null) }
    var accessibilityOn by remember { mutableStateOf(isMappingAccessibilityEnabled(context)) }
    val selectedGame = games.firstOrNull { it.packageName == selectedPackage }

    DisposableEffect(profile, mappings, keyCaptureIndex) {
        KeyCaptureBridge.onKeyCaptured = { code ->
            val index = keyCaptureIndex
            val forbidden = setOf(AndroidKeyEvent.KEYCODE_UNKNOWN, AndroidKeyEvent.KEYCODE_BACK, AndroidKeyEvent.KEYCODE_VOLUME_UP, AndroidKeyEvent.KEYCODE_VOLUME_DOWN, AndroidKeyEvent.KEYCODE_POWER)
            if (index != null && index in mappings.indices && code !in forbidden) {
                val keyName = AndroidKeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
                val next = mappings.toMutableList().also { it[index] = it[index].copy(keyCode = code, label = keyName) }
                mappings = next
                MappingProfile.save(context, next, profile)
                keyCaptureIndex = null
                Toast.makeText(context, "Đã gán phím $keyName", Toast.LENGTH_SHORT).show()
                true
            } else false
        }
        onDispose { KeyCaptureBridge.onKeyCaptured = null }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 28.dp, bottom = 32.dp)) {
        item {
            Text("ChillOTG", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Teal)
            Text("Kết nối tay điều khiển · chọn game · bắt đầu chơi", color = Color.LightGray)
            Spacer(Modifier.height(8.dp))
        }
        item {
            Section("KẾT NỐI OTG") {
                Text(otgStatus, color = if (devices.isEmpty()) Color(0xFFFFC56B) else Teal, fontWeight = FontWeight.SemiBold)
                devices.forEach { Text("• $it", color = Color.White) }
                Text("USB HID được Android tự nhận khi cắm OTG; app không ghép đôi như Bluetooth. Bấm nút để quét lại.", color = Color.LightGray, fontSize = 12.sp)
                Button(onClick = {
                    devices = UsbDevices.list(context)
                    otgStatus = if (devices.isEmpty()) "Chưa thấy bàn phím/chuột USB HID — kiểm tra đầu OTG và cắm lại thiết bị" else "Đã kết nối ${devices.size} thiết bị OTG/HID"
                    Toast.makeText(context, otgStatus, Toast.LENGTH_LONG).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("⟳  Kết nối / quét OTG") }
                OutlinedButton(onClick = { showAdbGuide = true }, modifier = Modifier.fillMaxWidth()) { Text("Hướng dẫn kết nối Wi‑Fi ADB") }
            }
        }
        item {
            Section("CHỌN GAME TRÊN MÁY") {
                Text(selectedGame?.label ?: "Chưa chọn game", color = if (selectedGame == null) Color.LightGray else Teal, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (selectedGame != null) Text(selectedGame.packageName, color = Color.LightGray, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { games = InstalledGames.list(context); showGamePicker = true }, modifier = Modifier.weight(1f)) { Text("Chọn game") }
                    OutlinedButton(onClick = { games = InstalledGames.list(context) }) { Text("Làm mới") }
                }
                Button(
                    enabled = selectedGame != null,
                    onClick = {
                        val game = selectedGame
                        if (game != null && !InstalledGames.launch(context, game.packageName)) {
                            Toast.makeText(context, "Không mở được game này. Có thể game không có màn hình khởi chạy.", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("▶  CHƠI — MỞ GAME ĐÃ CHỌN", fontWeight = FontWeight.Bold) }
                Text("Khi chọn game, ứng dụng tạo hồ sơ phím riêng theo package của game. Nút Chơi sẽ mở app game đã cài.", color = Color.LightGray, fontSize = 12.sp)
            }
        }
        item {
            Section("BẬT QUYỀN ĐIỀU KHIỂN") {
                Text(if (accessibilityOn) "Trợ năng ChillOTG: ĐÃ BẬT" else "Trợ năng ChillOTG: CHƯA BẬT", color = if (accessibilityOn) Teal else Color(0xFFFFC56B), fontWeight = FontWeight.Bold)
                Text("Android yêu cầu bật dịch vụ Trợ năng để nhận phím ở ngoài ứng dụng. Quyền hiển thị nổi chỉ cần nếu muốn dùng bong bóng điều khiển.", color = Color.LightGray)
                OutlinedButton(onClick = { accessibilityOn = isMappingAccessibilityEnabled(context) }, modifier = Modifier.fillMaxWidth()) { Text("Kiểm tra lại quyền Trợ năng") }
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier = Modifier.fillMaxWidth()) { Text("Mở cài đặt Trợ năng") }
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier = Modifier.fillMaxWidth()) { Text("Cấp quyền nút nổi") }
                OutlinedButton(onClick = {
                    if (!Settings.canDrawOverlays(context)) context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    else context.startForegroundService(Intent(context, FloatingBubbleService::class.java))
                }, modifier = Modifier.fillMaxWidth()) { Text("Bật nút nổi") }
            }
        }
        item {
            Section("ÁNH XẠ PHÍM CHO GAME") {
                Text("Hồ sơ: ${selectedGame?.label ?: profile}", color = Teal, fontWeight = FontWeight.Bold)
                Text("Trạng thái: ${if (enabled) "ĐANG BẬT" else "ĐANG TẮT"}", color = if (enabled) Teal else Color.LightGray)
                Button(onClick = {
                    if (!enabled && !isMappingAccessibilityEnabled(context)) {
                        Toast.makeText(context, "Hãy bật dịch vụ Trợ năng ChillOTG trước", Toast.LENGTH_LONG).show()
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } else {
                        enabled = !enabled
                        context.getSharedPreferences("runtime", Context.MODE_PRIVATE).edit().putBoolean("mapping_enabled", enabled).apply()
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (enabled) "Tắt ánh xạ phím" else "Bật ánh xạ phím") }
                mappings.forEachIndexed { index, mapping ->
                    OutlinedButton(onClick = {
                        if (enabled) {
                            enabled = false
                            context.getSharedPreferences("runtime", Context.MODE_PRIVATE).edit().putBoolean("mapping_enabled", false).apply()
                        }
                        keyCaptureIndex = index
                    }, modifier = Modifier.fillMaxWidth()) { Text("Gán phím cho ${mapping.label}") }
                    Text("${mapping.label} · X ${(mapping.xRatio * 100).toInt()}%", color = Color.White)
                    Slider(value = mapping.xRatio, onValueChange = { value ->
                        val next = mappings.toMutableList().also { it[index] = mapping.copy(xRatio = value) }
                        mappings = next; MappingProfile.save(context, next, profile)
                    })
                    Text("${mapping.label} · Y ${(mapping.yRatio * 100).toInt()}%", color = Color.White)
                    Slider(value = mapping.yRatio, onValueChange = { value ->
                        val next = mappings.toMutableList().also { it[index] = mapping.copy(yRatio = value) }
                        mappings = next; MappingProfile.save(context, next, profile)
                    })
                }
                OutlinedButton(onClick = {
                    mappings = MappingProfile.defaults()
                    MappingProfile.save(context, mappings, profile)
                }, modifier = Modifier.fillMaxWidth()) { Text("Khôi phục WASD + SPACE") }
                Text("Mỗi game dùng profile riêng. Android/game có thể giới hạn việc lọc phím hoặc chạm tổng hợp; chức năng còn tùy thiết bị và game.", color = Color(0xFFFFC56B), fontSize = 12.sp)
                Text(if (keyCaptureIndex == null) "Để đổi phím, bấm ‘Gán phím’ ở mục tương ứng rồi nhấn phím trên bàn phím OTG." else "Đang chờ phím mới… nhấn một phím trên bàn phím OTG. Tắt ánh xạ trước khi gán phím." , color = Teal, fontSize = 12.sp)
            }
        }
    }

    if (showGamePicker) AlertDialog(
        onDismissRequest = { showGamePicker = false },
        title = { Text("Chọn game / ứng dụng") },
        text = {
            if (games.isEmpty()) Text("Không tìm thấy ứng dụng có biểu tượng mở trên máy.")
            else LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(games, key = { it.packageName }) { game ->
                    TextButton(onClick = {
                        selectedPackage = game.packageName
                        profile = game.packageName
                        mappings = MappingProfile.load(context, profile)
                        prefs.edit().putString("selected_game", game.packageName).putString("selected", profile).apply()
                        showGamePicker = false
                    }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(game.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(game.packageName, color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showGamePicker = false }) { Text("Đóng") } }
    )

    if (showAdbGuide) AlertDialog(
        onDismissRequest = { showAdbGuide = false },
        title = { Text("Kết nối USB OTG / Wi‑Fi ADB") },
        text = { Text("OTG: cắm bàn phím/chuột USB qua đầu OTG; Android tự nhận thiết bị HID. Quay lại trang chủ và bấm ‘Kết nối / quét OTG’.\n\nWi‑Fi ADB: bật Gỡ lỗi không dây trong Tùy chọn nhà phát triển, rồi ghép nối bằng ứng dụng ADB tương thích. App thông thường không thể tự cấp quyền ADB hoặc tự kết nối có đặc quyền.") },
        confirmButton = { TextButton(onClick = { showAdbGuide = false }) { Text("Đã hiểu") } }
    )
}

private fun isMappingAccessibilityEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { service ->
        service.resolveInfo.serviceInfo.packageName == context.packageName && service.resolveInfo.serviceInfo.name.endsWith("KeyMappingService")
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            content()
        }
    }
}
