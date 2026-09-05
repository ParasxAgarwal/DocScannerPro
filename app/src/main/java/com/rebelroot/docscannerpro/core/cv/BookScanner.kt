package com.rebelroot.docscannerpro.core.cv
import android.graphics.Bitmap
import android.graphics.PointF
import com.rebelroot.docscannerpro.core.model.QuadCorners
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
class BookScanner {
    fun processSpread(bitmap: Bitmap, corners: QuadCorners): List<Bitmap> {
        val pixelCorners = QuadCorners(
            PointF(corners.topLeft.x * bitmap.width, corners.topLeft.y * bitmap.height),
            PointF(corners.topRight.x * bitmap.width, corners.topRight.y * bitmap.height),
            PointF(corners.bottomRight.x * bitmap.width, corners.bottomRight.y * bitmap.height),
            PointF(corners.bottomLeft.x * bitmap.width, corners.bottomLeft.y * bitmap.height)
        )
        val flattened = PerspectiveTransformer.transform(bitmap, pixelCorners)
        return splitSpread(flattened).also { if (flattened !== bitmap && it.isNotEmpty()) flattened.recycle() }
    }
    private fun splitSpread(flattened: Bitmap): List<Bitmap> {
        val left = flattened.width / 2
        val gutter = max(4, (flattened.width * 0.012f).toInt())
        val centerLeft = (left - gutter).coerceAtLeast(1)
        val centerRight = (left + gutter).coerceAtMost(flattened.width - 1)
        val leftPage = Bitmap.createBitmap(
            flattened, 0, 0, centerLeft, flattened.height
        ).copy(Bitmap.Config.ARGB_8888, true)
        val rightPage = Bitmap.createBitmap(
            flattened, centerRight, 0, flattened.width - centerRight, flattened.height
        ).copy(Bitmap.Config.ARGB_8888, true)
        val minPageWidth = max(100, (flattened.width * 0.30f).toInt())
        return if (leftPage.width < minPageWidth || rightPage.width < minPageWidth) {
            leftPage.recycle(); rightPage.recycle()
            listOf(flattened.copy(Bitmap.Config.ARGB_8888, true))
        } else {
            listOf(leftPage, rightPage)
        }
    }
    fun detectSpread(bitmap: Bitmap): QuadCorners? {
        val maxWidth = 900
        val scale = minOf(1f, maxWidth.toFloat() / bitmap.width.toFloat())
        val w = max(1, (bitmap.width * scale).toInt())
        val h = max(1, (bitmap.height * scale).toInt())
        val small = if (w == bitmap.width && h == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)
        val rgba = ByteArray(w * h * 4)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        var k = 0
        for (px in pixels) {
            rgba[k++] = ((px shr 16) and 255).toByte()
            rgba[k++] = ((px shr 8) and 255).toByte()
            rgba[k++] = (px and 255).toByte()
            rgba[k++] = 255.toByte()
        }
        val src = Mat(h, w, CvType.CV_8UC4)
        val gray = Mat()
        val blur = Mat()
        val edges = Mat()
        try {
            src.put(0, 0, rgba)
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blur, edges, 45.0, 135.0)
            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            val imageArea = w.toDouble() * h.toDouble()
            var best: Pair<QuadCorners, Double>? = null
            for (contour in contours) {
                val area = abs(Imgproc.contourArea(contour))
                if (area < imageArea * 0.15 || area > imageArea * 0.94) { contour.release(); continue }
                val c2f = MatOfPoint2f(*contour.toArray())
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(c2f, approx, 0.02 * Imgproc.arcLength(c2f, true), true)
                val pts = approx.toArray()
                if (pts.size == 4 && Imgproc.isContourConvex(MatOfPoint(*pts))) {
                    val q = orderAndNormalize(pts, w, h)
                    val score = area * (1.0 - abs(aspectRatio(q).toDouble() - 1.45) / 1.45).coerceAtLeast(0.15)
                    if (best == null || score > best!!.second) best = q to score
                }
                approx.release(); c2f.release(); contour.release()
            }
            return best?.first
        } finally {
            src.release(); gray.release(); blur.release(); edges.release()
            if (small !== bitmap) small.recycle()
        }
    }
    private fun orderAndNormalize(points: Array<Point>, width: Int, height: Int): QuadCorners {
        val sorted = points.sortedBy { it.x + it.y }
        val tl = sorted.first(); val br = sorted.last()
        val rem = sorted.drop(1).dropLast(1)
        val tr = rem.maxBy { it.x - it.y }; val bl = rem.minBy { it.x - it.y }
        return QuadCorners(
            PointF((tl.x / width).toFloat().coerceIn(0f,1f), (tl.y / height).toFloat().coerceIn(0f,1f)),
            PointF((tr.x / width).toFloat().coerceIn(0f,1f), (tr.y / height).toFloat().coerceIn(0f,1f)),
            PointF((br.x / width).toFloat().coerceIn(0f,1f), (br.y / height).toFloat().coerceIn(0f,1f)),
            PointF((bl.x / width).toFloat().coerceIn(0f,1f), (bl.y / height).toFloat().coerceIn(0f,1f))
        )
    }
    private fun aspectRatio(c: QuadCorners): Float {
        val top = kotlin.math.hypot((c.topRight.x - c.topLeft.x).toDouble(), (c.topRight.y - c.topLeft.y).toDouble()).toFloat()
        val bottom = kotlin.math.hypot((c.bottomRight.x - c.bottomLeft.x).toDouble(), (c.bottomRight.y - c.bottomLeft.y).toDouble()).toFloat()
        val left = kotlin.math.hypot((c.bottomLeft.x - c.topLeft.x).toDouble(), (c.bottomLeft.y - c.topLeft.y).toDouble()).toFloat()
        val right = kotlin.math.hypot((c.bottomRight.x - c.topRight.x).toDouble(), (c.bottomRight.y - c.topRight.y).toDouble()).toFloat()
        return ((top + bottom) / 2f) / max(0.001f, (left + right) / 2f)
    }
}
