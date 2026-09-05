package com.rebelroot.docscannerpro.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rebelroot.docscannerpro.core.cv.FileStorageManager
import com.rebelroot.docscannerpro.core.export.PdfExportConfig
import com.rebelroot.docscannerpro.core.export.PdfExporter
import com.rebelroot.docscannerpro.core.export.PdfPageSize
import com.rebelroot.docscannerpro.core.export.PdfQuality
import com.rebelroot.docscannerpro.core.pdf.PdfToolsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The PDF utilities offered in the Tools tab. */
enum class PdfToolType(
    val title: String,
    val description: String,
    val needsImages: Boolean
) {
    IMAGES_TO_PDF("Images to PDF", "Turn gallery photos into a single PDF", true),
    MERGE("Merge PDFs", "Combine several PDFs into one document", false),
    SPLIT("Split / Extract pages", "Split every page or pull out a page range", false),
    PDF_TO_IMAGES("PDF to images", "Export each page of a PDF as a JPEG", false),
    COMPRESS("Compress PDF", "Re-encode pages to shrink the file size", false),
    PROTECT("Protect PDF", "Add a password so only you can open it", false)
}

data class PdfToolUiState(
    val tool: PdfToolType? = null,
    val selectedUris: List<Uri> = emptyList(),
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val resultFiles: List<File> = emptyList(),
    val error: String? = null,
    val pageCountHint: Int? = null
)

class PdfToolsViewModel(application: Application) : AndroidViewModel(application) {
    private val storageManager = FileStorageManager(application)
    private val pdfExporter = PdfExporter(application)

    private val _state = MutableStateFlow(PdfToolUiState())
    val state: StateFlow<PdfToolUiState> = _state.asStateFlow()

    // Options (the screen only shows the ones relevant to the open tool)
    val pageSize = MutableStateFlow(PdfPageSize.FIT_IMAGE)
    val splitMode = MutableStateFlow(PdfToolsEngine.SplitMode.EVERY_PAGE)
    val rangeFrom = MutableStateFlow("1")
    val rangeTo = MutableStateFlow("1")
    val compressQuality = MutableStateFlow(PdfQuality.MEDIUM)
    val password = MutableStateFlow("")

    fun open(tool: PdfToolType) {
        _state.value = PdfToolUiState(tool = tool)
        splitMode.value = PdfToolsEngine.SplitMode.EVERY_PAGE
        rangeFrom.value = "1"
        rangeTo.value = "1"
        password.value = ""
        pageSize.value = if (tool == PdfToolType.IMAGES_TO_PDF) PdfPageSize.FIT_IMAGE else PdfPageSize.A4
    }

    fun setSelection(uris: List<Uri>) {
        val tool = _state.value.tool ?: return
        val limited = if (tool == PdfToolType.MERGE) uris.take(20) else uris.take(1)
        _state.value = _state.value.copy(
            selectedUris = limited,
            resultFiles = emptyList(),
            error = null,
            pageCountHint = null
        )
        if (limited.size == 1 && !tool.needsImages) inspectPageCount(limited.first())
    }

    private fun inspectPageCount(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = copyToCache(uri) ?: return@launch
            try {
                val count = PdfToolsEngine.getPdfPageCount(cached)
                rangeFrom.value = "1"
                rangeTo.value = count.toString()
                _state.value = _state.value.copy(pageCountHint = count)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message)
            }
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    fun resetResults() {
        _state.value = _state.value.copy(resultFiles = emptyList())
    }

    fun uriFor(file: File): Uri = storageManager.getUriForFile(file)

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun updateProgress(progress: Float, status: String) {
        // MutableStateFlow.value writes are thread-safe; safe to call from IO callbacks.
        _state.value = _state.value.copy(progress = progress.coerceIn(0f, 1f), statusText = status)
    }

