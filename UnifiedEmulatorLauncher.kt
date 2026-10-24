package com.retro.unifiedlauncher

import android.app.Application
import android.content.*
import android.net.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.net.*
import java.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

const val RA_PKG = "com.retroarch.aarch64"
const val RA_ACT = "com.retroarch.browser.retroactivity.RetroActivityFuture"
val IceCyan = Color(0xFF00E5FF)
val IceGreen = Color(0xFF69F0AE)
val IceDarkBg = Color(0xFF060A12)
val IceCardBg = Color(0xCC0B1324)
val IceRowBg = Color(0xFF131F38)
val Pill = RoundedCornerShape(50)
val VCenter = Alignment.CenterVertically
val WHT = Color.White
val BLK = Color.Black
fun rDp(v: Int) = RoundedCornerShape(v.dp)
fun hSp(v: Int) = Arrangement.spacedBy(v.dp)
fun vSp(v: Int) = Arrangement.spacedBy(v.dp)

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
        EmulatorPreset("Yuzu", "org.yuzu.yuzu_emu", "org.yuzu.yuzu_emu.activities.EmulationActivity")
    ));
    companion object { fun fromId(id: String) = entries.find { it.id == id } ?: PSP }
}

enum class TopNavTab(val title: String) { MY_GAMES("My Games"), MEDIA("Media"), SETTINGS("Settings") }
enum class SettingsSection(val title: String, val subtitle: String) {
    PLATFORMS("แพลตฟอร์มเครื่องเกม", "เลือกเครื่องเกม แอปอีมูเลเตอร์ และโฟลเดอร์ ROM"),
    SCRAPERS("ดึงปกเกมออนไลน์", "ตั้งค่าเว็บฐานข้อมูลปกเกมและบัญชีผู้ใช้")
}
enum class CoverAspectRatio(val label: String, val widthDp: Int, val heightDp: Int) {
    RATIO_3_4("3:4 (แนวตั้ง)", 146, 206), RATIO_1_1("1:1 (จัตุรัส)", 170, 170), RATIO_16_9("16:9 (แนวนอน)", 220, 132);
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

data class ScrapedCandidate(
    val title: String, val sourceName: String, val coverUrl: String, val snapUrl: String?,
    val releaseDate: String = "พ.ย. 2, 2010", val genre: String = "Beat'em Up",
    val developer: String = "Ready At Dawn", val publisher: String = "Sony Computer Entertainment"
)

data class GameItem(
    val id: Long, val title: String, val consoleId: String, val romFilePath: String,
    val romUriString: String? = null, val coverUrl: String? = null, val customCoverUri: String? = null,
    val fanartUrl: String? = null,
    val description: String = "วันวางจำหน่าย: พ.ย. 2, 2010 • แนวเกม: Beat'em Up • ผู้พัฒนา: Ready At Dawn • ผู้จัดจำหน่าย: Sony Computer Entertainment",
    val releaseYear: String = "พ.ย. 2, 2010", val developer: String = "Ready At Dawn", val genre: String = "Beat'em Up",
    val timePlayedMinutes: Int = 0, val progressPercent: Int = 0, val trophiesEarned: Int = 0, val trophiesTotal: Int = 30,
    val publisher: String = "Sony Computer Entertainment", val customFanartUri: String? = null,
    val overrideEmulatorName: String? = null, val overrideEmulatorPackage: String? = null,
    val overrideEmulatorActivity: String? = null, val overrideRetroArchCore: String? = null
) {
    val releaseDate: String get() = (releaseYear as String?) ?: "พ.ย. 2, 2010"
    val effectiveCoverModel: String? get() = customCoverUri?.takeIf { it.isNotBlank() } ?: coverUrl?.takeIf { it.isNotBlank() }
    val effectiveFanartModel: String? get() = customFanartUri?.takeIf { it.isNotBlank() } ?: fanartUrl?.takeIf { it.isNotBlank() }
    val romFileName: String get() = romFilePath.substringAfterLast('/').ifBlank { romFilePath }
    val formattedPlayTime: String get() = if (timePlayedMinutes >= 60) "${timePlayedMinutes / 60}h ${timePlayedMinutes % 60}m" else "${timePlayedMinutes}m"
}

class LauncherPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("ice_launcher_prefs_v3", Context.MODE_PRIVATE)
    private val oldPrefs = context.getSharedPreferences("ice_launcher_prefs_v2", Context.MODE_PRIVATE)
    private val gson = Gson()
    private fun str(k: String) = prefs.getString(k, null) ?: oldPrefs.getString(k, null)

