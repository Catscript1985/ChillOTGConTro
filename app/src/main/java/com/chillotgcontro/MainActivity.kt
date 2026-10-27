package com.chillotgcontro

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent as AndroidKeyEvent
import android.view.accessibility.AccessibilityManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.chillotgcontro.connection.InstalledGame
import com.chillotgcontro.connection.InstalledGames
import com.chillotgcontro.connection.UsbDevices
import com.chillotgcontro.overlay.FloatingBubbleService
import com.chillotgcontro.profiles.KeyMapping
import com.chillotgcontro.profiles.MappingProfile

private val Bg = Color(0xFF090D15)
private val Panel = Color(0xFF141B28)
private val PanelRaised = Color(0xFF1B2535)
private val Purple = Color(0xFF8C63FF)
private val Aqua = Color(0xFF52E4C2)
private val TextMain = Color(0xFFF3F5FB)
private val TextMuted = Color(0xFF98A5B8)
private val Amber = Color(0xFFFFC66D)

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
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Purple, secondary = Aqua, background = Bg, surface = Panel, onSurface = TextMain)) {
                ChillHome()
            }
        }
    }
}

private enum class HomeTab { GAMES, CONTROLS, SETTINGS }

@Composable
private fun ChillHome() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("profiles", Context.MODE_PRIVATE) }
    val runtime = remember { context.getSharedPreferences("runtime", Context.MODE_PRIVATE) }
    val privacyPrefs = remember { context.getSharedPreferences("privacy", Context.MODE_PRIVATE) }

    var tab by remember { mutableStateOf(HomeTab.GAMES) }
    var games by remember { mutableStateOf(InstalledGames.list(context)) }
    var favoritePackages by remember {
        val saved = prefs.getStringSet("selected_games", null)?.toSet()
            ?: prefs.getString("selected_game", "")?.takeIf { it.isNotBlank() }?.let { setOf(it) }
            ?: emptySet()
        mutableStateOf(saved)
    }
    var selectedPackage by remember { mutableStateOf(prefs.getString("selected_game", "") ?: "") }
    var profile by remember { mutableStateOf(prefs.getString("selected", "Mặc định") ?: "Mặc định") }
    var mappings by remember { mutableStateOf(MappingProfile.load(context, profile)) }
    var devices by remember { mutableStateOf(UsbDevices.list(context)) }
    var otgStatus by remember { mutableStateOf(if (devices.isEmpty()) "Chưa kết nối USB HID" else "Đã thấy ${devices.size} thiết bị OTG") }
    var mappingEnabled by remember { mutableStateOf(runtime.getBoolean("mapping_enabled", false)) }
    var accessibilityOn by remember { mutableStateOf(isMappingAccessibilityEnabled(context)) }
    var showGamePicker by remember { mutableStateOf(false) }
    var showAdbGuide by remember { mutableStateOf(false) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var disclosureChecked by remember { mutableStateOf(false) }
    var disclosureAccepted by remember { mutableStateOf(privacyPrefs.getBoolean("accessibility_disclosure_accepted", false)) }
    var keyCaptureIndex by remember { mutableStateOf<Int?>(null) }

    val selectedGame = games.firstOrNull { it.packageName == selectedPackage }
    val myGames = games.filter { it.packageName in favoritePackages }

    fun chooseGame(game: InstalledGame) {
        selectedPackage = game.packageName
        profile = game.packageName
        mappings = MappingProfile.load(context, profile)
        prefs.edit().putString("selected_game", game.packageName).putString("selected", profile).apply()
        if (game.packageName !in favoritePackages) {
            favoritePackages = favoritePackages + game.packageName
            prefs.edit().putStringSet("selected_games", favoritePackages).apply()
        }
    }

    fun toggleFavorite(game: InstalledGame) {
        favoritePackages = if (game.packageName in favoritePackages) favoritePackages - game.packageName else favoritePackages + game.packageName
        prefs.edit().putStringSet("selected_games", favoritePackages).apply()
        if (favoritePackages.isEmpty()) {
            selectedPackage = ""
            profile = "Mặc định"
            mappings = MappingProfile.load(context, profile)
            prefs.edit().putString("selected_game", "").putString("selected", profile).apply()
        } else if (selectedPackage !in favoritePackages || games.none { it.packageName == selectedPackage }) {
            games.firstOrNull { it.packageName in favoritePackages }?.let(::chooseGame)
        }
    }

    fun refreshOtg() {
        devices = UsbDevices.list(context)
        otgStatus = if (devices.isEmpty()) "Chưa thấy thiết bị USB HID — kiểm tra đầu OTG" else "Đã nhận ${devices.size} thiết bị OTG/HID"
        Toast.makeText(context, otgStatus, Toast.LENGTH_SHORT).show()
    }

    fun openAccessibilitySettings() {
        if (disclosureAccepted) {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            disclosureChecked = false
            showAccessibilityDisclosure = true
        }
    }

    fun toggleMapping() {
        if (!mappingEnabled && !isMappingAccessibilityEnabled(context)) {
            openAccessibilitySettings()
            return
        }
        mappingEnabled = !mappingEnabled
        runtime.edit().putBoolean("mapping_enabled", mappingEnabled).apply()
    }

    fun playSelectedGame() {
        val game = selectedGame
        if (game == null) {
            showGamePicker = true
            return
        }
        if (Settings.canDrawOverlays(context)) startFloatingBubble(context)
        Handler(Looper.getMainLooper()).postDelayed({ InstalledGames.launch(context, game.packageName) }, 350)
    }

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

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF101622), tonalElevation = 0.dp) {
                NavigationBarItem(selected = tab == HomeTab.GAMES, onClick = { tab = HomeTab.GAMES }, icon = { Text("🎮", fontSize = 19.sp) }, label = { Text("Trò chơi") }, colors = navColors())
                NavigationBarItem(selected = tab == HomeTab.CONTROLS, onClick = { tab = HomeTab.CONTROLS }, icon = { Text("⌨", fontSize = 19.sp) }, label = { Text("Điều khiển") }, colors = navColors())
                NavigationBarItem(selected = tab == HomeTab.SETTINGS, onClick = { tab = HomeTab.SETTINGS }, icon = { Text("⚙", fontSize = 19.sp) }, label = { Text("Cài đặt") }, colors = navColors())
            }
        }
    ) { inner ->
        when (tab) {
            HomeTab.GAMES -> GamesScreen(
                modifier = Modifier.padding(inner),
                games = myGames,
                selectedGame = selectedGame,
                devices = devices,
                otgStatus = otgStatus,
                onAddGames = { games = InstalledGames.list(context); showGamePicker = true },
                onSelectGame = ::chooseGame,
                onPlay = ::playSelectedGame,
                onRefreshOtg = ::refreshOtg,
                onSettings = { tab = HomeTab.SETTINGS }
            )
            HomeTab.CONTROLS -> ControlsScreen(
                modifier = Modifier.padding(inner),
                selectedGame = selectedGame,
                games = games,
                profile = profile,
                mappings = mappings,
                mappingEnabled = mappingEnabled,
                accessibilityOn = accessibilityOn,
                keyCaptureIndex = keyCaptureIndex,
                onPickGame = { games = InstalledGames.list(context); showGamePicker = true },
                onToggleMapping = ::toggleMapping,
                onCaptureKey = { index ->
                    if (mappingEnabled) { mappingEnabled = false; runtime.edit().putBoolean("mapping_enabled", false).apply() }
                    keyCaptureIndex = index
                },
                onEditOverlay = {
                    if (!Settings.canDrawOverlays(context)) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    } else if (!isMappingAccessibilityEnabled(context)) {
                        openAccessibilitySettings()
                    } else {
                        Toast.makeText(context, "Vào game rồi chạm bong bóng G → Sửa nút để đặt phím chính xác", Toast.LENGTH_LONG).show()
                        playSelectedGame()
                    }
                },
                onRefreshAccessibility = { accessibilityOn = isMappingAccessibilityEnabled(context) },
                onResetMappings = { mappings = MappingProfile.defaults(); MappingProfile.save(context, mappings, profile) }
            )
            HomeTab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(inner),
                devices = devices,
                otgStatus = otgStatus,
                accessibilityOn = accessibilityOn,
                mappingEnabled = mappingEnabled,
                onRefreshOtg = ::refreshOtg,
                onRefreshAccessibility = { accessibilityOn = isMappingAccessibilityEnabled(context) },
                onOpenAccessibility = ::openAccessibilitySettings,
                onOpenOverlayPermission = { context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) },
                onStartBubble = { if (Settings.canDrawOverlays(context)) startFloatingBubble(context) else context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))) },
                onStopBubble = { context.stopService(Intent(context, FloatingBubbleService::class.java)) },
                onToggleMapping = ::toggleMapping,
                onAdbGuide = { showAdbGuide = true }
            )
        }
    }

    if (showGamePicker) GamePickerDialog(
        games = games,
        selectedPackages = favoritePackages,
        onToggle = { game -> toggleFavorite(game) },
        onSelect = { game -> chooseGame(game); showGamePicker = false },
        onDismiss = { showGamePicker = false }
    )

    if (showAdbGuide) AlertDialog(
        onDismissRequest = { showAdbGuide = false },
        title = { Text("Kết nối OTG / Wi‑Fi ADB") },
        text = { Text("USB OTG: cắm bàn phím hoặc chuột HID rồi bấm ‘Quét thiết bị’. Android tự nhận USB HID; không ghép đôi như Bluetooth.\n\nWi‑Fi ADB: bật Gỡ lỗi không dây trong Tùy chọn nhà phát triển và ghép nối bằng công cụ ADB tương thích. Android không cho ứng dụng thường tự cấp quyền ADB.") },
        confirmButton = { TextButton(onClick = { showAdbGuide = false }) { Text("Đã hiểu") } }
    )

    if (showAccessibilityDisclosure) AlertDialog(
        onDismissRequest = { showAccessibilityDisclosure = false },
        title = { Text("Quyền Trợ năng của ChillOTG") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ChillOTG dùng dịch vụ Trợ năng để nhận phím vật lý khi bạn bật ánh xạ và gửi thao tác chạm tại các vị trí do bạn tự đặt. Dịch vụ không đọc nội dung màn hình, không thu thập mật khẩu và không gửi phím/màn hình lên máy chủ. Hồ sơ được lưu trên thiết bị.")
                Text("Bạn có thể tắt quyền trong Cài đặt Android. Khi bật, các phím được gán sẽ tác động lên ứng dụng đang mở.", color = Amber)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = disclosureChecked, onCheckedChange = { disclosureChecked = it })
                    Text("Tôi đã đọc và đồng ý bật dịch vụ để ánh xạ phím.")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = disclosureChecked, onClick = {
                context.getSharedPreferences("privacy", Context.MODE_PRIVATE).edit().putBoolean("accessibility_disclosure_accepted", true).apply()
                disclosureAccepted = true
                showAccessibilityDisclosure = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Đồng ý & tiếp tục") }
        },
        dismissButton = { TextButton(onClick = { showAccessibilityDisclosure = false }) { Text("Không đồng ý") } }
    )
}

