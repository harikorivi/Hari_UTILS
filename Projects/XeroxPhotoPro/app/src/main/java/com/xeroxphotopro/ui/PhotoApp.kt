package com.xeroxphotopro.ui

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xeroxphotopro.PhotoViewModel
import com.xeroxphotopro.camera.CameraScreen
import com.xeroxphotopro.core.*
import com.xeroxphotopro.export.*

private fun measure(value: Double) = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
private fun PhotoSize.label() = "${measure(width)} × ${measure(height)} mm"

@Composable
fun PhotoApp(vm: PhotoViewModel = viewModel()) {
    val screen = vm.screen
    val context = LocalContext.current
    val screenScroll = rememberScrollState()
    LaunchedEffect(screen) { screenScroll.scrollTo(0) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.importPhoto(it, onReady = { vm.screen = "preview" }) }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { it?.let { vm.export(it, ExportFormat.PDF) } }
    val saveJpeg = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { it?.let { vm.export(it, ExportFormat.JPEG) } }
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { it?.let { vm.export(it, ExportFormat.PNG) } }
    var selectedFormat by rememberSaveable { mutableStateOf("PDF") }
    val format = ExportFormat.valueOf(selectedFormat)
    LaunchedEffect(vm.busy, vm.prepared) { if (!vm.busy && screen == "preview" && vm.prepared == null) vm.screen = "setup" }
    BackHandler(screen != "setup" && !vm.busy) { vm.screen = "setup" }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (screen == "camera") CameraScreen(onPhoto = { vm.screen = "setup"; vm.importPhoto(it, true) { vm.screen = "preview" } }, onBack = { vm.screen = "setup" })
            else Column(Modifier.weight(1f).verticalScroll(screenScroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("XEROX PHOTO PRO", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(if (screen == "preview") "Your A4 sheet" else "Photo to print.\nMade simple.", style = MaterialTheme.typography.headlineLarge)
                Text(if (screen == "preview") "One side • One page • Exact photo sizes" else "Capture → Review → One A4 sheet", style = MaterialTheme.typography.labelMedium)
                if (vm.photo != null) {
                    OutlinedTextField(value = vm.personName, onValueChange = vm::updatePersonName,
                        label = { Text("Person’s name (optional)") },
                        supportingText = { Text("Used for saved and shared filenames. Leave blank for the default name.") },
                        singleLine = true, enabled = !vm.busy, modifier = Modifier.fillMaxWidth())
                }
                if (screen == "preview" && vm.prepared != null) {
                    Validation(vm)
                    SheetPreview(vm)
                    OrientationControls(vm)
                    CopyControls(vm)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.screen = "setup" }, enabled = !vm.busy) { Text("Adjust photo / layout") }
                    }
                    Text("PRINT AT ACTUAL SIZE / 100% SCALE", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Use A4. Disable Fit to page / Shrink to fit. Image files contain 300 DPI metadata, but printer apps may ignore it. PDF is preferred for precise size. Measure your first print.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportFormat.values().forEach { type -> FilterChip(selected = format == type, onClick = { selectedFormat = type.name }, enabled = !vm.busy, label = { Text(type.name) }) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(modifier = Modifier.weight(1f), enabled = !vm.busy, onClick = {
                            val name = vm.suggestedFileName(format)
                            when (format) { ExportFormat.PDF -> savePdf.launch(name); ExportFormat.JPEG -> saveJpeg.launch(name); ExportFormat.PNG -> savePng.launch(name) }
                        }) { Text("Save ${format.name}") }
                        OutlinedButton(modifier = Modifier.weight(1f), enabled = !vm.busy, onClick = {
                            vm.share(format) { context.startActivity(Intent.createChooser(it, "Share one A4 sheet")) }
                        }) { Text("Share ${format.name}") }
                    }
                } else {
                    Setup(vm, { vm.screen = "camera" }, { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                    if (vm.photo != null && vm.crop != null) { CropEditor(vm); BackgroundControls(vm) }
                    LayoutSettings(vm)
                    if (vm.photo != null) {
                        Button(onClick = { vm.prepare { vm.screen = "preview" } }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Review A4 sheet") }
                        TextButton(onClick = vm::clearPhoto, enabled = !vm.busy) { Text("Delete working photo") }
                    }
                    Text("Private on-device processing. One working photo is retained until replaced or deleted. Saved exports are managed separately.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    vm.overflow?.let { pending ->
        AlertDialog(onDismissRequest = vm::dismissOverflow, title = { Text("One A4 sheet only") }, text = { Text(pending.explanation) },
            confirmButton = { TextButton(onClick = vm::useMaximum) { Text("Use maximum (${pending.maximum})") } },
            dismissButton = { TextButton(onClick = vm::dismissOverflow) { Text("Keep current settings") } })
    }
    vm.message?.let { message -> AlertDialog(onDismissRequest = vm::dismissMessage, title = { Text("Photo workspace") }, text = { Text(message) }, confirmButton = { TextButton(onClick = vm::dismissMessage) { Text("OK") } }) }
}

@Composable
private fun Setup(vm: PhotoViewModel, camera: () -> Unit, gallery: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F2EC))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Studio photo · ${vm.config.photo.label()}", style = MaterialTheme.typography.titleMedium)
            Text("Check the document's own size and editing requirements. These sizes are not official approval presets.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = camera, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("Take photo") }
                OutlinedButton(onClick = gallery, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("Gallery") }
            }
        }
    }
}

@Composable
private fun Validation(vm: PhotoViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val issues = vm.checks.count { it.status != CheckStatus.PASS }
    Card(colors = CardDefaults.cardColors(containerColor = if (issues > 0) Color(0xFFFFF1D6) else Color(0xFFE6F2EC))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (issues > 0) "Review required · $issues check(s)" else "Checks complete · inspect the photo", style = MaterialTheme.typography.titleSmall)
            Text("Guidance only, not official acceptance. Inspect hair, chin, eyes and background before printing.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide photo checks" else "Show photo checks") }
            if (expanded) vm.checks.forEach { check ->
                Text("${check.label}: ${check.status.name}", style = MaterialTheme.typography.labelLarge)
                Text(check.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CropEditor(vm: PhotoViewModel) {
    val photo = vm.photo ?: return
    val crop = vm.crop ?: return
    Text("Check & adjust", style = MaterialTheme.typography.titleLarge)
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
            drawLine(Color.White.copy(alpha = .6f), Offset(0f, size.height * .38f), Offset(size.width, size.height * .38f))
        }
    }
    Text(vm.quality)
    Text("The eye line is a guide only and is not exported. Keep natural facial features unchanged.", style = MaterialTheme.typography.bodySmall)
    Text(if (vm.effectiveDpi < 300) "Low-resolution warning: ${vm.effectiveDpi} source DPI. Retake for a sharper 300 DPI print." else "Source resolution: ${vm.effectiveDpi} DPI • 300 DPI export target", color = if (vm.effectiveDpi < 300) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
    ControlSlider("Zoom", vm.zoom.toFloat(), 1f..5f, !vm.busy) { vm.adjust(z = it.toDouble()) }
    ControlSlider("Left / right", vm.panX.toFloat(), 0f..1f, !vm.busy) { vm.adjust(x = it.toDouble()) }
    ControlSlider("Up / down", vm.panY.toFloat(), 0f..1f, !vm.busy) { vm.adjust(y = it.toDouble()) }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = vm::autoCrop, enabled = !vm.busy) { Text("Auto crop") }
        OutlinedButton(onClick = vm::rotate, enabled = !vm.busy) { Text("Rotate") }
        TextButton(onClick = { vm.adjust(1.0, .5, .5) }, enabled = !vm.busy) { Text("Reset") }
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
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Orientation.values().forEach { orientation ->
            FilterChip(selected = c.orientation == orientation, onClick = { vm.configure(c.copy(orientation = orientation)) }, enabled = !vm.busy,
                label = { Text(when (orientation) { Orientation.AUTO -> "Auto · maximum"; Orientation.PORTRAIT -> "Portrait"; Orientation.LANDSCAPE -> "Landscape" }) })
        }
    }
}

