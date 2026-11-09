package com.xeroxphotopro.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xeroxphotopro.PhotoViewModel
import com.xeroxphotopro.camera.CameraScreen
import com.xeroxphotopro.core.*
import com.xeroxphotopro.export.*
import com.xeroxphotopro.image.EdgeMode
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import kotlin.math.roundToInt

private fun measure(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
private fun PhotoSize.label() = "${measure(width)} × ${measure(height)} mm"

@Composable
fun PhotoApp(main: PhotoViewModel = viewModel()) {
    val context = LocalContext.current
    val app=context.applicationContext as android.app.Application
    val second: PhotoViewModel = viewModel(key="second-photo", factory=remember(app) {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = PhotoViewModel(app,1) as T
        }
    })
    var activeSlot by rememberSaveable { mutableIntStateOf(0) }
    val dual=main.twoPhotoMode
    val vm=if(dual && activeSlot==1) second else main
    val screen=main.screen
    val ready=main.prepared!=null && (!dual || second.prepared!=null)
    val working=main.busy || (dual && second.busy)
    fun imported() { main.screen=if(main.prepared!=null && (!main.twoPhotoMode || second.prepared!=null)) "preview" else "setup" }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val screenScroll = rememberScrollState()
    LaunchedEffect(screen, activeSlot) { screenScroll.scrollTo(0) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.importPhoto(it, onReady = { imported() }) }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let { main.export(it, ExportFormat.PDF, if(dual) second.prepared else null) } }
    val saveJpeg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { it?.let { main.export(it, ExportFormat.JPEG, if(dual) second.prepared else null) } }
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { it?.let { main.export(it, ExportFormat.PNG, if(dual) second.prepared else null) } }
    var selectedFormat by rememberSaveable { mutableStateOf("JPEG") }
    val format = ExportFormat.valueOf(selectedFormat)
    LaunchedEffect(vm.busy, vm.prepared) { if (!vm.busy && screen == "preview" && vm.prepared == null) main.screen = "setup" }
    BackHandler(screen != "setup" && !vm.busy) { main.screen = "setup" }
    fun save() {
        focus.clearFocus(); keyboard?.hide()
        val name = main.suggestedFileName(format,second.personName)
        when (format) { ExportFormat.PDF -> savePdf.launch(name); ExportFormat.JPEG -> saveJpeg.launch(name); ExportFormat.PNG -> savePng.launch(name) }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            if (screen == "camera") CameraScreen(onPhoto = { main.screen = "setup"; vm.importPhoto(it, true) { imported() } }, onBack = { main.screen = "setup" })
            else {
                Column(Modifier.weight(1f).verticalScroll(screenScroll).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("XEROX PHOTO PRO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(if (screen == "preview") "Your A4 sheet" else "Passport photos", style = MaterialTheme.typography.headlineMedium)
                    CustomSection("Custom sheet mode", !main.busy && !second.busy) {
                        OutlinedButton(onClick={
                            main.updateTwoPhotoMode(false);second.updateTwoPhotoMode(false);activeSlot=0;main.screen="setup"
                        },enabled=!main.busy&&!second.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(if(!dual) "Single photo · selected" else "Single photo") }
                        OutlinedButton(onClick={
                            main.configure(SheetConfig());second.configure(SheetConfig())
                            main.updateTwoPhotoMode(true);second.updateTwoPhotoMode(true);activeSlot=0;main.screen="setup"
                        },enabled=!main.busy&&!second.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(if(dual) "Two photos · 15 + 15 · selected" else "Two photos · 15 + 15") }
                    }
                    if(dual) {
                        Text("One full A4 • two different photos",style=MaterialTheme.typography.titleMedium)
                        listOf(main,second).forEachIndexed { index,item ->
                            val label=if(index==0) "Top photo · 15 copies" else "Bottom photo · 15 copies"
                            OutlinedButton(onClick={activeSlot=index;main.screen="setup"},enabled=!working,
                                modifier=Modifier.fillMaxWidth().heightIn(min=64.dp)) {
                                item.prepared?.let { Image(it.asImageBitmap(),null,Modifier.size(42.dp,54.dp)) }
                                Text("$label — ${if(item.photo==null) "Add photo" else if(activeSlot==index) "Editing" else "Edit"}",Modifier.padding(start=8.dp))
                            }
                        }
                        if(!ready) Text("Add and review both photos to finish the sheet.",style=MaterialTheme.typography.bodySmall)
                    }
                    if (screen == "preview" && ready) {
                        Text("${vm.config.photo.label()} • One A4 page", style = MaterialTheme.typography.titleMedium)
                        if(dual) Text("Top: 15 copies • Bottom: 15 copies", style=MaterialTheme.typography.titleSmall) else CopyControls(main)
                        SheetPreview(main,if(dual) second else null)
                        if(dual) Text("Top photo checks",style=MaterialTheme.typography.titleSmall)
                        Validation(main)
                        if(dual) { Text("Bottom photo checks",style=MaterialTheme.typography.titleSmall); Validation(second) }
                        OutlinedButton(onClick = { main.screen = "setup" }, enabled = !vm.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Custom photo / layout") }
                        CustomSection("Custom export", !vm.busy) {
                            Text("Choose file format", style = MaterialTheme.typography.titleSmall)
                            ExportFormat.values().forEach { type ->
                                OutlinedButton(onClick = { selectedFormat = type.name }, enabled = !vm.busy,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (format == type) "${type.name} · selected" else type.name) }
                            }
                            Text("PDF keeps precise physical dimensions. Images include 300 DPI metadata; printer apps may ignore it.", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedTextField(value = vm.personName, onValueChange = vm::updatePersonName,
                            label = { Text("Person’s name (optional)") }, supportingText = { Text("Used in the saved filename") },
                            singleLine = true, enabled = !vm.busy, modifier = Modifier.fillMaxWidth())
                    } else {
                        Setup(vm, { main.screen = "camera" }, { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                        Text("Photo size: ${vm.config.photo.label()}", style = MaterialTheme.typography.titleMedium)
                        if (vm.photo != null && vm.crop != null) {
                            vm.prepared?.let { Image(it.asImageBitmap(), "Current portrait", Modifier.align(Alignment.CenterHorizontally).heightIn(max = 220.dp)) }
                            CustomSection("Custom crop", !vm.busy) { CropEditor(vm) }
                            CustomSection("Custom background", !vm.busy) { BackgroundControls(vm) }
                            if(dual) OutlinedTextField(value=vm.personName,onValueChange=vm::updatePersonName,label={Text("Person’s name (optional)")},singleLine=true,enabled=!working,modifier=Modifier.fillMaxWidth())
                            if(dual) Text("Two-photo mode uses 35 × 45 mm, 15 copies each, portrait A4.") else LayoutSettings(vm)
                            TextButton(onClick = { main.updateTwoPhotoMode(false); second.updateTwoPhotoMode(false); activeSlot=0; main.clearPhoto(); second.clearPhoto() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if(dual) "Delete both working photos" else "Delete working photo") }
                        } else Text("Take a photo or select one from Gallery. Default size is 35 × 45 mm. Custom settings are available after selecting a photo.")
                        Text("Photos are processed on this device.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (vm.photo != null) Surface(shadowElevation = 6.dp) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (screen == "preview" && ready) {
                            Text("Print on A4 • Actual size / 100%", style = MaterialTheme.typography.labelLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { save() }, enabled = !working && ready, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Save ${format.name}") }
                                OutlinedButton(onClick = { main.share(format,if(dual) second.prepared else null,second.personName) { context.startActivity(Intent.createChooser(it, "Share one A4 sheet")) } },
                                    enabled = !working && ready, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("Share ${format.name}") }
                            }
                        } else Button(onClick = { vm.prepare { imported() } }, enabled = !vm.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Review A4 sheet") }
                    }
                }
            }
            if (working) { Text("Processing photo…", Modifier.padding(horizontal = 16.dp)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
    }
    vm.overflow?.let { pending ->
        AlertDialog(onDismissRequest = vm::dismissOverflow, title = { Text("One A4 sheet only") }, text = { Text(pending.explanation) },
            confirmButton = { TextButton(onClick = vm::useMaximum) { Text("Use maximum (${pending.maximum})") } },
            dismissButton = { TextButton(onClick = vm::dismissOverflow) { Text("Keep current settings") } })
    }
    (main.message ?: if(dual) second.message else null)?.let { message ->
        val dismiss = { main.dismissMessage(); second.dismissMessage() }
        AlertDialog(onDismissRequest = dismiss, title = { Text("Photo workspace") }, text = { Text(message) }, confirmButton = { TextButton(onClick = dismiss) { Text("OK") } }) }
}

@Composable
private fun Setup(vm: PhotoViewModel, camera: () -> Unit, gallery: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F2EC))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Studio photo · ${vm.config.photo.label()}", style = MaterialTheme.typography.titleMedium)
            Text("Check the document's own size and editing requirements. These sizes are not official approval presets.", style = MaterialTheme.typography.bodySmall)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = camera, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Take photo") }
                OutlinedButton(onClick = gallery, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Gallery") }
            }
        }
    }
}

@Composable
private fun CustomSection(label: String, enabled: Boolean, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedButton(onClick = { focus.clearFocus(); keyboard?.hide(); expanded = !expanded }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (expanded) "Close · $label" else label) }
    if (expanded) content()
}

@Composable
private fun Validation(vm: PhotoViewModel) {
    if(vm.quality.contains("Insufficient space") || vm.quality.contains("Reliable eye/chin")) {
        Text("Automatic framing: ${vm.quality}",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }
    val issues = vm.checks.count { it.status != CheckStatus.PASS }
    CustomSection(if (issues > 0) "Photo checks · $issues to review" else "Photo checks", !vm.busy) {
        Text("Guidance only. Inspect face, hair and background before printing.", style = MaterialTheme.typography.bodySmall)
        vm.checks.forEach { check ->
            Text("${check.label}: ${check.status.name}", style = MaterialTheme.typography.labelLarge)
            Text(check.detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CropEditor(vm: PhotoViewModel) {
    val photo = vm.photo ?: return
    val crop = vm.crop ?: return
    Text("Check & adjust", style = MaterialTheme.typography.titleLarge)
    var guides by rememberSaveable { mutableStateOf(true) }
    ToggleRow("Show framing guides",guides,!vm.busy) { guides=it }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val ratio = (vm.config.photo.width / vm.config.photo.height).toFloat()
        val width = minOf(maxWidth, 240.dp, 340.dp * ratio)
        Canvas(Modifier.width(width).height(width / ratio).background(Color.White)) {
            val canvas = drawContext.canvas.nativeCanvas
            val save = canvas.save()
            canvas.clipRect(0f, 0f, size.width, size.height)
            canvas.scale((size.width / crop.width).toFloat(), (size.height / crop.height).toFloat())
            canvas.translate(-crop.x.toFloat(), -crop.y.toFloat())
            canvas.drawBitmap(photo, 0f, 0f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG))
            canvas.restoreToCount(save)
            if(guides) {
                drawLine(Color.White.copy(alpha = .8f), Offset(0f,size.height*vm.framingTarget.eyeLine.toFloat()), Offset(size.width,size.height*vm.framingTarget.eyeLine.toFloat()))
                drawLine(Color.White.copy(alpha = .5f), Offset(size.width*.5f,0f), Offset(size.width*.5f,size.height))
                val chin=(vm.framingTarget.eyeLine+vm.framingTarget.eyeToChin).toFloat()
                drawLine(Color.White.copy(alpha = .5f), Offset(0f,size.height*chin), Offset(size.width,size.height*chin))
            }
        }
    }
    Text(vm.quality)
    CustomSection("Custom framing targets",!vm.busy) {
        var eye by rememberSaveable(vm.framingTarget) { mutableStateOf((vm.framingTarget.eyeLine*100).roundToInt().toString()) }
        var chin by rememberSaveable(vm.framingTarget) { mutableStateOf((vm.framingTarget.eyeToChin*100).roundToInt().toString()) }
        Text("Operator guidance for this photo size, not an official document preset. Use the same targets for both people. Crown/hair height must be reviewed manually.")
        OutlinedTextField(eye,{eye=it.take(2)},label={Text("Eye line from top (%) · 32–48")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
        OutlinedTextField(chin,{chin=it.take(2)},label={Text("Eye-to-chin height (%) · 24–40")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
        Button(onClick={runCatching { vm.updateFraming(FramingTarget((eye.toDoubleOrNull()?:0.0)/100,(chin.toDoubleOrNull()?:0.0)/100)) }.onFailure { vm.notify("Enter targets within the displayed ranges.") }},enabled=!vm.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Apply targets & auto frame")}
        OutlinedButton(onClick={vm.updateFraming(FramingTarget())},enabled=!vm.busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Reset framing targets")}
    }
    Text("The eye line is a guide only and is not exported. Keep natural facial features unchanged.", style = MaterialTheme.typography.bodySmall)
    Text(if (vm.effectiveDpi < 300) "Low-resolution warning: ${vm.effectiveDpi} source DPI. Retake for a sharper 300 DPI print." else "Source resolution: ${vm.effectiveDpi} DPI • 300 DPI export target", color = if (vm.effectiveDpi < 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    ControlSlider("Zoom", vm.zoom.toFloat(), 1f..5f, !vm.busy) { vm.adjust(z = it.toDouble()) }
    ControlSlider("Left / right", vm.panX.toFloat(), 0f..1f, !vm.busy) { vm.adjust(x = it.toDouble()) }
    ControlSlider("Up / down", vm.panY.toFloat(), 0f..1f, !vm.busy) { vm.adjust(y = it.toDouble()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = vm::autoCrop, enabled = !vm.busy) { Text("Auto crop") }
        OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = vm::rotate, enabled = !vm.busy) { Text("Rotate") }
        TextButton(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), onClick = { vm.adjust(1.0, .5, .5) }, enabled = !vm.busy) { Text("Reset") }
    }
}

@Composable
private fun ControlSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, onValue: (Float) -> Unit) {
    Column { Text(label, style = MaterialTheme.typography.labelMedium); Slider(value = value, onValueChange = onValue, valueRange = range, enabled = enabled) }
}

@Composable
private fun OrientationControls(vm: PhotoViewModel) {
    val c = vm.config
    val portrait = LayoutEngine.grid(c, Orientation.PORTRAIT).capacity
    val landscape = LayoutEngine.grid(c, Orientation.LANDSCAPE).capacity
    Text("Portrait: $portrait photos • Landscape: $landscape photos", style = MaterialTheme.typography.labelLarge)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Orientation.values().forEach { orientation ->
            FilterChip(modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), selected = c.orientation == orientation, onClick = { vm.configure(c.copy(orientation = orientation)) }, enabled = !vm.busy,
                label = { Text(when (orientation) { Orientation.AUTO -> "Auto · maximum"; Orientation.PORTRAIT -> "Portrait"; Orientation.LANDSCAPE -> "Landscape" }) })
        }
    }
}

@Composable
private fun CopyControls(vm: PhotoViewModel) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val c = vm.config; val layout = vm.layout; val count = layout.placements.size
    var custom by rememberSaveable(count) { mutableStateOf(count.toString()) }
    Text("Selected copies: $count • Maximum: ${layout.capacity}", style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(custom, { custom = it.filter(Char::isDigit).take(5) }, label = { Text("Copies") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), enabled = !vm.busy, singleLine = true)
        Button(onClick = {
            runCatching { vm.configure(c.copy(copies = custom.toIntOrNull() ?: 0)) }.onFailure { vm.notify(it.message ?: "Enter a valid copy count.") }
            focus.clearFocus(); keyboard?.hide()
        }, modifier = Modifier.heightIn(min = 56.dp), enabled = !vm.busy) { Text("Apply copies") }
    }
    CustomSection("Custom quantity", !vm.busy) {
        OutlinedButton(onClick = { vm.configure(c.copy(copies = null)) }, enabled = !vm.busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Use maximum (${layout.capacity})") }
    }
}

@Composable
private fun LayoutSettings(vm: PhotoViewModel) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val c = vm.config
    var width by rememberSaveable(c.photo.width) { mutableStateOf(measure(c.photo.width)) }
    var height by rememberSaveable(c.photo.height) { mutableStateOf(measure(c.photo.height)) }
    fun update(block: () -> SheetConfig) { runCatching { vm.configure(block()) }.onFailure { vm.notify(it.message ?: "Invalid layout") } }
    CustomSection("Custom size", !vm.busy) {
        OutlinedTextField(width, { width = it.take(7) }, label = { Text("Width (mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), enabled = !vm.busy, singleLine = true)
        OutlinedTextField(height, { height = it.take(7) }, label = { Text("Height (mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), enabled = !vm.busy, singleLine = true)
        Button(onClick = { update { c.copy(photo = PhotoSize(width.toDoubleOrNull() ?: 0.0, height.toDoubleOrNull() ?: 0.0), copies = null) }; focus.clearFocus(); keyboard?.hide() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !vm.busy) { Text("Apply size") }
        OutlinedButton(onClick = { update { c.copy(photo = PhotoSize(), copies = null) } }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !vm.busy) { Text("Reset to 35 × 45 mm") }
    }
    CustomSection("Custom sheet layout", !vm.busy) {
        OrientationControls(vm)
        ToggleRow("External cutting marks", c.guides, !vm.busy) { update { c.copy(guides = it, gap = if (it) maxOf(2.0, c.gap) else c.gap) } }
        ToggleRow("Thin gray inside border", c.border, !vm.busy) { update { c.copy(border = it) } }
        ControlSlider("Printer-safe margin: ${measure(c.margin)} mm", c.margin.toFloat(), 2f..30f, !vm.busy) { update { c.copy(margin = it.toInt().toDouble()) } }
        ControlSlider("Cutting gap: ${measure(c.gap)} mm", c.gap.toFloat(), (if (c.guides) 2f else 0f)..20f, !vm.busy) { update { c.copy(gap = it.toInt().toDouble()) } }
        Text("Photos start at the top-left safe margin. Leave margins your printer can print; cut at the photo edge.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, enabled = enabled,
        role = androidx.compose.ui.semantics.Role.Switch, onValueChange = change), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SheetPreview(vm: PhotoViewModel, second: PhotoViewModel? = null) {
    val bitmap = vm.prepared ?: return
    val images=if(second==null) listOf(bitmap) else listOf(bitmap,second.prepared ?: return)
    val layout = remember(vm.config) { vm.layout }
    Text("${layout.placements.size} photos · ${layout.columns} columns × ${layout.rows} rows", style = MaterialTheme.typography.titleSmall)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val width = minOf(maxWidth, 190.dp)
        Canvas(Modifier.width(width).height(width * (layout.height / layout.width).toFloat()).background(Color.White).border(1.dp, Color.LightGray)) {
            SheetRenderer.draw(drawContext.canvas.nativeCanvas, images, layout, size.width / layout.width.toFloat())
        }
    }
    Text(if(second==null) "Packed from the top-left. Unused paper stays together below." else "Top three rows: photo 1. Bottom three rows: photo 2. Safe margins and cutting gaps are retained.", style = MaterialTheme.typography.bodySmall)
}


private val backgroundPalette = listOf(
    "Original" to null, "White" to 0xFFFFFFFF.toInt(),
    "Light blue" to 0xFFD9EAF7.toInt(), "Blue" to 0xFF438CCD.toInt(),
    "Light gray" to 0xFFE5E7EB.toInt(), "Cream" to 0xFFFFF3D6.toInt())

@Composable
private fun BackgroundControls(vm: PhotoViewModel) {
    var before by rememberSaveable { mutableStateOf(false) }
    var enlarge by rememberSaveable { mutableStateOf(false) }
    var repair by rememberSaveable { mutableStateOf(false) }
    var red by rememberSaveable { mutableFloatStateOf(217f) }
    var green by rememberSaveable { mutableFloatStateOf(234f) }
    var blue by rememberSaveable { mutableFloatStateOf(247f) }
    val customColor = android.graphics.Color.rgb(red.roundToInt(), green.roundToInt(), blue.roundToInt())
    val chosen = backgroundPalette.firstOrNull { it.second == vm.backgroundColor }?.first ?: "Custom color"
    val portrait = if (before) vm.originalPrepared else vm.prepared
    Text("Choose a background", style = MaterialTheme.typography.titleLarge)
    Text("Selected: $chosen", style = MaterialTheme.typography.titleMedium)
    Text("Tap a color to update the portrait. Original keeps the photo’s existing background.", style = MaterialTheme.typography.bodySmall)
    portrait?.let { image ->
        Image(image.asImageBitmap(), "Enlarge portrait", Modifier.fillMaxWidth().heightIn(max = 220.dp).clickable { enlarge = true })
        Text(if (before) "Before · original photo" else "After · $chosen", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { before = !before }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (before) "Show edited photo" else "Compare with original")
        }
        Text("Tap the portrait to inspect hair and shoulders.", style = MaterialTheme.typography.bodySmall)
    } ?: Text("Tap a color to prepare the updated crop.")
    backgroundPalette.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { (label,color) ->
                FilterChip(selected = vm.backgroundColor == color, enabled = !vm.busy,
                    onClick = { before = false; vm.setBackground(color) },
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                    leadingIcon = { Box(Modifier.size(24.dp).clip(CircleShape).background(color?.let { Color(it) } ?: Color.Transparent).border(1.dp,Color.Gray,CircleShape)) {
                        if (color == null) Text("↺", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurface)
                    } },
                    label = { Text(label) })
            }
        }
    }
    CustomSection("Custom color", !vm.busy) {
        Box(Modifier.fillMaxWidth().height(48.dp).background(Color(customColor)).border(1.dp, Color.Gray))
        Text("Mix your color", style = MaterialTheme.typography.titleSmall)
        ControlSlider("Red: ${red.roundToInt()}", red, 0f..255f, !vm.busy) { red = it }
        ControlSlider("Green: ${green.roundToInt()}", green, 0f..255f, !vm.busy) { green = it }
        ControlSlider("Blue: ${blue.roundToInt()}", blue, 0f..255f, !vm.busy) { blue = it }
        Button(onClick = { before = false; vm.setBackground(customColor) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Apply custom color") }
    }
    OutlinedButton(onClick = { repair = true }, enabled = !vm.busy && vm.backgroundColor != null && vm.prepared != null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Repair ears / edges") }
    Text("Missing part of an ear? Restore it from the original photo with the repair brush.", style = MaterialTheme.typography.bodySmall)
    if (repair) CutoutRepair(vm) { repair = false }
    CustomSection("Refine cutout", !vm.busy && vm.backgroundColor != null) {
        Text("Natural balances detail and cleanup. Cleaner edge reduces faint background halos. Softer hair retains more of the original soft boundary.", style = MaterialTheme.typography.bodySmall)
        EdgeMode.values().forEach { mode ->
            FilterChip(selected = vm.edgeMode == mode, enabled = !vm.busy,
                onClick = { before = false; vm.setBackground(vm.backgroundColor,mode) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), label = { Text(mode.label) })
        }
    }
    Text("Check hair, ears and shoulders before saving. Use Original if editing is not allowed or the cutout loses detail.", style = MaterialTheme.typography.bodySmall)
    if (enlarge && portrait != null) {
        var zoom by remember { mutableFloatStateOf(1f) }
        AlertDialog(onDismissRequest = { enlarge = false }, title = { Text(if (before) "Original · inspect edges" else "$chosen · inspect edges") },
            text = { Column {
                Box(Modifier.fillMaxWidth().height(330.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                    val width = 250.dp * zoom
                    Image(portrait.asImageBitmap(), "Detailed portrait", Modifier.width(width).height(width * portrait.height / portrait.width))
                }
                ControlSlider("Detail zoom",zoom,1f..3f,true) { zoom = it }
                Text("Drag the enlarged photo to inspect each edge.", style = MaterialTheme.typography.bodySmall)
            } }, confirmButton = { TextButton(onClick = { enlarge = false }) { Text("Done") } })
    }
}