@Composable
private fun GamesScreen(
    modifier: Modifier,
    games: List<InstalledGame>,
    selectedGame: InstalledGame?,
    devices: List<String>,
    otgStatus: String,
    onAddGames: () -> Unit,
    onSelectGame: (InstalledGame) -> Unit,
    onPlay: () -> Unit,
    onRefreshOtg: () -> Unit,
    onSettings: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 22.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Aqua, Purple))), contentAlignment = Alignment.Center) {
                    Text("G", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text("CHILLOTG", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("GAME CONTROL CENTER", color = TextMuted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                }
                StatusPill(label = if (devices.isEmpty()) "OTG CHƯA KẾT NỐI" else "OTG SẴN SÀNG", color = if (devices.isEmpty()) Amber else Aqua)
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF4930A8), Color(0xFF272663), Color(0xFF162A49))), RoundedCornerShape(28.dp)).padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("KHU VỰC CHIẾN GAME", color = Color(0xFFD5C9FF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (selectedGame != null) AppIcon(selectedGame.packageName, Modifier.size(58.dp)) else GamePlaceholder(58)
                        Column(Modifier.weight(1f)) {
                            Text(selectedGame?.label ?: "Chọn game yêu thích", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (selectedGame == null) "Thêm game để bắt đầu" else "Hồ sơ phím riêng đã sẵn sàng", color = Color(0xFFD6D8F4), fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = onPlay,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF39247E))
                    ) { Text("▶   CHƠI NGAY", fontWeight = FontWeight.Black, letterSpacing = 1.sp) }
                    Text("Bong bóng ChillOTG sẽ sẵn sàng trong game nếu đã cấp quyền nổi.", color = Color(0xFFD6D8F4), fontSize = 11.sp)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStatCard(title = "GAME CỦA TÔI", value = games.size.toString(), accent = Purple, modifier = Modifier.weight(1f))
                MiniStatCard(title = "THIẾT BỊ HID", value = devices.size.toString(), accent = Aqua, modifier = Modifier.weight(1f))
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("TRÒ CHƠI CỦA TÔI", color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                    Text("Chọn game để nạp bộ phím riêng", color = TextMuted, fontSize = 11.sp)
                }
                TextButton(onClick = onAddGames) { Text("＋ THÊM", color = Aqua, fontWeight = FontWeight.Bold) }
            }
        }
        if (games.isEmpty()) {
            item {
                EmptyGamesCard(onAddGames)
            }
        } else {
            items(games, key = { it.packageName }) { game ->
                GameRow(game = game, selected = game.packageName == selectedGame?.packageName, onClick = { onSelectGame(game) })
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(if (devices.isEmpty()) Color(0xFF332B1E) else Color(0xFF153A35)), contentAlignment = Alignment.Center) {
                        Text(if (devices.isEmpty()) "↯" else "✓", color = if (devices.isEmpty()) Amber else Aqua, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Kết nối tay điều khiển", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(otgStatus, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = onRefreshOtg) { Text("QUÉT", color = Aqua, fontWeight = FontWeight.Bold) }
                }
            }
        }
        item {
            TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("Cần trợ giúp thiết lập?  ·  MỞ CÀI ĐẶT", color = TextMuted, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun ControlsScreen(
    modifier: Modifier,
    selectedGame: InstalledGame?,
    games: List<InstalledGame>,
    profile: String,
    mappings: List<KeyMapping>,
    mappingEnabled: Boolean,
    accessibilityOn: Boolean,
    keyCaptureIndex: Int?,
    onPickGame: () -> Unit,
    onToggleMapping: () -> Unit,
    onCaptureKey: (Int) -> Unit,
    onEditOverlay: () -> Unit,
    onRefreshAccessibility: () -> Unit,
    onResetMappings: () -> Unit
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            ScreenTitle(eyebrow = "TÙY CHỈNH", title = "Bản đồ phím", subtitle = "Mỗi game giữ bộ điều khiển riêng")
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("GAME ĐANG CHỌN", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (selectedGame != null) AppIcon(selectedGame.packageName, Modifier.size(48.dp)) else GamePlaceholder(48)
                        Column(Modifier.weight(1f)) {
                            Text(selectedGame?.label ?: "Chưa chọn game", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Profile: ${selectedGame?.packageName?.takeLast(20) ?: profile}", color = TextMuted, fontSize = 10.sp)
                        }
                        TextButton(onClick = onPickGame) { Text("ĐỔI", color = Aqua) }
                    }
                    Button(onClick = onEditOverlay, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
                        Text("MỞ GAME + BONG BÓNG", fontWeight = FontWeight.Bold)
                    }
                    Text("Trong game: chạm bong bóng G → Thêm nút phím / Sửa nút. Chạm vị trí, bấm phím OTG, rồi kéo nút để căn chỉnh.", color = TextMuted, fontSize = 11.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ÁNH XẠ ĐANG ${if (mappingEnabled) "BẬT" else "TẮT"}", color = if (mappingEnabled) Aqua else Amber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(if (accessibilityOn) "Dịch vụ Trợ năng đã bật" else "Cần bật quyền Trợ năng", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(checked = mappingEnabled, onCheckedChange = { onToggleMapping() })
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("CÁC NÚT PHÍM", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${mappings.size} điểm chạm trong profile này", color = TextMuted, fontSize = 11.sp)
                }
                TextButton(onClick = onResetMappings) { Text("MẶC ĐỊNH", color = TextMuted, fontSize = 11.sp) }
            }
        }
        items(mappings.indices.toList(), key = { "${profile}_$it" }) { index ->
            val map = mappings[index]
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Purple), contentAlignment = Alignment.Center) {
                        Text(map.label.take(4), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("${map.label}  →  chạm", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("Vị trí ${ (map.xRatio * 100).toInt() }% · ${ (map.yRatio * 100).toInt() }%", color = TextMuted, fontSize = 10.sp)
                    }
                    OutlinedButton(onClick = { onCaptureKey(index) }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(if (keyCaptureIndex == index) "NHẤN PHÍM…" else "ĐỔI", fontSize = 10.sp, color = Aqua)
                    }
                }
            }
        }
        item {
            Text("Để thêm hoặc đặt vị trí mới, vào game và dùng editor nổi. Android/game có thể giới hạn thao tác chạm tổng hợp.", color = Amber, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    devices: List<String>,
    otgStatus: String,
    accessibilityOn: Boolean,
    mappingEnabled: Boolean,
    onRefreshOtg: () -> Unit,
    onRefreshAccessibility: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onStartBubble: () -> Unit,
    onStopBubble: () -> Unit,
    onToggleMapping: () -> Unit,
    onAdbGuide: () -> Unit
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenTitle(eyebrow = "THIẾT LẬP", title = "Cài đặt", subtitle = "Kết nối thiết bị và quản lý quyền") }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("USB OTG / HID", color = TextMain, fontWeight = FontWeight.Bold)
                            Text(otgStatus, color = if (devices.isEmpty()) Amber else Aqua, fontSize = 11.sp)
                        }
                        Button(onClick = onRefreshOtg, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text("Quét") }
                    }
                    devices.forEach { Text("•  $it", color = TextMuted, fontSize = 12.sp) }
                    Text("USB HID do Android tự nhận, không cần ghép đôi Bluetooth.", color = TextMuted, fontSize = 11.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("QUYỀN ỨNG DỤNG", color = TextMain, fontWeight = FontWeight.Bold)
                    PermissionLine("Trợ năng để nhận phím và gửi chạm", accessibilityOn)
                    Button(onClick = onOpenAccessibility, modifier = Modifier.fillMaxWidth()) { Text(if (accessibilityOn) "Mở cài đặt Trợ năng" else "Đọc giải thích & bật Trợ năng") }
                    OutlinedButton(onClick = onRefreshAccessibility, modifier = Modifier.fillMaxWidth()) { Text("Kiểm tra trạng thái Trợ năng") }
                    OutlinedButton(onClick = onOpenOverlayPermission, modifier = Modifier.fillMaxWidth()) { Text("Cấp quyền cửa sổ nổi") }
                    Text("Trợ năng chỉ nhận phím để ánh xạ; hồ sơ được lưu cục bộ. Android hiển thị cảnh báo hệ thống khi bạn bật quyền.", color = TextMuted, fontSize = 11.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("BONG BÓNG TRONG GAME", color = TextMain, fontWeight = FontWeight.Bold)
                    Text("Dùng nút nổi để thêm, sửa, kéo hoặc xóa nút phím khi game đang mở.", color = TextMuted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStartBubble, modifier = Modifier.weight(1f)) { Text("Hiện bong bóng") }
                        OutlinedButton(onClick = onStopBubble, modifier = Modifier.weight(1f)) { Text("Tắt bong bóng") }
                    }
                    OutlinedButton(onClick = onToggleMapping, modifier = Modifier.fillMaxWidth()) { Text(if (mappingEnabled) "Tắt ánh xạ" else "Bật ánh xạ") }
                }
            }
        }
        item {
            TextButton(onClick = onAdbGuide, modifier = Modifier.fillMaxWidth()) { Text("Hướng dẫn OTG / Wi‑Fi ADB", color = TextMuted) }
            Text("ChillOTGConTro  ·  0.1.4", color = TextMuted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun GamePickerDialog(
    games: List<InstalledGame>,
    selectedPackages: Set<String>,
    onToggle: (InstalledGame) -> Unit,
    onSelect: (InstalledGame) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm game trên máy") },
        text = {
            if (games.isEmpty()) Text("Không tìm thấy game hoặc ứng dụng có biểu tượng mở.")
            else LazyColumn(Modifier.heightIn(max = 540.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(games, key = { it.packageName }) { game ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).clickable { onSelect(game) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppIcon(game.packageName, Modifier.size(42.dp))
                        Column(Modifier.weight(1f)) {
                            Text(game.label, color = TextMain, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(game.packageName, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TextButton(onClick = { onToggle(game) }) { Text(if (game.packageName in selectedPackages) "✓" else "+", color = if (game.packageName in selectedPackages) Aqua else Purple, fontSize = 20.sp) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Xong") } }
    )
}

@Composable
private fun GameRow(game: InstalledGame, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFF1C253A) else Panel)
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(game.packageName, Modifier.size(50.dp))
            Column(Modifier.weight(1f)) {
                Text(game.label, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (selected) "ĐANG CHỌN · PROFILE RIÊNG" else "CHẠM ĐỂ CHỌN GAME", color = if (selected) Aqua else TextMuted, fontSize = 9.sp, letterSpacing = .5.sp)
            }
            Text(if (selected) "✓" else "›", color = if (selected) Aqua else TextMuted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyGamesCard(onAddGames: () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🎮", fontSize = 34.sp)
            Text("Chưa có game trong thư viện", color = TextMain, fontWeight = FontWeight.Bold)
            Text("Thêm game đã cài để lưu profile phím và mở nhanh từ đây.", color = TextMuted, fontSize = 12.sp)
            Button(onClick = onAddGames, shape = RoundedCornerShape(14.dp)) { Text("＋  THÊM GAME") }
        }
    }
}

@Composable
private fun ScreenTitle(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(eyebrow, color = Aqua, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
        Text(title, color = TextMain, fontSize = 27.sp, fontWeight = FontWeight.Black)
        Text(subtitle, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun MiniStatCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
            Column {
                Text(title, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp)
                Text(value, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Row(Modifier.clip(CircleShape).background(color.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, color = color, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = .3.sp)
    }
}

@Composable
private fun PermissionLine(label: String, granted: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (granted) Aqua else Amber))
        Text(label, color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(if (granted) "BẬT" else "TẮT", color = if (granted) Aqua else Amber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GamePlaceholder(size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Purple, Color(0xFF4530A0)))), contentAlignment = Alignment.Center) {
        Text("G", color = Color.White, fontSize = (size * .42f).sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.clip(RoundedCornerShape(15.dp)),
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setPadding(2, 2, 2, 2) } },
        update = { view -> view.setImageDrawable(runCatching { view.context.packageManager.getApplicationIcon(packageName) }.getOrNull()) }
    )
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Aqua,
    selectedTextColor = Aqua,
    indicatorColor = Color(0xFF22313C),
    unselectedIconColor = TextMuted,
    unselectedTextColor = TextMuted
)

private fun isMappingAccessibilityEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any { service ->
        service.resolveInfo.serviceInfo.packageName == context.packageName && service.resolveInfo.serviceInfo.name.endsWith("KeyMappingService")
    }
}

private fun startFloatingBubble(context: Context) {
    val intent = Intent(context, FloatingBubbleService::class.java)
    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
}
