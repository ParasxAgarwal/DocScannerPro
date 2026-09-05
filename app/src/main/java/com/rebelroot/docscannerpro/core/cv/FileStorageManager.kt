package com.rebelroot.docscannerpro.core.cv
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
class FileStorageManager(private val context: Context) {
    private val documentsDir = File(context.filesDir, "documents").apply { mkdirs() }
    private val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
    fun getDocumentFolder(docId: String): File {
        val dir = File(documentsDir, docId)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    suspend fun saveBitmap(
        docId: String,
        prefix: String,
        pageId: String,
        bitmap: Bitmap,
        quality: Int = 90
    ): String = withContext(Dispatchers.IO) {
        val folder = getDocumentFolder(docId)
        val file = File(folder, "${prefix}_${pageId}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        file.absolutePath
    }

    /**
     * Moves an already-encoded JPEG (e.g. the scanner-session copy) into a
     * document folder without re-encoding, preserving quality and time.
     */
    suspend fun importJpegFile(docId: String, prefix: String, pageId: String, source: File): String =
        withContext(Dispatchers.IO) {
            val folder = getDocumentFolder(docId)
            val file = File(folder, "${prefix}_${pageId}.jpg")
            source.inputStream().use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
            file.absolutePath
        }
    suspend fun saveThumbnail(
        docId: String,
        pageId: String,
        bitmap: Bitmap
    ): String = withContext(Dispatchers.IO) {
        val folder = getDocumentFolder(docId)
        val file = File(folder, "thumb_${pageId}.jpg")
        val maxDim = 320
        val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1.0f)
        val thumb = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }
        FileOutputStream(file).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }
        file.absolutePath
    }
    suspend fun loadBitmap(path: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) return@withContext null
            BitmapFactory.decodeFile(path)
        } catch (_: Exception) {
            null
        }
    }
    fun getExportFile(filename: String): File {
        if (!exportsDir.exists()) exportsDir.mkdirs()
        return File(exportsDir, filename)
    }
    fun getUriForFile(file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }
    suspend fun deleteDocumentFiles(docId: String) = withContext(Dispatchers.IO) {
        val folder = File(documentsDir, docId)
        if (folder.exists()) {
            folder.deleteRecursively()
        }
    }
    suspend fun calculateStorageUsage(): Long = withContext(Dispatchers.IO) {
        fun dirSize(dir: File): Long {
            var size: Long = 0
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) dirSize(file) else file.length()
            }
            return size
        }
        dirSize(documentsDir) + dirSize(exportsDir)
    }
    suspend fun clearExports() = withContext(Dispatchers.IO) {
        exportsDir.listFiles()?.forEach { it.delete() }
    }
}
