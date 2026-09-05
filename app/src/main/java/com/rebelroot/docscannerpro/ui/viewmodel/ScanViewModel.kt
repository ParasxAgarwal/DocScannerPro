package com.rebelroot.docscannerpro.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rebelroot.docscannerpro.core.cv.DocumentDetector
import com.rebelroot.docscannerpro.core.cv.FileStorageManager
import com.rebelroot.docscannerpro.core.cv.ImageEnhancer
import com.rebelroot.docscannerpro.core.cv.PageFingerprint
import com.rebelroot.docscannerpro.core.cv.PerspectiveTransformer
import com.rebelroot.docscannerpro.core.cv.BookScanner
import com.rebelroot.docscannerpro.core.database.AppDatabase
import com.rebelroot.docscannerpro.core.database.DocumentRepository
import com.rebelroot.docscannerpro.core.model.BarcodeItem
import com.rebelroot.docscannerpro.core.model.Document
import com.rebelroot.docscannerpro.core.model.DocumentPage
import com.rebelroot.docscannerpro.core.model.DocumentType
import com.rebelroot.docscannerpro.core.model.FilterType
import com.rebelroot.docscannerpro.core.model.QuadCorners
import com.rebelroot.docscannerpro.core.ocr.BarcodeEngine
import com.rebelroot.docscannerpro.core.ocr.DocumentUnderstanding
import com.rebelroot.docscannerpro.core.ocr.OcrEngine
import com.rebelroot.docscannerpro.core.qr.QrContent
import com.rebelroot.docscannerpro.core.qr.QrHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class ScanMode {
    DOCUMENT,
    ID_CARD,
    RECEIPT,
    BUSINESS_CARD,
    BOOK,
    QR_BARCODE,
    MULTI_PAGE
}

enum class IdCardSide {
    FRONT, BACK
}

/** Health of the camera pipeline, surfaced to the UI. */
sealed class ScannerStatus {
    data object Starting : ScannerStatus()
    data object Ready : ScannerStatus()
    data class RecoverableError(val message: String) : ScannerStatus()
    data class Unavailable(val message: String) : ScannerStatus()
}

/** Progress of a single capture; Failed is always visible and recoverable. */
sealed class CaptureState {
    data object Idle : CaptureState()
    data object Capturing : CaptureState()
    data object Processing : CaptureState()
    data class Failed(val message: String) : CaptureState()
}

data class QrResult(
    val item: BarcodeItem,
    val content: QrContent,
    val timestamp: Long
)

