package com.rebelroot.docscannerpro.core.cv
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import com.rebelroot.docscannerpro.core.model.AnnotationItem
import com.rebelroot.docscannerpro.core.model.FilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
object ImageEnhancer {
    suspend fun applyFilter(bitmap: Bitmap, filterType: FilterType): Bitmap = withContext(Dispatchers.Default) {
        when (filterType) {
            FilterType.ORIGINAL -> bitmap.copy(Bitmap.Config.ARGB_8888, true)
            FilterType.AUTO_ENHANCE -> enhanceColorAndContrast(bitmap, contrast = 1.25f, brightness = 15f)
            FilterType.DOCUMENT_BW -> convertToDocumentBw(bitmap)
            FilterType.GRAYSCALE -> convertToGrayscale(bitmap)
            FilterType.COLOR_BOOST -> boostColor(bitmap, saturation = 1.4f, contrast = 1.2f)
            FilterType.RECEIPT_CONTRAST -> enhanceReceipt(bitmap)
        }
    }
    fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()
        val cm = ColorMatrix().apply { setSaturation(0f) }
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    fun boostColor(bitmap: Bitmap, saturation: Float = 1.35f, contrast: Float = 1.15f): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val satMatrix = ColorMatrix().apply { setSaturation(saturation) }
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        contrastMatrix.preConcat(satMatrix)
        paint.colorFilter = ColorMatrixColorFilter(contrastMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    fun enhanceColorAndContrast(bitmap: Bitmap, contrast: Float = 1.2f, brightness: Float = 10f): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f + brightness
        val matrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }
    fun convertToDocumentBw(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var totalLum: Long = 0
        val gray = IntArray(pixels.size)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val l = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            gray[i] = l
            totalLum += l
        }
        val meanLum = (totalLum / pixels.size).toInt()
        val threshold = (meanLum * 0.92).toInt().coerceIn(80, 200)
        for (i in pixels.indices) {
            val l = gray[i]
            pixels[i] = if (l > threshold) {
                0xFFFFFFFF.toInt()
            } else {
                0xFF111827.toInt()
            }
        }
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
    fun enhanceReceipt(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val l = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            val enhanced = if (l > 175) 255 else if (l < 85) 0 else ((l - 85) * 255 / 90)
            pixels[i] = (0xFF shl 24) or (enhanced shl 16) or (enhanced shl 8) or enhanced
        }
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
    fun applyRedactionsAndAnnotations(
        source: Bitmap,
        annotations: List<AnnotationItem>
    ): Bitmap {
        if (annotations.isEmpty()) return source
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val redactPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        for (ann in annotations) {
            when (ann.type) {
                "REDACT" -> {
                    val rect = RectF(
                        ann.left * result.width,
                        ann.top * result.height,
                        ann.right * result.width,
                        ann.bottom * result.height
                    )
                    canvas.drawRect(rect, redactPaint)
                }
                "HIGHLIGHTER" -> {
                    val hlPaint = Paint().apply {
                        color = try { Color.parseColor(ann.colorHex) } catch (_: Exception) { Color.YELLOW }
                        alpha = 100
                        strokeWidth = ann.strokeWidth * (result.width / 400f)
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                    }
                    val rect = RectF(
                        ann.left * result.width,
                        ann.top * result.height,
                        ann.right * result.width,
                        ann.bottom * result.height
                    )
                    canvas.drawRect(rect, hlPaint)
                }
            }
        }
        return result
    }
}
