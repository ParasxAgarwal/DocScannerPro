package com.rebelroot.docscannerpro.core.cv
import android.graphics.Bitmap
import android.graphics.PointF
import com.rebelroot.docscannerpro.core.model.QuadCorners
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
data class DetectionResult(
    val corners: QuadCorners,
    val confidence: Float,
    val isBlurry: Boolean,
    val isLowLight: Boolean,
    val isStable: Boolean,
    val guidanceText: String,
    val readyForAutoCapture: Boolean,
    val hasRealDetection: Boolean
)
class DocumentDetector {
    private var previousCorners: QuadCorners? = null
    private var stableFrameCount = 0
    private val requiredStableFrames = 5
    fun analyze(bitmap: Bitmap, aspectRatioHint: Float? = null): DetectionResult {
        val width = bitmap.width
        val height = bitmap.height
        if (width < 32 || height < 32) {
            val def = guideQuad(aspectRatioHint)
            return DetectionResult(def, 0f, false, false, false, "Position document in frame", false, false)
        }
        val (avgLum, sharpnessMetric) = estimateImageQuality(bitmap)
        val isLowLight = avgLum < 42
        val isBlurry = sharpnessMetric < 30.0
        val detection = findDocumentQuad(bitmap, aspectRatioHint)
        val detected = detection?.corners ?: guideQuad(aspectRatioHint)
        val hasRealDetection = detection != null
        val isStable = if (hasRealDetection) checkStability(detected) else false
        if (isStable) stableFrameCount++ else stableFrameCount = max(0, stableFrameCount - 1)
        previousCorners = detected
        val areaRatio = calculateAreaRatio(detected)
        val isTooFar = areaRatio < 0.17f
        val isOffCenter = checkOffCenter(detected)
        val guidanceText = when {
            isLowLight -> "More light or turn on flash"
            isBlurry -> "Hold steady"
            !hasRealDetection -> when {
                aspectRatioHint != null -> "Align inside the guide"
                else -> "Move the document into view"
            }
            isTooFar -> "Move closer"
            isOffCenter -> "Center the document"
            stableFrameCount >= requiredStableFrames -> "Ready to capture"
            else -> "Document detected"
        }
        val confidence = when {
            !hasRealDetection -> 0.25f
            isLowLight || isBlurry -> 0.45f
            isTooFar -> 0.60f
            stableFrameCount >= 3 -> 0.96f
            else -> 0.82f
        }
        val ready = hasRealDetection && !isBlurry && !isLowLight && !isTooFar && !isOffCenter &&
            stableFrameCount >= requiredStableFrames
        return DetectionResult(
            corners = detected,
            confidence = confidence,
            isBlurry = isBlurry,
            isLowLight = isLowLight,
            isStable = ready,
            guidanceText = guidanceText,
            readyForAutoCapture = ready,
            hasRealDetection = hasRealDetection
        )
    }
    private fun findDocumentQuad(bitmap: Bitmap, aspectRatioHint: Float?): QuadDetection? {
        val maxWidth = 800
        val scale = minOf(1f, maxWidth.toFloat() / bitmap.width.toFloat())
        val w = maxOf(1, (bitmap.width * scale).toInt())
        val h = maxOf(1, (bitmap.height * scale).toInt())
        val small = if (w == bitmap.width && h == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        val rgba = ByteArray(w * h * 4)
        var j = 0
        for (px in pixels) {
            rgba[j++] = ((px shr 16) and 0xFF).toByte()
            rgba[j++] = ((px shr 8) and 0xFF).toByte()
            rgba[j++] = (px and 0xFF).toByte()
            rgba[j++] = ((px ushr 24) and 0xFF).toByte()
        }
        val src = Mat(h, w, org.opencv.core.CvType.CV_8UC4)
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val dilated = Mat()
        try {
            src.put(0, 0, rgba)
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 55.0, 165.0)
            Imgproc.dilate(edges, dilated, Mat(), Point(-1.0, -1.0), 1)
            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(dilated, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            var best: QuadCandidate? = null
            val imageArea = w.toDouble() * h.toDouble()
            for (contour in contours) {
                val area = abs(Imgproc.contourArea(contour))
                if (area < imageArea * 0.10 || area > imageArea * 0.94) {
                    contour.release()
                    continue
                }
                val curve = MatOfPoint2f(*contour.toArray())
                val perimeter = Imgproc.arcLength(curve, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(curve, approx, 0.025 * perimeter, true)
                val pts = approx.toArray()
                if (pts.size == 4 && Imgproc.isContourConvex(MatOfPoint(*pts))) {
                    val corners = orderAndNormalize(pts, w, h)
                    val ratio = aspectRatio(corners)
                    val ratioScore = aspectRatioHint?.let { 1f - (abs(ratio - it) / max(it, 0.01f)).coerceAtMost(1f) } ?: 1f
                    val rectangularity = area / max(polygonArea(pts), 1.0)
                    val centerPenalty = distanceFromCenter(corners) * 0.5f
                    val score = (area / imageArea).toFloat() * 0.75f + ratioScore * 0.18f + rectangularity.toFloat() * 0.12f - centerPenalty
                    if (best == null || score > best!!.score) best = QuadCandidate(corners, score)
                }
                approx.release()
                curve.release()
                contour.release()
            }
            return best?.let { QuadDetection(it.corners, it.score) }
        } finally {
            src.release(); gray.release(); blurred.release(); edges.release(); dilated.release()
            if (small !== bitmap) small.recycle()
        }
    }
    private fun estimateImageQuality(bitmap: Bitmap): Pair<Int, Double> {
        val sampleW = 64
        val scale = minOf(1f, sampleW.toFloat() / bitmap.width.toFloat())
        val w = maxOf(1, (bitmap.width * scale).toInt())
        val h = maxOf(1, (bitmap.height * scale).toInt())
        val small = if (w == bitmap.width && h == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)
        var total = 0L
        var n = 0
        var edgeVariance = 0.0
        val row = IntArray(w)
        for (y in 0 until h) {
            small.getPixels(row, 0, w, 0, y, w, 1)
            var prev = 0
            for (x in 0 until w) {
                val p = row[x]
                val lum = (0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255)).toInt()
                total += lum; n++
                if (x > 0) {
                    val d = lum - prev
                    edgeVariance += d * d
                }
                prev = lum
            }
        }
        if (small !== bitmap) small.recycle()
        return (if (n == 0) 128 else (total / n).toInt()) to (edgeVariance / max(1, n - h)).coerceAtLeast(0.0)
    }
    private fun guideQuad(aspectRatioHint: Float?): QuadCorners {
        val insetX = 0.08f
        val insetY = 0.10f
        if (aspectRatioHint == null) {
            return QuadCorners(PointF(insetX, insetY), PointF(1f - insetX, insetY), PointF(1f - insetX, 1f - insetY), PointF(insetX, 1f - insetY))
        }
        val width = 0.82f
        val height = (width / aspectRatioHint).coerceIn(0.18f, 0.62f)
        val left = (1f - width) / 2f
        val top = (1f - height) / 2f
        return QuadCorners(PointF(left, top), PointF(left + width, top), PointF(left + width, top + height), PointF(left, top + height))
    }
    private fun orderAndNormalize(points: Array<Point>, width: Int, height: Int): QuadCorners {
        val sorted = points.sortedBy { it.x + it.y }
        val tl = sorted.first()
        val br = sorted.last()
        val remaining = sorted.drop(1).dropLast(1)
        val tr = remaining.maxBy { it.x - it.y }
        val bl = remaining.minBy { it.x - it.y }
        return QuadCorners(
            PointF((tl.x / width).toFloat().coerceIn(0f, 1f), (tl.y / height).toFloat().coerceIn(0f, 1f)),
            PointF((tr.x / width).toFloat().coerceIn(0f, 1f), (tr.y / height).toFloat().coerceIn(0f, 1f)),
            PointF((br.x / width).toFloat().coerceIn(0f, 1f), (br.y / height).toFloat().coerceIn(0f, 1f)),
            PointF((bl.x / width).toFloat().coerceIn(0f, 1f), (bl.y / height).toFloat().coerceIn(0f, 1f))
        )
    }
    private fun polygonArea(points: Array<Point>): Double {
        var area = 0.0
        for (i in points.indices) {
            val a = points[i]; val b = points[(i + 1) % points.size]
            area += a.x * b.y - b.x * a.y
        }
        return abs(area) / 2.0
    }
    private fun aspectRatio(c: QuadCorners): Float {
        val width = (distance(c.topLeft, c.topRight) + distance(c.bottomLeft, c.bottomRight)) / 2f
        val height = (distance(c.topLeft, c.bottomLeft) + distance(c.topRight, c.bottomRight)) / 2f
        return width / max(height, 0.001f)
    }
    private fun distanceFromCenter(c: QuadCorners): Float {
        val x = (c.topLeft.x + c.topRight.x + c.bottomRight.x + c.bottomLeft.x) / 4f
        val y = (c.topLeft.y + c.topRight.y + c.bottomRight.y + c.bottomLeft.y) / 4f
        return (hypot(x - 0.5f, y - 0.5f) * 0.7f).coerceAtMost(0.7f)
    }
    private fun checkStability(current: QuadCorners): Boolean {
        val prev = previousCorners ?: return false
        val threshold = 0.018f
        val d = listOf(
            hypot(current.topLeft.x - prev.topLeft.x, current.topLeft.y - prev.topLeft.y),
            hypot(current.topRight.x - prev.topRight.x, current.topRight.y - prev.topRight.y),
            hypot(current.bottomRight.x - prev.bottomRight.x, current.bottomRight.y - prev.bottomRight.y),
            hypot(current.bottomLeft.x - prev.bottomLeft.x, current.bottomLeft.y - prev.bottomLeft.y)
        )
        return d.average().toFloat() < threshold
    }
    private fun calculateAreaRatio(corners: QuadCorners): Float {
        val width = ((corners.topRight.x - corners.topLeft.x) + (corners.bottomRight.x - corners.bottomLeft.x)) / 2f
        val height = ((corners.bottomLeft.y - corners.topLeft.y) + (corners.bottomRight.y - corners.topRight.y)) / 2f
        return (width * height).coerceIn(0f, 1f)
    }
    private fun checkOffCenter(corners: QuadCorners): Boolean {
        val centerX = (corners.topLeft.x + corners.topRight.x + corners.bottomRight.x + corners.bottomLeft.x) / 4f
        val centerY = (corners.topLeft.y + corners.topRight.y + corners.bottomRight.y + corners.bottomLeft.y) / 4f
        return abs(centerX - 0.5f) > 0.22f || abs(centerY - 0.5f) > 0.25f
    }
    private fun distance(a: PointF, b: PointF): Float = hypot(a.x - b.x, a.y - b.y)
    fun reset() {
        previousCorners = null
        stableFrameCount = 0
    }
    private data class QuadCandidate(val corners: QuadCorners, val score: Float)
    private data class QuadDetection(val corners: QuadCorners, val confidence: Float)
}
