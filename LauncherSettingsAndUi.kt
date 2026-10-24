package com.retro.unifiedlauncher
import android.content.*
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
import androidx.core.view.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

@Composable
fun SpaceRow(mod:Modifier=Modifier.fillMaxWidth(),content:@Composable RowScope.()->Unit) =
Row(modifier=mod,horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=VCenter,content=content)

@Composable
fun buildCoilReq(u:String?):ImageRequest? {
val ctx=LocalContext.current
return if (u.isNullOrBlank()) null else remember(u,ctx) {
ImageRequest.Builder(ctx).data(u).addHeader("User-Agent","Mozilla/5.0 (Linux; Android 14; IceEmulatorLauncher/3.0)").crossfade(true).build()
}
}

@Composable
fun UnifiedEmulatorLauncherApp(vm:LauncherViewModel=androidx.lifecycle.viewmodel.compose.viewModel()) {
val ctx=LocalContext.current
val state by vm.uiState.collectAsState()
val launcher=remember(ctx) { EmulatorIntentLauncher(ctx) }
val focusReq=remember { FocusRequester() }
val listState=rememberLazyListState()
val coverPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri:Uri?->
if (uri != null) { try { ctx.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_:Exception) {}; vm.applyCustomCover(uri) }
}
val fanartPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri:Uri?->
if (uri != null) { try { ctx.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_:Exception) {}; vm.applyCustomFanart(uri) }
}
LaunchedEffect(state.focusedGameIndex,state.selectedPlatformId) {
if (state.filteredGames.isNotEmpty()) listState.animateScrollToItem(state.focusedGameIndex.coerceIn(0,state.filteredGames.lastIndex))
}
LaunchedEffect(Unit) { focusReq.requestFocus() }
val playFocused:()->Unit={
val g=state.focusedGame; val cfg=state.activePlatformConfig
if (g != null && cfg != null) {
when (val r=launcher.launch(g,cfg)) {
is EmulatorIntentLauncher.LaunchResult.Success->Toast.makeText(ctx,"กำลังรัน ${g.title}",Toast.LENGTH_SHORT).show()
is EmulatorIntentLauncher.LaunchResult.EmulatorNotInstalled->Toast.makeText(ctx,"กรุณาติดตั้งแอป ${r.name} (${r.pkg})",Toast.LENGTH_LONG).show()
is EmulatorIntentLauncher.LaunchResult.Error->Toast.makeText(ctx,r.msg,Toast.LENGTH_LONG).show()
}
}
}
val accent=Color(state.activePlatformConfig?.console?.accentColorHex?:0xFF00E5FF)
val bgModel=state.focusedGame?.effectiveFanartModel?:state.activePlatformConfig?.customWallpaperUri?:state.focusedGame?.effectiveCoverModel
Box(
modifier=Modifier.fillMaxSize().background(IceDarkBg).focusRequester(focusReq).focusable().onPreviewKeyEvent { ev->
if (ev.type != KeyEventType.KeyDown || state.editingGame != null) return@onPreviewKeyEvent false
when (ev.nativeKeyEvent.keyCode) {
AndroidKeyEvent.KEYCODE_DPAD_LEFT->if (state.activeTopTab== TopNavTab.MY_GAMES) { vm.moveGameFocus(-1); true } else false
AndroidKeyEvent.KEYCODE_DPAD_RIGHT->if (state.activeTopTab== TopNavTab.MY_GAMES) { vm.moveGameFocus(1); true } else false
AndroidKeyEvent.KEYCODE_BUTTON_L1,AndroidKeyEvent.KEYCODE_PAGE_UP->{ vm.cycleConfiguredPlatform(-1); true }
AndroidKeyEvent.KEYCODE_BUTTON_R1,AndroidKeyEvent.KEYCODE_PAGE_DOWN->{ vm.cycleConfiguredPlatform(1); true }
AndroidKeyEvent.KEYCODE_BUTTON_A,AndroidKeyEvent.KEYCODE_DPAD_CENTER,AndroidKeyEvent.KEYCODE_ENTER->if (state.activeTopTab== TopNavTab.MY_GAMES) { playFocused(); true } else false
AndroidKeyEvent.KEYCODE_BUTTON_Y->{ vm.toggleSearchDialog(!state.isSearchDialogVisible); true }
AndroidKeyEvent.KEYCODE_BUTTON_X->{ state.focusedGame?.let { vm.openGameEditor(it) }; true }
AndroidKeyEvent.KEYCODE_BUTTON_START,AndroidKeyEvent.KEYCODE_MENU->{ vm.selectTopTab(if (state.activeTopTab== TopNavTab.SETTINGS) TopNavTab.MY_GAMES else TopNavTab.SETTINGS); true }
else->false
}
}
) {
DynamicFanartBackground(bgModel,accent)
Column(modifier=Modifier.fillMaxSize().padding(horizontal=30.dp,vertical=16.dp),verticalArrangement=Arrangement.SpaceBetween) {
Column(verticalArrangement=vSp(10)) {
TopConsoleNavBar(state.activeTopTab,vm::selectTopTab,{ vm.toggleSearchDialog(true) },{ vm.selectTopTab(TopNavTab.SETTINGS) })
if (state.activeTopTab== TopNavTab.MY_GAMES && state.editingGame== null) {
ConsoleCategorySelector(state.configuredPlatforms,state.activePlatformConfig?.platformId,vm::selectPlatformById) { vm.openPlatformEditor(null) }
}
}
Box(modifier=Modifier.weight(1f).fillMaxWidth().padding(vertical=6.dp)) {
if (state.editingGame != null) {
GameCustomizationStudio(
game=state.editingGame!!,
platformCfg=state.configuredPlatforms.find { it.platformId== state.editingGame!!.consoleId }?:state.activePlatformConfig,
isScraping=state.isScrapingBusy,
onPickCover={ coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
onPickFanart={ fanartPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
onScrapeNow={ q->vm.scrapeSingleGame(state.editingGame!!,q) },
onOpenCoverPicker={ q->vm.searchCoverOptionsForGame(state.editingGame!!,q) },
onSave=vm::saveEditedGame,
onDelete={ vm.deleteGame(state.editingGame!!.id) },
onBack={ vm.openGameEditor(null) }
)
} else if (state.activeTopTab== TopNavTab.SETTINGS) {
IceConsoleSettingsHub(
state,vm::selectSettingsSection,{ vm.openPlatformEditor(null) },{ vm.openPlatformEditor(it) },
{ vm.openPlatformGameList(it) },{ vm.openGameEditor(it) },
{ vm.saveAndScanPlatform(it,true) },vm::deleteConfiguredPlatform,vm::saveAndScanPlatform,vm::closePlatformEditor,
vm::toggleScraperSource,vm::moveScraperPriority,vm::openScraperAccountDialog,vm::saveScraperAccount,vm::scrapeAllInCurrentPlatform
)
} else if (state.configuredPlatforms.isEmpty()) {
EmptyPlatformOnboardingCard { vm.openPlatformEditor(null) }
} else {
Column(modifier=Modifier.fillMaxSize(),verticalArrangement=Arrangement.SpaceBetween) {
HorizontalGameCarousel(state.filteredGames,state.focusedGameIndex,state.activePlatformConfig?.aspectRatio?:CoverAspectRatio.RATIO_3_4,accent,listState,vm::setFocusedGameIndex,playFocused) { vm.openPlatformEditor(state.activePlatformConfig) }
GameInformationPanel(
game=state.focusedGame,cfg=state.activePlatformConfig,isScraping=state.isScrapingBusy,
onPlay=playFocused,onEditGame={ state.focusedGame?.let { vm.openGameEditor(it) } },
onScrape={ state.focusedGame?.let { vm.scrapeSingleGame(it) } },
onChooseOnlineCover={ state.focusedGame?.let { vm.searchCoverOptionsForGame(it,it.title) } }
)
}
}
}
GamepadButtonPromptsFooter(state.statusBannerMessage,playFocused,{ vm.toggleSearchDialog(true) },{ state.focusedGame?.let { vm.openGameEditor(it) } }) {
vm.selectTopTab(if (state.activeTopTab== TopNavTab.SETTINGS) TopNavTab.MY_GAMES else TopNavTab.SETTINGS)
}
}
if (state.isCoverPickerOpen) {
(state.editingGame?:state.focusedGame)?.let { tg->
OnlineCoverPickerDialog(tg,state.coverSearchCandidates,state.isScrapingBusy,{ q->vm.searchCoverOptionsForGame(tg,q) },{ c->vm.selectScrapedCandidate(tg,c) },vm::closeCoverPicker)
}
}
if (state.isSearchDialogVisible) {
AlertDialog(
onDismissRequest={ vm.toggleSearchDialog(false) },containerColor=Color(0xFF121826),titleContentColor=WHT,textContentColor=WHT,
title={ Text("ค้นหาเกมในคลัง",fontWeight=FontWeight.Bold) },
text={ OutlinedTextField(value=state.searchQuery,onValueChange=vm::updateSearchQuery,singleLine=true,modifier=Modifier.fillMaxWidth()) },
confirmButton={ TextButton(onClick={ vm.toggleSearchDialog(false) }) { Text("ตกลง",color=IceCyan) } },
dismissButton={ TextButton(onClick={ vm.updateSearchQuery(""); vm.toggleSearchDialog(false) }) { Text("ล้าง",color=WHT) } }
)
}
}
}

@Composable
fun GameCustomizationStudio(
game:GameItem,platformCfg:ConfiguredPlatform?,isScraping:Boolean,
onPickCover:()->Unit,onPickFanart:()->Unit,onScrapeNow:(String)->Unit,onOpenCoverPicker:(String)->Unit,
onSave:(GameItem)->Unit,onDelete:()->Unit,onBack:()->Unit
) {
var d by remember(game) { mutableStateOf(game) }
val console=ConsolePlatform.fromId(d.consoleId)
Surface(color=IceCardBg,shape=rDp(22),border=BorderStroke(1.dp,IceCyan.copy(alpha=0.35f)),modifier=Modifier.fillMaxSize()) {
Column(modifier=Modifier.fillMaxSize().padding(horizontal=26.dp,vertical=16.dp).verticalScroll(rememberScrollState()),verticalArrangement=vSp(12)) {
SpaceRow() {
Row(verticalAlignment=VCenter,horizontalArrangement=hSp(8)) {
IconButton(onClick=onBack) { Icon(Icons.Default.ArrowBack,null,tint=WHT) }
Text("ปรับแต่งเกม",color=WHT,fontSize=20.sp,fontWeight=FontWeight.Bold)
}
Surface(color=Color(console.accentColorHex),shape=rDp(8)) {
Text(console.displayName,color=WHT,fontSize=11.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=10.dp,vertical=4.dp))
}
}
OutlinedTextField(value=d.title,onValueChange={ d=d.copy(title=it) },label={ Text("ชื่อ",color=IceCyan) },singleLine=true,modifier=Modifier.fillMaxWidth())
Row(verticalAlignment=VCenter,horizontalArrangement=hSp(8)) {
Icon(Icons.Default.InsertDriveFile,null,tint=IceCyan,modifier=Modifier.size(18.dp))
Text(d.romFileName,color=WHT.copy(alpha=0.85f),fontSize=13.sp,fontWeight=FontWeight.SemiBold)
}
Row(modifier=Modifier.fillMaxWidth(),horizontalArrangement=hSp(10)) {
OutlinedButton(onClick={ onScrapeNow(d.title) },shape=Pill,border=BorderStroke(1.2.dp,IceCyan),modifier=Modifier.weight(1f).height(44.dp)) {
Icon(Icons.Default.CloudDownload,null,tint=IceCyan); Spacer(Modifier.width(8.dp))
Text(if (isScraping) "กำลังดึงหน้าปกเกม..." else "สเครปหน้าปกเกม",color=WHT,fontWeight=FontWeight.Bold)
}
OutlinedButton(onClick={ onOpenCoverPicker(d.title) },shape=Pill,border=BorderStroke(1.2.dp,IceGreen),modifier=Modifier.height(44.dp)) {
Icon(Icons.Default.Collections,null,tint=IceGreen); Spacer(Modifier.width(6.dp))
Text("เลือกปกจากเว็บ",color=IceGreen,fontWeight=FontWeight.Bold)
}
}
Row(modifier=Modifier.fillMaxWidth(),horizontalArrangement=hSp(22),verticalAlignment=VCenter) {
Box(modifier=Modifier.width(110.dp).height(156.dp).clip(rDp(12)).background(Color(0xFF141E33)).border(1.5.dp,IceCyan.copy(alpha=0.5f),rDp(12)),contentAlignment=Alignment.Center) {
buildCoilReq(d.effectiveCoverModel)?.let { AsyncImage(model=it,null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize()) }?:Text("ไม่มีปก",color=WHT.copy(alpha=0.5f),fontSize=11.sp)
}
Column(modifier=Modifier.weight(1f),verticalArrangement=vSp(10)) {
OutlinedButton(onClick=onPickCover,shape=Pill,border=BorderStroke(1.dp,WHT.copy(alpha=0.35f)),modifier=Modifier.fillMaxWidth().height(42.dp)) { Text("เปลี่ยนหน้าปก",color=WHT) }
OutlinedButton(onClick={ d=d.copy(coverUrl=null,customCoverUri=null) },shape=Pill,border=BorderStroke(1.dp,Color(0xFFFF5252)),modifier=Modifier.fillMaxWidth().height(42.dp)) { Text("ลบหน้าปก",color=Color(0xFFFF5252)) }
}
}
Row(modifier=Modifier.fillMaxWidth(),horizontalArrangement=hSp(22),verticalAlignment=VCenter) {
Box(modifier=Modifier.width(188.dp).height(106.dp).clip(rDp(12)).background(Color(0xFF141E33)).border(1.5.dp,WHT.copy(alpha=0.25f),rDp(12)),contentAlignment=Alignment.Center) {
buildCoilReq(d.effectiveFanartModel)?.let { AsyncImage(model=it,null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize()) }?:Text("ไม่มีภาพในเกม",color=WHT.copy(alpha=0.5f),fontSize=11.sp)
}
Column(modifier=Modifier.weight(1f),verticalArrangement=vSp(10)) {
OutlinedButton(onClick=onPickFanart,shape=Pill,border=BorderStroke(1.dp,WHT.copy(alpha=0.35f)),modifier=Modifier.fillMaxWidth().height(42.dp)) { Text("เปลี่ยนภาพในเกม",color=WHT) }
OutlinedButton(onClick={ d=d.copy(fanartUrl=null,customFanartUri=null) },shape=Pill,border=BorderStroke(1.dp,Color(0xFFFF5252)),modifier=Modifier.fillMaxWidth().height(42.dp)) { Text("ลบภาพในเกม",color=Color(0xFFFF5252)) }
}
}
Text("Metadata",color=IceCyan,fontSize=15.sp,fontWeight=FontWeight.Bold)
MetaField("เลือกวัน",d.releaseDate,{ d=d.copy(releaseYear=it) },true)
MetaField("แนวเกม",d.genre,{ d=d.copy(genre=it) })
MetaField("ผู้พัฒนา",d.developer,{ d=d.copy(developer=it) })
MetaField("ผู้จัดจำหน่าย",d.publisher,{ d=d.copy(publisher=it) })
Text("เขียนทับการตั้งค่าแพลตฟอร์ม",color=IceCyan,fontSize=15.sp,fontWeight=FontWeight.Bold)
LazyRow(horizontalArrangement=hSp(8)) {
item {
val def=d.overrideEmulatorPackage.isNullOrBlank()
Surface(color=if (def) Color(0x3300E5FF) else WHT.copy(alpha=0.06f),shape=rDp(12),border=BorderStroke(1.dp,if (def) IceCyan else WHT.copy(alpha=0.15f)),modifier=Modifier.clickable { d=d.copy(overrideEmulatorName=null,overrideEmulatorPackage=null,overrideEmulatorActivity=null,overrideRetroArchCore=null) }) {
Column(modifier=Modifier.padding(horizontal=14.dp,vertical=8.dp)) { Text("อีมูเลเตอร์",color=WHT.copy(alpha=0.6f),fontSize=10.sp); Text("ใช้การตั้งค่าแพลตฟอร์ม",color=WHT,fontSize=12.sp,fontWeight=FontWeight.Bold) }
}
}
itemsIndexed(console.emulatorPresets) { _,pr->
val sel=d.overrideEmulatorPackage== pr.packageName && d.overrideEmulatorName== pr.displayName
Surface(color=if (sel) Color(0x3300E5FF) else WHT.copy(alpha=0.06f),shape=rDp(12),border=BorderStroke(1.dp,if (sel) IceCyan else WHT.copy(alpha=0.15f)),modifier=Modifier.clickable { d=d.copy(overrideEmulatorName=pr.displayName,overrideEmulatorPackage=pr.packageName,overrideEmulatorActivity=pr.activityName,overrideRetroArchCore=pr.retroArchCore) }) {
Column(modifier=Modifier.padding(horizontal=14.dp,vertical=8.dp)) { Text("กำหนดแยกเฉพาะเกมนี้",color=IceCyan,fontSize=10.sp); Text(pr.displayName,color=WHT,fontSize=12.sp,fontWeight=FontWeight.Bold) }
}
}
}
Row(modifier=Modifier.fillMaxWidth().padding(top=4.dp),horizontalArrangement=Arrangement.SpaceBetween) {
OutlinedButton(onClick=onDelete,shape=Pill,border=BorderStroke(1.dp,Color(0xFFFF5252))) { Text("ลบ",color=Color(0xFFFF5252),fontWeight=FontWeight.Bold) }
Button(onClick={ onSave(d) },colors=ButtonDefaults.buttonColors(containerColor=IceCyan),shape=Pill) { Text("เซฟ",color=BLK,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=16.dp)) }
}
}
}
}

