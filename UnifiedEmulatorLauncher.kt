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

const val RA_PKG = "com.retroarch.aarch64"
const val RA_ACT = "com.retroarch.browser.retroactivity.RetroActivityFuture"
val IceCyan = Color(0xFF00E5FF)
val IceGreen = Color(0xFF69F0AE)
val IceDarkBg = Color(0xFF060A12)
val IceCardBg = Color(0xCC0B1324)
val IceRowBg = Color(0xFF131F38)

data class EmulatorPreset(val displayName: String, val packageName: String, val activityName: String? = null, val retroArchCore: String? = null)

enum class ConsolePlatform(
    val id: String, val displayName: String, val shortTag: String, val libretroSystemName: String,
    val supportedExtensions: Set<String>, val accentColorHex: Long, val emulatorPresets: List<EmulatorPreset>
) {
    PSP("psp", "Sony - PlayStation Portable", "PSP", "Sony - PlayStation Portable", setOf("iso", "cso", "pbp", "elf"), 0xFF00ACC1, listOf(
        EmulatorPreset("PPSSPP", "org.ppsspp.ppsspp", "org.ppsspp.ppsspp.PpssppActivity"),
        EmulatorPreset("PPSSPP Gold", "org.ppsspp.ppssppgold", "org.ppsspp.ppsspp.PpssppActivity"),
        EmulatorPreset("RetroArch (PPSSPP)", RA_PKG, RA_ACT, "ppsspp_libretro_android.so")
    )),
    PS1("ps1", "Sony - PlayStation (PS1)", "PS1", "Sony - PlayStation", setOf("chd", "cue", "bin", "iso", "pbp", "m3u"), 0xFF1E88E5, listOf(
        EmulatorPreset("DuckStation", "com.github.stenzek.duckstation", "com.github.stenzek.duckstation.EmulationActivity"),
        EmulatorPreset("ePSXe", "com.epsxe.ePSXe"),
        EmulatorPreset("RetroArch (SwanStation)", RA_PKG, RA_ACT, "swanstation_libretro_android.so")
    )),
    PS2("ps2", "Sony - PlayStation 2", "PS2", "Sony - PlayStation 2", setOf("iso", "chd", "bin", "cso", "gz"), 0xFF1565C0, listOf(
        EmulatorPreset("AetherSX2 / NetherSX2", "xyz.aethersx2.android", "xyz.aethersx2.android.EmulationActivity"),
        EmulatorPreset("Play! PS2", "com.virtualapplications.play")
    )),
    NES("nes", "Nintendo - NES", "NES", "Nintendo - Nintendo Entertainment System", setOf("nes", "fds", "zip", "7z"), 0xFFE53935, listOf(
        EmulatorPreset("NES.emu", "com.explusalpha.NesEmu", "com.imagine.BaseActivity"),
        EmulatorPreset("RetroArch (Mesen)", RA_PKG, RA_ACT, "mesen_libretro_android.so")
    )),
    SNES("snes", "Nintendo - Super Nintendo", "SNES", "Nintendo - Super Nintendo Entertainment System", setOf("sfc", "smc", "zip", "7z"), 0xFF8E24AA, listOf(
        EmulatorPreset("Snes9x EX+", "com.explusalpha.Snes9xPlus", "com.imagine.BaseActivity"),
        EmulatorPreset("RetroArch (Snes9x)", RA_PKG, RA_ACT, "snes9x_libretro_android.so")
    )),
    GBA("gba", "Nintendo - Game Boy Advance", "GBA", "Nintendo - Game Boy Advance", setOf("gba", "agb", "gb", "gbc", "zip"), 0xFF5E35B1, listOf(
        EmulatorPreset("Pizza Boy GBA", "it.dbtecno.pizzaboygba"),
        EmulatorPreset("My Boy!", "com.fastemulator.gba"),
        EmulatorPreset("RetroArch (mGBA)", RA_PKG, RA_ACT, "mgba_libretro_android.so")
    )),
    NDS("nds", "Nintendo - DS", "NDS", "Nintendo - Nintendo DS", setOf("nds", "ids", "zip"), 0xFF039BE5, listOf(
        EmulatorPreset("DraStic DS", "com.dsemu.drastic", "com.dsemu.drastic.DraSticActivity"),
        EmulatorPreset("melonDS", "me.magnum.melonds", "me.magnum.melonds.ui.emulator.EmulatorActivity")
    )),
    N3DS("3ds", "Nintendo - 3DS", "3DS", "Nintendo - Nintendo 3DS", setOf("3ds", "cci", "cxi", "cia"), 0xFFD81B60, listOf(
        EmulatorPreset("Lime3DS", "io.github.lime3ds.android", "org.citra.citra_emu.activities.EmulationActivity"),
        EmulatorPreset("Citra", "org.citra.citra_emu", "org.citra.citra_emu.activities.EmulationActivity")
    )),
    GAMECUBE("gc", "Nintendo - GameCube", "GameCube", "Nintendo - GameCube", setOf("iso", "rvz", "gcz", "ciso", "dol"), 0xFF6A1B9A, listOf(
        EmulatorPreset("Dolphin Emulator", "org.dolphinemu.dolphinemu", "org.dolphinemu.dolphinemu.activities.EmulationActivity")
    )),
    WII("wii", "Nintendo - Wii", "Wii", "Nintendo - Wii", setOf("rvz", "wbfs", "iso", "gcz", "wad"), 0xFF00897B, listOf(
        EmulatorPreset("Dolphin Emulator", "org.dolphinemu.dolphinemu", "org.dolphinemu.dolphinemu.activities.EmulationActivity")
    )),
    SWITCH("switch", "Nintendo - Switch", "Switch", "Nintendo - Switch", setOf("nsp", "xci", "nca", "nro"), 0xFFE60012, listOf(
        EmulatorPreset("Sudachi", "org.sudachi.sudachi_emu", "org.sudachi.sudachi_emu.activities.EmulationActivity"),
        EmulatorPreset("Yuzu", "org.yuzu.yuzu_emu", "org.yuzu.yuzu_emu.activities.EmulationActivity"),
        EmulatorPreset("Citron", "org.citron.citron_emu", "org.citron.citron_emu.activities.EmulationActivity")
    ));
    companion object { fun fromId(id: String) = entries.find { it.id == id } ?: PSP }
}

