package com.rebelroot.docscannerpro.core.cv
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.rebelroot.docscannerpro.core.model.QuadCorners
import kotlin.math.hypot
import kotlin.math.max
object PerspectiveTransformer {
    fun transform(bitmap: Bitmap, corners: QuadCorners): Bitmap {
        val widthTop = hypot(
            (corners.topRight.x - corners.topLeft.x).toDouble(),
            (corners.topRight.y - corners.topLeft.y).toDouble()
        ).toFloat()
        val widthBottom = hypot(
            (corners.bottomRight.x - corners.bottomLeft.x).toDouble(),
            (corners.bottomRight.y - corners.bottomLeft.y).toDouble()
        ).toFloat()
        val targetWidth = max(widthTop, widthBottom).toInt().coerceIn(100, 4096)
        val heightLeft = hypot(
            (corners.bottomLeft.x - corners.topLeft.x).toDouble(),
            (corners.bottomLeft.y - corners.topLeft.y).toDouble()
        ).toFloat()
        val heightRight = hypot(
            (corners.bottomRight.x - corners.topRight.x).toDouble(),
            (corners.bottomRight.y - corners.topRight.y).toDouble()
        ).toFloat()
        val targetHeight = max(heightLeft, heightRight).toInt().coerceIn(100, 4096)
        val src = corners.toFloatArray()
        val dst = floatArrayOf(
            0f, 0f,
            targetWidth.toFloat(), 0f,
            targetWidth.toFloat(), targetHeight.toFloat(),
            0f, targetHeight.toFloat()
        )
        val matrix = Matrix()
        val success = matrix.setPolyToPoly(src, 0, dst, 0, 4)
        if (!success) {
            return bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, true)
        }
        val resultBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isDither = true
        }
        canvas.drawBitmap(bitmap, matrix, paint)
        return resultBitmap
    }
    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