@Composable
fun MetaField(label:String,v:String,onChg:(String)->Unit,cal:Boolean=false) {
OutlinedTextField(
value=v,onValueChange=onChg,label={ Text(label) },singleLine=true,
leadingIcon={ IconButton(onClick={ onChg("") }) { Icon(Icons.Default.Clear,null,tint=WHT.copy(alpha=0.7f)) } },
trailingIcon=if (cal) ({ Icon(Icons.Default.CalendarMonth,null,tint=IceCyan) }) else null,
modifier=Modifier.fillMaxWidth()
)
}

@Composable
fun OnlineCoverPickerDialog(game:GameItem,cands:List<ScrapedCandidate>,busy:Boolean,onSearch:(String)->Unit,onSelect:(ScrapedCandidate)->Unit,onDismiss:()->Unit) {
var q by remember(game.id) { mutableStateOf(game.title) }
AlertDialog(
onDismissRequest=onDismiss,containerColor=Color(0xFF0E1628),titleContentColor=WHT,textContentColor=WHT,
title={ Text("เลือกหน้าปกออนไลน์ (${game.title})",fontWeight=FontWeight.Bold,fontSize=16.sp) },
text={
Column(modifier=Modifier.fillMaxWidth(),verticalArrangement=vSp(10)) {
Row(horizontalArrangement=hSp(8),verticalAlignment=VCenter) {
OutlinedTextField(value=q,onValueChange={ q=it },label={ Text("ชื่อเกมภาษาอังกฤษ") },singleLine=true,modifier=Modifier.weight(1f))
Button(onClick={ onSearch(q) },colors=ButtonDefaults.buttonColors(containerColor=IceCyan)) { Text("ค้นหา",color=BLK,fontWeight=FontWeight.Bold) }
}
if (busy) Box(modifier=Modifier.fillMaxWidth().height(140.dp),contentAlignment=Alignment.Center) { Text("กำลังค้นหาหน้าปกจากเซิร์ฟเวอร์...",color=IceCyan) }
else if (cands.isEmpty()) Box(modifier=Modifier.fillMaxWidth().height(110.dp),contentAlignment=Alignment.Center) { Text("ไม่พบหน้าปก ลองค้นด้วยชื่อสั้นๆ เช่น God of War",color=WHT.copy(alpha=0.7f)) }
else LazyRow(horizontalArrangement=hSp(12),modifier=Modifier.fillMaxWidth().height(205.dp)) {
itemsIndexed(cands) { _,c->
Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.width(130.dp).clip(rDp(12)).background(IceRowBg).border(1.dp,IceCyan.copy(alpha=0.5f),rDp(12)).clickable { onSelect(c) }.padding(8.dp)) {
Box(modifier=Modifier.width(110.dp).height(140.dp).clip(rDp(8)).background(BLK)) {
buildCoilReq(c.coverUrl)?.let { AsyncImage(model=it,null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize()) }
}
Spacer(Modifier.height(4.dp))
Text(c.title,color=WHT,fontSize=11.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Clip)
Text(c.sourceName,color=IceCyan,fontSize=10.sp,maxLines=1)
}
}
}
}
},
confirmButton={ TextButton(onClick=onDismiss) { Text("ปิด",color=IceCyan) } }
)
}