data class ScannedPageDraft(
    val id: String = UUID.randomUUID().toString(),
    var originalBitmap: Bitmap,
    var processedBitmap: Bitmap,
    var thumbnail: Bitmap? = null,
    var corners: QuadCorners,
    var filterType: FilterType = FilterType.AUTO_ENHANCE,
    var rotation: Int = 0,
    var ocrText: String? = null,
    var ocrConfidence: Float = 0f,
    var sessionOrigPath: String? = null,
    var sessionProcPath: String? = null
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DocumentRepository(AppDatabase.getInstance(application))
    private val storageManager = FileStorageManager(application)
    private val detector = DocumentDetector()
    private val ocrEngine = OcrEngine(application)
    private val barcodeEngine = BarcodeEngine()
    private val bookScanner = BookScanner()
    private val qrHistoryStore = QrHistoryStore(application)
    private val sessionDirectory = File(application.cacheDir, "scanner_session")

    private var lastCapturedFingerprint: LongArray? = null
    private var lastQrRawValue: String? = null
    private var lastQrAtMillis: Long = 0L

    val scanMode = MutableStateFlow(ScanMode.DOCUMENT)
    val idCardSide = MutableStateFlow(IdCardSide.FRONT)
    val isFlashOn = MutableStateFlow(false)
    val isAutoCaptureEnabled = MutableStateFlow(true)

    data class FrameSize(val width: Int = 4, val height: Int = 3)

    val analyzedFrameSize = MutableStateFlow(FrameSize())
    val detectedCorners = MutableStateFlow(QuadCorners.defaultQuad(1f, 1f))
    val hasRealDetection = MutableStateFlow(false)
    val guidanceMessage = MutableStateFlow("Point camera at document")
    val isStableAndReady = MutableStateFlow(false)
    val isBlurry = MutableStateFlow(false)
    val isLowLight = MutableStateFlow(false)
    val scannerStatus = MutableStateFlow<ScannerStatus>(ScannerStatus.Starting)
    val captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val scannedBarcode = MutableStateFlow<BarcodeItem?>(null)
    val qrResult = MutableStateFlow<QrResult?>(null)
    val capturedPages = MutableStateFlow<List<ScannedPageDraft>>(emptyList())
    val editingPage = MutableStateFlow<ScannedPageDraft?>(null)
    val isProcessing = MutableStateFlow(false)

    val qrHistory = qrHistoryStore.entries

    init {
        restoreSessionIfNeeded()
    }

    fun setScannerStatus(status: ScannerStatus) {
        scannerStatus.value = status
    }

    fun beginCapture() {
        if (captureState.value == CaptureState.Idle || captureState.value is CaptureState.Failed) {
            captureState.value = CaptureState.Capturing
            // If the camera never calls back (HAL wedged, session lost), fail visibly
            // instead of leaving the shutter stuck in "Capturing" forever.
            viewModelScope.launch {
                kotlinx.coroutines.delay(CAPTURE_TIMEOUT_MS)
                if (captureState.value == CaptureState.Capturing) {
                    captureState.value = CaptureState.Failed("Camera did not respond. Try again.")
                    isProcessing.value = false
                }
            }
        }
    }

    fun setCaptureFailed(message: String) {
        captureState.value = CaptureState.Failed(message)
        isProcessing.value = false
    }

    fun dismissCaptureFailure() {
        if (captureState.value is CaptureState.Failed) {
            captureState.value = CaptureState.Idle
        }
    }

    fun setScanMode(mode: ScanMode) {
        scanMode.value = mode
        // Captured pages survive mode switches and process death; only explicit
        // page removal or a successful save clears them (crash-safety contract).
        editingPage.value = null
        scannedBarcode.value = null
        qrResult.value = null
        idCardSide.value = IdCardSide.FRONT
        isProcessing.value = false
        captureState.value = CaptureState.Idle
        lastCapturedFingerprint = null
        lastQrRawValue = null
        detector.reset()
        hasRealDetection.value = false
        guidanceMessage.value = when (mode) {
            ScanMode.ID_CARD -> "Align ID card inside rectangle"
            ScanMode.RECEIPT -> "Align full receipt in frame"
            ScanMode.BUSINESS_CARD -> "Align business card"
            ScanMode.BOOK -> "Open book flat in frame"
            ScanMode.QR_BARCODE -> "Center QR / Barcode in frame"
            else -> "Point camera at document"
        }
    }

    fun toggleFlash() {
        isFlashOn.value = !isFlashOn.value
    }

    fun toggleAutoCapture() {
        isAutoCaptureEnabled.value = !isAutoCaptureEnabled.value
    }

    fun onFrameAnalyzed(bitmap: Bitmap) {
        if (isProcessing.value || captureState.value is CaptureState.Processing) return
        if (scanMode.value == ScanMode.QR_BARCODE) {
            viewModelScope.launch(Dispatchers.Default) {
                val region = cropCenterRegion(bitmap, REGION_FRACTION)
                val barcodes = barcodeEngine.scanBarcodes(region)
                if (barcodes.isNotEmpty()) onQrDecoded(barcodes.first())
            }
            return
        }
        val aspectHint = when (scanMode.value) {
            ScanMode.ID_CARD -> 1.58f
            ScanMode.BUSINESS_CARD -> 1.75f
            else -> null
        }
        val result = detector.analyze(bitmap, aspectHint)
        analyzedFrameSize.value = FrameSize(bitmap.width, bitmap.height)
        detectedCorners.value = result.corners
        hasRealDetection.value = result.hasRealDetection
        guidanceMessage.value = result.guidanceText
        isStableAndReady.value = result.readyForAutoCapture
        isBlurry.value = result.isBlurry
        isLowLight.value = result.isLowLight
        if (isAutoCaptureEnabled.value &&
            result.readyForAutoCapture &&
            editingPage.value == null &&
            captureState.value == CaptureState.Idle
        ) {
            val fingerprint = fingerprintOf(bitmap)
            val previous = lastCapturedFingerprint
            val isNewContent = previous == null || !PageFingerprint.areSamePage(previous, fingerprint)
            if (isNewContent) {
                lastCapturedFingerprint = fingerprint
                captureFrame(bitmap)
            }
        }
    }

    private fun onQrDecoded(item: BarcodeItem) {
        val now = System.currentTimeMillis()
        if (item.rawValue == lastQrRawValue && now - lastQrAtMillis < QR_DUPLICATE_SUPPRESS_MS) return
        lastQrRawValue = item.rawValue
        lastQrAtMillis = now
        val content = QrContent.parse(item.rawValue)
        qrResult.value = QrResult(item, content, now)
        viewModelScope.launch {
            qrHistoryStore.add(item.rawValue, content.displayLabel(), item.formatName)
        }
    }

    fun dismissQrResult() {
        qrResult.value = null
    }

    fun deleteHistoryEntry(id: String) {
        viewModelScope.launch { qrHistoryStore.remove(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { qrHistoryStore.clear() }
    }

    fun captureFrame(bitmap: Bitmap, manualCorners: QuadCorners? = null, treatAsUndetected: Boolean = false) {
        if (captureState.value == CaptureState.Processing) return
        captureState.value = CaptureState.Processing
        isProcessing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val copy = try {
                    bitmap.copy(Bitmap.Config.ARGB_8888, true)
                } catch (_: Throwable) {
                    null
                } ?: run {
                    captureState.value = CaptureState.Failed("Not enough memory to process this capture")
                    isProcessing.value = false
                    return@launch
                }
                if (scanMode.value == ScanMode.BOOK) {
                    handleBookCapture(copy)
                    return@launch
                }
                val detected = hasRealDetection.value && !treatAsUndetected
                val corners = manualCorners ?: detectedCorners.value
                val draft = if (detected) {
                    val pixelCorners = QuadCorners(
                        android.graphics.PointF(corners.topLeft.x * copy.width, corners.topLeft.y * copy.height),
                        android.graphics.PointF(corners.topRight.x * copy.width, corners.topRight.y * copy.height),
                        android.graphics.PointF(corners.bottomRight.x * copy.width, corners.bottomRight.y * copy.height),
                        android.graphics.PointF(corners.bottomLeft.x * copy.width, corners.bottomLeft.y * copy.height)
                    )
                    val warped = PerspectiveTransformer.transform(copy, pixelCorners)
                    val enhanced = ImageEnhancer.enhanceColorAndContrast(warped)
                    ScannedPageDraft(
                        originalBitmap = warped,
                        processedBitmap = enhanced,
                        thumbnail = scaleForThumbnail(enhanced),
                        corners = QuadCorners.defaultQuad(1f, 1f, 0.01f)
                    )
                } else {
                    // Manual capture fallback: keep the full frame and let the user crop.
                    val enhanced = ImageEnhancer.enhanceColorAndContrast(copy)
                    ScannedPageDraft(
                        originalBitmap = copy,
                        processedBitmap = enhanced,
                        thumbnail = scaleForThumbnail(enhanced),
                        corners = QuadCorners.defaultQuad(1f, 1f)
                    )
                }
                persistDraft(draft)
                withContext(Dispatchers.Main) {
                    capturedPages.value = capturedPages.value + draft
                    if (scanMode.value == ScanMode.ID_CARD && idCardSide.value == IdCardSide.FRONT) {
                        idCardSide.value = IdCardSide.BACK
                        guidanceMessage.value = "Now flip and scan ID Back"
                    } else {
                        guidanceMessage.value = "Page captured • keep going or tap Done"
                    }
                    detector.reset()
                    captureState.value = CaptureState.Idle
                    isProcessing.value = false
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    captureState.value = CaptureState.Failed("Capture failed. Try again.")
                    isProcessing.value = false
                }
                android.util.Log.e("ScanViewModel", "Capture processing failed", t)
            }
        }
    }

    private suspend fun handleBookCapture(copy: Bitmap) {
        val corners = detectedCorners.value
        val bookPages = bookScanner.processSpread(copy, corners)
        val drafts = bookPages.map { page ->
            val enhanced = ImageEnhancer.enhanceColorAndContrast(page, contrast = 1.12f, brightness = 8f)
            ScannedPageDraft(
                originalBitmap = page,
                processedBitmap = enhanced,
                thumbnail = scaleForThumbnail(enhanced),
                corners = QuadCorners.defaultQuad(1f, 1f, 0.01f)
            )
        }
        drafts.forEach { persistDraft(it) }
        withContext(Dispatchers.Main) {
            capturedPages.value = capturedPages.value + drafts
            editingPage.value = null
            detector.reset()
            lastCapturedFingerprint = fingerprintOf(copy)
            guidanceMessage.value = "Spread captured • turn the page"
            captureState.value = CaptureState.Idle
            isProcessing.value = false
        }
    }

    /** Opens a captured page for manual crop/rotation without leaving the session. */
    fun editPage(draft: ScannedPageDraft) {
        editingPage.value = draft
    }

    fun retakePage(draft: ScannedPageDraft) {
        capturedPages.value = capturedPages.value.filterNot { it.id == draft.id }
        draft.sessionOrigPath?.let { File(it).delete() }
        draft.sessionProcPath?.let { File(it).delete() }
    }

    fun updateEditingPageCorners(newCorners: QuadCorners) {
        val current = editingPage.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val copy = current.originalBitmap
                val pixelCorners = QuadCorners(
                    android.graphics.PointF(newCorners.topLeft.x * copy.width, newCorners.topLeft.y * copy.height),
                    android.graphics.PointF(newCorners.topRight.x * copy.width, newCorners.topRight.y * copy.height),
                    android.graphics.PointF(newCorners.bottomRight.x * copy.width, newCorners.bottomRight.y * copy.height),
                    android.graphics.PointF(newCorners.bottomLeft.x * copy.width, newCorners.bottomLeft.y * copy.height)
                )
                val warped = PerspectiveTransformer.transform(copy, pixelCorners)
                val enhanced = ImageEnhancer.applyFilter(warped, current.filterType)
                current.corners = newCorners
                current.processedBitmap = enhanced
                current.thumbnail = scaleForThumbnail(enhanced)
                withContext(Dispatchers.Main) {
                    editingPage.value = current
                    capturedPages.value = capturedPages.value.map { if (it.id == current.id) current else it }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    captureState.value = CaptureState.Failed("Could not update crop: ${t.message ?: "unexpected error"}")
                }
            }
        }
    }

    fun applyFilterToEditingPage(filterType: FilterType) {
        val current = editingPage.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val copy = current.originalBitmap
                val corners = current.corners
                val pixelCorners = QuadCorners(
                    android.graphics.PointF(corners.topLeft.x * copy.width, corners.topLeft.y * copy.height),
                    android.graphics.PointF(corners.topRight.x * copy.width, corners.topRight.y * copy.height),
                    android.graphics.PointF(corners.bottomRight.x * copy.width, corners.bottomRight.y * copy.height),
                    android.graphics.PointF(corners.bottomLeft.x * copy.width, corners.bottomLeft.y * copy.height)
                )
                val warped = PerspectiveTransformer.transform(copy, pixelCorners)
                val filtered = ImageEnhancer.applyFilter(warped, filterType)
                val rotated = if (current.rotation != 0) PerspectiveTransformer.rotate(filtered, current.rotation) else filtered
                current.filterType = filterType
                current.processedBitmap = rotated
                current.thumbnail = scaleForThumbnail(rotated)
                withContext(Dispatchers.Main) {
                    editingPage.value = current
                    capturedPages.value = capturedPages.value.map { if (it.id == current.id) current else it }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    captureState.value = CaptureState.Failed("Could not apply filter: ${t.message ?: "unexpected error"}")
                }
            }
        }
    }

    fun rotateEditingPage() {
        val current = editingPage.value ?: return
        val newRot = (current.rotation + 90) % 360
        current.rotation = newRot
        current.processedBitmap = PerspectiveTransformer.rotate(current.processedBitmap, 90)
        editingPage.value = current
    }

    fun finishBatchAndSave(
        customTitle: String? = null,
        onSaved: (documentId: String) -> Unit
    ) {
        val drafts = capturedPages.value
        if (drafts.isEmpty()) return
        isProcessing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docId = saveDrafts(drafts, customTitle)
                withContext(Dispatchers.Main) {
                    isProcessing.value = false
                    capturedPages.value = emptyList()
                    editingPage.value = null
                    onSaved(docId)
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) {
                    isProcessing.value = false
                    captureState.value = CaptureState.Failed("Could not save document: ${t.message ?: "unexpected error"}")
                }
            }
        }
    }

    private suspend fun saveDrafts(drafts: List<ScannedPageDraft>, customTitle: String?): String {
        val docId = UUID.randomUUID().toString()
        val docType = when (scanMode.value) {
            ScanMode.ID_CARD -> DocumentType.ID_CARD
            ScanMode.RECEIPT -> DocumentType.RECEIPT
            ScanMode.BUSINESS_CARD -> DocumentType.BUSINESS_CARD
            ScanMode.QR_BARCODE -> DocumentType.QR_BARCODE
            ScanMode.BOOK -> DocumentType.BOOK
            else -> DocumentType.DOCUMENT
        }
        var firstOcrText: String? = null
        val savedPages = mutableListOf<DocumentPage>()
        for ((index, draft) in drafts.withIndex()) {
            val origPath = storageManager.saveBitmap(docId, "orig", draft.id, draft.originalBitmap)
            val procPath = storageManager.saveBitmap(docId, "proc", draft.id, draft.processedBitmap)
            val thumbPath = storageManager.saveThumbnail(docId, draft.id, draft.processedBitmap)
            val ocrResult = ocrEngine.recognizeText(draft.processedBitmap)
            if (index == 0) firstOcrText = ocrResult.fullText
            val structuredJson = when (docType) {
                DocumentType.RECEIPT -> {
                    val r = DocumentUnderstanding.extractReceipt(ocrResult.fullText)
                    org.json.JSONObject().apply {
                        put("merchant", r.merchant)
                        put("total", r.total)
                        put("date", r.date)
                    }.toString()
                }
                DocumentType.BUSINESS_CARD -> {
                    val c = DocumentUnderstanding.extractContact(ocrResult.fullText)
                    org.json.JSONObject().apply {
                        put("name", c.name)
                        put("company", c.company)
                        put("phone", c.phone)
                        put("email", c.email)
                    }.toString()
                }
                else -> null
            }
            savedPages.add(
                DocumentPage(
                    id = draft.id,
                    documentId = docId,
                    pageIndex = index,
                    originalPath = origPath,
                    processedPath = procPath,
                    thumbnailPath = thumbPath,
                    rotation = draft.rotation,
                    filterType = draft.filterType,
                    ocrText = ocrResult.fullText,
                    ocrConfidence = ocrResult.confidence,
                    width = draft.processedBitmap.width,
                    height = draft.processedBitmap.height,
                    structuredJson = structuredJson
                )
            )
        }
        val title = customTitle?.takeIf { it.isNotBlank() }
            ?: DocumentUnderstanding.suggestFilename(docType, firstOcrText).replace(".pdf", "")
        val document = Document(
            id = docId,
            title = title,
            type = docType,
            pageCount = savedPages.size,
            thumbnailPath = savedPages.firstOrNull()?.thumbnailPath
        )
        repository.insertDocument(document)
        repository.insertPages(savedPages)
        clearSessionFiles()
        return docId
    }

    fun dismissScannedBarcode() {
        scannedBarcode.value = null
    }

    private fun fingerprintOf(bitmap: Bitmap): LongArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val pixels = IntArray(32 * 32)
        scaled.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        if (scaled !== bitmap) scaled.recycle()
        val gray = IntArray(pixels.size) { i ->
            val p = pixels[i]
            (0.299 * ((p shr 16) and 255) + 0.587 * ((p shr 8) and 255) + 0.114 * (p and 255)).toInt()
        }
        return PageFingerprint.computeHash(gray)
    }

    private fun cropCenterRegion(bitmap: Bitmap, fraction: Float): Bitmap {
        val side = (minOf(bitmap.width, bitmap.height) * fraction).toInt()
            .coerceIn(1, minOf(bitmap.width, bitmap.height))
        val left = (bitmap.width - side) / 2
        val top = (bitmap.height - side) / 2
        return Bitmap.createBitmap(bitmap, left, top, side, side)
    }

    private fun scaleForThumbnail(bitmap: Bitmap): Bitmap {
        val target = 160
        val scale = target.toFloat() / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    /** Pages survive process death: JPEG copies in cache, restored on next launch. */
    private fun persistDraft(draft: ScannedPageDraft) {
        runCatching {
            sessionDirectory.mkdirs()
            val orig = File(sessionDirectory, "${draft.id}_orig.jpg")
            val proc = File(sessionDirectory, "${draft.id}_proc.jpg")
            FileOutputStream(orig).use { draft.originalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            FileOutputStream(proc).use { draft.processedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            draft.sessionOrigPath = orig.absolutePath
            draft.sessionProcPath = proc.absolutePath
        }
    }

    private fun restoreSessionIfNeeded() {
        val files = sessionDirectory.listFiles().orEmpty()
        if (files.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            val drafts = mutableListOf<ScannedPageDraft>()
            val originals = files.filter { it.name.endsWith("_orig.jpg") }
            for (orig in originals) {
                val proc = File(sessionDirectory, orig.name.replace("_orig.jpg", "_proc.jpg"))
                val origBitmap = BitmapFactory.decodeFile(orig.absolutePath) ?: continue
                val procBitmap = BitmapFactory.decodeFile(proc.absolutePath) ?: origBitmap
                drafts += ScannedPageDraft(
                    id = orig.name.removeSuffix("_orig.jpg"),
                    originalBitmap = origBitmap,
                    processedBitmap = procBitmap,
                    thumbnail = scaleForThumbnail(procBitmap),
                    corners = QuadCorners.defaultQuad(1f, 1f),
                    sessionOrigPath = orig.absolutePath,
                    sessionProcPath = proc.absolutePath
                )
            }
            if (drafts.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    capturedPages.value = drafts
                    guidanceMessage.value = "Restored ${drafts.size} unsaved page(s)"
                }
            }
        }
    }

    private fun clearSessionFiles() {
        sessionDirectory.listFiles()?.forEach { it.delete() }
    }

    override fun onCleared() {
        super.onCleared()
        ocrEngine.close()
    }

    companion object {
        private const val QR_DUPLICATE_SUPPRESS_MS = 2000L
        private const val REGION_FRACTION = 0.7f
        private const val CAPTURE_TIMEOUT_MS = 8000L
    }
}

private fun QrContent.displayLabel(): String = when (this) {
    is QrContent.Url -> url
    is QrContent.Phone -> number
    is QrContent.Email -> address
    is QrContent.Sms -> number
    is QrContent.Wifi -> ssid
    is QrContent.Contact -> name ?: org ?: "Contact"
    is QrContent.Geo -> "$latitude,$longitude"
    is QrContent.Plain -> text.take(80)
}
