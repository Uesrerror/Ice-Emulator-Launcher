package com.retro.unifiedlauncher
import android.app.Application
import android.content.*
import android.content.pm.PackageManager
import android.net.*
import android.os.*
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.core.content.FileProvider
import androidx.core.view.*
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@Composable
fun UnifiedEmulatorLauncherApp(vm: LauncherViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val ctx = LocalContext.current
    val state by vm.uiState.collectAsState()
    val launcher = remember(ctx) { EmulatorIntentLauncher(ctx) }
    val focusReq = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) { try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}; vm.applyCustomCover(uri) }
    }

    LaunchedEffect(state.focusedGameIndex, state.selectedPlatformId) {
        if (state.filteredGames.isNotEmpty()) listState.animateScrollToItem(state.focusedGameIndex.coerceIn(0, state.filteredGames.lastIndex))
    }
    LaunchedEffect(Unit) { focusReq.requestFocus() }

    val playFocused: () -> Unit = {
        val g = state.focusedGame; val cfg = state.activePlatformConfig
        if (g != null && cfg != null) {
            when (val r = launcher.launch(g, cfg)) {
                is EmulatorIntentLauncher.LaunchResult.Success -> Toast.makeText(ctx, "กำลังรัน ${g.title} ด้วย ${cfg.emulatorName}", Toast.LENGTH_SHORT).show()
                is EmulatorIntentLauncher.LaunchResult.EmulatorNotInstalled -> Toast.makeText(ctx, "กรุณาติดตั้งแอป ${r.name} (${r.pkg})", Toast.LENGTH_LONG).show()
                is EmulatorIntentLauncher.LaunchResult.Error -> Toast.makeText(ctx, r.msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val accent = Color(state.activePlatformConfig?.console?.accentColorHex ?: 0xFF00E5FF)
    val bgModel = state.focusedGame?.fanartUrl ?: state.activePlatformConfig?.customWallpaperUri ?: state.focusedGame?.effectiveCoverModel

    Box(
        modifier = Modifier.fillMaxSize().background(IceDarkBg).focusRequester(focusReq).focusable().onPreviewKeyEvent { ev ->
            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (ev.nativeKeyEvent.keyCode) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> if (state.activeTopTab == TopNavTab.MY_GAMES) { vm.moveGameFocus(-1); true } else false
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> if (state.activeTopTab == TopNavTab.MY_GAMES) { vm.moveGameFocus(1); true } else false
                AndroidKeyEvent.KEYCODE_BUTTON_L1, AndroidKeyEvent.KEYCODE_PAGE_UP -> { vm.cycleConfiguredPlatform(-1); true }
                AndroidKeyEvent.KEYCODE_BUTTON_R1, AndroidKeyEvent.KEYCODE_PAGE_DOWN -> { vm.cycleConfiguredPlatform(1); true }
                AndroidKeyEvent.KEYCODE_BUTTON_A, AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> if (state.activeTopTab == TopNavTab.MY_GAMES) { playFocused(); true } else false
                AndroidKeyEvent.KEYCODE_BUTTON_Y -> { vm.toggleSearchDialog(!state.isSearchDialogVisible); true }
                AndroidKeyEvent.KEYCODE_BUTTON_X -> { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)); true }
                AndroidKeyEvent.KEYCODE_BUTTON_START, AndroidKeyEvent.KEYCODE_MENU -> { vm.selectTopTab(if (state.activeTopTab == TopNavTab.SETTINGS) TopNavTab.MY_GAMES else TopNavTab.SETTINGS); true }
                else -> false
            }
        }
    ) {
        DynamicFanartBackground(bgModel, accent)
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TopConsoleNavBar(state.activeTopTab, vm::selectTopTab, { vm.toggleSearchDialog(true) }, { vm.selectTopTab(TopNavTab.SETTINGS) })
                if (state.activeTopTab == TopNavTab.MY_GAMES) {
                    ConsoleCategorySelector(state.configuredPlatforms, state.activePlatformConfig?.platformId, vm::selectPlatformById) { vm.openPlatformEditor(null) }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                if (state.activeTopTab == TopNavTab.SETTINGS) {
                    IceConsoleSettingsHub(
                        state, vm::selectSettingsSection, { vm.openPlatformEditor(null) }, { vm.openPlatformEditor(it) },
                        { vm.saveAndScanPlatform(it, true) }, vm::deleteConfiguredPlatform, vm::saveAndScanPlatform, vm::closePlatformEditor,
                        vm::toggleScraperSource, vm::moveScraperPriority, vm::openScraperAccountDialog, vm::saveScraperAccount, vm::scrapeAllInCurrentPlatform
                    )
                } else if (state.configuredPlatforms.isEmpty()) {
                    EmptyPlatformOnboardingCard { vm.openPlatformEditor(null) }
                } else {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        HorizontalGameCarousel(state.filteredGames, state.focusedGameIndex, state.activePlatformConfig?.aspectRatio ?: CoverAspectRatio.RATIO_3_4, accent, listState, vm::setFocusedGameIndex, playFocused) { vm.openPlatformEditor(state.activePlatformConfig) }
                        GameInformationPanel(state.focusedGame, state.activePlatformConfig, playFocused, { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, vm::scrapeFocusedGame)
                    }
                }
            }
            GamepadButtonPromptsFooter(state.statusBannerMessage, playFocused, { vm.toggleSearchDialog(true) }, { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                vm.selectTopTab(if (state.activeTopTab == TopNavTab.SETTINGS) TopNavTab.MY_GAMES else TopNavTab.SETTINGS)
            }
        }
        if (state.isSearchDialogVisible) {
            AlertDialog(
                onDismissRequest = { vm.toggleSearchDialog(false) }, containerColor = Color(0xFF121826), titleContentColor = Color.White, textContentColor = Color.White,
                title = { Text("ค้นหาเกมในคลัง", fontWeight = FontWeight.Bold) },
                text = { OutlinedTextField(value = state.searchQuery, onValueChange = vm::updateSearchQuery, singleLine = true, modifier = Modifier.fillMaxWidth()) },
                confirmButton = { TextButton(onClick = { vm.toggleSearchDialog(false) }) { Text("ตกลง", color = IceCyan) } },
                dismissButton = { TextButton(onClick = { vm.updateSearchQuery(""); vm.toggleSearchDialog(false) }) { Text("ล้าง", color = Color.White) } }
            )
        }
    }
}