@Composable
fun TopConsoleNavBar(activeTab:TopNavTab,onSelectTab:(TopNavTab)->Unit,onOpenSearch:()->Unit,onOpenSettings:()->Unit) {
val ctx=LocalContext.current
var timeText by remember { mutableStateOf("") }
var batteryPct by remember { mutableIntStateOf(100) }
var wifiOn by remember { mutableStateOf(true) }
LaunchedEffect(ctx) {
val fmt=SimpleDateFormat("HH:mm",Locale.getDefault())
while (true) {
timeText=fmt.format(Date())
val bm=ctx.registerReceiver(null as BroadcastReceiver?,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
val l=bm?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)?:-1; val s=bm?.getIntExtra(BatteryManager.EXTRA_SCALE,-1)?:-1
if (l >= 0 && s > 0) batteryPct=(l * 100) / s
val cm=ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
wifiOn=cm?.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
delay(15_000L)
}
}
SpaceRow() {
Row(verticalAlignment=VCenter) {
Box(modifier=Modifier.size(44.dp).shadow(12.dp,rDp(12),spotColor=IceCyan).clip(rDp(12)).border(1.5.dp,Color(0xFF40C4FF),rDp(12)).clickable { onSelectTab(TopNavTab.MY_GAMES) }) {
Image(painter=painterResource(id=R.drawable.app_logo),contentDescription="Ice Logo",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
}
Spacer(Modifier.width(18.dp))
Row(verticalAlignment=VCenter,horizontalArrangement=hSp(18)) {
Row(verticalAlignment=VCenter,horizontalArrangement=hSp(10),modifier=Modifier.clip(Pill).background(WHT.copy(alpha=0.08f)).border(1.dp,WHT.copy(alpha=0.12f),Pill).padding(horizontal=12.dp,vertical=6.dp)) {
Box(modifier=Modifier.size(28.dp).clip(CircleShape).background(IceCyan),contentAlignment=Alignment.Center) { Icon(Icons.Default.Person,null,tint=BLK,modifier=Modifier.size(17.dp)) }
Text("Player One",color=WHT,fontSize=13.sp,fontWeight=FontWeight.Bold)
}
Row(horizontalArrangement=hSp(8)) {
TopNavTab.entries.forEach { tab->
val sel=tab== activeTab
Text(tab.title,color=if (sel) WHT else WHT.copy(alpha=0.58f),fontSize=15.sp,fontWeight=if (sel) FontWeight.ExtraBold else FontWeight.Medium,modifier=Modifier.clip(Pill).background(if (sel) Color(0x3300E5FF) else Color.Transparent).border(if (sel) 1.dp else 0.dp,if (sel) IceCyan else Color.Transparent,Pill).clickable { onSelectTab(tab) }.padding(horizontal=16.dp,vertical=6.dp))
}
}
}
}
Row(verticalAlignment=VCenter,horizontalArrangement=hSp(14),modifier=Modifier.clip(Pill).background(BLK.copy(alpha=0.40f)).padding(horizontal=16.dp,vertical=8.dp)) {
IconButton(onClick=onOpenSearch,modifier=Modifier.size(24.dp)) { Icon(Icons.Default.Search,null,tint=WHT) }
IconButton(onClick=onOpenSettings,modifier=Modifier.size(24.dp)) { Icon(Icons.Default.Settings,null,tint=IceCyan) }
Icon(if (wifiOn) Icons.Default.Wifi else Icons.Default.WifiOff,null,tint=IceCyan,modifier=Modifier.size(19.dp))
Text("$batteryPct%",color=IceGreen,fontSize=13.sp,fontWeight=FontWeight.Bold)
Text(timeText,color=WHT,fontSize=15.sp,fontWeight=FontWeight.Bold)
}
}
}

@Composable
fun ConsoleCategorySelector(platforms:List<ConfiguredPlatform>,selectedId:String?,onSelect:(String)->Unit,onAdd:()->Unit) {
Row(modifier=Modifier.fillMaxWidth(),verticalAlignment=VCenter,horizontalArrangement=hSp(10)) {
Surface(color=WHT.copy(alpha=0.12f),shape=rDp(8)) { Text("L1",color=WHT,fontSize=11.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=9.dp,vertical=5.dp)) }
LazyRow(modifier=Modifier.weight(1f),horizontalArrangement=hSp(8),verticalAlignment=VCenter) {
itemsIndexed(platforms) { _,cfg->
val sel=cfg.platformId== selectedId
Surface(color=if (sel) Color(cfg.console.accentColorHex) else WHT.copy(alpha=0.08f),shape=Pill,border=BorderStroke(if (sel) 1.5.dp else 1.dp,WHT.copy(alpha=if (sel) 0.9f else 0.15f)),modifier=Modifier.clickable { onSelect(cfg.platformId) }) {
Text(cfg.console.shortTag,color=WHT,fontSize=13.sp,fontWeight=if (sel) FontWeight.ExtraBold else FontWeight.Medium,modifier=Modifier.padding(horizontal=16.dp,vertical=6.dp))
}
}
item {
Surface(color=Color(0x2600E5FF),shape=Pill,border=BorderStroke(1.dp,IceCyan),modifier=Modifier.clickable { onAdd() }) {
Row(verticalAlignment=VCenter,horizontalArrangement=hSp(4),modifier=Modifier.padding(horizontal=14.dp,vertical=6.dp)) {
Icon(Icons.Default.Add,null,tint=IceCyan,modifier=Modifier.size(15.dp))
Text("เพิ่มหมวดเกม",color=IceCyan,fontSize=12.sp,fontWeight=FontWeight.Bold)
}
}
}
}
Surface(color=WHT.copy(alpha=0.12f),shape=rDp(8)) { Text("R1",color=WHT,fontSize=11.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=9.dp,vertical=5.dp)) }
}
}

