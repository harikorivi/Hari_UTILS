package com.xeroxphotopro.smart

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xeroxphotopro.camera.CameraScreen
import com.xeroxphotopro.core.*
import com.xeroxphotopro.export.ExportFormat
import kotlin.math.roundToInt

@Composable
fun SmartApp(vm: SmartViewModel=viewModel()) {
    val context=LocalContext.current
    var camera by rememberSaveable { mutableStateOf(false) }
    var replacement by rememberSaveable { mutableStateOf<String?>(null) }
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { if(it.isNotEmpty()) vm.importImages(it) }
    val replace=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri-> if(uri!=null) vm.importImages(listOf(uri),replacement);replacement=null }
    val pdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let { uri->vm.export(uri,ExportFormat.PDF) } }
    val jpeg=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { it?.let { uri->vm.export(uri,ExportFormat.JPEG) } }
    val png=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { it?.let { uri->vm.export(uri,ExportFormat.PNG) } }
    var format by rememberSaveable { mutableStateOf("JPEG") }
    val scroll=rememberScrollState()
    LaunchedEffect(vm.preview) { if(vm.preview!=null) scroll.scrollTo(0) }
    BackHandler(camera) {camera=false}
    if(camera) {CameraScreen(onPhoto={camera=false;vm.importImages(listOf(it),temporary=true)},onBack={camera=false},faceGuidance=false);return}
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("Smart A4 Photo Layout Maker",style=MaterialTheme.typography.headlineSmall)
            Text("Any image · exact physical sizes · one A4 page",style=MaterialTheme.typography.bodyMedium)
            if(vm.preview!=null) {
                val layout=vm.layout!!
                Text("SMART A4 LAYOUT PREVIEW",style=MaterialTheme.typography.titleMedium)
                BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
                    val pageWidth=minOf(maxWidth,300.dp*(layout.width/layout.height).toFloat())
                    Image(vm.preview!!.asImageBitmap(),"Complete Smart A4 preview",Modifier.width(pageWidth).height(pageWidth*(layout.height/layout.width).toFloat()).border(1.dp,androidx.compose.ui.graphics.Color.LightGray))
                }
                if(vm.settings.mode==SmartMode.MAXIMUM) Text("Maximum practical capacity: ${layout.placements.size} copies")
                Text("A4 ${if(layout.width>layout.height) "Landscape" else "Portrait"} · Pages: 1 · Total items: ${layout.placements.size}")
                vm.assets.forEach { a->Text("${a.name}: ${layout.counts[a.request.id]?:0} copies · ${a.request.size.width} × ${a.request.size.height} mm") }
                Text("Unused sheet area: ${layout.unusedPercent.roundToInt()}% (includes margins and gaps). Margins ${vm.settings.margin} mm; spacing ${vm.settings.gapX} × ${vm.settings.gapY} mm.",style=MaterialTheme.typography.bodySmall)
                Text("Tip: allow rotation on suitable images to improve the fit. Fit Inside includes any white margins within the chosen cut size.",style=MaterialTheme.typography.bodySmall)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    ExportFormat.values().forEach { f->FilterChip(selected=format==f.name,onClick={format=f.name},label={Text(f.name)},enabled=!vm.busy) }
                }
            }
            Button(onClick={gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!vm.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Select / add images from Gallery")}
            OutlinedButton(onClick={camera=true},enabled=!vm.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Take photo")}
            if(vm.assets.isNotEmpty()) {
                Text("Edit images / change copies",style=MaterialTheme.typography.titleMedium)
                Text(if(vm.settings.mode==SmartMode.MAXIMUM && vm.assets.size==1) "Maximum Fit repeats this image to fill the sheet. Switch to Exact Copies to choose a quantity." else if(vm.settings.mode==SmartMode.MAXIMUM) "Copies below are ratio units: 2:1 repeats complete batches of two of A and one of B. Every image is included." else "Only the requested copies will be printed. Apply each image's changes before optimizing.")
                vm.assets.forEach { asset->key(asset.request.id) { AssetEditor(asset,vm) { replacement=asset.request.id;replace.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } } }
                LayoutOptions(vm)
                vm.overflow?.let { failure ->
                    Text("No complete arrangement found on one A4.",color=MaterialTheme.colorScheme.error)
                    failure.missing.forEach { (id,count)->Text("${vm.assets.find { it.request.id==id }?.name ?: "Image"}: $count copies could not be placed.") }
                    Text("Reduce copies above, change sizes, or try Auto orientation and Best Fit. Nothing has been exported or omitted.")
                    failure.alternative?.let { alternative->
                        Button(onClick={vm.configure(vm.settings.copy(orientation=if(alternative.width>alternative.height) Orientation.LANDSCAPE else Orientation.PORTRAIT));vm.optimize()},enabled=!vm.busy) {Text("Use fitting alternative orientation")}
                    }
                }
                TextButton(onClick=vm::clear,enabled=!vm.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Clear Smart job")}
            }
            Text("Fit Inside preserves the entire image on white paper. Fill and Crop trims the center. No automatic face detection or background editing is applied. Original files are copied unchanged into the private job. Rendering is bounded to a 3508-pixel source edge for memory safety.",style=MaterialTheme.typography.bodySmall)
        }
        if(vm.busy) {Text("Processing Smart layout…",Modifier.padding(horizontal=16.dp));LinearProgressIndicator(Modifier.fillMaxWidth())}
        Surface(shadowElevation=5.dp) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Button(onClick=vm::optimize,enabled=!vm.busy&&vm.assets.isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Optimize layout")}
                if(vm.preview!=null) {
                    Text("Print A4 · Actual size / 100%",style=MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick={val f=ExportFormat.valueOf(format);val name=vm.suggestedName(f);when(f) {ExportFormat.PDF->pdf.launch(name);ExportFormat.JPEG->jpeg.launch(name);ExportFormat.PNG->png.launch(name)}},enabled=!vm.busy,modifier=Modifier.weight(1f).heightIn(min=52.dp)) {Text("Save $format")}
                        OutlinedButton(onClick={vm.share(ExportFormat.valueOf(format)) { context.startActivity(Intent.createChooser(it,"Share Smart A4 sheet")) }},enabled=!vm.busy,modifier=Modifier.weight(1f).heightIn(min=52.dp)) {Text("Share $format")}
                    }
                }
            }
        }
    }
    vm.message?.let { AlertDialog(onDismissRequest=vm::dismiss,title={Text("Smart A4")},text={Text(it)},confirmButton={TextButton(onClick=vm::dismiss) {Text("OK")}}) }
}

