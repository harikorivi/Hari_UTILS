package com.printassist.android

import android.os.Bundle
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.os.Handler
import android.os.Looper
import android.print.*
import java.io.FileOutputStream
import java.util.concurrent.Executor

class NativePrintAdapter(private val session: PdfSession, private val count: Int,
    private val executor: Executor, private val filename: String = "Document.pdf", private val finished: () -> Unit) : PrintDocumentAdapter() {
    private var attributes = defaultPrintAttributes()
    private val main = Handler(Looper.getMainLooper())
    private var activeSignal: CancellationSignal? = null
    override fun onLayout(old: PrintAttributes?, new: PrintAttributes, cancellation: CancellationSignal,
                          callback: LayoutResultCallback, extras: Bundle?) {
        if (cancellation.isCanceled) { callback.onLayoutCancelled(); return }
        attributes = new
        callback.onLayoutFinished(documentInfo(), old != new)
    }
    internal fun documentInfo() = PrintDocumentInfo.Builder(filename)
        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(count).build()
    override fun onWrite(ranges: Array<PageRange>, destination: ParcelFileDescriptor,
                         cancellation: CancellationSignal, callback: WriteResultCallback) {
        writePages(ranges, destination, cancellation, callback::onWriteFinished, callback::onWriteFailed, callback::onWriteCancelled)
    }
    internal fun writePages(ranges: Array<PageRange>, destination: ParcelFileDescriptor,
        cancellation: CancellationSignal, success: (Array<PageRange>) -> Unit,
        failure: (CharSequence) -> Unit, cancelled: () -> Unit) {
        val requested = (0 until count).filter { page -> ranges.any { page in it.start..it.end } }.toSet()
        val settings = attributes
        activeSignal = cancellation
        executor.execute {
            try {
                FileOutputStream(destination.fileDescriptor).use { session.printPdf(it, settings, requested, cancellation) }
                main.post { if (cancellation.isCanceled) cancelled() else success(requested.sorted().map { PageRange(it,it) }.toTypedArray()) }
            } catch (_: OperationCanceledException) { main.post { cancelled() } }
            catch (_: Exception) { main.post { failure("Could not prepare this print job. Select at most 10 pages.") } }
        }
    }
    override fun onFinish() { activeSignal?.cancel(); finished() }
}
fun defaultPrintAttributes() = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4)
    .setResolution(PrintAttributes.Resolution("printassist", "PrintAssist", 200, 200))
    .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