@Composable
fun EmptyPlatformOnboardingCard(onAddFirstPlatform:()->Unit) {
Box(modifier=Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
Surface(color=Color(0xCC0D1526),shape=rDp(24),border=BorderStroke(1.5.dp,IceCyan.copy(alpha=0.6f)),modifier=Modifier.widthIn(max=560.dp)) {
Column(modifier=Modifier.padding(horizontal=32.dp,vertical=26.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=vSp(12)) {
Image(painter=painterResource(id=R.drawable.app_logo),null,modifier=Modifier.size(64.dp).clip(rDp(16)))
Text("ยินดีต้อนรับสู่ Ice Emulator Launcher v3.0",color=WHT,fontSize=21.sp,fontWeight=FontWeight.Bold)
Text("กดปุ่มด้านล่างเพื่อเลือกเครื่องเกม แอปอีมูเลเตอร์ และโฟลเดอร์เก็บเกม ระบบจะสแกนและดึงหน้าปกแท้พร้อมภาพในเกมให้อัตโนมัติทันที",color=WHT.copy(alpha=0.78f),fontSize=13.sp,textAlign=TextAlign.Center)
Button(onClick=onAddFirstPlatform,colors=ButtonDefaults.buttonColors(containerColor=IceCyan),shape=Pill,contentPadding=PaddingValues(horizontal=24.dp,vertical=12.dp)) {
Icon(Icons.Default.AddCircleOutline,null,tint=BLK); Spacer(Modifier.width(8.dp))
Text("ตั้งค่าเพิ่มแพลตฟอร์มเครื่องเกมแรกของคุณ",color=BLK,fontWeight=FontWeight.Bold)
}
}
}
}
}

@Composable
fun IceConsoleSettingsHub(
state:LauncherUiState,onSelectSection:(SettingsSection)->Unit,onAddPlatform:()->Unit,onEditPlatform:(ConfiguredPlatform)->Unit,
onManageGamesInPlatform:(ConfiguredPlatform?)->Unit,onEditSingleGame:(GameItem)->Unit,
onRescanPlatform:(ConfiguredPlatform)->Unit,onDeletePlatform:(String)->Unit,onSavePlatform:(ConfiguredPlatform,Boolean)->Unit,
onCloseEditor:()->Unit,onToggleScraper:(String)->Unit,onMoveScraper:(Int,Int)->Unit,
onOpenAccount:(ScraperSourceConfig?)->Unit,onSaveAccount:(String,String,String)->Unit,onScrapeAll:()->Unit
) {
Row(modifier=Modifier.fillMaxSize(),horizontalArrangement=hSp(18)) {
Surface(color=IceCardBg,shape=rDp(22),border=BorderStroke(1.dp,IceCyan.copy(alpha=0.28f)),modifier=Modifier.width(230.dp).fillMaxHeight()) {
Column(modifier=Modifier.padding(16.dp),verticalArrangement=vSp(10)) {
Text("ICE CONSOLE SETTINGS",color=IceCyan,fontSize=11.sp,fontWeight=FontWeight.Bold)
SettingsSection.entries.forEach { sec->
val sel=state.activeSettingsSection== sec
Surface(color=if (sel) Color(0x3300E5FF) else WHT.copy(alpha=0.04f),shape=rDp(14),border=BorderStroke(1.dp,if (sel) IceCyan else WHT.copy(alpha=0.08f)),modifier=Modifier.fillMaxWidth().clickable { onSelectSection(sec) }) {
Row(modifier=Modifier.padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=VCenter,horizontalArrangement=hSp(10)) {
Icon(if (sec== SettingsSection.PLATFORMS) Icons.Default.SportsEsports else Icons.Default.CloudDownload,null,tint=if (sel) IceCyan else WHT,modifier=Modifier.size(20.dp))
Column {
Text(sec.title,color=WHT,fontSize=13.sp,fontWeight=FontWeight.Bold)
Text(sec.subtitle,color=WHT.copy(alpha=0.55f),fontSize=10.sp,maxLines=1,overflow=TextOverflow.Clip)
}
}
}
}
}
}
Surface(color=IceCardBg,shape=rDp(22),border=BorderStroke(1.dp,WHT.copy(alpha=0.14f)),modifier=Modifier.weight(1f).fillMaxHeight()) {
if (state.isPlatformEditorOpen && state.editingPlatform != null) {
PlatformConfigurationStudio(state.editingPlatform,onSavePlatform,{ onDeletePlatform(state.editingPlatform.platformId) },onCloseEditor)
} else if (state.managingGamesPlatform != null) {
PlatformGamesListScreen(state.managingGamesPlatform,state.allGames.filter { it.consoleId== state.managingGamesPlatform.platformId },onEditSingleGame) { onManageGamesInPlatform(null) }
} else if (state.activeSettingsSection== SettingsSection.PLATFORMS) {
PlatformsManagementPanel(state.configuredPlatforms,onAddPlatform,onEditPlatform,onManageGamesInPlatform,onRescanPlatform)
} else {
WebCoverScraperSettingsPanel(state.scraperSources,state.editingScraperAccount,onToggleScraper,onMoveScraper,onOpenAccount,onSaveAccount,onScrapeAll)
}
}
}
}

@Composable
fun PlatformGamesListScreen(cfg:ConfiguredPlatform,games:List<GameItem>,onEditGame:(GameItem)->Unit,onBack:()->Unit) {
Column(modifier=Modifier.fillMaxSize().padding(20.dp),verticalArrangement=vSp(12)) {
SpaceRow() {
Row(verticalAlignment=VCenter,horizontalArrangement=hSp(10)) {
IconButton(onClick=onBack) { Icon(Icons.Default.ArrowBack,null,tint=WHT) }
Text(cfg.console.displayName,color=WHT,fontSize=19.sp,fontWeight=FontWeight.Bold)
}
Text("ทั้งหมด ${games.size} เกม",color=IceCyan,fontSize=13.sp,fontWeight=FontWeight.Bold)
}
LazyColumn(verticalArrangement=vSp(10)) {
itemsIndexed(games) { _,g->
Surface(color=IceRowBg,shape=rDp(16),border=BorderStroke(1.dp,IceCyan.copy(alpha=0.35f)),modifier=Modifier.fillMaxWidth().clickable { onEditGame(g) }) {
Row(modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=VCenter) {
Row(verticalAlignment=VCenter,horizontalArrangement=hSp(14)) {
Box(modifier=Modifier.width(42.dp).height(58.dp).clip(rDp(8)).background(BLK)) {
buildCoilReq(g.effectiveCoverModel)?.let { AsyncImage(model=it,null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize()) }
}
Column {
Text(g.title,color=WHT,fontSize=15.sp,fontWeight=FontWeight.Bold)
Text("${g.releaseDate} • ${g.genre} • ${g.developer}",color=IceCyan,fontSize=11.sp)
}
}
IconButton(onClick={ onEditGame(g) }) { Icon(Icons.Default.Edit,null,tint=WHT) }
}
}
}
}
}
}

@Composable
fun PlatformsManagementPanel(platforms:List<ConfiguredPlatform>,onAdd:()->Unit,onEdit:(ConfiguredPlatform)->Unit,onManageGames:(ConfiguredPlatform)->Unit,onRescan:(ConfiguredPlatform)->Unit) {
Column(modifier=Modifier.fillMaxSize().padding(20.dp)) {
SpaceRow() {
Column {
Text("แพลตฟอร์มเครื่องเกมที่เปิดใช้งาน",color=WHT,fontSize=19.sp,fontWeight=FontWeight.Bold)
Text("แตะที่แพลตฟอร์มเพื่อจัดการรายชื่อเกมและปรับแต่งหน้าปกเป็นรายเกม",color=WHT.copy(alpha=0.65f),fontSize=12.sp)
}
Button(onClick=onAdd,colors=ButtonDefaults.buttonColors(containerColor=IceCyan),shape=Pill) {
Icon(Icons.Default.Add,null,tint=BLK); Spacer(Modifier.width(6.dp))
Text("เพิ่มแพลตฟอร์ม",color=BLK,fontWeight=FontWeight.Bold)
}
}
Spacer(Modifier.height(14.dp))
if (platforms.isEmpty()) {
Box(modifier=Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { Text("ยังไม่มีแพลตฟอร์มที่ตั้งค่าไว้ กดปุ่ม '+ เพิ่มแพลตฟอร์ม' มุมขวาบนเพื่อเริ่มต้น",color=WHT.copy(alpha=0.6f)) }
} else {
LazyColumn(verticalArrangement=vSp(10)) {
itemsIndexed(platforms) { _,cfg->
Surface(color=IceRowBg,shape=rDp(16),border=BorderStroke(1.dp,Color(cfg.console.accentColorHex).copy(alpha=0.55f)),modifier=Modifier.fillMaxWidth().clickable { onManageGames(cfg) }) {
Row(modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=VCenter) {
Row(verticalAlignment=VCenter,horizontalArrangement=hSp(14),modifier=Modifier.weight(1f)) {
Surface(color=Color(cfg.console.accentColorHex),shape=rDp(12),modifier=Modifier.size(44.dp)) {
Box(contentAlignment=Alignment.Center) { Text(cfg.console.shortTag,color=WHT,fontSize=11.sp,fontWeight=FontWeight.Bold) }
}
Column {
Text(cfg.console.displayName,color=WHT,fontSize=15.sp,fontWeight=FontWeight.Bold)
Text("แอปอีมูเลเตอร์:${cfg.emulatorName}",color=IceCyan,fontSize=12.sp)
Text("โฟลเดอร์ ROM:${cfg.romFolderPath}",color=WHT.copy(alpha=0.65f),fontSize=11.sp,maxLines=1,overflow=TextOverflow.Clip)
}
}
Row(horizontalArrangement=hSp(4)) {
OutlinedButton(onClick={ onManageGames(cfg) },shape=Pill,border=BorderStroke(1.dp,IceCyan)) { Text("จัดการเกม",color=IceCyan,fontSize=11.sp) }
IconButton(onClick={ onRescan(cfg) }) { Icon(Icons.Default.Refresh,null,tint=IceGreen) }
IconButton(onClick={ onEdit(cfg) }) { Icon(Icons.Default.Edit,null,tint=WHT) }
}
}
}
}
}
}
}
}

@Composable
fun PlatformConfigurationStudio(init:ConfiguredPlatform,onSave:(ConfiguredPlatform,Boolean)->Unit,onDelete:()->Unit,onCancel:()->Unit) {
val ctx=LocalContext.current
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
val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri:Uri?->
if (uri != null) {
try { ctx.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_:Exception) {}
treeUri=uri.toString(); folderPath=uri.lastPathSegment?.replace("primary:","/storage/emulated/0/")?:uri.toString()
}
}
val wallPicker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri:Uri?->if (uri != null) wallUri=uri.toString() }
Column(modifier=Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=vSp(12)) {
SpaceRow() {
Text("ปรับแต่งแพลตฟอร์มเครื่องเกม",color=WHT,fontSize=18.sp,fontWeight=FontWeight.Bold)
TextButton(onClick=onCancel) { Text("ย้อนกลับ",color=IceCyan) }
}
Text("1. เลือกประเภทของเครื่องเกม",color=IceCyan,fontSize=13.sp,fontWeight=FontWeight.Bold)
LazyRow(horizontalArrangement=hSp(8)) {
itemsIndexed(ConsolePlatform.entries) { _,c->
val sel=c== console
Surface(color=if (sel) Color(c.accentColorHex) else WHT.copy(alpha=0.08f),shape=rDp(12),border=BorderStroke(1.dp,if (sel) WHT else WHT.copy(alpha=0.15f)),modifier=Modifier.clickable {
console=c; val pr=c.emulatorPresets.first(); emuName=pr.displayName; emuPkg=pr.packageName; emuAct=pr.activityName; core=pr.retroArchCore; folderPath="android/download/รวมเกม${c.id}"
}) { Text(c.displayName,color=WHT,fontSize=12.sp,fontWeight=if (sel) FontWeight.ExtraBold else FontWeight.Medium,modifier=Modifier.padding(horizontal=14.dp,vertical=8.dp)) }
}
}
Text("2. เลือกแอปอีมูเลเตอร์ที่จะใช้รันเกมของหมวดนี้",color=IceCyan,fontSize=13.sp,fontWeight=FontWeight.Bold)
LazyRow(horizontalArrangement=hSp(8)) {
itemsIndexed(console.emulatorPresets) { _,pr->
val sel=pr.packageName== emuPkg && pr.displayName== emuName
Surface(color=if (sel) Color(0x3300E5FF) else WHT.copy(alpha=0.06f),shape=rDp(12),border=BorderStroke(1.dp,if (sel) IceCyan else WHT.copy(alpha=0.15f)),modifier=Modifier.clickable { emuName=pr.displayName; emuPkg=pr.packageName; emuAct=pr.activityName; core=pr.retroArchCore }) {
Column(modifier=Modifier.padding(horizontal=14.dp,vertical=8.dp)) {
Text(pr.displayName,color=WHT,fontSize=13.sp,fontWeight=FontWeight.Bold)
Text(pr.packageName,color=WHT.copy(alpha=0.6f),fontSize=10.sp)
}
}
}
}
Text("3. ตำแหน่งที่อยู่ของโฟลเดอร์รวม ROM ของเครื่องนี้",color=IceCyan,fontSize=13.sp,fontWeight=FontWeight.Bold)
Row(modifier=Modifier.fillMaxWidth(),horizontalArrangement=hSp(10),verticalAlignment=VCenter) {
OutlinedTextField(value=folderPath,onValueChange={ folderPath=it },singleLine=true,modifier=Modifier.weight(1f))
Button(onClick={ folderPicker.launch(null) },colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF1E293B)),shape=rDp(12)) {
Icon(Icons.Default.FolderOpen,null,tint=IceCyan); Spacer(Modifier.width(6.dp))
Text("เลือกโฟลเดอร์",color=WHT,fontSize=12.sp)
}
}
SpaceRow() {
Row(horizontalArrangement=hSp(8),verticalAlignment=VCenter) {
Text("อัตราส่วนหน้าปก:",color=WHT,fontSize=12.sp)
CoverAspectRatio.entries.forEach { r->
val sel=r.label== aspect
Surface(color=if (sel) Color(0x3300E5FF) else WHT.copy(alpha=0.06f),shape=rDp(8),border=BorderStroke(1.dp,if (sel) IceCyan else WHT.copy(alpha=0.15f)),modifier=Modifier.clickable { aspect=r.label }) {
Text(r.label,color=WHT,fontSize=11.sp,modifier=Modifier.padding(horizontal=10.dp,vertical=6.dp))
}
}
}
OutlinedButton(onClick={ wallPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },border=BorderStroke(1.dp,IceCyan)) {
Text(if (wallUri != null) "เปลี่ยนภาพพื้นหลังแล้ว" else "เลือกภาพพื้นหลัง",color=WHT,fontSize=12.sp)
}
}
Row(verticalAlignment=VCenter,modifier=Modifier.clickable { addDemo=!addDemo }) {
Checkbox(checked=addDemo,onCheckedChange={ addDemo=it })
Text("แสดงปกเกมตัวอย่างหากในโฟลเดอร์ยังไม่มีไฟล์ ROM",color=WHT.copy(alpha=0.85f),fontSize=12.sp)
}
Row(modifier=Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
OutlinedButton(onClick=onDelete,border=BorderStroke(1.dp,Color(0xFFFF5252)),shape=Pill) { Text("ลบ",color=Color(0xFFFF5252),fontWeight=FontWeight.Bold) }
Button(onClick={ onSave(ConfiguredPlatform(console.id,emuName,emuPkg,emuAct,core,folderPath,treeUri,aspect,wallUri),addDemo) },colors=ButtonDefaults.buttonColors(containerColor=IceCyan),shape=Pill,contentPadding=PaddingValues(horizontal=28.dp,vertical=12.dp)) { Text("เซฟ",color=BLK,fontWeight=FontWeight.Bold) }
}
}
}