@Composable
private fun AssetEditor(asset: SmartAsset,vm: SmartViewModel,replace: ()->Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var width by rememberSaveable(asset.request.size) { mutableStateOf(asset.request.size.width.toString()) }
    var height by rememberSaveable(asset.request.size) { mutableStateOf(asset.request.size.height.toString()) }
    var copies by rememberSaveable(asset.request.copies) { mutableStateOf(asset.request.copies.toString()) }
    var name by rememberSaveable(asset.name) { mutableStateOf(asset.name) }
    var fit by rememberSaveable(asset.fit) { mutableStateOf(asset.fit.name) }
    var lock by rememberSaveable(asset.lockRatio) { mutableStateOf(asset.lockRatio) }
    var auto by rememberSaveable(asset.request.allowRotation) { mutableStateOf(asset.request.allowRotation) }
    val focus=LocalFocusManager.current
    val sourceRatio=if(asset.turns%2==0) asset.sourceWidth.toDouble()/asset.sourceHeight else asset.sourceHeight.toDouble()/asset.sourceWidth
    val number=KeyboardOptions(keyboardType=KeyboardType.Decimal)
    Card {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Image(asset.thumbnail.asImageBitmap(),asset.name,Modifier.size(64.dp).graphicsLayer(rotationZ=asset.turns*90f))
                Column(Modifier.weight(1f)) {Text(asset.name,style=MaterialTheme.typography.titleMedium);Text("${asset.request.size.width} × ${asset.request.size.height} mm · "+(if(vm.settings.mode==SmartMode.MAXIMUM && vm.assets.size==1) "Maximum Fit" else "${asset.request.copies} ${if(vm.settings.mode==SmartMode.MAXIMUM) "ratio units" else "copies"}")+" · ${asset.turns*90}°")}
            }
            OutlinedButton(onClick={expanded=!expanded},enabled=!vm.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(if(expanded) "Close ${asset.name} settings" else "Edit ${asset.name}")}
            if(expanded) {
                OutlinedTextField(name,{name=it},label={Text("Image label")},modifier=Modifier.fillMaxWidth(),enabled=!vm.busy)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(width,{width=it;if(lock) it.toDoubleOrNull()?.let { value->height="%.2f".format(java.util.Locale.US,value/sourceRatio) }},label={Text("Width (mm)")},keyboardOptions=number,modifier=Modifier.weight(1f),enabled=!vm.busy)
                    OutlinedTextField(height,{height=it;if(lock) it.toDoubleOrNull()?.let { value->width="%.2f".format(java.util.Locale.US,value*sourceRatio) }},label={Text("Height (mm)")},keyboardOptions=number,modifier=Modifier.weight(1f),enabled=!vm.busy)
                }
                SmartToggle("Lock source aspect ratio",lock,!vm.busy) {lock=it}
                Text("General print-size presets (not document standards):",style=MaterialTheme.typography.bodySmall)
                listOf(20 to 30,25 to 35,35 to 45,40 to 50,50 to 50,50 to 75,100 to 150).chunked(2).forEach { row ->
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        row.forEach { (w,h)->OutlinedButton(onClick={width=w.toString();height=h.toString();lock=false},enabled=!vm.busy,modifier=Modifier.weight(1f).heightIn(min=48.dp)) {Text("$w × $h")} }
                    }
                }
                Text("Custom size: enter width and height above. Presets unlock the frame ratio; Fit Inside still preserves the image.",style=MaterialTheme.typography.bodySmall)
                if(vm.settings.mode!=SmartMode.MAXIMUM || vm.assets.size>1) OutlinedTextField(copies,{copies=it},label={Text(if(vm.settings.mode==SmartMode.MAXIMUM) "Ratio units / minimum copies" else "Copies")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth(),enabled=!vm.busy)
                ImageFit.values().forEach { f->OutlinedButton(onClick={fit=f.name},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text((if(fit==f.name) "Selected: " else "")+if(f==ImageFit.INSIDE) "Fit Inside · no cropping" else "Fill and Crop · trims center")}}
                SmartToggle("Allow automatic 90° rotation for better fit",auto,!vm.busy) {auto=it}
                Button(onClick={runCatching { vm.update(asset.request.id,PhotoSize(width.toDouble(),height.toDouble()),copies.toInt(),ImageFit.valueOf(fit),lock,auto,name);focus.clearFocus() }.onFailure {vm.notify(it.message?:"Check dimensions and copies.")}},enabled=!vm.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Apply image settings")}
                OutlinedButton(onClick={vm.rotate(asset.request.id)},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text("Rotate 90° · swaps print width / height")}
                OutlinedButton(onClick=replace,enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text("Replace image")}
                TextButton(onClick={vm.remove(asset.request.id)},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text("Remove image")}
                val dpi=if(asset.turns%2==0) minOf(asset.sourceWidth/asset.request.size.width,asset.sourceHeight/asset.request.size.height)*25.4 else minOf(asset.sourceHeight/asset.request.size.width,asset.sourceWidth/asset.request.size.height)*25.4
                if(dpi<300) Text("Low source resolution: approximately ${dpi.roundToInt()} DPI at this size. Upscaling cannot restore detail.",color=MaterialTheme.colorScheme.error)
            }
        }
    }
}
@Composable private fun SmartToggle(label: String,value: Boolean,enabled: Boolean,change: (Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(enabled=enabled) { change(!value) },verticalAlignment=Alignment.CenterVertically) {Text(label,Modifier.weight(1f));Switch(value,onCheckedChange=null,enabled=enabled)}
}
@Composable private fun LayoutOptions(vm: SmartViewModel) {
    val s=vm.settings
    Text("Layout mode",style=MaterialTheme.typography.titleMedium)
    SmartMode.values().forEach { m->OutlinedButton(onClick={vm.configure(s.copy(mode=m))},enabled=!vm.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text((if(s.mode==m) "Selected: " else "")+when(m) {SmartMode.MAXIMUM->"Maximum Fit";SmartMode.EXACT->"Exact Copies · input order";SmartMode.BEST_FIT->"Exact Copies + Best Fit"})}}
    var custom by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick={custom=!custom},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text("Paper, spacing & cutting guides")}
    if(custom) {
        Orientation.values().forEach { o->OutlinedButton(onClick={vm.configure(s.copy(orientation=o))},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text("A4 ${o.name}"+if(s.orientation==o) " · selected" else "")}}
        var margin by rememberSaveable(s.margin) {mutableStateOf(s.margin.toString())}
        var gx by rememberSaveable(s.gapX) {mutableStateOf(s.gapX.toString())}
        var gy by rememberSaveable(s.gapY) {mutableStateOf(s.gapY.toString())}
        OutlinedTextField(margin,{margin=it},label={Text("Printer margin (mm)")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=!vm.busy)
        OutlinedTextField(gx,{gx=it},label={Text("Horizontal gap (mm)")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=!vm.busy)
        OutlinedTextField(gy,{gy=it},label={Text("Vertical gap (mm)")},modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),enabled=!vm.busy)
        Button(onClick={runCatching {vm.configure(s.copy(margin=margin.toDouble(),gapX=gx.toDouble(),gapY=gy.toDouble()))}.onFailure {vm.notify(it.message?:"Check spacing.")}},enabled=!vm.busy) {Text("Apply paper spacing")}
        CutStyle.values().forEach { c->OutlinedButton(onClick={runCatching {vm.configure(s.copy(cut=c))}.onFailure {vm.notify(it.message?:"Check spacing.")}},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()) {Text((if(c==s.cut) "Selected: " else "")+when(c) {CutStyle.NONE->"No border";CutStyle.BLACK->"Thin black border";CutStyle.GRAY->"Thin gray border";CutStyle.DASHED->"Dashed cutting lines";CutStyle.CORNERS->"Corner cutting marks"})}}
    }
}