    fun loadConfiguredPlatforms(): List<ConfiguredPlatform> = try {
        str("configured_platforms")?.let { gson.fromJson(it, object : TypeToken<List<ConfiguredPlatform>>() {}.type) } ?: emptyList()
    } catch (_: Exception) { emptyList() }
    fun saveConfiguredPlatforms(l: List<ConfiguredPlatform>) = prefs.edit().putString("configured_platforms", gson.toJson(l)).apply()

    fun loadScraperSources(): List<ScraperSourceConfig> = try {
        str("scraper_sources")?.let { gson.fromJson(it, object : TypeToken<List<ScraperSourceConfig>>() {}.type) } ?: listOf(
            ScraperSourceConfig("screenscraper", "Screenscraper.fr", "screenscraper.fr", true, true),
            ScraperSourceConfig("libretro", "Libretro", "thumbnails.libretro.com", true, false),
            ScraperSourceConfig("thegamesdb", "TheGamesDb.net", "thegamesdb.net", true, false),
            ScraperSourceConfig("igdb", "IGDB.com", "igdb.com", true, false),
            ScraperSourceConfig("steamgriddb", "SteamGridDB.com", "steamgriddb.com", false, true)
        )
    } catch (_: Exception) { emptyList() }
    fun saveScraperSources(s: List<ScraperSourceConfig>) = prefs.edit().putString("scraper_sources", gson.toJson(s)).apply()

    fun loadGames(engine: RomScannerAndScraperEngine): List<GameItem> = try {
        val raw = prefs.getString("saved_games_v3_clean", null) ?: oldPrefs.getString("saved_games_v2", null)
        val list: List<GameItem> = raw?.let { gson.fromJson(it, object : TypeToken<List<GameItem>>() {}.type) } ?: emptyList()
        list.map { g ->
            val c = ConsolePlatform.fromId((g.consoleId as String?) ?: "psp")
            val init = engine.buildInitialItem((g.title as String?) ?: "Game", c, (g.romFilePath as String?) ?: "", g.romUriString)
            val valid = !(g.coverUrl as String?).isNullOrBlank() && (!(g.coverUrl as String).contains("libretro.com") || (g.coverUrl as String).contains("("))
            init.copy(
                id = if (g.id != 0L) g.id else init.id,
                coverUrl = if (valid) g.coverUrl else init.coverUrl, customCoverUri = g.customCoverUri,
                fanartUrl = if (valid && !(g.fanartUrl as String?).isNullOrBlank()) g.fanartUrl else init.fanartUrl, customFanartUri = g.customFanartUri
            )
        }.also { if (!prefs.contains("saved_games_v3_clean") && it.isNotEmpty()) saveGames(it) }
    } catch (_: Exception) { emptyList() }
    fun saveGames(g: List<GameItem>) = prefs.edit().putString("saved_games_v3_clean", gson.toJson(g)).apply()
}

data class KnownMeta(val title: String, val lrName: String, val date: String, val genre: String, val dev: String, val pub: String)