@Composable
fun WebCoverScraperSettingsPanel(sources:List<ScraperSourceConfig>,editingAccount:ScraperSourceConfig?,onToggle:(String)->Unit,onMove:(Int,Int)->Unit,onOpenAccount:(ScraperSourceConfig?)->Unit,onSaveAccount:(String,String,String)->Unit,onScrapeAll:()->Unit) {
Column(modifier=Modifier.fillMaxSize().padding(20.dp)) {
SpaceRow() {
Column {
Text("ปรับแต่งการดึงปกไฟล์เกม",color=WHT,fontSize=19.sp,fontWeight=FontWeight.Bold)
Text("เลือกเว็บฐานข้อมูลปกเกม (Screenscraper,Libretro,TheGamesDb,IGDB,SteamGridDB) และผูกบัญชี",color=WHT.copy(alpha=0.65f),fontSize=12.sp)
}
Button(onClick=onScrapeAll,colors=ButtonDefaults.buttonColors(containerColor=IceGreen),shape=Pill) {
Icon(Icons.Default.CloudDownload,null,tint=BLK); Spacer(Modifier.width(6.dp))
Text("ดึงปกทุกเกมทันที",color=BLK,fontWeight=FontWeight.Bold,fontSize=12.sp)
}
}
Spacer(Modifier.height(12.dp))
LazyColumn(verticalArrangement=vSp(8)) {
itemsIndexed(sources) { idx,src->
Surface(color=IceRowBg,shape=rDp(16),border=BorderStroke(1.dp,if (src.isEnabled) IceGreen.copy(alpha=0.5f) else WHT.copy(alpha=0.1f)),modifier=Modifier.fillMaxWidth()) {
Row(modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=10.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=VCenter) {
Row(verticalAlignment=VCenter,horizontalArrangement=hSp(12)) {
Column {
Icon(Icons.Default.KeyboardArrowUp,null,tint=WHT,modifier=Modifier.size(18.dp).clickable { onMove(idx,-1) })
Icon(Icons.Default.KeyboardArrowDown,null,tint=WHT,modifier=Modifier.size(18.dp).clickable { onMove(idx,1) })
}
Checkbox(checked=src.isEnabled,onCheckedChange={ onToggle(src.id) },colors=CheckboxDefaults.colors(checkedColor=IceGreen,checkmarkColor=BLK))
Column {
Text(src.name,color=WHT,fontSize=15.sp,fontWeight=FontWeight.Bold)
if (src.accountUsername.isNotBlank()) Text("บัญชี:${src.accountUsername}",color=IceCyan,fontSize=11.sp)
}
}
if (src.requiresAccountOrKey) {
OutlinedButton(onClick={ onOpenAccount(src) },shape=Pill,border=BorderStroke(1.dp,IceCyan)) {
Icon(Icons.Default.PersonAdd,null,tint=IceCyan,modifier=Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
Text("เพิ่มบัญชี",color=WHT,fontSize=12.sp)
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
onDismissRequest={ onOpenAccount(null) },containerColor=Color(0xFF101829),titleContentColor=WHT,textContentColor=WHT,
title={ Text("เพิ่มบัญชี (${editingAccount.name})",fontWeight=FontWeight.Bold) },
text={ Column(verticalArrangement=vSp(8)) { OutlinedTextField(value=u,onValueChange={ u=it },label={ Text("Username") },singleLine=true); OutlinedTextField(value=p,onValueChange={ p=it },label={ Text("Password / API Key") },singleLine=true) } },
confirmButton={ Button(onClick={ onSaveAccount(editingAccount.id,u,p) }) { Text("บันทึก") } },
dismissButton={ TextButton(onClick={ onOpenAccount(null) }) { Text("ยกเลิก",color=WHT) } }
)
}
}

@Composable
fun DynamicFanartBackground(fanartUrl:String?,accentColor:Color) {
Box(modifier=Modifier.fillMaxSize()) {
Crossfade(targetState=fanartUrl,animationSpec=tween(600,easing=FastOutSlowInEasing),label="Fanart") { url->
buildCoilReq(url)?.let { AsyncImage(model=it,null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize().alpha(0.55f).blur(2.dp)) }
?:Box(modifier=Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accentColor.copy(alpha=0.35f),IceDarkBg),radius=1400f)))
}
Box(modifier=Modifier.fillMaxSize().background(Brush.verticalGradient(0.0f to Color(0xCC060911),0.4f to Color(0x66060911),1.0f to Color(0xF505070D))))
}
}

@Composable
fun HorizontalGameCarousel(games:List<GameItem>,focusedIndex:Int,ratio:CoverAspectRatio,accent:Color,listState:LazyListState,onSelect:(Int)->Unit,onLaunch:()->Unit,onEditPlatform:()->Unit) {
if (games.isEmpty()) {
Box(modifier=Modifier.fillMaxWidth().height(190.dp),contentAlignment=Alignment.Center) { OutlinedButton(onClick=onEditPlatform,border=BorderStroke(1.dp,IceCyan)) { Text("ตั้งค่าโฟลเดอร์ ROM ของหมวดนี้",color=IceCyan) } }
return
}
LazyRow(state=listState,modifier=Modifier.fillMaxWidth().height(230.dp),contentPadding=PaddingValues(horizontal=18.dp,vertical=10.dp),horizontalArrangement=hSp(22),verticalAlignment=VCenter) {
itemsIndexed(items=games,key={ _,g->g.id }) { idx,game->
val isFocused=idx== focusedIndex
val scale by animateFloatAsState(if (isFocused) 1.14f else 0.94f,tween(240),label="scale")
val glowBrush=Brush.linearGradient(if (isFocused) listOf(IceCyan,WHT,accent) else listOf(WHT.copy(alpha=0.18f),WHT.copy(alpha=0.06f)))
Box(modifier=Modifier.width(ratio.widthDp.dp).height(ratio.heightDp.dp).scale(scale).shadow(if (isFocused) 24.dp else 4.dp,rDp(16),spotColor=accent).clip(rDp(16)).background(Brush.linearGradient(listOf(Color(0xFF162238),Color(0xFF0D1322)))).border(if (isFocused) 3.dp else 1.dp,glowBrush,rDp(16)).clickable(interactionSource=remember { MutableInteractionSource() },indication=null) { if (isFocused) onLaunch() else onSelect(idx) }) {
buildCoilReq(game.effectiveCoverModel)?.let { AsyncImage(model=it,contentDescription=game.title,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize()) }
Box(modifier=Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent,1.0f to Color(0xE6060911))))
Text(game.title,color=WHT,fontSize=11.sp,fontWeight=if (isFocused) FontWeight.Bold else FontWeight.Medium,maxLines=2,overflow=TextOverflow.Clip,modifier=Modifier.align(Alignment.BottomStart).padding(9.dp))
}
}
}
}

@Composable
fun GameInformationPanel(game:GameItem?,cfg:ConfiguredPlatform?,isScraping:Boolean,onPlay:()->Unit,onEditGame:()->Unit,onScrape:()->Unit,onChooseOnlineCover:()->Unit) {
if (game== null || cfg== null) return
val c=cfg.console; val emu=game.overrideEmulatorName?:cfg.emulatorName
AnimatedContent(targetState=game,transitionSpec={ fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },label="Info") { active->
Row(modifier=Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Bottom) {
Column(modifier=Modifier.weight(1.45f),verticalArrangement=vSp(6)) {
Row(verticalAlignment=VCenter,horizontalArrangement=hSp(10)) {
Surface(color=Color(c.accentColorHex),shape=rDp(6)) { Text(c.shortTag,color=WHT,fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=8.dp,vertical=3.dp)) }
Text("รันด้วย:$emu • ไฟล์:${active.romFileName}",color=WHT.copy(alpha=0.76f),fontSize=11.sp,maxLines=1,overflow=TextOverflow.Clip)
}
Text(active.title,color=WHT,fontSize=25.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Clip)
Row(horizontalArrangement=hSp(22),verticalAlignment=VCenter) {
MetadataBadgeColumn("วันวางจำหน่าย",active.releaseDate.ifBlank { "-" })
MetadataBadgeColumn("แนวเกม",active.genre.ifBlank { "-" })
MetadataBadgeColumn("ผู้พัฒนา",active.developer.ifBlank { "-" })
MetadataBadgeColumn("ผู้จัดจำหน่าย",active.publisher.ifBlank { "-" })
}
Row(horizontalArrangement=hSp(8),modifier=Modifier.padding(top=4.dp)) {
Button(onClick=onPlay,colors=ButtonDefaults.buttonColors(containerColor=WHT),shape=Pill) {
Icon(Icons.Default.PlayArrow,null,tint=BLK,modifier=Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
Text("เล่นเกม",color=BLK,fontWeight=FontWeight.Bold,fontSize=12.sp)
}
OutlinedButton(onClick=onEditGame,shape=Pill,border=BorderStroke(1.dp,IceCyan)) {
Icon(Icons.Default.Edit,null,tint=IceCyan,modifier=Modifier.size(15.dp)); Spacer(Modifier.width(4.dp))
Text("ปรับแต่งเกม",color=IceCyan,fontSize=12.sp,fontWeight=FontWeight.Bold)
}
OutlinedButton(onClick=onScrape,shape=Pill,border=BorderStroke(1.dp,IceGreen)) {
Icon(Icons.Default.CloudDownload,null,tint=IceGreen,modifier=Modifier.size(15.dp)); Spacer(Modifier.width(4.dp))
Text(if (isScraping) "กำลังดึงปก..." else "ดึงปกทันที",color=IceGreen,fontSize=12.sp,fontWeight=FontWeight.Bold)
}
OutlinedButton(onClick=onChooseOnlineCover,shape=Pill,border=BorderStroke(1.dp,WHT.copy(alpha=0.35f))) { Text("เลือกปกเว็บ",color=WHT,fontSize=12.sp) }
}
}
Surface(color=Color(0xAA101726),shape=rDp(18),border=BorderStroke(1.dp,WHT.copy(alpha=0.14f)),modifier=Modifier.weight(0.85f)) {
Row(modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=VCenter) {
StatMetricColumn(Icons.Default.Schedule,IceCyan,"TIME PLAYED",active.formattedPlayTime)
StatMetricColumn(Icons.Default.TrendingUp,IceGreen,"PROGRESS","${active.progressPercent}%")
StatMetricColumn(Icons.Default.EmojiEvents,Color(0xFFFFD54F),"TROPHIES","${active.trophiesEarned} / ${active.trophiesTotal}")
}
}
}
}
}

@Composable
fun MetadataBadgeColumn(label:String,value:String) {
Column {
Text(label,color=WHT.copy(alpha=0.55f),fontSize=10.sp,fontWeight=FontWeight.Medium)
Text(value,color=WHT,fontSize=12.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Clip)
}
}

@Composable
fun StatMetricColumn(icon:ImageVector,tint:Color,label:String,value:String) {
Column(horizontalAlignment=Alignment.CenterHorizontally) {
Row(verticalAlignment=VCenter,horizontalArrangement=hSp(4)) {
Icon(icon,contentDescription=label,tint=tint,modifier=Modifier.size(15.dp))
Text(label,color=WHT.copy(alpha=0.6f),fontSize=10.sp,fontWeight=FontWeight.Bold)
}
Spacer(Modifier.height(4.dp))
Text(value,color=WHT,fontSize=16.sp,fontWeight=FontWeight.Bold)
}
}

@Composable
fun GamepadButtonPromptsFooter(status:String?,onPlay:()->Unit,onSearch:()->Unit,onEditGame:()->Unit,onOptions:()->Unit) {
SpaceRow() {
Text(status?:"Ice Console v3.0 • กด 'ปรับแต่งเกม' หรือ 'ดึงปกทันที' เพื่อสเครปหน้าปกและภาพในเกมแท้",color=WHT.copy(alpha=0.72f),fontSize=12.sp,maxLines=1,modifier=Modifier.weight(1f))
Row(horizontalArrangement=hSp(14),verticalAlignment=VCenter) {
ControllerPromptChip("✕",Color(0xFF42A5F5),"เล่น",onPlay)
ControllerPromptChip("△",Color(0xFF26A69A),"ค้นหา",onSearch)
ControllerPromptChip("□",Color(0xFFEC407A),"ปรับแต่งเกม",onEditGame)
ControllerPromptChip("OPTIONS",Color(0xFF90A4AE),"ตั้งค่า",onOptions)
}
}
}

@Composable
fun ControllerPromptChip(symbol:String,color:Color,label:String,onClick:()->Unit) {
Row(verticalAlignment=VCenter,horizontalArrangement=hSp(5),modifier=Modifier.clickable(onClick=onClick)) {
Surface(color=WHT.copy(alpha=0.14f),shape=Pill,border=BorderStroke(1.dp,color)) { Text(symbol,color=color,fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=7.dp,vertical=2.dp)) }
Text(label,color=WHT.copy(alpha=0.9f),fontSize=12.sp,fontWeight=FontWeight.SemiBold)
}
}

