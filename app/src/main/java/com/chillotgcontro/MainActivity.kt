package com.chillotgcontro

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
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
import com.chillotgcontro.connection.UsbDevices
import com.chillotgcontro.overlay.FloatingBubbleService
import com.chillotgcontro.profiles.KeyMapping
import com.chillotgcontro.profiles.MappingProfile

private val Ink = Color(0xFF101820)
private val Teal = Color(0xFF55D6BE)
private val Card = Color(0xFF1A2A32)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Teal, background = Ink, surface = Card)) { ChillHome() } }
    }
}

@Composable
private fun ChillHome() {
    val context = LocalContext.current
    var devices by remember { mutableStateOf(UsbDevices.list(context)) }
    var mappings by remember { mutableStateOf(MappingProfile.load(context)) }
    var enabled by remember { mutableStateOf(context.getSharedPreferences("runtime", Context.MODE_PRIVATE).getBoolean("mapping_enabled", false)) }
    var profile by remember { mutableStateOf(context.getSharedPreferences("profiles", Context.MODE_PRIVATE).getString("selected", "Mặc định") ?: "Mặc định") }
    var showGuide by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 28.dp, bottom = 32.dp)) {
        item {
            Text("ChillOTG", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Teal)
            Text("Bộ điều khiển bàn phím OTG cho Android", color = Color.LightGray)
            Spacer(Modifier.height(8.dp))
        }
        item {
            Section("KẾT NỐI USB OTG") {
                Text(if (devices.isEmpty()) "Chưa thấy bàn phím/chuột USB. Hãy cắm OTG rồi làm mới." else "Đã phát hiện ${devices.size} thiết bị", color = Teal)
                devices.forEach { Text("• $it", color = Color.White, modifier = Modifier.padding(top = 5.dp)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { devices = UsbDevices.list(context) }) { Text("Quét lại") }
                    OutlinedButton(onClick = { showGuide = true }) { Text("Wi‑Fi ADB") }
                }
            }
        }
        item {
            Section("CẤP QUYỀN CẦN THIẾT") {
                Text("Để dùng chạm ánh xạ, bật dịch vụ trợ năng của ChillOTG trong Cài đặt Android. Để hiện nút nổi, cấp quyền ‘Hiển thị trên ứng dụng khác’.", color = Color.LightGray)
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier = Modifier.fillMaxWidth()) { Text("Mở cài đặt Trợ năng") }
                OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, modifier = Modifier.fillMaxWidth()) { Text("Cấp quyền nút nổi") }
                OutlinedButton(onClick = {
                    if (!Settings.canDrawOverlays(context)) context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    else context.startForegroundService(Intent(context, FloatingBubbleService::class.java))
                }, modifier = Modifier.fillMaxWidth()) { Text("Bật / tắt nút nổi") }
            }
        }
        item {
            Section("ÁNH XẠ PHÍM") {
                Text("Bật: ${if (enabled) "ĐANG BẬT" else "ĐANG TẮT"}", color = if (enabled) Teal else Color.LightGray, fontWeight = FontWeight.Bold)
                Text("Ánh xạ dùng thao tác chạm đơn tại tọa độ màn hình tương đối. Bốn phím WASD và SPACE là cấu hình mẫu.", color = Color.LightGray)
                Button(onClick = {
                    enabled = !enabled
                    context.getSharedPreferences("runtime", Context.MODE_PRIVATE).edit().putBoolean("mapping_enabled", enabled).apply()
                }, modifier = Modifier.fillMaxWidth()) { Text(if (enabled) "Tắt ánh xạ" else "Bật ánh xạ") }
                mappings.forEach { m -> Text("${m.label}: x ${(m.xRatio * 100).toInt()}%, y ${(m.yRatio * 100).toInt()}%", color = Color.White) }
                OutlinedButton(onClick = { mappings = MappingProfile.defaults(); MappingProfile.save(context, mappings) }, modifier = Modifier.fillMaxWidth()) { Text("Khôi phục WASD mặc định") }
                Text("Lưu ý: ứng dụng/game có thể chặn lọc phím hoặc chạm tổng hợp. Thao tác nhấn giữ, chuột và camera chưa được bảo đảm.", color = Color(0xFFFFC56B), fontSize = 12.sp)
            }
        }
        item {
            Section("HỒ SƠ TRÒ CHƠI") {
                Text("Hồ sơ hiện tại: $profile", color = Teal, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Mặc định", "Bắn súng", "MOBA").forEach { name -> OutlinedButton(onClick = {
                        profile = name; context.getSharedPreferences("profiles", Context.MODE_PRIVATE).edit().putString("selected", name).apply()
                    }) { Text(name) } }
                }
                Text("Bản đầu lưu bộ phím trên thiết bị; chưa tự nhận diện ứng dụng/game đang mở.", color = Color.LightGray, fontSize = 12.sp)
            }
        }
        item {
            Section("GIỚI HẠN ANDROID") {
                Text("Chế độ Wi‑Fi ADB chỉ mở hướng dẫn ghép nối hệ thống. App thường không thể tự chạy ADB pairing hoặc tự kết nối lại như một dịch vụ ADB.", color = Color.LightGray)
                Text("Android không cho ứng dụng bên thứ ba đưa con trỏ hệ thống về tâm màn hình. Vì vậy chế độ camera chuột kiểu FPS trong prompt chưa thể triển khai tin cậy.", color = Color.LightGray)
            }
        }
    }
    if (showGuide) AlertDialog(
        onDismissRequest = { showGuide = false },
        title = { Text("Ghép nối Wi‑Fi ADB") },
        text = { Text("1. Mở Tùy chọn nhà phát triển.\n2. Bật Gỡ lỗi không dây.\n3. Chọn ‘Ghép nối thiết bị bằng mã ghép nối’.\n4. Làm theo hướng dẫn trên điện thoại để ghép nối bằng một ứng dụng ADB tương thích.\n\nAndroid không cho phép app ChillOTG thông thường tự gửi mã ghép nối và tạo kết nối ADB có đặc quyền.") },
        confirmButton = { TextButton(onClick = { showGuide = false; runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) } }) { Text("Mở Tùy chọn nhà phát triển") } },
        dismissButton = { TextButton(onClick = { showGuide = false }) { Text("Đóng") } }
    )
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Card), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Teal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            content()
        }
    }
}
