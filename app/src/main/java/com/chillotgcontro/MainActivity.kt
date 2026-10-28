package com.chillotgcontro

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.chillotgcontro.adb.AdbPairingNotifications
import com.chillotgcontro.adb.AdbInputMappingService
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
    companion object {
        const val EXTRA_OPEN_KEYMAP_LIBRARY = "open_keymap_library"
        const val EXTRA_IMPORT_KEYMAP_NOW = "import_keymap_now"
    }

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

    var tab by remember { mutableStateOf(HomeTab.GAMES) }
    val openKeymapOnLaunch = remember { (context as? Activity)?.intent?.getBooleanExtra(MainActivity.EXTRA_OPEN_KEYMAP_LIBRARY, false) == true }
    var showKeymapLibrary by remember { mutableStateOf(openKeymapOnLaunch) }
    var importKeymapOnLaunch by remember { mutableStateOf((context as? Activity)?.intent?.getBooleanExtra(MainActivity.EXTRA_IMPORT_KEYMAP_NOW, false) == true) }
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
    var adbConnected by remember { mutableStateOf(context.getSharedPreferences("adb_config", Context.MODE_PRIVATE).getBoolean("connected", false)) }
    var showGamePicker by remember { mutableStateOf(false) }
    var showAdbGuide by remember { mutableStateOf(false) }
    var keyCaptureIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(openKeymapOnLaunch, importKeymapOnLaunch) {
        if (openKeymapOnLaunch || importKeymapOnLaunch) {
            tab = HomeTab.CONTROLS
            showKeymapLibrary = true
        }
    }

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

    fun toggleMapping() {
        adbConnected = context.getSharedPreferences("adb_config", Context.MODE_PRIVATE).getBoolean("connected", false)
        if (!mappingEnabled && !adbConnected) {
            tab = HomeTab.SETTINGS
            Toast.makeText(context, "Ghép nối Wi‑Fi ADB trước khi bật ánh xạ", Toast.LENGTH_LONG).show()
            return
        }
        mappingEnabled = !mappingEnabled
        runtime.edit().putBoolean("mapping_enabled", mappingEnabled).putBoolean("adb_mode", mappingEnabled).apply()
        if (mappingEnabled) AdbInputMappingService.start(context) else AdbInputMappingService.stop(context)
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
                adbConnected = adbConnected,
                keyCaptureIndex = keyCaptureIndex,
                onPickGame = { games = InstalledGames.list(context); showGamePicker = true },
                onToggleMapping = ::toggleMapping,
                onCaptureKey = { index ->
                    if (mappingEnabled) {
                        mappingEnabled = false
                        runtime.edit().putBoolean("mapping_enabled", false).putBoolean("adb_mode", false).apply()
                        AdbInputMappingService.stop(context)
                    }
                    keyCaptureIndex = index
                },
                onEditOverlay = {
                    if (!Settings.canDrawOverlays(context)) {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                    } else {
                        adbConnected = context.getSharedPreferences("adb_config", Context.MODE_PRIVATE).getBoolean("connected", false)
                        if (adbConnected) {
                            runtime.edit().putBoolean("adb_mode", true).apply()
                            AdbInputMappingService.start(context)
                            Toast.makeText(context, "ADB đang nhận phím OTG; dùng bong bóng G để mở editor", Toast.LENGTH_LONG).show()
                            playSelectedGame()
                        } else {
                            Toast.makeText(context, "Ghép nối Wi‑Fi ADB trong Cài đặt trước khi mở editor", Toast.LENGTH_LONG).show()
                            tab = HomeTab.SETTINGS
                        }
                    }
                },
                onRefreshAdb = { adbConnected = context.getSharedPreferences("adb_config", Context.MODE_PRIVATE).getBoolean("connected", false) },
                onResetMappings = { mappings = MappingProfile.defaults(); MappingProfile.save(context, mappings, profile) },
                onManageKeymaps = { showKeymapLibrary = true }
            )
            HomeTab.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(inner),
                devices = devices,
                otgStatus = otgStatus,
                adbConnected = adbConnected,
                mappingEnabled = mappingEnabled,
                onRefreshOtg = ::refreshOtg,
                onRefreshAdb = { adbConnected = context.getSharedPreferences("adb_config", Context.MODE_PRIVATE).getBoolean("connected", false) },
                onDisconnectAdb = {
                    AdbInputMappingService.stop(context)
                    runtime.edit().putBoolean("mapping_enabled", false).putBoolean("adb_mode", false).apply()
                    mappingEnabled = false
                    adbConnected = false
                    AdbPairingNotifications.disconnect(context)
                },
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

    if (showKeymapLibrary) KeymapLibraryDialog(
        profile = profile,
        mappings = mappings,
        autoOpenImport = importKeymapOnLaunch,
        onImportLaunched = { importKeymapOnLaunch = false },
        onApply = { loaded ->
            mappings = loaded
            MappingProfile.save(context, loaded, profile, synchronous = true)
            Toast.makeText(context, "Đã tải keymap cho game này", Toast.LENGTH_SHORT).show()
        },
        onDismiss = { showKeymapLibrary = false }
    )

    if (showAdbGuide) AlertDialog(
        onDismissRequest = { showAdbGuide = false },
        title = { Text("Kết nối OTG / Wi‑Fi ADB") },
        text = { Text("USB OTG: cắm bàn phím hoặc chuột HID rồi bấm ‘Quét thiết bị’. Android tự nhận USB HID.\n\nWi‑Fi ADB: Android 11+ cần bật Wireless debugging. Bấm ‘Bắt đầu ghép nối tự động’, rồi mở Wireless debugging → Pair device with pairing code. ChillOTG tự dò IP và cổng trên điện thoại; notification sẽ hiện ô nhập mã 6 số. Không cần nhập IP/cổng.\n\nUSB debugging có dây không thay thế Wireless debugging và app thông thường không thể tự bật/cấp quyền ADB hệ thống. ADB mode đọc phím bằng getevent và gửi tap; một số ROM có thể chặn đọc input event.") },
        confirmButton = { TextButton(onClick = { showAdbGuide = false }) { Text("Đã hiểu") } }
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
    adbConnected: Boolean,
    keyCaptureIndex: Int?,
    onPickGame: () -> Unit,
    onToggleMapping: () -> Unit,
    onCaptureKey: (Int) -> Unit,
    onEditOverlay: () -> Unit,
    onRefreshAdb: () -> Unit,
    onResetMappings: () -> Unit,
    onManageKeymaps: () -> Unit
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
                    Text("Trong game: bong bóng G → Thêm nút phím → + Thành phần. Chọn nút chạm hoặc joystick WASD; chạm hướng để đổi phím, kéo để căn vị trí rồi Lưu.", color = TextMuted, fontSize = 11.sp)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ÁNH XẠ ĐANG ${if (mappingEnabled) "BẬT" else "TẮT"}", color = if (mappingEnabled) Aqua else Amber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(if (adbConnected) "Wireless ADB đã kết nối" else "Ghép Wi‑Fi ADB để nhận phím", color = TextMuted, fontSize = 11.sp)
                    }
                    Switch(checked = mappingEnabled, onCheckedChange = { onToggleMapping() })
                }
            }
        }
        item {
            OutlinedButton(onClick = onManageKeymaps, modifier = Modifier.fillMaxWidth()) {
                Text("LƯU / TẢI KEYMAP CHO GAME NÀY", color = Aqua)
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
                        Text("${map.label}  →  ${if (map.controlType == "joystick") "joystick ${map.direction}" else "chạm"}", color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
    adbConnected: Boolean,
    mappingEnabled: Boolean,
    onRefreshOtg: () -> Unit,
    onRefreshAdb: () -> Unit,
    onDisconnectAdb: () -> Unit,
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
        item { AdbPairingCard(adbConnected, onRefreshAdb, onDisconnectAdb) }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CƠ CHẾ NHẬN PHÍM", color = TextMain, fontWeight = FontWeight.Bold)
                    PermissionLine("Wi‑Fi ADB đã kết nối", adbConnected)
                    OutlinedButton(onClick = onRefreshAdb, modifier = Modifier.fillMaxWidth()) { Text("Kiểm tra trạng thái kết nối") }
                    OutlinedButton(onClick = onOpenOverlayPermission, modifier = Modifier.fillMaxWidth()) { Text("Cấp quyền cửa sổ nổi") }
                    Text("Bản này không khai báo AccessibilityService. Nếu ROM chặn getevent, ánh xạ phím không hoạt động trên thiết bị đó; Android không có cách tổng quát an toàn khác để đọc/chặn phím toàn hệ thống nếu thiếu quyền đặc biệt.", color = TextMuted, fontSize = 11.sp)
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
            Text("ChillOTGConTro  ·  0.1.7", color = TextMuted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AdbPairingCard(adbConnected: Boolean, onRefresh: () -> Unit, onDisconnect: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("adb_config", Context.MODE_PRIVATE) }
    var status by remember { mutableStateOf(prefs.getString("last_status", "Chưa ghép đôi") ?: "Chưa ghép đôi") }

    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("GHÉP NỐI WI‑FI ADB", color = TextMain, fontWeight = FontWeight.Bold)
            Text("Tự dò IP và cổng qua mDNS · Android 11 trở lên · cùng mạng Wi‑Fi", color = TextMuted, fontSize = 11.sp)
            Text("1. Bật Wireless debugging trong Tùy chọn nhà phát triển.\n2. Bấm Bắt đầu ghép nối.\n3. Mở ‘Pair device with pairing code’.\n4. Nhập mã 6 số khi ChillOTG gửi thông báo.", color = TextMuted, fontSize = 12.sp)
            Text("USB debugging có dây khác với Wireless debugging; ứng dụng không thể tự bật gỡ lỗi hệ thống.", color = Amber, fontSize = 11.sp)
            Text("Trạng thái: ${if (adbConnected) "Đã kết nối Wi‑Fi ADB" else status}", color = if (adbConnected) Aqua else Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= 33) {
                    val required = listOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.NEARBY_WIFI_DEVICES)
                    val missing = required.filter { context.checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
                    if (missing.isNotEmpty()) {
                        (context as? Activity)?.requestPermissions(missing.toTypedArray(), 742)
                        Toast.makeText(context, "Cho phép Thông báo và Thiết bị ở gần, rồi bấm lại để bắt đầu.", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                }
                status = "Đang tự dò dịch vụ ghép đôi trên điện thoại này…"
                prefs.edit().putString("last_status", status).apply()
                runCatching {
                    AdbPairingNotifications.startDiscovery(context)
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    Toast.makeText(context, "Mở Wireless debugging → Pair device with pairing code. ChillOTG sẽ tự gửi thông báo khi tìm thấy.", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(context, "Mở Tùy chọn nhà phát triển từ Cài đặt Android.", Toast.LENGTH_LONG).show()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("BẮT ĐẦU GHÉP NỐI TỰ ĐỘNG") }
            OutlinedButton(onClick = {
                status = prefs.getString("last_status", if (prefs.getBoolean("connected", false)) "Đã kết nối" else "Chưa ghép đôi") ?: "Chưa ghép đôi"
                onRefresh()
            }, modifier = Modifier.fillMaxWidth()) { Text("Làm mới trạng thái") }
            if (adbConnected) OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Ngắt phiên ADB của ChillOTG") }
            OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { Toast.makeText(context, "Mở Tùy chọn nhà phát triển từ Cài đặt Android.", Toast.LENGTH_LONG).show() }
            }, modifier = Modifier.fillMaxWidth()) { Text("MỞ TÙY CHỌN NHÀ PHÁT TRIỂN") }
            Text("Mã ghép đôi chỉ dùng một lần và không lưu. Chỉ ghép nối trên mạng tin cậy.", color = Amber, fontSize = 10.sp)
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

private fun startFloatingBubble(context: Context) {
    val intent = Intent(context, FloatingBubbleService::class.java)
    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
}


@Composable
private fun KeymapLibraryDialog(
    profile: String,
    mappings: List<KeyMapping>,
    autoOpenImport: Boolean,
    onImportLaunched: () -> Unit,
    onApply: (List<KeyMapping>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var presetName by remember(profile) { mutableStateOf(profile.takeLast(30).ifBlank { "Keymap mới" }) }
    var presets by remember { mutableStateOf(MappingProfile.presets(context)) }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val raw = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } }.getOrNull()
            val imported = raw?.let { MappingProfile.importJson(context, it, profile) }
            if (imported != null) {
                onApply(imported)
                Toast.makeText(context, "Keymap đã nhập và áp dụng cho game hiện tại", Toast.LENGTH_LONG).show()
            } else Toast.makeText(context, "Không đọc được keymap JSON hợp lệ", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(autoOpenImport) {
        if (autoOpenImport) {
            importPicker.launch(arrayOf("application/json", "text/plain"))
            onImportLaunched()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thư viện keymap") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Game hiện tại: ${profile.takeLast(38)} · ${mappings.size} thành phần", color = TextMuted, fontSize = 11.sp)
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it.take(48) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Tên cấu hình để lưu") },
                    placeholder = { Text("Ví dụ: PUBG · 4 ngón") }
                )
                Button(onClick = {
                    if (MappingProfile.savePreset(context, presetName, profile, mappings)) {
                        presets = MappingProfile.presets(context)
                        Toast.makeText(context, "Đã lưu keymap ‘${presetName.trim()}’", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(context, "Nhập tên cấu hình trước khi lưu", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("LƯU KEYMAP HIỆN TẠI") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { importPicker.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.weight(1f)) { Text("Nhập JSON") }
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_SUBJECT, "ChillOTG keymap · $profile")
                            putExtra(Intent.EXTRA_TEXT, MappingProfile.exportJson(profile, mappings))
                        }
                        context.startActivity(Intent.createChooser(send, "Chia sẻ keymap"))
                    }, modifier = Modifier.weight(1f)) { Text("Chia sẻ") }
                }
                Text("CẤU HÌNH ĐÃ LƯU", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                if (presets.isEmpty()) Text("Chưa có preset. Lưu cấu hình hiện tại để dùng lại cho game khác.", color = TextMuted, fontSize = 12.sp)
                presets.forEach { preset ->
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(preset.name, color = TextMain, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Nguồn: ${preset.sourceProfile.takeLast(30)} · ${preset.mappings.size} thành phần", color = TextMuted, fontSize = 10.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Button(onClick = {
                                    val loaded = MappingProfile.applyPreset(context, preset.id, profile)
                                    if (loaded != null) {
                                        onApply(loaded)
                                        Toast.makeText(context, "Đã tải ‘${preset.name}’ vào game này", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) { Text("Tải cho game", fontSize = 10.sp) }
                                OutlinedButton(onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_SUBJECT, "ChillOTG keymap · ${preset.name}")
                                        putExtra(Intent.EXTRA_TEXT, MappingProfile.exportJson(preset.sourceProfile, preset.mappings))
                                    }
                                    context.startActivity(Intent.createChooser(send, "Chia sẻ ${preset.name}"))
                                }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) { Text("Chia sẻ", fontSize = 10.sp) }
                                TextButton(onClick = {
                                    MappingProfile.deletePreset(context, preset.id)
                                    presets = MappingProfile.presets(context)
                                }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)) { Text("Xóa", color = Amber, fontSize = 10.sp) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}