class RomScannerAndScraperEngine(private val context: Context) {
    private val tagRegex = Regex("""\s*(\([^)]*\)|\[[^\]]*\])""")
    private val hrefPngRegex = Regex("""href=["']([^"']+\.png)["']""", RegexOption.IGNORE_CASE)
    private val curated = listOf(
        KnownMeta("God of War : Ghost of Sparta", "God of War - Ghost of Sparta (USA)", "พ.ย. 2, 2010", "Beat'em Up", "Ready At Dawn", "Sony Computer Entertainment"),
        KnownMeta("God of War : Chains of Olympus", "God of War - Chains of Olympus (USA)", "มี.ค. 4, 2008", "Beat'em Up", "Ready At Dawn", "Sony Computer Entertainment"),
        KnownMeta("Monster Hunter Freedom Unite", "Monster Hunter Freedom Unite (USA)", "มิ.ย. 23, 2009", "Action RPG", "Capcom", "Capcom"),
        KnownMeta("Monster Hunter Portable 3rd", "Monster Hunter Portable 3rd (Japan)", "ธ.ค. 1, 2010", "Action RPG", "Capcom", "Capcom"),
        KnownMeta("Crisis Core : Final Fantasy VII", "Crisis Core - Final Fantasy VII (USA)", "มี.ค. 24, 2008", "Action RPG", "Square Enix", "Square Enix"),
        KnownMeta("Tekken 6", "Tekken 6 (USA)", "พ.ย. 24, 2009", "Fighting", "Bandai Namco", "Bandai Namco")
    )