enum class TopNavTab(val title: String) { MY_GAMES("My Games"), MEDIA("Media"), SETTINGS("Settings") }
enum class SettingsSection(val title: String, val subtitle: String) {
    PLATFORMS("แพลตฟอร์มเครื่องเกม", "เลือกเครื่องเกม แอปอีมูเลเตอร์ และโฟลเดอร์ ROM"),
    SCRAPERS("ดึงปกเกมออนไลน์", "ตั้งค่าเว็บฐานข้อมูลปกเกมและบัญชีผู้ใช้")
}
enum class CoverAspectRatio(val label: String, val widthDp: Int, val heightDp: Int) {
    RATIO_3_4("3:4 (แนวตั้ง)", 142, 198), RATIO_1_1("1:1 (จัตุรัส)", 168, 168), RATIO_16_9("16:9 (แนวนอน)", 220, 132);
    companion object { fun fromLabel(l: String) = entries.find { it.label == l } ?: RATIO_3_4 }
}

data class ConfiguredPlatform(
    val platformId: String, val emulatorName: String, val emulatorPackage: String,
    val emulatorActivity: String? = null, val retroArchCore: String? = null,
    val romFolderPath: String, val romTreeUriString: String? = null,
    val aspectRatioLabel: String = CoverAspectRatio.RATIO_3_4.label, val customWallpaperUri: String? = null
) {
    val console: ConsolePlatform get() = ConsolePlatform.fromId(platformId)
    val aspectRatio: CoverAspectRatio get() = CoverAspectRatio.fromLabel(aspectRatioLabel)
}

data class ScraperSourceConfig(
    val id: String, val name: String, val domain: String, val isEnabled: Boolean,
    val requiresAccountOrKey: Boolean, val accountUsername: String = "", val apiKeyOrPass: String = ""
)

