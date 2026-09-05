package com.rebelroot.docscannerpro.core.ocr
import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import com.rebelroot.docscannerpro.core.model.OcrBlock
import com.rebelroot.docscannerpro.core.model.OcrLine
import com.rebelroot.docscannerpro.core.model.OcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
class OcrEngine(private val context: Context) {
    private val dataDirectory = File(context.filesDir, "tesseract")
    private var initializedLanguage: String? = null
    private var tess: TessBaseAPI? = null
    private val engineMutex = Mutex()
    suspend fun recognizeText(bitmap: Bitmap, language: String = "eng"): OcrResult = withContext(Dispatchers.Default) {
        engineMutex.withLock {
            try {
            ensureDataFiles()
            val engine = ensureEngine(language)
            engine.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            engine.setImage(bitmap)
            val text = engine.getUTF8Text().orEmpty()
            val confidence = (engine.meanConfidence().coerceIn(0, 100) / 100f)
            val lines = text.lines().filter { it.isNotBlank() }
            val blocks = if (text.isBlank()) emptyList() else listOf(
                OcrBlock(
                    text = text,
                    confidence = confidence,
                    left = 0f,
                    top = 0f,
                    right = bitmap.width.toFloat(),
                    bottom = bitmap.height.toFloat(),
                    lines = lines.map { line ->
                        OcrLine(
                            text = line,
                            left = 0f,
                            top = 0f,
                            right = bitmap.width.toFloat(),
                            bottom = 0f
                        )
                    }
                )
            )
            OcrResult(
                fullText = text,
                blocks = blocks,
                confidence = confidence,
                language = language
            )
            } catch (_: Exception) {
                OcrResult(fullText = "", blocks = emptyList(), confidence = 0f, language = language)
            }
        }
    }
    private fun ensureEngine(language: String): TessBaseAPI {
        val current = tess
        if (current != null && initializedLanguage == language) {
            return current
        }
        current?.recycle()
        val engine = TessBaseAPI()
        val ready = engine.init(dataDirectory.absolutePath + File.separator, language, TessBaseAPI.OEM_LSTM_ONLY)
        if (!ready) {
            engine.recycle()
            throw IllegalStateException("Unable to initialize OCR data")
        }
        tess = engine
        initializedLanguage = language
        return engine
    }
    private fun ensureDataFiles() {
        val tessData = File(dataDirectory, "tessdata")
        if (!tessData.exists()) tessData.mkdirs()
        listOf("eng.traineddata", "hin.traineddata").forEach { name ->
            val destination = File(tessData, name)
            if (!destination.exists()) {
                context.assets.open("tessdata/$name").use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
    fun close() {
        tess?.recycle()
        tess = null
        initializedLanguage = null
    }
}
