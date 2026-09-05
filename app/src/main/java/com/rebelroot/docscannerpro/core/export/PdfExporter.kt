package com.rebelroot.docscannerpro.core.export
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.rebelroot.docscannerpro.core.model.DocumentPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
enum class PdfPageSize(val widthPoints: Int, val heightPoints: Int) {
    A4(595, 842),
    US_LETTER(612, 792),
    FIT_IMAGE(0, 0)
}
enum class PdfQuality(val scale: Float, val jpegQuality: Int) {
    HIGH(1.0f, 92),
    MEDIUM(0.85f, 75),
    COMPACT(0.65f, 50)
}
data class PdfExportConfig(
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val quality: PdfQuality = PdfQuality.HIGH,
    val isSearchable: Boolean = true,
    val addPageNumbers: Boolean = true,
    val marginPt: Int = 18
)
class PdfExporter(private val context: Context) {
    suspend fun exportToPdf(
        pages: List<DocumentPage>,
        outputFile: File,
        config: PdfExportConfig = PdfExportConfig(),
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val pdfDoc = PdfDocument()
        try {
            for ((index, page) in pages.withIndex()) {
                onProgress(index + 1, pages.size)
                val bitmapFile = File(page.processedPath)
                if (!bitmapFile.exists()) continue
                val srcBitmap = BitmapFactory.decodeFile(page.processedPath) ?: continue
                drawBitmapPage(
                    pdfDoc,
                    srcBitmap,
                    index,
                    pages.size,
                    page.ocrText,
                    config
                ) { pdfPage ->
                    pdfDoc.finishPage(pdfPage)
                }
                srcBitmap.recycle()
            }
            FileOutputStream(outputFile).use { out ->
                pdfDoc.writeTo(out)
            }
        } finally {
            pdfDoc.close()
        }
        outputFile
    }

    /**
     * Builds a PDF straight from decoded bitmaps (used by the Images→PDF tool);
     * no OCR layer is available here.
     */
    suspend fun exportBitmapsToPdf(
        bitmaps: List<Bitmap>,
        outputFile: File,
        config: PdfExportConfig = PdfExportConfig(isSearchable = false),
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val pdfDoc = PdfDocument()
        try {
            for ((index, bitmap) in bitmaps.withIndex()) {
                onProgress(index + 1, bitmaps.size)
                drawBitmapPage(pdfDoc, bitmap, index, bitmaps.size, null, config) { pdfPage ->
                    pdfDoc.finishPage(pdfPage)
                }
            }
            FileOutputStream(outputFile).use { out ->
                pdfDoc.writeTo(out)
            }
        } finally {
            pdfDoc.close()
        }
        outputFile
    }

    private inline fun drawBitmapPage(
        pdfDoc: PdfDocument,
        srcBitmap: Bitmap,
        pageIndex: Int,
        pageCount: Int,
        ocrText: String?,
        config: PdfExportConfig,
        finishPage: (PdfDocument.Page) -> Unit
    ) {
        val targetWidth = if (config.pageSize == PdfPageSize.FIT_IMAGE) {
            (srcBitmap.width * 72f / 200f).toInt().coerceAtLeast(300)
        } else {
            config.pageSize.widthPoints
        }
        val targetHeight = if (config.pageSize == PdfPageSize.FIT_IMAGE) {
            (srcBitmap.height * 72f / 200f).toInt().coerceAtLeast(400)
        } else {
            config.pageSize.heightPoints
        }
        val pageInfo = PdfDocument.PageInfo.Builder(targetWidth, targetHeight, pageIndex + 1).create()
        val pdfPage = pdfDoc.startPage(pageInfo)
        val canvas = pdfPage.canvas
        canvas.drawColor(Color.WHITE)
        val margin = config.marginPt.toFloat()
        val availableWidth = targetWidth - (margin * 2)
        val availableHeight = targetHeight - (margin * 2)
        val scale = minOf(
            availableWidth / srcBitmap.width.toFloat(),
            availableHeight / srcBitmap.height.toFloat()
        )
        val destW = srcBitmap.width * scale
        val destH = srcBitmap.height * scale
        val destX = margin + (availableWidth - destW) / 2f
        val destY = margin + (availableHeight - destH) / 2f
        val destRect = RectF(destX, destY, destX + destW, destY + destH)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(srcBitmap, null, destRect, paint)
        if (config.isSearchable && !ocrText.isNullOrBlank()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.TRANSPARENT
                textSize = 10f * scale
            }
            val lines = ocrText.lines()
            var textY = destY + 20f
            for (line in lines) {
                if (line.isNotBlank()) {
                    canvas.drawText(line, destX + 10f, textY, textPaint)
                    textY += 14f * scale
                    if (textY > destY + destH - 10f) break
                }
            }
        }
        if (config.addPageNumbers && pageCount > 1) {
            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.DKGRAY
                textSize = 9f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                "${pageIndex + 1} / $pageCount",
                targetWidth / 2f,
                targetHeight - 8f,
                numPaint
            )
        }
        finishPage(pdfPage)
    }
}
