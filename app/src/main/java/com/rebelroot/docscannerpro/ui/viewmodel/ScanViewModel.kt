package com.rebelroot.docscannerpro.ui.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
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
    PHOTO,
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

/**
 * An unsaved page in the scanner session. Immutable: edits produce a new
 * instance via [copy] so StateFlow consumers always observe the change.
 *
 * The full-size bitmaps are transient caches. The source of truth is the
 * session JPEG pair ([sessionOrigPath]/[sessionProcPath]); both are evicted
 * from memory right after persist so multi-page sessions (imports of dozens
 * of photos) stay within the heap budget. Bitmaps are reloaded on demand by
 * [ScanViewModel.editPage] / save.
 */
data class ScannedPageDraft(
    val id: String = UUID.randomUUID().toString(),
    val originalBitmap: Bitmap? = null,
    val processedBitmap: Bitmap? = null,
    val thumbnail: Bitmap? = null,
    val corners: QuadCorners,
    val filterType: FilterType = FilterType.AUTO_ENHANCE,
    val rotation: Int = 0,
    val ocrText: String? = null,
    val ocrConfidence: Float = 0f,
    val sessionOrigPath: String? = null,
    val sessionProcPath: String? = null
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DocumentRepository(AppDatabase.getInstance(application))
    private val storageManager = FileStorageManager(application)
    private val detector = DocumentDetector()
    private val barcodeEngine = BarcodeEngine()
    private val bookScanner = BookScanner()
    private val qrHistoryStore = QrHistoryStore(application)
    // filesDir, not cacheDir: the OS may wipe cache at any time, which would
    // silently destroy unsaved pages and break the crash-recovery guarantee.
    private val sessionDirectory = File(application.filesDir, "scanner_session")

    private var lastCapturedFingerprint: LongArray? = null
    private var lastAutoCaptureAtMillis: Long = 0L
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

    /** Fast-save progress: surfaced as a blocking overlay while document files are written. */
    val isSaving = MutableStateFlow(false)
    val savingPage = MutableStateFlow(0)
    val savingTotalPages = MutableStateFlow(0)

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
            ScanMode.PHOTO -> "Tap shutter to add photos"
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
        if (scanMode.value == ScanMode.PHOTO) {
            // Plain photo mode: no detection, no auto-capture, near-zero CPU.
            return
        }
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
            val now = System.currentTimeMillis()
            val fingerprint = fingerprintOf(cropToQuad(bitmap, result.corners))
            val previous = lastCapturedFingerprint
            val isNewContent = previous == null || !PageFingerprint.areSamePage(previous, fingerprint)
            val intervalElapsed = now - lastAutoCaptureAtMillis >= AUTO_CAPTURE_MIN_INTERVAL_MS
            if (isNewContent && intervalElapsed) {
                lastCapturedFingerprint = fingerprint
                lastAutoCaptureAtMillis = now
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
                // Cap resolution: camera sensors emit 12-200 MP; beyond ~2560 px it is
                // wasted on PDF export, slows down every transform, and multiplies OOM risk.
                val frame = downscaleTo(copy, MAX_PAGE_DIM)
                if (scanMode.value == ScanMode.BOOK) {
                    handleBookCapture(frame)
                    return@launch
                }
                if (scanMode.value == ScanMode.PHOTO) {
                    // Photo mode: keep the frame exactly as captured — no crop,
                    // no warp, no document enhancement.
                    val draft = ScannedPageDraft(
                        originalBitmap = frame,
                        processedBitmap = frame,
                        thumbnail = scaleForThumbnail(frame),
                        corners = QuadCorners.defaultQuad(1f, 1f)
                    )
                    val persisted = persistDraft(draft)
                    withContext(Dispatchers.Main) {
                        capturedPages.value = capturedPages.value + persisted
                        guidanceMessage.value = "Photo added • keep going or tap Done"
                        captureState.value = CaptureState.Idle
                        isProcessing.value = false
                    }
                    return@launch
                }
                val detected = hasRealDetection.value && !treatAsUndetected
                val corners = manualCorners ?: detectedCorners.value
                val draft = if (detected) {
                    val warped = PerspectiveTransformer.transform(frame, toPixelCorners(corners, frame))
                    val enhanced = ImageEnhancer.enhanceColorAndContrast(warped)
                    ScannedPageDraft(
                        originalBitmap = warped,
                        processedBitmap = enhanced,
                        thumbnail = scaleForThumbnail(enhanced),
                        corners = QuadCorners.defaultQuad(1f, 1f, 0.01f)
                    )
                } else {
                    // Manual capture fallback: keep the full frame and let the user crop.
                    val enhanced = ImageEnhancer.enhanceColorAndContrast(frame)
                    ScannedPageDraft(
                        originalBitmap = frame,
                        processedBitmap = enhanced,
                        thumbnail = scaleForThumbnail(enhanced),
                        corners = QuadCorners.defaultQuad(1f, 1f)
                    )
                }
                val persisted = persistDraft(draft)
                withContext(Dispatchers.Main) {
                    capturedPages.value = capturedPages.value + persisted
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
                Log.e("ScanViewModel", "Capture processing failed", t)
            }
        }
    }

    private suspend fun handleBookCapture(frame: Bitmap) {
        val corners = detectedCorners.value
        val bookPages = bookScanner.processSpread(frame, corners)
        val drafts = bookPages.map { page ->
            val enhanced = ImageEnhancer.enhanceColorAndContrast(page, contrast = 1.12f, brightness = 8f)
            persistDraft(
                ScannedPageDraft(
                    originalBitmap = page,
                    processedBitmap = enhanced,
                    thumbnail = scaleForThumbnail(enhanced),
                    corners = QuadCorners.defaultQuad(1f, 1f, 0.01f)
                )
            )
        }
        withContext(Dispatchers.Main) {
            capturedPages.value = capturedPages.value + drafts
            editingPage.value = null
            detector.reset()
            lastCapturedFingerprint = fingerprintOf(frame)
            guidanceMessage.value = "Spread captured • turn the page"
            captureState.value = CaptureState.Idle
            isProcessing.value = false
        }
    }

    /**
     * Opens a captured page for manual crop/rotation without leaving the session.
     * The draft is published immediately (screens show a loading state) and its
     * full-size bitmaps are reloaded from the session files in the background.
     */
    fun editPage(draft: ScannedPageDraft) {
        if (isProcessing.value) return
        editingPage.value = draft
        isProcessing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            val loaded = withBitmaps(draft)
            withContext(Dispatchers.Main) {
                // Ignore the result if the user moved on to another page meanwhile.
                if (editingPage.value?.id == draft.id) {
                    editingPage.value = loaded
                }
                isProcessing.value = false
            }
        }
    }

    fun retakePage(draft: ScannedPageDraft) {
        capturedPages.value = capturedPages.value.filterNot { it.id == draft.id }
        if (editingPage.value?.id == draft.id) editingPage.value = null
        draft.sessionOrigPath?.let { File(it).delete() }
        draft.sessionProcPath?.let { File(it).delete() }
    }

    /**
     * Batch import: turns gallery images (or PDF pages) into pages of the current
     * session. Decoding happens off the main thread with downsampling and EXIF
     * correction; pages appear one by one with progress feedback.
     */
    fun importImages(uris: List<Uri>, onReady: (List<ScannedPageDraft>) -> Unit = {}) {
        importUrisInternal(uris, onReady, ::decodeImageUri)
    }

    fun importPdfs(uris: List<Uri>, onReady: (List<ScannedPageDraft>) -> Unit = {}) {
        importUrisInternal(uris, onReady, ::decodePdfUri)
    }

    private fun importUrisInternal(
        uris: List<Uri>,
        onReady: (List<ScannedPageDraft>) -> Unit,
        decode: (Uri) -> List<Bitmap>
    ) {
        if (uris.isEmpty()) return
        if (scanMode.value == ScanMode.QR_BARCODE) {
            // Importing makes no sense in the QR session — pages would be invisible.
            scanMode.value = ScanMode.DOCUMENT
        }
        captureState.value = CaptureState.Processing
        isProcessing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            val drafts = mutableListOf<ScannedPageDraft>()
            var failedFiles = 0
            try {
                for (uri in uris) {
                    val bitmaps = try {
                        decode(uri)
                    } catch (t: Throwable) {
                        Log.e("ScanViewModel", "Import of $uri failed", t)
                        emptyList()
                    }
                    if (bitmaps.isEmpty()) {
                        failedFiles++
                        continue
                    }
                    for (bitmap in bitmaps) {
                        val draft = buildImportDraft(bitmap) ?: run {
                            if (!bitmap.isRecycled) bitmap.recycle()
                            failedFiles++
                            continue
                        }
                        val persisted = persistDraft(draft)
                        drafts += persisted
                        withContext(Dispatchers.Main) {
                            capturedPages.value = capturedPages.value + persisted
                            guidanceMessage.value = "Importing… ${drafts.size} page(s) added"
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    captureState.value = CaptureState.Idle
                    isProcessing.value = false
                    guidanceMessage.value = buildString {
                        append("${drafts.size} page(s) imported • tap Done to save")
                        if (failedFiles > 0) append(" • $failedFiles file(s) could not be read")
                    }
                    onReady(drafts)
                }
            } catch (t: Throwable) {
                Log.e("ScanViewModel", "Batch import failed", t)
                withContext(Dispatchers.Main) {
                    isProcessing.value = false
                    captureState.value = CaptureState.Failed("Import failed. Try again.")
                }
            }
        }
    }

    private fun buildImportDraft(source: Bitmap): ScannedPageDraft? {
        return try {
            val scaled = downscaleTo(source, MAX_PAGE_DIM)
            val enhanced = ImageEnhancer.enhanceColorAndContrast(scaled)
            ScannedPageDraft(
                originalBitmap = scaled,
                processedBitmap = enhanced,
                thumbnail = scaleForThumbnail(enhanced),
                corners = QuadCorners.defaultQuad(1f, 1f)
            )
        } catch (t: Throwable) {
            Log.e("ScanViewModel", "Failed to import an image", t)
            null
        }
    }

    fun updateEditingPageCorners(newCorners: QuadCorners) {
        val current = editingPage.value ?: return
        val source = current.originalBitmap ?: return
        if (isProcessing.value) return
        isProcessing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val warped = PerspectiveTransformer.transform(source, toPixelCorners(newCorners, source))
                val filtered = ImageEnhancer.applyFilter(warped, current.filterType)
                if (filtered !== warped) warped.recycle()
                val updated = persistProcessed(
                    current.copy(
                        corners = newCorners,
                        processedBitmap = filtered,
                        thumbnail = scaleForThumbnail(filtered)
                    )
                )
                withContext(Dispatchers.Main) {
                    editingPage.value = updated
                    capturedPages.value = capturedPages.value.map { if (it.id == updated.id) updated else it }
                    isProcessing.value = false
                }
            } catch (t: Throwable) {
                Log.e("ScanViewModel", "Crop update failed", t)
                withContext(Dispatchers.Main) {
                    isProcessing.value = false
                    captureState.value = CaptureState.Failed("Could not update crop: ${t.message ?: "unexpected error"}")
                }
            }
        }
    }

    fun applyFilterToEditingPage(filterType: FilterType) {
        val current = editingPage.value ?: return
        val source = current.originalBitmap ?: return
        if (isProcessing.value) return
        isProcessing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val warped = PerspectiveTransformer.transform(source, toPixelCorners(current.corners, source))
                val filtered = ImageEnhancer.applyFilter(warped, filterType)
                if (filtered !== warped) warped.recycle()
                val rotated = if (current.rotation != 0) PerspectiveTransformer.rotate(filtered, current.rotation) else filtered
                val updated = persistProcessed(
                    current.copy(
                        filterType = filterType,
                        processedBitmap = rotated,
                        thumbnail = scaleForThumbnail(rotated)
                    )
                )
                withContext(Dispatchers.Main) {
                    editingPage.value = updated
                    capturedPages.value = capturedPages.value.map { if (it.id == updated.id) updated else it }
                    isProcessing.value = false
                }
            } catch (t: Throwable) {
                Log.e("ScanViewModel", "Filter update failed", t)
                withContext(Dispatchers.Main) {
                    isProcessing.value = false
                    captureState.value = CaptureState.Failed("Could not apply filter: ${t.message ?: "unexpected error"}")
                }
            }
        }
    }

    fun rotateEditingPage() {
        val current = editingPage.value ?: return
        if (isProcessing.value) return
        isProcessing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val source = current.processedBitmap
                    ?: current.sessionProcPath?.let { BitmapFactory.decodeFile(it) }
                    ?: throw IllegalStateException("Page bitmap unavailable")
                val rotated = PerspectiveTransformer.rotate(source, 90)
                val updated = persistProcessed(
                    current.copy(
                        rotation = (current.rotation + 90) % 360,
                        processedBitmap = rotated,
                        thumbnail = scaleForThumbnail(rotated)
                    )
                )
                withContext(Dispatchers.Main) {
                    editingPage.value = updated
                    capturedPages.value = capturedPages.value.map { if (it.id == updated.id) updated else it }
                    isProcessing.value = false
                }
            } catch (t: Throwable) {
                Log.e("ScanViewModel", "Rotate failed", t)
                withContext(Dispatchers.Main) {
                    isProcessing.value = false
                    captureState.value = CaptureState.Failed("Could not rotate page: ${t.message ?: "unexpected error"}")
                }
            }
        }
    }

    /**
     * Saves the session quickly: writes page files, inserts the document and
     * navigates on. OCR runs afterwards in the background (DocumentViewModel)
     * so "Done" no longer blocks for seconds on Tesseract.
     */
    fun finishBatchAndSave(
        customTitle: String? = null,
        onSaved: (documentId: String) -> Unit
    ) {
        val drafts = capturedPages.value
        if (drafts.isEmpty()) return
        if (isSaving.value) return
        isSaving.value = true
        savingTotalPages.value = drafts.size
        savingPage.value = 0
        isProcessing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docId = saveDrafts(drafts, customTitle)
                withContext(Dispatchers.Main) {
                    isSaving.value = false
                    isProcessing.value = false
                    capturedPages.value = emptyList()
                    editingPage.value = null
                    onSaved(docId)
                }
            } catch (t: Throwable) {
                Log.e("ScanViewModel", "Save failed", t)
                withContext(Dispatchers.Main) {
                    isSaving.value = false
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
        val savedPages = mutableListOf<DocumentPage>()
        for ((index, draft) in drafts.withIndex()) {
            savingPage.value = index + 1
            val origPath = draft.sessionOrigPath?.let { storageManager.importJpegFile(docId, "orig", draft.id, File(it)) }
                ?: draft.originalBitmap?.let { storageManager.saveBitmap(docId, "orig", draft.id, it) }
            val procPath = draft.sessionProcPath?.let { storageManager.importJpegFile(docId, "proc", draft.id, File(it)) }
                ?: draft.processedBitmap?.let { storageManager.saveBitmap(docId, "proc", draft.id, it) }
            if (procPath == null) continue
            val thumbnailSource = decodeScaled(procPath, 320)
            val thumbPath = thumbnailSource?.let { storageManager.saveThumbnail(docId, draft.id, it) }
            thumbnailSource?.recycle()
            val (width, height) = decodeImageSize(procPath)
            savedPages.add(
                DocumentPage(
                    id = draft.id,
                    documentId = docId,
                    pageIndex = index,
                    originalPath = origPath ?: procPath,
                    processedPath = procPath,
                    thumbnailPath = thumbPath ?: procPath,
                    rotation = draft.rotation,
                    filterType = draft.filterType,
                    ocrText = null,
                    ocrConfidence = 0f,
                    width = width,
                    height = height
                )
            )
        }
        if (savedPages.isEmpty()) throw IllegalStateException("No pages could be saved")
        val title = customTitle?.takeIf { it.isNotBlank() }
            ?: DocumentUnderstanding.suggestFilename(docType)
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

    // region Draft helpers

    /** Returns a copy of [draft] with original/processed bitmaps reloaded from session files. */
    private suspend fun withBitmaps(draft: ScannedPageDraft): ScannedPageDraft = withContext(Dispatchers.Default) {
        val orig = draft.originalBitmap
            ?: draft.sessionOrigPath?.let { BitmapFactory.decodeFile(it) }
        val proc = draft.processedBitmap
            ?: draft.sessionProcPath?.let { BitmapFactory.decodeFile(it) }
            ?: orig
        draft.copy(originalBitmap = orig, processedBitmap = proc)
    }

    /**
     * Writes the session JPEG pair for [draft] and, on success, returns a copy
     * holding only the file paths — the full-size bitmaps are released so long
     * sessions stay within the heap budget. On write failure the bitmaps are
     * kept in RAM as the fallback source.
     */
    private fun persistDraft(draft: ScannedPageDraft): ScannedPageDraft {
        val persisted = runCatching {
            sessionDirectory.mkdirs()
            val origBitmap = draft.originalBitmap
                ?: draft.processedBitmap
                ?: throw IllegalStateException("Draft has neither bitmaps nor files")
            val procBitmap = draft.processedBitmap ?: origBitmap
            val orig = File(sessionDirectory, "${draft.id}_orig.jpg")
            val proc = File(sessionDirectory, "${draft.id}_proc.jpg")
            FileOutputStream(orig).use { origBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            FileOutputStream(proc).use { procBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            draft.copy(
                sessionOrigPath = orig.absolutePath,
                sessionProcPath = proc.absolutePath,
                originalBitmap = null,
                processedBitmap = null
            )
        }.getOrDefault(draft)
        if (persisted.originalBitmap == null) {
            // recycle() is idempotent; original and processed can be the same instance.
            draft.originalBitmap?.recycle()
            draft.processedBitmap?.recycle()
        }
        return persisted
    }

    /** Rewrites only the processed session JPEG after an edit (crop/filter/rotate). */
    private fun persistProcessed(draft: ScannedPageDraft): ScannedPageDraft {
        val procBitmap = draft.processedBitmap ?: return draft
        return runCatching {
            sessionDirectory.mkdirs()
            val proc = File(sessionDirectory, "${draft.id}_proc.jpg")
            FileOutputStream(proc).use { procBitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            draft.copy(sessionProcPath = proc.absolutePath)
        }.getOrDefault(draft)
    }

    // endregion

    // region Decoding

    /** Decodes a gallery image, downsampling to [MAX_PAGE_DIM] and fixing EXIF orientation. */
    private fun decodeImageUri(uri: Uri): List<Bitmap> {
        val resolver: ContentResolver = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return emptyList()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return emptyList()
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_PAGE_DIM) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return emptyList()
        return listOf(applyExifOrientation(resolver, uri, decoded))
    }

    /** Renders each page of a PDF document to a bitmap (white background, capped page count). */
    private fun decodePdfUri(uri: Uri): List<Bitmap> {
        val resolver: ContentResolver = getApplication<Application>().contentResolver
        val bitmaps = mutableListOf<Bitmap>()
        val pfd = try {
            resolver.openFileDescriptor(uri, "r")
        } catch (t: Throwable) {
            Log.e("ScanViewModel", "Cannot open PDF $uri", t)
            null
        } ?: return emptyList()
        var renderer: PdfRenderer? = null
        try {
            renderer = PdfRenderer(pfd)
            val count = minOf(renderer.pageCount, MAX_PDF_PAGES_PER_FILE)
            for (i in 0 until count) {
                var page: PdfRenderer.Page? = null
                try {
                    page = renderer.openPage(i)
                    val scale = PDF_RENDER_LONG_EDGE / maxOf(page.width, page.height).toFloat()
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmaps += bitmap
                } finally {
                    page?.close()
                }
            }
        } catch (t: Throwable) {
            Log.e("ScanViewModel", "PDF rendering failed for $uri", t)
            bitmaps.forEach { it.recycle() }
            bitmaps.clear()
        } finally {
            runCatching { renderer?.close() }
            runCatching { pfd.close() }
        }
        return bitmaps
    }

    private fun applyExifOrientation(resolver: ContentResolver, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            resolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.preScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.preScale(-1f, 1f) }
            else -> return bitmap
        }
        val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (result !== bitmap) bitmap.recycle()
        return result
    }

    // endregion

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

    /**
     * Crops the detected document region (with margin) so page fingerprints
     * track the page itself — background motion or animated scenery must not
     * be mistaken for a page turn.
     */
    private fun cropToQuad(bitmap: Bitmap, corners: QuadCorners): Bitmap {
        val xs = listOf(corners.topLeft.x, corners.topRight.x, corners.bottomRight.x, corners.bottomLeft.x)
        val ys = listOf(corners.topLeft.y, corners.topRight.y, corners.bottomRight.y, corners.bottomLeft.y)
        val margin = 0.04f
        val left = ((xs.min()) - margin).coerceIn(0f, 1f)
        val right = ((xs.max()) + margin).coerceIn(0f, 1f)
        val top = ((ys.min()) - margin).coerceIn(0f, 1f)
        val bottom = ((ys.max()) + margin).coerceIn(0f, 1f)
        val w = ((right - left) * bitmap.width).toInt()
        val h = ((bottom - top) * bitmap.height).toInt()
        if (w < 8 || h < 8) return bitmap
        return Bitmap.createBitmap(bitmap, (left * bitmap.width).toInt(), (top * bitmap.height).toInt(), w, h)
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

    private fun downscaleTo(bitmap: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun decodeScaled(path: String, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return downscaleTo(decoded, maxDim)
    }

    private fun decodeImageSize(path: String): Pair<Int, Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        return opts.outWidth to opts.outHeight
    }

    private fun toPixelCorners(corners: QuadCorners, bitmap: Bitmap): QuadCorners = QuadCorners(
        android.graphics.PointF(corners.topLeft.x * bitmap.width, corners.topLeft.y * bitmap.height),
        android.graphics.PointF(corners.topRight.x * bitmap.width, corners.topRight.y * bitmap.height),
        android.graphics.PointF(corners.bottomRight.x * bitmap.width, corners.bottomRight.y * bitmap.height),
        android.graphics.PointF(corners.bottomLeft.x * bitmap.width, corners.bottomLeft.y * bitmap.height)
    )

    /** Pages survive process death: JPEG copies in cache, restored on next launch. */
    private fun restoreSessionIfNeeded() {
        val files = sessionDirectory.listFiles().orEmpty()
        if (files.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            val drafts = mutableListOf<ScannedPageDraft>()
            val originals = files.filter { it.name.endsWith("_orig.jpg") }
            for (orig in originals) {
                val proc = File(sessionDirectory, orig.name.replace("_orig.jpg", "_proc.jpg"))
                val thumb = decodeScaled(proc.absolutePath, 160)
                    ?: decodeScaled(orig.absolutePath, 160)
                    ?: continue
                drafts += ScannedPageDraft(
                    id = orig.name.removeSuffix("_orig.jpg"),
                    thumbnail = thumb,
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

    companion object {
        private const val QR_DUPLICATE_SUPPRESS_MS = 2000L
        private const val REGION_FRACTION = 0.7f
        private const val CAPTURE_TIMEOUT_MS = 8000L
        private const val AUTO_CAPTURE_MIN_INTERVAL_MS = 2500L

        /** Longest edge kept in memory and on disk for a page. */
        private const val MAX_PAGE_DIM = 2560
        private const val PDF_RENDER_LONG_EDGE = 2200
        private const val MAX_PDF_PAGES_PER_FILE = 60
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