    private suspend fun copyToCache(uri: Uri): File? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(getApplication<Application>().cacheDir, "pdftool_${System.nanoTime()}.bin")
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            target
        }.getOrNull()
    }

    fun run() {
        val tool = _state.value.tool ?: return
        if (_state.value.isRunning) return
        val uris = _state.value.selectedUris
        if (uris.isEmpty()) {
            _state.value = _state.value.copy(error = "Select a file first.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isRunning = true, progress = 0f, error = null, resultFiles = emptyList())
            try {
                val results: List<File> = when (tool) {
                    PdfToolType.IMAGES_TO_PDF -> runImagesToPdf(uris)
                    PdfToolType.MERGE -> runMerge(uris)
                    PdfToolType.SPLIT -> runSplit(uris)
                    PdfToolType.PDF_TO_IMAGES -> runPdfToImages(uris)
                    PdfToolType.COMPRESS -> runCompress(uris)
                    PdfToolType.PROTECT -> runProtect(uris)
                }
                if (results.isEmpty()) throw PdfToolsEngine.PdfToolsException("Nothing was produced.")
                _state.value = _state.value.copy(isRunning = false, progress = 1f, statusText = "Done", resultFiles = results)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    isRunning = false,
                    error = t.message ?: "Something went wrong. Try a different file."
                )
            }
        }
    }

    private suspend fun runImagesToPdf(uris: List<Uri>): List<File> {
        val bitmaps = mutableListOf<Bitmap>()
        try {
            uris.forEachIndexed { index, uri ->
                val bitmap = decodeImageUri(uri) ?: throw PdfToolsEngine.PdfToolsException(
                    "Image ${index + 1} could not be read."
                )
                bitmaps += bitmap
                updateProgress((index + 1).toFloat() / (uris.size + 1), "Reading ${index + 1}/${uris.size}…")
            }
            val output = storageManager.getExportFile("images-${timestamp()}.pdf")
            pdfExporter.exportBitmapsToPdf(
                bitmaps,
                output,
                PdfExportConfig(pageSize = pageSize.value, isSearchable = false)
            ) { current, total ->
                updateProgress(current.toFloat() / total, "Building PDF $current/$total…")
            }
            return listOf(output)
        } finally {
            bitmaps.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    private suspend fun runMerge(uris: List<Uri>): List<File> {
        val inputs = uris.mapIndexedNotNull { index, uri ->
            updateProgress(index.toFloat() / uris.size * 0.3f, "Preparing ${index + 1}/${uris.size}…")
            copyToCache(uri)
        }
        if (inputs.size < 2) throw PdfToolsEngine.PdfToolsException("Select at least two PDFs to merge.")
        val output = storageManager.getExportFile("merged-${timestamp()}.pdf")
        PdfToolsEngine.mergePdfs(inputs, output) { p ->
            updateProgress(0.3f + p * 0.7f, "Merging…")
        }
        inputs.forEach { it.delete() }
        return listOf(output)
    }

    private suspend fun runSplit(uris: List<Uri>): List<File> {
        val input = copyToCache(uris.first())
            ?: throw PdfToolsEngine.PdfToolsException("The selected file could not be read.")
        try {
            val count = PdfToolsEngine.getPdfPageCount(input)
            return when (splitMode.value) {
                PdfToolsEngine.SplitMode.EVERY_PAGE -> {
                    val dir = File(getApplication<Application>().filesDir, "exports/split-${timestamp()}")
                    PdfToolsEngine.splitPdf(
                        input = input,
                        outputDir = dir,
                        baseName = "page",
                        output = dir,
                        mode = PdfToolsEngine.SplitMode.EVERY_PAGE,
                        from = 1,
                        to = count,
                        onProgress = { p -> updateProgress(p, "Splitting…") }
                    )
                }
                PdfToolsEngine.SplitMode.EXTRACT_RANGE -> {
                    val from = rangeFrom.value.trim().toIntOrNull()
                        ?: throw PdfToolsEngine.PdfToolsException("Enter a valid start page.")
                    val to = rangeTo.value.trim().toIntOrNull()
                        ?: throw PdfToolsEngine.PdfToolsException("Enter a valid end page.")
                    if (from < 1 || to < from || from > count) {
                        throw PdfToolsEngine.PdfToolsException("Pages must be between 1 and $count.")
                    }
                    val output = storageManager.getExportFile("pages-$from-$to-${timestamp()}.pdf")
                    PdfToolsEngine.splitPdf(
                        input = input,
                        outputDir = output.parentFile ?: File("."),
                        baseName = "pages",
                        output = output,
                        mode = PdfToolsEngine.SplitMode.EXTRACT_RANGE,
                        from = from,
                        to = to,
                        onProgress = { p -> updateProgress(p, "Extracting…") }
                    )
                }
            }
        } finally {
            input.delete()
        }
    }

    private suspend fun runPdfToImages(uris: List<Uri>): List<File> {
        val input = copyToCache(uris.first())
            ?: throw PdfToolsEngine.PdfToolsException("The selected file could not be read.")
        try {
            val dir = File(getApplication<Application>().filesDir, "exports/pdf-images-${timestamp()}")
            return PdfToolsEngine.pdfToImages(
                input = input,
                outputDir = dir,
                baseName = "page",
                onProgress = { p -> updateProgress(p, "Rendering…") }
            )
        } finally {
            input.delete()
        }
    }

    private suspend fun runCompress(uris: List<Uri>): List<File> {
        val input = copyToCache(uris.first())
            ?: throw PdfToolsEngine.PdfToolsException("The selected file could not be read.")
        try {
            val output = storageManager.getExportFile("compressed-${timestamp()}.pdf")
            val (quality, scale) = when (compressQuality.value) {
                PdfQuality.HIGH -> 80 to 1.0f
                PdfQuality.MEDIUM -> 60 to 0.9f
                PdfQuality.COMPACT -> 40 to 0.75f
            }
            PdfToolsEngine.compressPdf(
                input = input,
                output = output,
                jpegQuality = quality,
                scale = scale,
                onProgress = { p -> updateProgress(p, "Compressing…") }
            )
            return listOf(output)
        } finally {
            input.delete()
        }
    }

    private suspend fun runProtect(uris: List<Uri>): List<File> {
        if (password.value.isBlank()) throw PdfToolsEngine.PdfToolsException("Enter a password first.")
        val input = copyToCache(uris.first())
            ?: throw PdfToolsEngine.PdfToolsException("The selected file could not be read.")
        try {
            val output = storageManager.getExportFile("protected-${timestamp()}.pdf")
            PdfToolsEngine.protectPdf(
                input = input,
                output = output,
                password = password.value,
                onProgress = { p -> updateProgress(p, "Encrypting…") }
            )
            return listOf(output)
        } finally {
            input.delete()
        }
    }

    /** Decode with downsampling + EXIF correction, mirroring the scanner import. */
    private fun decodeImageUri(uri: Uri): Bitmap? {
        val resolver = getApplication<Application>().contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2560) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
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
            }
            if (matrix.isIdentity) decoded else {
                val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                if (rotated !== decoded) decoded.recycle()
                rotated
            }
        } catch (_: Throwable) {
            null
        }
    }
}