@Composable
private fun CopyControls(vm: PhotoViewModel) {
    val c = vm.config; val layout = vm.layout; val count = layout.placements.size
    var custom by rememberSaveable(count) { mutableStateOf(count.toString()) }
    Text("Selected copies: $count • Maximum: ${layout.capacity}", style = MaterialTheme.typography.titleSmall)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { vm.configure(c.copy(copies = count - 1)) }, enabled = !vm.busy && count > 1) { Text("−") }
        Text("$count", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { vm.configure(c.copy(copies = count + 1)) }, enabled = !vm.busy && count < layout.capacity) { Text("+") }
        FilterChip(selected = c.copies == null, onClick = { vm.configure(c.copy(copies = null)) }, enabled = !vm.busy, label = { Text("Maximum") })
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(custom, { custom = it.filter(Char::isDigit).take(5) }, label = { Text("Copies (1–${layout.capacity})") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), enabled = !vm.busy, singleLine = true)
        TextButton(onClick = {
            runCatching { vm.configure(c.copy(copies = custom.toIntOrNull() ?: 0)) }.onFailure { vm.notify(it.message ?: "Enter a valid copy count.") }
        }, enabled = !vm.busy) { Text("Apply") }
    }
}

@Composable
private fun LayoutSettings(vm: PhotoViewModel) {
    val c = vm.config
    var customSize by rememberSaveable { mutableStateOf(false) }
    var width by rememberSaveable(c.photo.width) { mutableStateOf(measure(c.photo.width)) }
    var height by rememberSaveable(c.photo.height) { mutableStateOf(measure(c.photo.height)) }
    fun update(block: () -> SheetConfig) { runCatching { vm.configure(block()) }.onFailure { vm.notify(it.message ?: "Invalid layout") } }
    Text("One A4 sheet", style = MaterialTheme.typography.titleLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(PhotoSize(), PhotoSize(35.0, 35.0), PhotoSize(25.0, 35.0), PhotoSize(50.0, 50.0)).forEach { size ->
            FilterChip(selected = c.photo == size, onClick = { update { c.copy(photo = size, copies = null) } }, enabled = !vm.busy, label = { Text(size.label()) })
        }
        FilterChip(selected = customSize, onClick = { customSize = !customSize }, enabled = !vm.busy, label = { Text("Custom size") })
    }
    if (customSize) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(width, { width = it.take(7) }, label = { Text("Width (mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), enabled = !vm.busy, singleLine = true)
            OutlinedTextField(height, { height = it.take(7) }, label = { Text("Height (mm)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f), enabled = !vm.busy, singleLine = true)
        }
        TextButton(onClick = { update { c.copy(photo = PhotoSize(width.toDoubleOrNull() ?: 0.0, height.toDoubleOrNull() ?: 0.0), copies = null) } }, enabled = !vm.busy) { Text("Apply size") }
    }
    OrientationControls(vm)
    CopyControls(vm)
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(c.guides, { update { c.copy(guides = it, gap = if (it) maxOf(2.0, c.gap) else c.gap) } }, enabled = !vm.busy); Text("External cutting marks", Modifier.padding(start = 8.dp)) }
    Row(verticalAlignment = Alignment.CenterVertically) { Switch(c.border, { update { c.copy(border = it) } }, enabled = !vm.busy); Text("Thin gray inside border", Modifier.padding(start = 8.dp)) }
    Text("Border ink stays inside the photo. Cut at the outer edge; the selected finished size is unchanged.", style = MaterialTheme.typography.bodySmall)
    ControlSlider("Printer-safe margin: ${measure(c.margin)} mm", c.margin.toFloat(), 2f..30f, !vm.busy) { update { c.copy(margin = it.toInt().toDouble()) } }
    ControlSlider("Cutting gap: ${measure(c.gap)} mm", c.gap.toFloat(), (if (c.guides) 2f else 0f)..20f, !vm.busy) { update { c.copy(gap = it.toInt().toDouble()) } }
    Text("Check your printer's non-printable margins. A 5 mm default is not a compatibility guarantee.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SheetPreview(vm: PhotoViewModel) {
    val bitmap = vm.prepared ?: return
    val layout = remember(vm.config) { vm.layout }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    Text("${layout.grid.orientation.name.lowercase().replaceFirstChar { it.uppercase() }} A4 · ${layout.config.photo.label()}\n${layout.placements.size} photos · 1 page · ${layout.columns} columns × ${layout.rows} rows", style = MaterialTheme.typography.titleSmall)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val baseWidth = minOf(maxWidth, 240.dp)
        Box(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Canvas(Modifier.width(baseWidth * zoom).height(baseWidth * zoom * (layout.height / layout.width).toFloat()).background(Color.White).border(1.dp, Color.LightGray)) {
                SheetRenderer.draw(drawContext.canvas.nativeCanvas, bitmap, layout, size.width / layout.width.toFloat())
            }
        }
    }
    ControlSlider("Preview zoom", zoom, 1f..3f, !vm.busy) { zoom = it }
}


@Composable
private fun BackgroundControls(vm: PhotoViewModel) {
    var custom by rememberSaveable { mutableStateOf("D9EAF7") }
    val valid = custom.matches(Regex("[0-9a-fA-F]{6}"))
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Background", style = MaterialTheme.typography.titleLarge)
            Text("Optional on-device replacement. The crop above shows your original photo. Choose Original when editing is not permitted.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Original" to null, "White" to -1, "Light blue" to 0xFFD9EAF7.toInt()).forEach { (label, color) ->
                    FilterChip(selected = vm.backgroundColor == color, enabled = !vm.busy,
                        onClick = { vm.setBackground(color) }, label = { Text(label) })
                }
            }
            OutlinedTextField(value = custom, onValueChange = { custom = it.removePrefix("#").take(6) },
                label = { Text("Custom color (6-digit hex)") }, singleLine = true, enabled = !vm.busy,
                isError = !valid, modifier = Modifier.fillMaxWidth())
            OutlinedButton(enabled = valid && !vm.busy, onClick = { vm.setBackground((0xFF000000L or custom.toLong(16)).toInt()) }) { Text("Apply custom color") }
            Text(if (vm.backgroundColor == null) "Selected: Original" else "Selected: #" + String.format("%06X", vm.backgroundColor!! and 0xFFFFFF))
            vm.prepared?.let { image ->
                Image(image.asImageBitmap(), "Portrait used in the A4 sheet", Modifier.align(Alignment.CenterHorizontally).heightIn(max = 280.dp))
                Text("Sheet portrait · inspect hair and shoulder edges before printing.", style = MaterialTheme.typography.bodySmall)
            } ?: Text("Review the A4 sheet to refresh the portrait after crop changes.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