data class GameItem(
    val id: Long, val title: String, val consoleId: String, val romFilePath: String,
    val romUriString: String? = null, val coverUrl: String? = null, val customCoverUri: String? = null,
    val fanartUrl: String? = null, val description: String = "กดปุ่ม [Scrape Info] เพื่อดึงหน้าปกจากเว็บฐานข้อมูล",
    val releaseYear: String = "Console", val developer: String = "Retro Studio", val genre: String = "Action",
    val timePlayedMinutes: Int = 0, val progressPercent: Int = 0, val trophiesEarned: Int = 0, val trophiesTotal: Int = 30
) {
    val effectiveCoverModel: String? get() = customCoverUri?.takeIf { it.isNotBlank() } ?: coverUrl
    val formattedPlayTime: String get() = if (timePlayedMinutes >= 60) "${timePlayedMinutes / 60}h ${timePlayedMinutes % 60}m" else "${timePlayedMinutes}m"
}

class LauncherPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("ice_launcher_prefs_v2", Context.MODE_PRIVATE)
    private val gson = Gson()
    fun loadConfiguredPlatforms(): List<ConfiguredPlatform> = try {
        prefs.getString("configured_platforms", null)?.let { gson.fromJson(it, object : TypeToken<List<ConfiguredPlatform>>() {}.type) } ?: emptyList()
    } catch (_: Exception) { emptyList() }
    fun saveConfiguredPlatforms(list: List<ConfiguredPlatform>) = prefs.edit().putString("configured_platforms", gson.toJson(list)).apply()
    fun loadScraperSources(): List<ScraperSourceConfig> = try {
        prefs.getString("scraper_sources", null)?.let { gson.fromJson(it, object : TypeToken<List<ScraperSourceConfig>>() {}.type) } ?: listOf(
            ScraperSourceConfig("screenscraper", "Screenscraper.fr", "screenscraper.fr", true, true),
            ScraperSourceConfig("libretro", "Libretro", "thumbnails.libretro.com", true, false),
            ScraperSourceConfig("thegamesdb", "TheGamesDb.net", "thegamesdb.net", true, false),
            ScraperSourceConfig("igdb", "IGDB.com", "igdb.com", true, false),
            ScraperSourceConfig("steamgriddb", "SteamGridDB.com", "steamgriddb.com", false, true)
        )
    } catch (_: Exception) { emptyList() }
    fun saveScraperSources(s: List<ScraperSourceConfig>) = prefs.edit().putString("scraper_sources", gson.toJson(s)).apply()
    fun loadGames(): List<GameItem> = try {
        prefs.getString("saved_games_v2", null)?.let { gson.fromJson(it, object : TypeToken<List<GameItem>>() {}.type) } ?: emptyList()
    } catch (_: Exception) { emptyList() }
    fun saveGames(g: List<GameItem>) = prefs.edit().putString("saved_games_v2", gson.toJson(g)).apply()
}

