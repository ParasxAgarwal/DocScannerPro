package com.rebelroot.docscannerpro.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * File-based PDF operations backing the Tools tab (merge, split, protect,
 * PDF→images, compress). All functions are main-safe and report progress in
 * the 0..1 range. Inputs are plain files picked via SAF and copied to cache.
 */
object PdfToolsEngine {

    enum class SplitMode { EVERY_PAGE, EXTRACT_RANGE }

    class PdfToolsException(message: String, cause: Throwable? = null) : Exception(message, cause)

    private const val RENDER_LONG_EDGE = 2200

    fun getPdfPageCount(input: File): Int {
        val doc = try {
            PDDocument.load(input)
        } catch (t: Throwable) {
            throw PdfToolsException("This file is not a readable PDF (it may be corrupt or password protected).", t)
        }
        return try {
            doc.numberOfPages
        } finally {
            doc.close()
        }
    }

    suspend fun mergePdfs(
        inputs: List<File>,
        output: File,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        if (inputs.size < 2) throw PdfToolsException("Select at least two PDFs to merge.")
        val merger = PDFMergerUtility()
        inputs.forEachIndexed { index, file ->
            if (!file.exists() || file.length() == 0L) {
                throw PdfToolsException("File ${index + 1} could not be read.")
            }
            merger.addSource(file)
        }
        merger.destinationFileName = output.absolutePath
        onProgress(0.2f)
        try {
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        } catch (t: Throwable) {
            throw PdfToolsException("Merging failed — one of the files may be corrupt or password protected.", t)
        }
        onProgress(1f)
        output
    }

    /**
     * [SplitMode.EVERY_PAGE] writes one single-page PDF per page and returns the
     * produced files; [SplitMode.EXTRACT_RANGE] writes [from]..[to] (1-based,
     * inclusive) into [output].
     */
    suspend fun splitPdf(
        input: File,
        outputDir: File,
        baseName: String,
        output: File,
        mode: SplitMode,
        from: Int,
        to: Int,
        onProgress: (Float) -> Unit = {}
    ): List<File> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val src = try {
            PDDocument.load(input)
        } catch (t: Throwable) {
            throw PdfToolsException("This file is not a readable PDF (it may be corrupt or password protected).", t)
        }
        val produced = mutableListOf<File>()
        try {
            when (mode) {
                SplitMode.EVERY_PAGE -> {
                    val parts = Splitter().split(src)
                    try {
                        parts.forEachIndexed { index, part ->
                            val target = File(outputDir, "$baseName - page ${index + 1}.pdf")
                            part.save(target)
                            produced += target
                            onProgress((index + 1).toFloat() / parts.size)
                        }
                    } finally {
                        parts.forEach { runCatching { it.close() } }
                    }
                }
                SplitMode.EXTRACT_RANGE -> {
                    val pageCount = src.numberOfPages
                    val start = from.coerceIn(1, pageCount)
                    val end = to.coerceIn(start, pageCount)
                    val dst = PDDocument()
                    try {
                        for (i in start - 1 until end) {
                            dst.importPage(src.getPage(i))
                        }
                        dst.save(output)
                        produced += output
                    } finally {
                        runCatching { dst.close() }
                    }
                    onProgress(1f)
                }
            }
        } catch (t: PdfToolsException) {
            throw t
        } catch (t: Throwable) {
            throw PdfToolsException("Splitting failed — the PDF may be corrupt or password protected.", t)
        } finally {
            runCatching { src.close() }
        }
        produced
    }

    suspend fun protectPdf(
        input: File,
        output: File,
        password: String,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        if (password.isBlank()) throw PdfToolsException("Enter a password first.")
        val doc = try {
            PDDocument.load(input)
        } catch (t: Throwable) {
            throw PdfToolsException("This file is not a readable PDF (it may be corrupt or already protected).", t)
        }
        try {
            val permissions = AccessPermission().apply {
                setCanPrint(true)
                setCanExtractContent(true)
            }
            val policy = StandardProtectionPolicy(password, password, permissions)
            policy.encryptionKeyLength = 128
            doc.protect(policy)
            doc.save(output)
        } catch (t: Throwable) {
            throw PdfToolsException("Protecting this PDF failed.", t)
        } finally {
            runCatching { doc.close() }
        }
        onProgress(1f)
        output
    }

    /** Renders every page to a JPEG in [outputDir]; returns the produced files. */
    suspend fun pdfToImages(
        input: File,
        outputDir: File,
        baseName: String,
        jpegQuality: Int = 90,
        onProgress: (Float) -> Unit = {}
    ): List<File> = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val produced = mutableListOf<File>()
        val pfd = try {
            ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (t: Throwable) {
            throw PdfToolsException("This file is not a readable PDF.", t)
        }
        var renderer: PdfRenderer? = null
        try {
            renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            for (i in 0 until count) {
                val page = renderer.openPage(i)
                val bitmap = try {
                    val scale = RENDER_LONG_EDGE / maxOf(page.width, page.height).toFloat()
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
                val target = File(outputDir, "$baseName - page ${i + 1}.jpg")
                FileOutputStream(target).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
                }
                bitmap.recycle()
                produced += target
                onProgress((i + 1).toFloat() / count)
            }
        } catch (t: PdfToolsException) {
            throw t
        } catch (t: Throwable) {
            throw PdfToolsException("Could not read this PDF.", t)
        } finally {
            runCatching { renderer?.close() }
            runCatching { pfd.close() }
        }
        produced
    }

    /**
     * Re-encodes every page as JPEG at [jpegQuality] and rebuilds the PDF —
     * scan-style PDFs shrink dramatically; vector-only text PDFs less so.
     */
    suspend fun compressPdf(
        input: File,
        output: File,
        jpegQuality: Int = 60,
        scale: Float = 1.0f,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val pfd = try {
            ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (t: Throwable) {
            throw PdfToolsException("This file is not a readable PDF.", t)
        }
        var renderer: PdfRenderer? = null
        val pdfDoc = PdfDocument()
        try {
            renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            for (i in 0 until count) {
                val page = renderer.openPage(i)
                val bitmap = try {
                    val w = (page.width * scale).toInt().coerceIn(1, 4096)
                    val h = (page.height * scale).toInt().coerceIn(1, 4096)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
                val pageInfo = PdfDocument.PageInfo.Builder(
                    page.width.coerceAtLeast(1),
                    page.height.coerceAtLeast(1),
                    i + 1
                ).create()
                val pdfPage = pdfDoc.startPage(pageInfo)
                pdfPage.canvas.drawColor(Color.WHITE)
                pdfPage.canvas.drawBitmap(
                    bitmap,
                    null,
                    RectF(0f, 0f, pageInfo.pageWidth.toFloat(), pageInfo.pageHeight.toFloat()),
                    paint
                )
                pdfDoc.finishPage(pdfPage)
                bitmap.recycle()
                onProgress((i + 1).toFloat() / count)
            }
            FileOutputStream(output).use { out ->
                pdfDoc.writeTo(out)
            }
        } catch (t: PdfToolsException) {
            throw t
        } catch (t: Throwable) {
            throw PdfToolsException("Compressing this PDF failed.", t)
        } finally {
            runCatching { pdfDoc.close() }
            runCatching { renderer?.close() }
            runCatching { pfd.close() }
        }
        output
    }
}
