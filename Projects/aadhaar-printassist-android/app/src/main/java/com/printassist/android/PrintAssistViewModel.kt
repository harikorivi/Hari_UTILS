package com.printassist.android

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.content.Context
import android.print.PrintManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.cert.CertificateFactory
import org.bouncycastle.cert.ocsp.OCSPResp
import java.io.File
import java.util.UUID

data class UiState(val busy: Boolean = false, val pages: Int = 0, val page: Int = 0,
                   val bitmap: Bitmap? = null, val message: String = "Choose a PDF to begin.",
                   val report: VerificationReport? = null, val customer: String = "Document")

class PrintAssistViewModel(application: Application) : AndroidViewModel(application) {
    private val executor = Executors.newSingleThreadExecutor()
    private val pdfDispatcher = executor.asCoroutineDispatcher()
    private val session = PdfSession(application)
    private val mutableState = MutableStateFlow(UiState())
    val state = mutableState.asStateFlow()
    private var generation = 0
    var externalOperation = false; private set
    fun beginExternal() { externalOperation = true }
    fun endExternal() { externalOperation = false }
    fun onBackground() { if (!externalOperation) clear() }

    fun open(uri: Uri, password: String, customer: String = "Document", aadhaar: Boolean = true) {
        if (state.value.busy) return
        val request = ++generation
        mutableState.value = UiState(busy = true, message = "Opening and verifying locally…", customer = ExportNames.base(customer))
        viewModelScope.launch {
            val result = withContext(pdfDispatcher) {
                try {
                    val count = session.open(uri, password)
                    val report = session.verify(TrustLoader.load(getApplication()), aadhaar)
                    UiState(pages = count, bitmap = session.render(0), report = report, customer = ExportNames.base(customer),
                        message = "Opened locally. Review signature results before printing.")
                } catch (e: Exception) {
                    session.close()
                    UiState(message = when(e) {
                        is InvalidPasswordException -> "Incorrect details or password. Correct them and try again."
                        is IllegalArgumentException -> "Unsupported document or size. Choose a PDF up to 20 MB and 100 pages."
                        else -> "Could not open this PDF. It may be damaged or unsupported."
                    })
                }
            }
            if (request == generation) mutableState.value = result
        }
    }
    fun page(index: Int) {
        val previous = state.value
        if (previous.busy || index !in 0 until previous.pages) return
        val request = generation
        mutableState.value = previous.copy(busy = true)
        viewModelScope.launch {
            val result = withContext(pdfDispatcher) {
                try { previous.copy(page = index, bitmap = session.render(index)) }
                catch (_: Exception) { previous.copy(message = "This page could not be rendered.") }
            }
            if (request == generation) mutableState.value = result
        }
    }
    fun export(uri: Uri, kind: String, dpi: Int) {
        endExternal()
        val previous = state.value
        if (previous.busy || previous.pages == 0) return
        val request = generation
        mutableState.value = previous.copy(busy = true)
        viewModelScope.launch {
            val message = withContext(pdfDispatcher) {
                try {
                    val resolver = getApplication<Application>().contentResolver
                    resolver.openOutputStream(uri,"w")?.use { output ->
                        when(kind) {
                            "original" -> session.writeOriginal(output)
                            "report" -> output.write(checkNotNull(previous.report).text().toByteArray())
                            "png", "jpg" -> session.writeImage(output, previous.page, kind == "jpg", dpi)
                            "pdf" -> session.printPdf(output, defaultPrintAttributes(), (0 until previous.pages).toSet())
                            else -> error("Unsupported export")
                        }
                    } ?: error("Destination unavailable")
                    "Saved successfully. Print/image copies have no embedded original signature."
                } catch (_: Exception) {
                    val removed = try { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) } catch (_: Exception) { false }
                    if (removed) "Export failed; incomplete output removed. PDF export supports up to 10 pages." else "Export failed. Remove any partial output at the chosen destination."
                }
            }
            if (request == generation) mutableState.value = previous.copy(message = message)
        }
    }
    fun print(context: Context) {
        val previous = state.value
        if (previous.busy || previous.pages == 0) return
        beginExternal()
        mutableState.value = previous.copy(busy = true)
        try {
            val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
            manager.print("${previous.customer}.pdf", NativePrintAdapter(session, previous.pages, executor, "${previous.customer}.pdf") { endExternal(); clear() }, defaultPrintAttributes())
        } catch (_: Exception) { endExternal(); mutableState.value = previous.copy(message = "Printing is unavailable on this device.") }
    }
    fun importEvidence(uri: Uri) {
        endExternal()
        val previous = state.value
        if (previous.busy) return
        val request = generation
        mutableState.value = previous.copy(busy = true)
        viewModelScope.launch {
            val message = withContext(pdfDispatcher) {
                try {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream(); val block = ByteArray(8192)
                        while (true) { val n=input.read(block); if(n<0) break; require(out.size()+n<=5*1024*1024); out.write(block,0,n) }; out.toByteArray()
                    } ?: error("No evidence")
                    val cf = CertificateFactory.getInstance("X.509")
                    val extension = try { cf.generateCRL(bytes.inputStream()); "crl" } catch (_: Exception) {
                        try { require(OCSPResp(bytes).responseObject != null); "ocsp" }
                        catch (_: Exception) { cf.generateCertificate(bytes.inputStream()); "cer" }
                    }
                    val folder = File(getApplication<Application>().filesDir,"public-evidence").apply { mkdirs() }
                    require(folder.listFiles().orEmpty().sumOf { it.length() } + bytes.size <= 20*1024*1024)
                    File(folder,"${UUID.randomUUID()}.$extension").writeBytes(bytes)
                    "Public evidence imported. Certificates are intermediates only, never new roots. Reopen the PDF to verify with it."
                } catch (_: Exception) { "Evidence import failed. Select a CA certificate, CRL or OCSP response under 5 MB." }
            }
            if (request == generation) { sessionClose(); mutableState.value = UiState(message = message) }
        }
    }
    fun resetEvidence() {
        if (state.value.busy) return
        clear()
        viewModelScope.launch(pdfDispatcher) { File(getApplication<Application>().filesDir,"public-evidence").listFiles().orEmpty().forEach { it.delete() } }
    }
    private fun sessionClose() { viewModelScope.launch(pdfDispatcher) { session.close() } }
    fun clear() {
        ++generation
        mutableState.value = UiState()
        sessionClose()
    }
    override fun onCleared() { executor.execute { session.close() }; executor.shutdown() }
}
