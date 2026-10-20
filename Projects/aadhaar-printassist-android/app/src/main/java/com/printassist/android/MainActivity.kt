package com.printassist.android

import android.os.Bundle
import android.view.WindowManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {
    private val model: PrintAssistViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF187A78))) { PrintAssistScreen(model) } }
    }
    override fun onStop() { super.onStop(); if (!isChangingConfigurations) model.onBackground() }
}

@Composable fun PrintAssistScreen(model: PrintAssistViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var aadhaar by remember { mutableStateOf(true) }
    var manual by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var expandedReport by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var exportKind by remember { mutableStateOf("pdf") }
    var dpi by remember { mutableIntStateOf(200) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { name = ""; year = ""; password = ""; consent = false }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        if (selected != null) { model.clear(); uri = selected; name = ""; year = ""; password = ""; consent = false; error = "" }
    }
    val evidence = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        model.endExternal(); if (selected != null) model.importEvidence(selected)
    }
    val export = rememberLauncherForActivityResult(object : ActivityResultContracts.CreateDocument("application/octet-stream") {
        override fun createIntent(context: android.content.Context, input: String): android.content.Intent =
            super.createIntent(context, input).setType(when(input.substringAfterLast('.')) {
                "pdf" -> "application/pdf"; "png" -> "image/png"; "jpg" -> "image/jpeg"; else -> "text/plain"
            })
    }) { selected -> model.endExternal(); if (selected != null) model.export(selected,exportKind,dpi) }

    confirm?.let { action ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text(if (action == "print") "Print this document?" else "Save this document?") },
            text = { Text(if (action == "original") "This saves an exact copy of the original, including its password protection and signature. Only save with the customer's permission."
                else if (action == "report") "The report contains signer information and the original file hash. Only save with permission."
                else "The output contains readable customer information and has no embedded original digital signature. Signature widgets are omitted when verification is incomplete or fails. This does not make the copy verified. Continue only with the customer's permission.") },
            confirmButton = { TextButton(onClick = {
                confirm = null
                if (action == "print") model.print(context) else {
                    exportKind = action; model.beginExternal()
                    export.launch(ExportNames.file(state.customer,action,state.page))
                }
            }) { Text("Continue") } }, dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } })
    }

    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            state.bitmap?.let { bitmap ->
                FixedDocumentPreview(bitmap, Modifier.fillMaxWidth().weight(1f))
                Text("Print preview • Page ${state.page+1} of ${state.pages}", Modifier.padding(horizontal = 20.dp))
            }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.bitmap == null) {
            Text("Aadhaar PrintAssist", style = MaterialTheme.typography.headlineMedium)
            Text("Unlock. Verify. Preview. Print.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(aadhaar, { if (!state.busy) { aadhaar = true; model.clear(); password = "" } }, label = { Text("Aadhaar") })
                FilterChip(!aadhaar, { if (!state.busy) { aadhaar = false; model.clear(); name = ""; year = "" } }, label = { Text("General PDF") })
            }
            Button(onClick = { picker.launch(arrayOf("application/pdf")) }, enabled = !state.busy) { Text(if (uri == null) "Choose PDF" else "Choose another PDF") }
            if (uri != null) Text("PDF selected • processed on this device")
            if (aadhaar) Row { Checkbox(manual, { manual = it }, enabled = !state.busy); Text("Enter PDF password manually", Modifier.padding(top = 12.dp)) }
            OutlinedTextField(name, { name = it }, label = { Text(if (aadhaar) "Full English name" else "Customer name for saved files (optional)") }, singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            if (aadhaar && !manual) {
                OutlinedTextField(year, { year = it.take(4) }, label = { Text("Four-digit birth year") }, singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(password, { password = it }, label = { Text("PDF password (blank if none)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
            }
            Row { Checkbox(consent, { consent = it }, enabled = !state.busy); Text("I have permission to open this document", Modifier.weight(1f).padding(top = 12.dp)) }
            Button(enabled = uri != null && consent && !state.busy, onClick = {
                try {
                    val openingPassword = if (aadhaar && !manual) AadhaarPassword.generate(name, year) else password
                    val customer = name
                    name = ""; year = ""; password = ""; error = ""
                    model.open(uri!!, openingPassword, customer, aadhaar)
                } catch (e: IllegalArgumentException) { error = e.message ?: "Check the details." }
            }) { Text("Unlock & verify original") }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(state.message)
            val report = state.report
            val color = when(report?.verdict) {
                Verdict.VERIFIED -> Color(0xFFDDF3DE); Verdict.INVALID -> Color(0xFFFFDFDC)
                Verdict.UNSIGNED -> Color(0xFFE5E7EB); else -> Color(0xFFFFF1D6)
            }
            Card(colors = CardDefaults.cardColors(containerColor = color)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (report?.verdict == Verdict.VERIFIED) "✓ VERIFIED" else "Signature: ${report?.verdict ?: "NOT CHECKED"}", style = MaterialTheme.typography.titleLarge)
                    Text(when(report?.verdict) {
                        Verdict.VERIFIED -> "All implemented checks passed under the configured trust policy."
                        Verdict.INVALID -> "Signature integrity or revocation check failed."
                        Verdict.UNSIGNED -> "No embedded document signature was found. Visible ticks alone do not establish verification."
                        Verdict.INDETERMINATE -> "Evidence or supported validation is incomplete. Do not treat this document as verified."
                        null -> "Select and unlock a PDF to check its original bytes."
                    })
                    if (report != null) {
                        TextButton({ expandedReport = !expandedReport }) { Text(if (expandedReport) "Hide verification details" else "View verification details") }
                        if (expandedReport) Text(report.text(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.bitmap?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ model.page(state.page-1) }, enabled = !state.busy && state.page > 0) { Text("Previous") }
                    OutlinedButton({ model.page(state.page+1) }, enabled = !state.busy && state.page+1 < state.pages) { Text("Next") }
                }
                Text(if (state.report?.verdict == Verdict.VERIFIED) "Preview and export copies preserve the original signature artwork."
                    else "Preview and export copies omit identifiable signature widgets because verification did not pass. Save original PDF keeps the downloaded file unchanged.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(dpi==150,{dpi=150},label={Text("150 DPI")})
                    FilterChip(dpi==200,{dpi=200},label={Text("200 DPI")})
                    FilterChip(dpi==300,{dpi=300},label={Text("300 DPI")})
                }
                Text("Image DPI is capped at 8 million pixels. Print/PDF output uses up to 200 DPI.",style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ confirm="print" },enabled=!state.busy) { Text("Print") }
                    OutlinedButton({ confirm="pdf" },enabled=!state.busy && state.pages<=10) { Text("Save print PDF") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({confirm="png"},enabled=!state.busy) { Text("Save PNG") }
                    OutlinedButton({confirm="jpg"},enabled=!state.busy) { Text("Save JPG") }
                }
                TextButton({confirm="original"},enabled=!state.busy) { Text("Save original PDF") }
                TextButton({confirm="report"},enabled=!state.busy) { Text("Save verification report") }
            }
            OutlinedButton(onClick = { model.clear(); uri = null; name = ""; year = ""; password = ""; consent = false; error = "" }, enabled = !state.busy) { Text("Clear customer session") }
            TextButton({settings=!settings}) { Text("Administrator settings") }
            if (settings) {
                Text("Bundled authenticated CCA roots are used automatically. Customers do not need to provide certificates. Import updated public CRL/OCSP evidence or intermediate certificates when verification evidence expires; imports cannot add trusted roots.")
                OutlinedButton({model.beginExternal();evidence.launch(arrayOf("*/*"))},enabled=!state.busy) { Text("Import public verification evidence") }
                TextButton({model.resetEvidence()},enabled=!state.busy) { Text("Remove imported evidence") }
            }
            Text("No uploads or analytics. Sessions clear on backgrounding except during system save/print dialogs. Exports and paper do not retain the original digital signature.", style = MaterialTheme.typography.bodySmall)
        }
    }
    }
}

/** Kept outside the scrolling controls; no gesture-driven translation or scaling. */
@Composable fun FixedDocumentPreview(bitmap: android.graphics.Bitmap, modifier: Modifier = Modifier) {
    Box(modifier.clipToBounds().background(Color.White)) {
        Image(bitmap.asImageBitmap(), "Print copy page preview",
            contentScale = androidx.compose.ui.layout.ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}