object SampleGameCatalog {
fun forPlatform(id:String,folder:String):List<GameItem> = when (id) {
"psp"->listOf(
GameItem(id=101,title="God of War : Ghost of Sparta",consoleId="psp",romFilePath="$folder/God of War - Ghost of Sparta.iso",coverUrl="https://thumbnails.libretro.com/Sony%20-%20PlayStation%20Portable/Named_Boxarts/God%20of%20War%20-%20Ghost%20of%20Sparta%20(USA).png",fanartUrl="https://thumbnails.libretro.com/Sony%20-%20PlayStation%20Portable/Named_Snaps/God%20of%20War%20-%20Ghost%20of%20Sparta%20(USA).png",description="ศึกแห่งสปาร์ตาบนเครื่อง PSP",releaseYear="พ.ย. 2, 2010",developer="Ready At Dawn",genre="Beat'em Up",timePlayedMinutes=1480,progressPercent=82,trophiesEarned=29,trophiesTotal=36,publisher="Sony Computer Entertainment"),
GameItem(id=102,title="Monster Hunter Freedom Unite",consoleId="psp",romFilePath="$folder/Monster Hunter Freedom Unite.iso",coverUrl="https://thumbnails.libretro.com/Sony%20-%20PlayStation%20Portable/Named_Boxarts/Monster%20Hunter%20Freedom%20Unite%20(USA).png",fanartUrl="https://thumbnails.libretro.com/Sony%20-%20PlayStation%20Portable/Named_Snaps/Monster%20Hunter%20Freedom%20Unite%20(USA).png",description="ออกล่ามังกรระดับตำนานบน PSP",releaseYear="มิ.ย. 23, 2009",developer="Capcom",genre="Action RPG",timePlayedMinutes=7620,progressPercent=91,trophiesEarned=44,trophiesTotal=50,publisher="Capcom")
)
else->ConsolePlatform.fromId(id).let { c->
listOf(GameItem(id=900L + c.ordinal,title="${c.shortTag} Sample Game",consoleId=c.id,romFilePath="$folder/Sample.${c.supportedExtensions.first()}",coverUrl="https://picsum.photos/seed/${c.id}_c/600/850",fanartUrl="https://picsum.photos/seed/${c.id}_f/1920/1080",description="เกมตัวอย่างประจำหมวด ${c.displayName}",releaseYear="2006",developer=c.shortTag,genre="Action",publisher=c.displayName))
}
}
}

class MainActivity :ComponentActivity() {
override fun onCreate(savedInstanceState:Bundle?) {
super.onCreate(savedInstanceState)
WindowCompat.setDecorFitsSystemWindows(window,false)
WindowInsetsControllerCompat(window,window.decorView).let { ctrl->
ctrl.hide(WindowInsetsCompat.Type.systemBars())
ctrl.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}
setContent { MaterialTheme(colorScheme=darkColorScheme()) { UnifiedEmulatorLauncherApp() } }
}
}