@Composable
fun TopConsoleNavBar(activeTab: TopNavTab, onSelectTab: (TopNavTab) -> Unit, onOpenSearch: () -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    var timeText by remember { mutableStateOf("") }
    var batteryPct by remember { mutableIntStateOf(100) }
    var wifiOn by remember { mutableStateOf(true) }
    LaunchedEffect(ctx) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            timeText = fmt.format(Date())
            val bm = ctx.registerReceiver(null as BroadcastReceiver?, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val l = bm?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1; val s = bm?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (l >= 0 && s > 0) batteryPct = (l * 100) / s
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            wifiOn = cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            delay(15_000L)
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        // โลโก้แอปตรงวงสีเขียวซ้ายสุด + ขยับวงสีแดง (Player One & แถบเมนู) ไปทางขวาตามรูปที่ 5
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).shadow(12.dp, RoundedCornerShape(12.dp), spotColor = IceCyan).clip(RoundedCornerShape(12.dp))
                    .border(1.5.dp, Color(0xFF40C4FF), RoundedCornerShape(12.dp)).clickable { onSelectTab(TopNavTab.MY_GAMES) }
            ) {
                Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = "Ice Logo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(IceCyan), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.Black, modifier = Modifier.size(17.dp))
                    }
                    Text("Player One", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TopNavTab.entries.forEach { tab ->
                        val sel = tab == activeTab
                        Text(
                            text = tab.title, color = if (sel) Color.White else Color.White.copy(alpha = 0.58f), fontSize = 15.sp, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (sel) Color(0x3300E5FF) else Color.Transparent)
                                .border(if (sel) 1.dp else 0.dp, if (sel) IceCyan else Color.Transparent, RoundedCornerShape(50)).clickable { onSelectTab(tab) }.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.40f)).padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onOpenSearch, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White) }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Settings, contentDescription = null, tint = IceCyan) }
            Icon(if (wifiOn) Icons.Default.Wifi else Icons.Default.WifiOff, contentDescription = null, tint = IceCyan, modifier = Modifier.size(19.dp))
            Text("$batteryPct%", color = IceGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(timeText, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ConsoleCategorySelector(platforms: List<ConfiguredPlatform>, selectedId: String?, onSelect: (String) -> Unit, onAdd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) { Text("L1", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) }
        LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(platforms) { _, cfg ->
                val sel = cfg.platformId == selectedId
                Surface(
                    color = if (sel) Color(cfg.console.accentColorHex) else Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(50),
                    border = BorderStroke(if (sel) 1.5.dp else 1.dp, Color.White.copy(alpha = if (sel) 0.9f else 0.15f)), modifier = Modifier.clickable { onSelect(cfg.platformId) }
                ) { Text(cfg.console.shortTag, color = Color.White, fontSize = 13.sp, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            }
            item {
                Surface(color = Color(0x2600E5FF), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, IceCyan), modifier = Modifier.clickable { onAdd() }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = IceCyan, modifier = Modifier.size(15.dp))
                        Text("เพิ่มหมวดเกม", color = IceCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) { Text("R1", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) }
    }
}

@Composable
fun EmptyPlatformOnboardingCard(onAddFirstPlatform: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = Color(0xCC0D1526), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.5.dp, IceCyan.copy(alpha = 0.6f)), modifier = Modifier.widthIn(max = 560.dp)) {
            Column(modifier = Modifier.padding(horizontal = 32.dp, vertical = 26.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)))
                Text("ยินดีต้อนรับสู่ Ice Emulator Launcher", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("กดปุ่มด้านล่างเพื่อเลือกเครื่องเกม (เช่น PSP) เลือกแอปที่จะใช้รันเกม (เช่น PPSSPP) และเลือกโฟลเดอร์เก็บเกมในเครื่อง หมวดเกมจึงจะแสดงบนหน้าหลักครับ", color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, textAlign = TextAlign.Center)
                Button(onClick = onAddFirstPlatform, colors = ButtonDefaults.buttonColors(containerColor = IceCyan), shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
                    Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("ตั้งค่าเพิ่มแพลตฟอร์มเครื่องเกมแรกของคุณ", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun IceConsoleSettingsHub(
    state: LauncherUiState, onSelectSection: (SettingsSection) -> Unit, onAddPlatform: () -> Unit, onEditPlatform: (ConfiguredPlatform) -> Unit,
    onRescanPlatform: (ConfiguredPlatform) -> Unit, onDeletePlatform: (String) -> Unit, onSavePlatform: (ConfiguredPlatform, Boolean) -> Unit,
    onCloseEditor: () -> Unit, onToggleScraper: (String) -> Unit, onMoveScraper: (Int, Int) -> Unit,
    onOpenAccount: (ScraperSourceConfig?) -> Unit, onSaveAccount: (String, String, String) -> Unit, onScrapeAll: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(color = IceCardBg, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, IceCyan.copy(alpha = 0.28f)), modifier = Modifier.width(230.dp).fillMaxHeight()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ICE CONSOLE SETTINGS", color = IceCyan, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                SettingsSection.entries.forEach { sec ->
                    val sel = state.activeSettingsSection == sec
                    Surface(
                        color = if (sel) Color(0x3300E5FF) else Color.White.copy(alpha = 0.04f), shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (sel) IceCyan else Color.White.copy(alpha = 0.08f)), modifier = Modifier.fillMaxWidth().clickable { onSelectSection(sec) }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(if (sec == SettingsSection.PLATFORMS) Icons.Default.SportsEsports else Icons.Default.CloudDownload, contentDescription = null, tint = if (sel) IceCyan else Color.White, modifier = Modifier.size(20.dp))
                            Column {
                                Text(sec.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(sec.subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        Surface(color = IceCardBg, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (state.isPlatformEditorOpen && state.editingPlatform != null) {
                PlatformConfigurationStudio(state.editingPlatform, onSavePlatform, { onDeletePlatform(state.editingPlatform.platformId) }, onCloseEditor)
            } else if (state.activeSettingsSection == SettingsSection.PLATFORMS) {
                PlatformsManagementPanel(state.configuredPlatforms, onAddPlatform, onEditPlatform, onRescanPlatform)
            } else {
                WebCoverScraperSettingsPanel(state.scraperSources, state.editingScraperAccount, onToggleScraper, onMoveScraper, onOpenAccount, onSaveAccount, onScrapeAll)
            }
        }
    }
}

@Composable
fun PlatformsManagementPanel(platforms: List<ConfiguredPlatform>, onAdd: () -> Unit, onEdit: (ConfiguredPlatform) -> Unit, onRescan: (ConfiguredPlatform) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("แพลตฟอร์มเครื่องเกมที่เปิดใช้งาน", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("เฉพาะหมวดเกมที่ตั้งค่าไว้ในหน้านี้เท่านั้นที่จะแสดงบนหน้าหลักของแอป", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
            }
            Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = IceCyan), shape = RoundedCornerShape(50)) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black); Spacer(Modifier.width(6.dp))
                Text("เพิ่มแพลตฟอร์ม", color = Color.Black, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(14.dp))
        if (platforms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ยังไม่มีแพลตฟอร์มที่ตั้งค่าไว้ กดปุ่ม '+ เพิ่มแพลตฟอร์ม' มุมขวาบนเพื่อเริ่มต้น", color = Color.White.copy(alpha = 0.6f)) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(platforms) { _, cfg ->
                    Surface(color = IceRowBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(cfg.console.accentColorHex).copy(alpha = 0.55f)), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Surface(color = Color(cfg.console.accentColorHex), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(44.dp)) {
                                    Box(contentAlignment = Alignment.Center) { Text(cfg.console.shortTag, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
                                }
                                Column {
                                    Text(cfg.console.displayName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text("แอปอีมูเลเตอร์: ${cfg.emulatorName} (${cfg.emulatorPackage})", color = IceCyan, fontSize = 12.sp)
                                    Text("โฟลเดอร์ ROM: ${cfg.romFolderPath}", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                IconButton(onClick = { onRescan(cfg) }) { Icon(Icons.Default.Refresh, contentDescription = null, tint = IceGreen) }
                                IconButton(onClick = { onEdit(cfg) }) { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlatformConfigurationStudio(init: ConfiguredPlatform, onSave: (ConfiguredPlatform, Boolean) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    var console by remember { mutableStateOf(init.console) }
    var emuName by remember { mutableStateOf(init.emulatorName) }
    var emuPkg by remember { mutableStateOf(init.emulatorPackage) }
    var emuAct by remember { mutableStateOf(init.emulatorActivity) }
    var core by remember { mutableStateOf(init.retroArchCore) }
    var folderPath by remember { mutableStateOf(init.romFolderPath) }
    var treeUri by remember { mutableStateOf(init.romTreeUriString) }
    var aspect by remember { mutableStateOf(init.aspectRatioLabel) }
    var wallUri by remember { mutableStateOf(init.customWallpaperUri) }
    var addDemo by remember { mutableStateOf(true) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            treeUri = uri.toString(); folderPath = uri.lastPathSegment?.replace("primary:", "/storage/emulated/0/") ?: uri.toString()
        }
    }
    val wallPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? -> if (uri != null) wallUri = uri.toString() }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("ปรับแต่งแพลตฟอร์มเครื่องเกม", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            TextButton(onClick = onCancel) { Text("ย้อนกลับ", color = IceCyan) }
        }
        Text("1. เลือกประเภทของเครื่องเกม", color = IceCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(ConsolePlatform.entries) { _, c ->
                val sel = c == console
                Surface(
                    color = if (sel) Color(c.accentColorHex) else Color.White.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (sel) Color.White else Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.clickable {
                        console = c; val pr = c.emulatorPresets.first()
                        emuName = pr.displayName; emuPkg = pr.packageName; emuAct = pr.activityName; core = pr.retroArchCore
                        folderPath = "android/download/รวมเกม${c.id}"
                    }
                ) { Text(c.displayName, color = Color.White, fontSize = 12.sp, fontWeight = if (sel) FontWeight.ExtraBold else FontWeight.Medium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
            }
        }
        Text("2. เลือกแอปอีมูเลเตอร์ที่จะใช้รันเกมของหมวดนี้", color = IceCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(console.emulatorPresets) { _, pr ->
                val sel = pr.packageName == emuPkg && pr.displayName == emuName
                Surface(
                    color = if (sel) Color(0x3300E5FF) else Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (sel) IceCyan else Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.clickable { emuName = pr.displayName; emuPkg = pr.packageName; emuAct = pr.activityName; core = pr.retroArchCore }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(pr.displayName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(pr.packageName, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                    }
                }
            }
        }
        Text("3. ตำแหน่งที่อยู่ของโฟลเดอร์รวม ROM ของเครื่องนี้ (เช่น android/download/รวมเกมpsp)", color = IceCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = folderPath, onValueChange = { folderPath = it }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = { folderPicker.launch(null) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = IceCyan); Spacer(Modifier.width(6.dp))
                Text("เลือกโฟลเดอร์", color = Color.White, fontSize = 12.sp)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("อัตราส่วนหน้าปก:", color = Color.White, fontSize = 12.sp)
                CoverAspectRatio.entries.forEach { r ->
                    val sel = r.label == aspect
                    Surface(color = if (sel) Color(0x3300E5FF) else Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, if (sel) IceCyan else Color.White.copy(alpha = 0.15f)), modifier = Modifier.clickable { aspect = r.label }) {
                        Text(r.label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
            OutlinedButton(onClick = { wallPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, border = BorderStroke(1.dp, IceCyan)) {
                Text(if (wallUri != null) "เปลี่ยนภาพพื้นหลังแล้ว" else "เลือกภาพพื้นหลัง", color = Color.White, fontSize = 12.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { addDemo = !addDemo }) {
            Checkbox(checked = addDemo, onCheckedChange = { addDemo = it })
            Text("แสดงปกเกมตัวอย่างหากในโฟลเดอร์ยังไม่มีไฟล์ ROM", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton(onClick = onDelete, border = BorderStroke(1.dp, Color(0xFFFF5252)), shape = RoundedCornerShape(50)) { Text("ลบ", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold) }
            Button(
                onClick = { onSave(ConfiguredPlatform(console.id, emuName, emuPkg, emuAct, core, folderPath, treeUri, aspect, wallUri), addDemo) },
                colors = ButtonDefaults.buttonColors(containerColor = IceCyan), shape = RoundedCornerShape(50), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
            ) { Text("เซฟ", color = Color.Black, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

@Composable
fun WebCoverScraperSettingsPanel(
    sources: List<ScraperSourceConfig>, editingAccount: ScraperSourceConfig?, onToggle: (String) -> Unit,
    onMove: (Int, Int) -> Unit, onOpenAccount: (ScraperSourceConfig?) -> Unit, onSaveAccount: (String, String, String) -> Unit, onScrapeAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("ปรับแต่งการดึงปกไฟล์เกม", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("เลือกเว็บฐานข้อมูลปกเกม (Screenscraper, Libretro, TheGamesDb, IGDB, SteamGridDB) และผูกบัญชี", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
            }
            Button(onClick = onScrapeAll, colors = ButtonDefaults.buttonColors(containerColor = IceGreen), shape = RoundedCornerShape(50)) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.Black); Spacer(Modifier.width(6.dp))
                Text("ดึงปกทุกเกมทันที", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(sources) { idx, src ->
                Surface(color = IceRowBg, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (src.isEnabled) IceGreen.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f)), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp).clickable { onMove(idx, -1) })
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp).clickable { onMove(idx, 1) })
                            }
                            Checkbox(checked = src.isEnabled, onCheckedChange = { onToggle(src.id) }, colors = CheckboxDefaults.colors(checkedColor = IceGreen, checkmarkColor = Color.Black))
                            Column {
                                Text(src.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                if (src.accountUsername.isNotBlank()) Text("บัญชี: ${src.accountUsername}", color = IceCyan, fontSize = 11.sp)
                            }
                        }
                        if (src.requiresAccountOrKey) {
                            OutlinedButton(onClick = { onOpenAccount(src) }, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, IceCyan)) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = IceCyan, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                                Text("เพิ่มบัญชี", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    if (editingAccount != null) {
        var u by remember(editingAccount) { mutableStateOf(editingAccount.accountUsername) }
        var p by remember(editingAccount) { mutableStateOf(editingAccount.apiKeyOrPass) }
        AlertDialog(
            onDismissRequest = { onOpenAccount(null) }, containerColor = Color(0xFF101829), titleContentColor = Color.White, textContentColor = Color.White,
            title = { Text("เพิ่มบัญชี (${editingAccount.name})", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = u, onValueChange = { u = it }, label = { Text("Username") }, singleLine = true)
                    OutlinedTextField(value = p, onValueChange = { p = it }, label = { Text("Password / API Key") }, singleLine = true)
                }
            },
            confirmButton = { Button(onClick = { onSaveAccount(editingAccount.id, u, p) }) { Text("บันทึก") } },
            dismissButton = { TextButton(onClick = { onOpenAccount(null) }) { Text("ยกเลิก", color = Color.White) } }
        )
    }
}

@Composable
fun DynamicFanartBackground(fanartUrl: String?, accentColor: Color) {
    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(targetState = fanartUrl, animationSpec = tween(600, easing = FastOutSlowInEasing), label = "Fanart") { url ->
            if (!url.isNullOrBlank()) {
                AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(0.52f).blur(3.dp))
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accentColor.copy(alpha = 0.35f), IceDarkBg), radius = 1400f)))
            }
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(0.0f to Color(0xCC060911), 0.4f to Color(0x66060911), 1.0f to Color(0xF505070D))))
    }
}

@Composable
fun HorizontalGameCarousel(games: List<GameItem>, focusedIndex: Int, ratio: CoverAspectRatio, accent: Color, listState: LazyListState, onSelect: (Int) -> Unit, onLaunch: () -> Unit, onEditPlatform: () -> Unit) {
    if (games.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
            OutlinedButton(onClick = onEditPlatform, border = BorderStroke(1.dp, IceCyan)) { Text("ตั้งค่าโฟลเดอร์ ROM ของหมวดนี้", color = IceCyan) }
        }
        return
    }
    LazyRow(state = listState, modifier = Modifier.fillMaxWidth().height(225.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
        itemsIndexed(items = games, key = { _, g -> g.id }) { idx, game ->
            val isFocused = idx == focusedIndex
            val scale by animateFloatAsState(if (isFocused) 1.15f else 0.94f, tween(240), label = "scale")
            val glowBrush = Brush.linearGradient(if (isFocused) listOf(IceCyan, Color.White, accent) else listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.05f)))
            Box(
                modifier = Modifier.width(ratio.widthDp.dp).height(ratio.heightDp.dp).scale(scale)
                    .shadow(if (isFocused) 24.dp else 4.dp, RoundedCornerShape(16.dp), spotColor = accent).clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF162238), Color(0xFF0D1322)))).border(if (isFocused) 3.dp else 1.dp, glowBrush, RoundedCornerShape(16.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (isFocused) onLaunch() else onSelect(idx) }
            ) {
                if (!game.effectiveCoverModel.isNullOrBlank()) {
                    AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(game.effectiveCoverModel).crossfade(true).build(), contentDescription = game.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1.0f to Color(0xE6060911))))
                Text(game.title, color = Color.White, fontSize = 12.sp, fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
            }
        }
    }
}

@Composable
fun GameInformationPanel(game: GameItem?, cfg: ConfiguredPlatform?, onPlay: () -> Unit, onCustomCover: () -> Unit, onScrape: () -> Unit) {
    if (game == null || cfg == null) return
    val c = cfg.console
    AnimatedContent(targetState = game, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) }, label = "Info") { active ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = Color(c.accentColorHex), shape = RoundedCornerShape(6.dp)) { Text(c.shortTag, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) }
                    Text("รันด้วย: ${cfg.emulatorName} • โฟลเดอร์: ${cfg.romFolderPath}", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(active.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(active.description, color = Color.White.copy(alpha = 0.80f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 2.dp)) {
                    Button(onClick = onPlay, colors = ButtonDefaults.buttonColors(containerColor = Color.White), shape = RoundedCornerShape(50)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("Play Game", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = onCustomCover, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))) { Text("Custom Cover", color = Color.White, fontSize = 12.sp) }
                    OutlinedButton(onClick = onScrape, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, IceCyan)) { Text("Scrape Info", color = IceCyan, fontSize = 12.sp) }
                }
            }
            Surface(color = Color(0xAA101726), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)), modifier = Modifier.weight(0.95f)) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    StatMetricColumn(Icons.Default.Schedule, IceCyan, "TIME PLAYED", active.formattedPlayTime)
                    StatMetricColumn(Icons.Default.TrendingUp, IceGreen, "PROGRESS", "${active.progressPercent}%")
                    StatMetricColumn(Icons.Default.EmojiEvents, Color(0xFFFFD54F), "TROPHIES", "${active.trophiesEarned} / ${active.trophiesTotal}")
                }
            }
        }
    }
}