    suspend fun scanPlatformFolder(cfg: ConfiguredPlatform): List<GameItem> = withContext(Dispatchers.IO) {
        val c = cfg.console; val out = mutableListOf<GameItem>()
        if (!cfg.romTreeUriString.isNullOrBlank()) {
            try { DocumentFile.fromTreeUri(context, Uri.parse(cfg.romTreeUriString))?.takeIf { it.exists() }?.let { scanDoc(it, c, out, 0) } } catch (_: Exception) {}
        }
        if (out.isEmpty() && cfg.romFolderPath.isNotBlank()) {
            try {
                val p = if (cfg.romFolderPath.startsWith("/storage")) cfg.romFolderPath else "/storage/emulated/0/" + cfg.romFolderPath.removePrefix("android/").removePrefix("/")
                val root = File(p)
                if (root.exists()) {
                    if (root.isFile && root.extension.lowercase(Locale.ROOT) in c.supportedExtensions) {
                        out.add(buildInitialItem(cleanTitle(root.nameWithoutExtension), c, root.absolutePath, null))
                    } else if (root.isDirectory) {
                        root.walkTopDown().maxDepth(3).filter { it.isFile && it.extension.lowercase(Locale.ROOT) in c.supportedExtensions }.forEach { f ->
                            val raw = if (f.name.equals("EBOOT.PBP", true)) f.parentFile?.name ?: f.nameWithoutExtension else f.nameWithoutExtension
                            out.add(buildInitialItem(cleanTitle(raw), c, f.absolutePath, null))
                        }
                        if (out.isEmpty() && root.name.isNotBlank() && !root.name.equals("GAME", true)) {
                            val ft = cleanTitle(root.name)
                            out.add(buildInitialItem(ft, c, "${root.absolutePath}/$ft.iso", null))
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        out
    }

    private fun scanDoc(dir: DocumentFile, c: ConsolePlatform, out: MutableList<GameItem>, d: Int) {
        if (d > 3) return
        dir.listFiles().forEach { doc ->
            if (doc.isDirectory) scanDoc(doc, c, out, d + 1)
            else if (doc.isFile) {
                val n = doc.name ?: return@forEach
                if (n.substringAfterLast('.', "").lowercase(Locale.ROOT) in c.supportedExtensions) {
                    val base = if (n.equals("EBOOT.PBP", true)) (dir.name ?: n.substringBeforeLast('.')) else n.substringBeforeLast('.')
                    out.add(buildInitialItem(cleanTitle(base), c, doc.uri.path ?: n, doc.uri.toString()))
                }
            }
        }
        if (d == 0 && out.isEmpty() && !dir.name.isNullOrBlank()) {
            val fn = cleanTitle(dir.name!!)
            out.add(buildInitialItem(fn, c, "$fn.iso", dir.uri.toString()))
        }
    }

    fun cleanTitle(raw: String) = raw.replace(tagRegex, "").replace("_", " ").replace(Regex("\\s+"), " ").trim().ifEmpty { raw }
    private fun safeLr(t: String) = cleanTitle(t).replace(": ", " - ").replace(":", " - ").replace("&", "_").replace("/", "_").replace(Regex("\\s+"), " ").trim()
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun lrUrl(sys: String, folder: String, name: String) = "https://thumbnails.libretro.com/${enc(sys)}/$folder/${enc(name)}.png"

    private fun overlap(a: String, b: String): Double {
        val wa = a.lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+")).filter { it.length > 1 }.toSet()
        val wb = b.lowercase(Locale.ROOT).split(Regex("[^a-z0-9]+")).filter { it.length > 1 }.toSet()
        return if (wa.isEmpty() || wb.isEmpty()) 0.0 else wa.intersect(wb).size.toDouble() / wa.size.coerceAtLeast(1)
    }

    private fun findMeta(t: String): KnownMeta? {
        val n = cleanTitle(t).lowercase(Locale.ROOT).replace(":", "").replace("-", " ").replace(Regex("\\s+"), " ").trim()
        return curated.find { m ->
            val mn = m.title.lowercase(Locale.ROOT).replace(":", "").replace("-", " ").replace(Regex("\\s+"), " ").trim()
            n.contains(mn) || mn.contains(n) || overlap(n, mn) >= 0.75
        }
    }

    fun buildInitialItem(title: String, c: ConsolePlatform, path: String, uriStr: String?): GameItem {
        val m = findMeta(title)
        val ex = m?.lrName ?: "${safeLr(title)} (USA)"
        val dt = m?.date ?: "พ.ย. 2, 2010"; val gn = m?.genre ?: "Beat'em Up"; val dv = m?.dev ?: "Ready At Dawn"; val pb = m?.pub ?: "Sony Computer Entertainment"
        return GameItem(
            id = (c.id + ":" + path).hashCode().toLong(), title = m?.title ?: title.replace(" - ", " : "),
            consoleId = c.id, romFilePath = path, romUriString = uriStr,
            coverUrl = lrUrl(c.libretroSystemName, "Named_Boxarts", ex), fanartUrl = lrUrl(c.libretroSystemName, "Named_Snaps", ex),
            description = "วันวางจำหน่าย: $dt • แนวเกม: $gn • ผู้พัฒนา: $dv • ผู้จัดจำหน่าย: $pb",
            releaseYear = dt, developer = dv, genre = gn, publisher = pb
        )
    }

    private fun httpGet(urlStr: String, headOnly: Boolean = false): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = if (headOnly) "HEAD" else "GET"
                connectTimeout = 5000; readTimeout = 7000; instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; IceEmulatorLauncher/3.0)")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                if (headOnly) ByteArray(1) else conn.inputStream.use { it.readBytes() }
            } else null
        } catch (_: Exception) { null } finally { conn?.disconnect() }
    }

    private fun cacheImg(url: String, prefix: String): String? {
        val bytes = httpGet(url, false) ?: return null
        if (bytes.size <= 1024) return null
        val dir = File(context.filesDir, "scraped_covers").apply { mkdirs() }
        val f = File(dir, "${prefix}_${System.currentTimeMillis()}.png")
        f.writeBytes(bytes)
        return f.toURI().toString()
    }

    suspend fun searchCoverCandidates(queryTitle: String, c: ConsolePlatform): List<ScrapedCandidate> = withContext(Dispatchers.IO) {
        val res = mutableListOf<ScrapedCandidate>()
        val m = findMeta(queryTitle)
        val base = safeLr(queryTitle)
        val names = LinkedHashSet<String>().apply {
            if (m != null) add(m.lrName)
            listOf("$base (USA)", "$base (USA) (En,Fr,De,Es,It)", "$base (Europe)", "$base (World)", "$base (Japan)", base).forEach { add(it) }
        }
        for (n in names) {
            val box = lrUrl(c.libretroSystemName, "Named_Boxarts", n)
            if (httpGet(box, true) != null) {
                res.add(ScrapedCandidate(m?.title ?: n, "Libretro Boxart", box, lrUrl(c.libretroSystemName, "Named_Snaps", n), m?.date ?: "พ.ย. 2, 2010", m?.genre ?: "Beat'em Up", m?.dev ?: "Ready At Dawn", m?.pub ?: "Sony Computer Entertainment"))
                if (res.size >= 3) break
            }
        }
        if (res.isEmpty()) {
            val html = httpGet("https://thumbnails.libretro.com/${enc(c.libretroSystemName)}/Named_Boxarts/", false)?.toString(Charsets.UTF_8) ?: ""
            val qn = cleanTitle(queryTitle).lowercase(Locale.ROOT)
            hrefPngRegex.findAll(html).map { URLDecoder.decode(it.groupValues[1], "UTF-8").removeSuffix(".png") }
                .filter { !it.startsWith("/") && !it.startsWith("http") }
                .map { fn ->
                    val b = fn.replace(tagRegex, "").lowercase(Locale.ROOT)
                    fn to (overlap(qn, b) + (if (b == qn) 1.0 else 0.0) + (if (fn.contains("(USA)", true)) 0.25 else 0.0))
                }.filter { it.second >= 0.55 }.sortedByDescending { it.second }.take(4).forEach { (fn, _) ->
                    res.add(ScrapedCandidate(m?.title ?: fn.replace(tagRegex, "").trim(), "Libretro Index", lrUrl(c.libretroSystemName, "Named_Boxarts", fn), lrUrl(c.libretroSystemName, "Named_Snaps", fn), m?.date ?: "2010", m?.genre ?: "Action", m?.dev ?: c.shortTag, m?.pub ?: "Official"))
                }
        }
        try {
            val q = URLEncoder.encode("${cleanTitle(queryTitle)} ${c.shortTag} video game", "UTF-8")
            val wTxt = httpGet("https://en.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$q&gsrlimit=2&prop=pageimages|extracts&piprop=original|thumbnail&pithumbsize=800&exintro=1&explaintext=1&format=json", false)?.toString(Charsets.UTF_8)
            if (!wTxt.isNullOrBlank()) {
                val pages = JSONObject(wTxt).optJSONObject("query")?.optJSONObject("pages")
                pages?.keys()?.forEach { k ->
                    val pg = pages.getJSONObject(k)
                    val img = pg.optJSONObject("original")?.optString("source") ?: pg.optJSONObject("thumbnail")?.optString("source")
                    if (!img.isNullOrBlank()) {
                        res.add(ScrapedCandidate(pg.optString("title", queryTitle).replace(" (video game)", ""), "Wikipedia / IGDB", img, img, m?.date ?: "2010", m?.genre ?: "Action", m?.dev ?: c.shortTag, m?.pub ?: "Official"))
                    }
                }
            }
        } catch (_: Exception) {}
        res
    }

    suspend fun applyCandidateToGame(g: GameItem, cand: ScrapedCandidate): GameItem = withContext(Dispatchers.IO) {
        val lc = cacheImg(cand.coverUrl, "cover_${g.id}")
        val ls = cand.snapUrl?.let { cacheImg(it, "snap_${g.id}") }
        val dt = cand.releaseDate.ifBlank { g.releaseDate }; val gn = cand.genre.ifBlank { g.genre }; val dv = cand.developer.ifBlank { g.developer }; val pb = cand.publisher.ifBlank { g.publisher }
        g.copy(
            title = cand.title.ifBlank { g.title }, coverUrl = lc ?: cand.coverUrl, customCoverUri = null,
            fanartUrl = ls ?: cand.snapUrl ?: cand.coverUrl, customFanartUri = null,
            releaseYear = dt, genre = gn, developer = dv, publisher = pb,
            description = "วันวางจำหน่าย: $dt • แนวเกม: $gn • ผู้พัฒนา: $dv • ผู้จัดจำหน่าย: $pb"
        )
    }

    suspend fun scrapeOnlineCoverForGame(g: GameItem, c: ConsolePlatform, sources: List<ScraperSourceConfig>): GameItem = withContext(Dispatchers.IO) {
        val cands = searchCoverCandidates(g.title, c)
        if (cands.isNotEmpty()) return@withContext applyCandidateToGame(g, cands.first())
        val m = findMeta(g.title)
        val fb = m?.lrName ?: "${safeLr(g.title)} (USA)"
        val box = lrUrl(c.libretroSystemName, "Named_Boxarts", fb)
        val snap = lrUrl(c.libretroSystemName, "Named_Snaps", fb)
        g.copy(title = m?.title ?: g.title, coverUrl = cacheImg(box, "cover_${g.id}") ?: box, fanartUrl = cacheImg(snap, "snap_${g.id}") ?: snap)
    }
}

class EmulatorIntentLauncher(private val context: Context) {
    sealed class LaunchResult {
        data object Success : LaunchResult()
        data class EmulatorNotInstalled(val name: String, val pkg: String) : LaunchResult()
        data class Error(val msg: String) : LaunchResult()
    }
    fun launch(g: GameItem, cfg: ConfiguredPlatform): LaunchResult {
        val pkg = g.overrideEmulatorPackage?.takeIf { it.isNotBlank() } ?: cfg.emulatorPackage
        val name = g.overrideEmulatorName?.takeIf { it.isNotBlank() } ?: cfg.emulatorName
        val act = if (!g.overrideEmulatorPackage.isNullOrBlank()) g.overrideEmulatorActivity else cfg.emulatorActivity
        val core = if (!g.overrideEmulatorPackage.isNullOrBlank()) g.overrideRetroArchCore else cfg.retroArchCore
        try { context.packageManager.getPackageInfo(pkg, 0) } catch (_: Exception) { return LaunchResult.EmulatorNotInstalled(name, pkg) }
        val uri: Uri = try {
            if (!g.romUriString.isNullOrBlank()) Uri.parse(g.romUriString)
            else File(g.romFilePath).let { if (it.exists()) FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) else Uri.fromFile(it) }
        } catch (_: Exception) { Uri.fromFile(File(g.romFilePath)) }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage(pkg)
            act?.takeIf { it.isNotBlank() }?.let { component = ComponentName(pkg, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (pkg.contains("retroarch") && !core.isNullOrBlank()) {
                action = Intent.ACTION_MAIN; addCategory(Intent.CATEGORY_LAUNCHER)
                putExtra("ROM", g.romFilePath); putExtra("LIBRETRO", "/data/data/$pkg/cores/$core")
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
    val managingGamesPlatform: ConfiguredPlatform? = null,
    val editingGame: GameItem? = null,
    val coverSearchCandidates: List<ScrapedCandidate> = emptyList(),
    val isCoverPickerOpen: Boolean = false,
    val isScrapingBusy: Boolean = false,
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
        LauncherUiState(configuredPlatforms = p, selectedPlatformId = p.firstOrNull()?.platformId, scraperSources = prefs.loadScraperSources(), allGames = prefs.loadGames(engine))
    })
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val gList = _uiState.value.allGames
            if (gList.isNotEmpty()) {
                val up = gList.map { g -> if (g.customCoverUri.isNullOrBlank() && (g.coverUrl.isNullOrBlank() || g.coverUrl.startsWith("http"))) engine.scrapeOnlineCoverForGame(g, ConsolePlatform.fromId(g.consoleId), _uiState.value.scraperSources) else g }
                prefs.saveGames(up); _uiState.update { it.copy(allGames = up) }
            }
        }
    }