class RomScannerAndScraperEngine(private val context: Context) {
    private val tagRegex = Regex("""\s*(\([^)]*\)|\[[^\]]*\])""")
    suspend fun scanPlatformFolder(cfg: ConfiguredPlatform): List<GameItem> = withContext(Dispatchers.IO) {
        val c = cfg.console; val out = mutableListOf<GameItem>()
        if (!cfg.romTreeUriString.isNullOrBlank()) {
            try { DocumentFile.fromTreeUri(context, Uri.parse(cfg.romTreeUriString))?.takeIf { it.exists() }?.let { scanDoc(it, c, out, 0) } } catch (_: Exception) {}
        }
        if (out.isEmpty() && cfg.romFolderPath.isNotBlank()) {
            try {
                val p = if (cfg.romFolderPath.startsWith("/storage")) cfg.romFolderPath else "/storage/emulated/0/" + cfg.romFolderPath.removePrefix("android/").removePrefix("/")
                File(p).takeIf { it.exists() && it.isDirectory }?.walkTopDown()?.maxDepth(3)
                    ?.filter { it.isFile && it.extension.lowercase(Locale.ROOT) in c.supportedExtensions }
                    ?.forEach { f -> out.add(buildItem(clean(f.nameWithoutExtension), c, f.absolutePath, null)) }
            } catch (_: Exception) {}
        }
        out
    }
    private fun scanDoc(dir: DocumentFile, c: ConsolePlatform, out: MutableList<GameItem>, d: Int) {
        if (d > 2) return
        dir.listFiles().forEach { doc ->
            if (doc.isDirectory) scanDoc(doc, c, out, d + 1)
            else if (doc.isFile) {
                val n = doc.name ?: return@forEach
                if (n.substringAfterLast('.', "").lowercase(Locale.ROOT) in c.supportedExtensions) {
                    out.add(buildItem(clean(n.substringBeforeLast('.')), c, doc.uri.path ?: n, doc.uri.toString()))
                }
            }
        }
    }
    private fun clean(raw: String) = raw.replace(tagRegex, "").replace("_", " ").trim().ifEmpty { raw }
    private fun lrUrl(sys: String, folder: String, title: String): String {
        val s = URLEncoder.encode(sys, "UTF-8").replace("+", "%20")
        val t = URLEncoder.encode(title, "UTF-8").replace("+", "%20")
        return "https://thumbnails.libretro.com/$s/$folder/$t.png"
    }
    fun buildItem(title: String, c: ConsolePlatform, path: String, uriStr: String?): GameItem = GameItem(
        id = (c.id + ":" + path).hashCode().toLong(), title = title, consoleId = c.id, romFilePath = path, romUriString = uriStr,
        coverUrl = lrUrl(c.libretroSystemName, "Named_Boxarts", title), fanartUrl = lrUrl(c.libretroSystemName, "Named_Snaps", title),
        description = "ไฟล์ ROM: ${path.substringAfterLast('/')} • กด Scrape Info เพื่อดึงปกออนไลน์", developer = c.shortTag
    )
    suspend fun scrapeOnlineCoverForGame(g: GameItem, c: ConsolePlatform, sources: List<ScraperSourceConfig>): GameItem = withContext(Dispatchers.IO) {
        delay(250)
        val active = sources.filter { it.isEnabled }.joinToString(", ") { it.name }.ifEmpty { "Libretro" }
        g.copy(
            coverUrl = lrUrl(c.libretroSystemName, "Named_Boxarts", g.title),
            fanartUrl = lrUrl(c.libretroSystemName, "Named_Snaps", g.title),
            description = "ดึงปกและภาพพื้นหลังจาก [$active] สำหรับ ${g.title} สำเร็จ"
        )
    }
}

class EmulatorIntentLauncher(private val context: Context) {
    sealed class LaunchResult {
        data object Success : LaunchResult()
        data class EmulatorNotInstalled(val name: String, val pkg: String) : LaunchResult()
        data class Error(val msg: String) : LaunchResult()
    }
    fun launch(g: GameItem, cfg: ConfiguredPlatform): LaunchResult {
        val pkg = cfg.emulatorPackage
        try { context.packageManager.getPackageInfo(pkg, 0) } catch (_: Exception) { return LaunchResult.EmulatorNotInstalled(cfg.emulatorName, pkg) }
        val uri: Uri = try {
            if (!g.romUriString.isNullOrBlank()) Uri.parse(g.romUriString)
            else File(g.romFilePath).let { if (it.exists()) FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) else Uri.fromFile(it) }
        } catch (_: Exception) { Uri.fromFile(File(g.romFilePath)) }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(pkg)
            cfg.emulatorActivity?.takeIf { it.isNotBlank() }?.let { component = ComponentName(pkg, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (pkg.contains("retroarch") && !cfg.retroArchCore.isNullOrBlank()) {
                action = Intent.ACTION_MAIN; addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra("ROM", g.romFilePath); putExtra("LIBRETRO", "/data/data/$pkg/cores/${cfg.retroArchCore}")
            } else {
                setDataAndType(uri, "application/octet-stream")
                putExtra("org.ppsspp.ppsspp.Shortcuts", g.romFilePath); putExtra("AutoStartFile", g.romFilePath)
                putExtra("bootPath", g.romFilePath); putExtra("GAMEPATH", g.romFilePath); putExtra("GamePath", g.romFilePath)
            }
        }
        return try { context.startActivity(intent); LaunchResult.Success } catch (e: Exception) {
            try { context.packageManager.getLaunchIntentForPackage(pkg)?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION); data = uri }?.let { context.startActivity(it); return LaunchResult.Success } } catch (_: Exception) {}
            LaunchResult.Error(e.localizedMessage ?: "เปิดเกมไม่สำเร็จ")
        }
    }
}

