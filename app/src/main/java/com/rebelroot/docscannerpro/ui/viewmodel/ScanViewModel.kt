package com.rebelroot.docscannerpro.ui.viewmodel
import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rebelroot.docscannerpro.core.cv.DocumentDetector
import com.rebelroot.docscannerpro.core.cv.FileStorageManager
import com.rebelroot.docscannerpro.core.cv.ImageEnhancer
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
data class ScannedPageDraft(
    val id: String = UUID.randomUUID().toString(),
    var originalBitmap: Bitmap,
    var processedBitmap: Bitmap,
    var corners: QuadCorners,
    var filterType: FilterType = FilterType.AUTO_ENHANCE,
    var rotation: Int = 0,
    var ocrText: String? = null,
    var ocrConfidence: Float = 0f
)
class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DocumentRepository(AppDatabase.getInstance(application))
    private val storageManager = FileStorageManager(application)
    private val detector = DocumentDetector()
    private val ocrEngine = OcrEngine(application)
    private val barcodeEngine = BarcodeEngine()
    private val bookScanner = BookScanner()
    private var lastAutoCapturedCorners: QuadCorners? = null
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
    val scannedBarcode = MutableStateFlow<BarcodeItem?>(null)
    val capturedPages = MutableStateFlow<List<ScannedPageDraft>>(emptyList())
    val editingPage = MutableStateFlow<ScannedPageDraft?>(null)
    val isProcessing = MutableStateFlow(false)
    fun setScanMode(mode: ScanMode) {
        scanMode.value = mode
        capturedPages.value = emptyList()
        editingPage.value = null
        scannedBarcode.value = null
        idCardSide.value = IdCardSide.FRONT
        isProcessing.value = false
        lastAutoCapturedCorners = null
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
        if (isProcessing.value) return
        if (scanMode.value == ScanMode.QR_BARCODE) {
            viewModelScope.launch(Dispatchers.Default) {
                val barcodes = barcodeEngine.scanBarcodes(bitmap)
                if (barcodes.isNotEmpty()) {
                    scannedBarcode.value = barcodes.first()
                }
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
        val canCaptureNewAutoFrame = when (scanMode.value) {
            ScanMode.BOOK -> {
                val previous = lastAutoCapturedCorners
                previous == null || cornersMovedEnough(result.corners, previous, 0.07f)
            }
            else -> lastAutoCapturedCorners == null || cornersMovedEnough(result.corners, lastAutoCapturedCorners!!, 0.035f)
        }
        if (isAutoCaptureEnabled.value && result.readyForAutoCapture && editingPage.value == null && canCaptureNewAutoFrame) {
            lastAutoCapturedCorners = result.corners
            captureFrame(bitmap)
        }
    }
    fun captureFrame(bitmap: Bitmap, manualCorners: QuadCorners? = null) {
        if (isProcessing.value) return
        isProcessing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val corners = manualCorners ?: detectedCorners.value
            if (scanMode.value == ScanMode.BOOK) {
                val bookPages = bookScanner.processSpread(copy, corners)
                val drafts = bookPages.map { page ->
                    ScannedPageDraft(
                        originalBitmap = page,
                        processedBitmap = ImageEnhancer.enhanceColorAndContrast(page, contrast = 1.12f, brightness = 8f),
                        corners = QuadCorners.defaultQuad(1f, 1f, 0.01f),
                        filterType = FilterType.AUTO_ENHANCE
                    )
                }
                withContext(Dispatchers.Main) {
                    capturedPages.value = capturedPages.value + drafts
                    editingPage.value = null
                    detector.reset()
                    lastAutoCapturedCorners = corners
                    guidanceMessage.value = "Spread captured • turn the page"
                    isProcessing.value = false
                }
                return@launch
            }
            val pixelCorners = QuadCorners(
                android.graphics.PointF(corners.topLeft.x * copy.width, corners.topLeft.y * copy.height),
                android.graphics.PointF(corners.topRight.x * copy.width, corners.topRight.y * copy.height),
                android.graphics.PointF(corners.bottomRight.x * copy.width, corners.bottomRight.y * copy.height),
                android.graphics.PointF(corners.bottomLeft.x * copy.width, corners.bottomLeft.y * copy.height)
            )
            val warped = PerspectiveTransformer.transform(copy, pixelCorners)
            val enhanced = ImageEnhancer.enhanceColorAndContrast(warped)
            val draft = ScannedPageDraft(
                originalBitmap = warped,
                processedBitmap = enhanced,
                corners = QuadCorners.defaultQuad(1f, 1f, 0.01f),
                filterType = FilterType.AUTO_ENHANCE
            )
            withContext(Dispatchers.Main) {
                if (scanMode.value == ScanMode.ID_CARD) {
                    if (idCardSide.value == IdCardSide.FRONT) {
                        capturedPages.value = capturedPages.value + draft
                        idCardSide.value = IdCardSide.BACK
                        guidanceMessage.value = "Now flip and scan ID Back"
                        isProcessing.value = false
                        return@withContext
                    }
                }
                capturedPages.value = capturedPages.value + draft
                if (scanMode.value != ScanMode.MULTI_PAGE) {
                    editingPage.value = draft
                } else {
                    editingPage.value = null
                    detector.reset()
                    guidanceMessage.value = "Ready for next page"
                }
                isProcessing.value = false
            }
        }
    }
    fun updateEditingPageCorners(newCorners: QuadCorners) {
        val current = editingPage.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
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
            withContext(Dispatchers.Main) {
                editingPage.value = current
            }
        }
    }
    fun applyFilterToEditingPage(filterType: FilterType) {
        val current = editingPage.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
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
            withContext(Dispatchers.Main) {
                editingPage.value = current
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
            withContext(Dispatchers.Main) {
                isProcessing.value = false
                capturedPages.value = emptyList()
                editingPage.value = null
                onSaved(docId)
            }
        }
    }
    private fun cornersMovedEnough(a: QuadCorners, b: QuadCorners, threshold: Float): Boolean {
        val distances = listOf(
            kotlin.math.hypot(a.topLeft.x - b.topLeft.x, a.topLeft.y - b.topLeft.y),
            kotlin.math.hypot(a.topRight.x - b.topRight.x, a.topRight.y - b.topRight.y),
            kotlin.math.hypot(a.bottomRight.x - b.bottomRight.x, a.bottomRight.y - b.bottomRight.y),
            kotlin.math.hypot(a.bottomLeft.x - b.bottomLeft.x, a.bottomLeft.y - b.bottomLeft.y)
        )
        return distances.average().toFloat() >= threshold
    }
    fun dismissScannedBarcode() {
        scannedBarcode.value = null
    }
    override fun onCleared() {
        super.onCleared()
        ocrEngine.close()
    }
}
