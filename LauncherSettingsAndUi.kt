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
Surface(color=if (def) Color(0x3300E5FF) else