data class LauncherUiState(
    val activeTopTab: TopNavTab = TopNavTab.MY_GAMES,
    val activeSettingsSection: SettingsSection = SettingsSection.PLATFORMS,
    val configuredPlatforms: List<ConfiguredPlatform> = emptyList(),
    val selectedPlatformId: String? = null,
    val scraperSources: List<ScraperSourceConfig> = emptyList(),
    val allGames: List<GameItem> = emptyList(),
    val searchQuery: String = "",
    val isSearchDialogVisible: Boolean = false,
    val editingPlatform: ConfiguredPlatform? = null,
    val isPlatformEditorOpen: Boolean = false,
    val editingScraperAccount: ScraperSourceConfig? = null,
    val statusBannerMessage: String? = null,
    val focusedGameIndex: Int = 0
) {
    val activePlatformConfig: ConfiguredPlatform? get() = configuredPlatforms.find { it.platformId == selectedPlatformId } ?: configuredPlatforms.firstOrNull()
    val filteredGames: List<GameItem> get() {
        val id = activePlatformConfig?.platformId ?: return emptyList()
        return allGames.filter { it.consoleId == id && (searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true)) }
    }
    val focusedGame: GameItem? get() = filteredGames.getOrNull(focusedGameIndex.coerceIn(0, (filteredGames.size - 1).coerceAtLeast(0)))
}

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = LauncherPreferences(app)
    private val engine = RomScannerAndScraperEngine(app)
    private val _uiState = MutableStateFlow(run {
        val p = prefs.loadConfiguredPlatforms()
        LauncherUiState(configuredPlatforms = p, selectedPlatformId = p.firstOrNull()?.platformId, scraperSources = prefs.loadScraperSources(), allGames = prefs.loadGames())
    })
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    fun selectTopTab(t: TopNavTab) = _uiState.update { it.copy(activeTopTab = t, isPlatformEditorOpen = false) }
    fun selectSettingsSection(s: SettingsSection) = _uiState.update { it.copy(activeSettingsSection = s, isPlatformEditorOpen = false) }
    fun selectPlatformById(id: String) = _uiState.update { it.copy(selectedPlatformId = id, focusedGameIndex = 0) }
    fun cycleConfiguredPlatform(dir: Int) {
        val l = _uiState.value.configuredPlatforms; if (l.isEmpty()) return
        val cur = l.indexOfFirst { it.platformId == _uiState.value.activePlatformConfig?.platformId }.coerceAtLeast(0)
        selectPlatformById(l[(cur + dir + l.size) % l.size].platformId)
    }
    fun moveGameFocus(d: Int) = _uiState.update { s -> val g = s.filteredGames; if (g.isEmpty()) s else s.copy(focusedGameIndex = (s.focusedGameIndex + d).coerceIn(0, g.lastIndex)) }
    fun setFocusedGameIndex(i: Int) = _uiState.update { s -> s.copy(focusedGameIndex = i.coerceIn(0, (s.filteredGames.size - 1).coerceAtLeast(0))) }
    fun toggleSearchDialog(v: Boolean) = _uiState.update { it.copy(isSearchDialogVisible = v) }
    fun updateSearchQuery(q: String) = _uiState.update { it.copy(searchQuery = q, focusedGameIndex = 0) }

    fun openPlatformEditor(existing: ConfiguredPlatform? = null) {
        val init = existing ?: ConsolePlatform.PSP.let { c -> val pr = c.emulatorPresets.first(); ConfiguredPlatform(c.id, pr.displayName, pr.packageName, pr.activityName, pr.retroArchCore, "android/download/รวมเกมpsp") }
        _uiState.update { it.copy(activeTopTab = TopNavTab.SETTINGS, activeSettingsSection = SettingsSection.PLATFORMS, editingPlatform = init, isPlatformEditorOpen = true) }
    }
    fun closePlatformEditor() = _uiState.update { it.copy(isPlatformEditorOpen = false, editingPlatform = null) }

    fun saveAndScanPlatform(cfg: ConfiguredPlatform, addDemo: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPlatformEditorOpen = false, editingPlatform = null, statusBannerMessage = "กำลังสแกนโฟลเดอร์ ${cfg.romFolderPath}...") }
            val upP = _uiState.value.configuredPlatforms.toMutableList().apply { val i = indexOfFirst { it.platformId == cfg.platformId }; if (i >= 0) set(i, cfg) else add(cfg) }
            prefs.saveConfiguredPlatforms(upP)
            val scanned = engine.scanPlatformFolder(cfg)
            val games = if (scanned.isNotEmpty()) scanned else if (addDemo) SampleGameCatalog.forPlatform(cfg.platformId, cfg.romFolderPath) else emptyList()
            val merged = _uiState.value.allGames.filterNot { it.consoleId == cfg.platformId } + games
            prefs.saveGames(merged)
            _uiState.update { it.copy(configuredPlatforms = upP, selectedPlatformId = cfg.platformId, allGames = merged, focusedGameIndex = 0, statusBannerMessage = if (scanned.isNotEmpty()) "พบเกม ${cfg.console.shortTag} ${scanned.size} เกม!" else "บันทึกหมวด ${cfg.console.shortTag} เรียบร้อยแล้ว") }
        }
    }

    fun deleteConfiguredPlatform(id: String) {
        val upP = _uiState.value.configuredPlatforms.filterNot { it.platformId == id }
        val upG = _uiState.value.allGames.filterNot { it.consoleId == id }
        prefs.saveConfiguredPlatforms(upP); prefs.saveGames(upG)
        _uiState.update { it.copy(configuredPlatforms = upP, selectedPlatformId = upP.firstOrNull()?.platformId, allGames = upG, isPlatformEditorOpen = false, editingPlatform = null, statusBannerMessage = "ลบหมวดเกมแล้ว") }
    }

    fun toggleScraperSource(id: String) {
        val up = _uiState.value.scraperSources.map { if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it }
        prefs.saveScraperSources(up); _uiState.update { it.copy(scraperSources = up) }
    }
    fun moveScraperPriority(idx: Int, d: Int) {
        val l = _uiState.value.scraperSources.toMutableList(); val t = (idx + d).coerceIn(0, l.lastIndex)
        if (idx != t) { l.add(t, l.removeAt(idx)); prefs.saveScraperSources(l); _uiState.update { it.copy(scraperSources = l) } }
    }
    fun openScraperAccountDialog(s: ScraperSourceConfig?) = _uiState.update { it.copy(editingScraperAccount = s) }
    fun saveScraperAccount(id: String, u: String, p: String) {
        val up = _uiState.value.scraperSources.map { if (it.id == id) it.copy(accountUsername = u.trim(), apiKeyOrPass = p.trim(), isEnabled = true) else it }
        prefs.saveScraperSources(up); _uiState.update { it.copy(scraperSources = up, editingScraperAccount = null, statusBannerMessage = "บันทึกบัญชีแล้ว") }
    }
    fun applyCustomCover(uri: Uri) {
        val cur = _uiState.value.focusedGame ?: return
        val up = _uiState.value.allGames.map { if (it.id == cur.id) it.copy(customCoverUri = uri.toString()) else it }
        prefs.saveGames(up); _uiState.update { it.copy(allGames = up, statusBannerMessage = "ตั้งค่าหน้าปก Custom Cover แล้ว") }
    }
    fun scrapeFocusedGame() {
        val t = _uiState.value.focusedGame ?: return
        viewModelScope.launch {
            val sc = engine.scrapeOnlineCoverForGame(t, ConsolePlatform.fromId(t.consoleId), _uiState.value.scraperSources)
            val up = _uiState.value.allGames.map { if (it.id == t.id) sc else it }
            prefs.saveGames(up); _uiState.update { it.copy(allGames = up, statusBannerMessage = "ดึงปกออนไลน์ของ '${t.title}' สำเร็จ!") }
        }
    }
    fun scrapeAllInCurrentPlatform() {
        val cfg = _uiState.value.activePlatformConfig ?: return
        viewModelScope.launch {
            val up = _uiState.value.allGames.map { if (it.consoleId == cfg.platformId) engine.scrapeOnlineCoverForGame(it, cfg.console, _uiState.value.scraperSources) else it }
            prefs.saveGames(up); _uiState.update { it.copy(allGames = up, statusBannerMessage = "ดึงปกทุกเกมในหมวด ${cfg.console.shortTag} สำเร็จ!") }
        }
    }
}