    fun selectTopTab(t: TopNavTab) = _uiState.update { it.copy(activeTopTab = t, isPlatformEditorOpen = false, managingGamesPlatform = null, editingGame = null) }
    fun selectSettingsSection(s: SettingsSection) = _uiState.update { it.copy(activeSettingsSection = s, isPlatformEditorOpen = false, managingGamesPlatform = null) }
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
    fun openPlatformEditor(ex: ConfiguredPlatform? = null) {
        val init = ex ?: ConsolePlatform.PSP.let { c -> val pr = c.emulatorPresets.first(); ConfiguredPlatform(c.id, pr.displayName, pr.packageName, pr.activityName, pr.retroArchCore, "android/download/รวมเกมpsp") }
        _uiState.update { it.copy(activeTopTab = TopNavTab.SETTINGS, activeSettingsSection = SettingsSection.PLATFORMS, editingPlatform = init, isPlatformEditorOpen = true, managingGamesPlatform = null) }
    }
    fun closePlatformEditor() = _uiState.update { it.copy(isPlatformEditorOpen = false, editingPlatform = null) }
    fun openPlatformGameList(cfg: ConfiguredPlatform?) = _uiState.update { it.copy(managingGamesPlatform = cfg, isPlatformEditorOpen = false) }
    fun openGameEditor(g: GameItem?) = _uiState.update { it.copy(editingGame = g, isCoverPickerOpen = false) }
    fun saveEditedGame(upG: GameItem) {
        val l = _uiState.value.allGames.map { if (it.id == upG.id) upG else it }
        prefs.saveGames(l); _uiState.update { it.copy(allGames = l, editingGame = null, statusBannerMessage = "บันทึก '${upG.title}' แล้ว!") }
    }
    fun deleteGame(id: Long) {
        val l = _uiState.value.allGames.filterNot { it.id == id }
        prefs.saveGames(l); _uiState.update { it.copy(allGames = l, editingGame = null, focusedGameIndex = 0, statusBannerMessage = "ลบเกมแล้ว") }
    }
    fun saveAndScanPlatform(cfg: ConfiguredPlatform, addDemo: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPlatformEditorOpen = false, editingPlatform = null, isScrapingBusy = true, statusBannerMessage = "กำลังสแกนและดึงหน้าปกเกม ${cfg.console.shortTag}...") }
            val upP = _uiState.value.configuredPlatforms.toMutableList().apply { val i = indexOfFirst { it.platformId == cfg.platformId }; if (i >= 0) set(i, cfg) else add(cfg) }
            prefs.saveConfiguredPlatforms(upP)
            val scanned = engine.scanPlatformFolder(cfg)
            val base = if (scanned.isNotEmpty()) scanned else if (addDemo) SampleGameCatalog.forPlatform(cfg.platformId, cfg.romFolderPath) else emptyList()
            val scraped = base.map { engine.scrapeOnlineCoverForGame(it, cfg.console, _uiState.value.scraperSources) }
            val merged = _uiState.value.allGames.filterNot { it.consoleId == cfg.platformId } + scraped
            prefs.saveGames(merged)
            _uiState.update { it.copy(configuredPlatforms = upP, selectedPlatformId = cfg.platformId, allGames = merged, focusedGameIndex = 0, isScrapingBusy = false, statusBannerMessage = "ดึงปกและภาพในเกม ${cfg.console.shortTag} สำเร็จ (${scraped.size} เกม)") }
        }
    }
    fun deleteConfiguredPlatform(id: String) {
        val upP = _uiState.value.configuredPlatforms.filterNot { it.platformId == id }
        val upG = _uiState.value.allGames.filterNot { it.consoleId == id }
        prefs.saveConfiguredPlatforms(upP); prefs.saveGames(upG)
        _uiState.update { it.copy(configuredPlatforms = upP, selectedPlatformId = upP.firstOrNull()?.platformId, allGames = upG, isPlatformEditorOpen = false, editingPlatform = null, managingGamesPlatform = null, statusBannerMessage = "ลบหมวดเกมแล้ว") }
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
        val t = _uiState.value.editingGame ?: _uiState.value.focusedGame ?: return
        val upG = t.copy(customCoverUri = uri.toString())
        val l = _uiState.value.allGames.map { if (it.id == t.id) upG else it }
        prefs.saveGames(l); _uiState.update { it.copy(allGames = l, editingGame = if (it.editingGame?.id == t.id) upG else it.editingGame, statusBannerMessage = "เปลี่ยนหน้าปกแล้ว") }
    }
    fun applyCustomFanart(uri: Uri) {
        val t = _uiState.value.editingGame ?: _uiState.value.focusedGame ?: return
        val upG = t.copy(customFanartUri = uri.toString())
        val l = _uiState.value.allGames.map { if (it.id == t.id) upG else it }
        prefs.saveGames(l); _uiState.update { it.copy(allGames = l, editingGame = if (it.editingGame?.id == t.id) upG else it.editingGame, statusBannerMessage = "เปลี่ยนภาพในเกมแล้ว") }
    }
    fun scrapeFocusedGame() = _uiState.value.focusedGame?.let { scrapeSingleGame(it) }
    fun scrapeSingleGame(t: GameItem, q: String? = null) {
        viewModelScope.launch {
            val qg = if (!q.isNullOrBlank()) t.copy(title = q) else t
            _uiState.update { it.copy(isScrapingBusy = true, statusBannerMessage = "กำลังดาวน์โหลดปกแท้ของ '${qg.title}'...") }
            val sc = engine.scrapeOnlineCoverForGame(qg, ConsolePlatform.fromId(t.consoleId), _uiState.value.scraperSources)
            val l = _uiState.value.allGames.map { if (it.id == t.id) sc else it }
            prefs.saveGames(l); _uiState.update { it.copy(allGames = l, editingGame = if (it.editingGame?.id == t.id) sc else it.editingGame, isScrapingBusy = false, statusBannerMessage = "ดึงหน้าปกและภาพในเกมของ '${sc.title}' สำเร็จ!") }
        }
    }
    fun searchCoverOptionsForGame(t: GameItem, q: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScrapingBusy = true, isCoverPickerOpen = true) }
            val cands = engine.searchCoverCandidates(q, ConsolePlatform.fromId(t.consoleId))
            _uiState.update { it.copy(coverSearchCandidates = cands, isScrapingBusy = false) }
        }
    }
    fun selectScrapedCandidate(t: GameItem, cand: ScrapedCandidate) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScrapingBusy = true, isCoverPickerOpen = false) }
            val upG = engine.applyCandidateToGame(t, cand)
            val l = _uiState.value.allGames.map { if (it.id == t.id) upG else it }
            prefs.saveGames(l); _uiState.update { it.copy(allGames = l, editingGame = if (it.editingGame?.id == t.id) upG else it.editingGame, isScrapingBusy = false, statusBannerMessage = "อัปเดตปกเกมสำเร็จ!") }
        }
    }
    fun closeCoverPicker() = _uiState.update { it.copy(isCoverPickerOpen = false) }
    fun scrapeAllInCurrentPlatform() {
        val cfg = _uiState.value.activePlatformConfig ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isScrapingBusy = true, statusBannerMessage = "กำลังดึงปกทุกเกมในหมวด ${cfg.console.shortTag}...") }
            val l = _uiState.value.allGames.map { if (it.consoleId == cfg.platformId) engine.scrapeOnlineCoverForGame(it, cfg.console, _uiState.value.scraperSources) else it }
            prefs.saveGames(l); _uiState.update { it.copy(allGames = l, isScrapingBusy = false, statusBannerMessage = "ดึงปกครบทุกเกมในหมวด ${cfg.console.shortTag} สำเร็จ!") }
        }
    }
}