@Composable
fun StatMetricColumn(icon: ImageVector, tint: Color, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(15.dp))
            Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun GamepadButtonPromptsFooter(status: String?, onPlay: () -> Unit, onSearch: () -> Unit, onCustomCover: () -> Unit, onOptions: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(status ?: "Ice Console Ready • ตั้งค่าเครื่องเกมและโฟลเดอร์ ROM ได้ที่เมนู Settings", color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp, maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ControllerPromptChip("✕", Color(0xFF42A5F5), "Play", onPlay)
            ControllerPromptChip("△", Color(0xFF26A69A), "Search", onSearch)
            ControllerPromptChip("□", Color(0xFFEC407A), "Custom Cover", onCustomCover)
            ControllerPromptChip("OPTIONS", Color(0xFF90A4AE), "Settings", onOptions)
        }
    }
}

@Composable
fun ControllerPromptChip(symbol: String, color: Color, label: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.clickable(onClick = onClick)) {
        Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, color)) { Text(symbol, color = color, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)) }
        Text(label, color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

object SampleGameCatalog {
    fun forPlatform(id: String, folder: String): List<GameItem> = when (id) {
        "psp" -> listOf(
            GameItem(101, "God of War: Ghost of Sparta (USA)", "psp", "$folder/God_of_War_Ghost_of_Sparta.iso", null, "https://thumbnails.libretro.com/Sony%20-%20PlayStation%20Portable/Named_Boxarts/God%20of%20War%20-%20Ghost%20of%20Sparta%20(USA).png", null, "https://thumbnails.libretro.com/Sony%20-%20PlayStation%20Portable/Named_Snaps/God%20of%20War%20-%20Ghost%20of%20Sparta%20(USA).png", "ศึกแห่งสปาร์ตาบนเครื่อง PSP ดึงปกแท้จาก Libretro Thumbnails", "2010", "Ready at Dawn", "Action", 1480, 82, 29, 36),
            GameItem(102, "Monster Hunter Freedom Unite (USA)", "psp", "$folder/Monster_Hunter_Freedom_Unite.iso", null, "https://thumbnails.libretro.com/Sony%20-%20PlayStation%20Portable/Named_Boxarts/Monster%20Hunter%20Freedom%20Unite%20(USA).png", null, "https://thumbnails.libretro.com/Sony%20-%20PlayStation%20Portable/Named_Snaps/Monster%20Hunter%20Freedom%20Unite%20(USA).png", "ออกล่ามังกรระดับตำนานพร้อมภารกิจ G-Rank บน PSP", "2008", "Capcom", "Action RPG", 7620, 91, 44, 50)
        )
        else -> ConsolePlatform.fromId(id).let { c ->
            listOf(GameItem(900L + c.ordinal, "${c.shortTag} Sample Game", c.id, "$folder/Sample.${c.supportedExtensions.first()}", null, "https://picsum.photos/seed/${c.id}_c/600/850", null, "https://picsum.photos/seed/${c.id}_f/1920/1080", "เกมตัวอย่างประจำหมวด ${c.displayName}", "2006", c.shortTag, "Action", 420, 65, 14, 30))
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { ctrl ->
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { UnifiedEmulatorLauncherApp() } }
    }
}
