package com.rebelroot.docscannerpro.ui.viewmodel
import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rebelroot.docscannerpro.core.cv.FileStorageManager
import com.rebelroot.docscannerpro.core.database.AppDatabase
import com.rebelroot.docscannerpro.core.database.DocumentRepository
import com.rebelroot.docscannerpro.core.export.DocxExporter
import com.rebelroot.docscannerpro.core.export.PdfExportConfig
import com.rebelroot.docscannerpro.core.export.PdfExporter
import com.rebelroot.docscannerpro.core.export.PdfPageSize
import com.rebelroot.docscannerpro.core.export.PdfQuality
import com.rebelroot.docscannerpro.core.export.TextExporter
import com.rebelroot.docscannerpro.core.model.ChecklistItem
import com.rebelroot.docscannerpro.core.model.Document
import com.rebelroot.docscannerpro.core.model.DocumentNote
import com.rebelroot.docscannerpro.core.model.DocumentPage
import com.rebelroot.docscannerpro.core.model.DocumentType
import com.rebelroot.docscannerpro.core.model.StructuredReceipt
import com.rebelroot.docscannerpro.core.ocr.DocumentUnderstanding
import com.rebelroot.docscannerpro.core.ocr.OcrEngineProvider
import com.rebelroot.docscannerpro.core.security.VaultManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
enum class CategoryFilter {
    ALL,
    DOCUMENTS,
    RECEIPTS,
    ID_CARDS,
    BOOKS,
    FAVORITES,
    VAULT
}
class DocumentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DocumentRepository(AppDatabase.getInstance(application))
    private val storageManager = FileStorageManager(application)
    private val pdfExporter = PdfExporter(application)
    private val docxExporter = DocxExporter()
    private val textExporter = TextExporter()
    private val ocrEngine = OcrEngineProvider.get(application)
    private val ocrJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    val vaultManager = VaultManager(application)
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow(CategoryFilter.ALL)
    val isExporting = MutableStateFlow(false)
    val exportProgress = MutableStateFlow(0f)
    val lastExportedUri = MutableStateFlow<Uri?>(null)
    val storageBytes = MutableStateFlow(0L)
    private val rawPublicDocs = repository.getPublicDocuments()
    private val rawVaultDocs = repository.getVaultDocuments()
    val allNotes = repository.getAllNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val documents: StateFlow<List<Document>> = combine(
        rawPublicDocs,
        rawVaultDocs,
        searchQuery,
        selectedCategory,
        vaultManager.isUnlocked
    ) { pubDocs, vDocs, query, cat, unlocked ->
        val sourceList = if (cat == CategoryFilter.VAULT) {
            if (unlocked) vDocs else emptyList()
        } else {
            pubDocs
        }
        var filtered = when (cat) {
            CategoryFilter.ALL -> sourceList
            CategoryFilter.DOCUMENTS -> sourceList.filter { it.type == DocumentType.DOCUMENT || it.type == DocumentType.WHITEBOARD }
            CategoryFilter.RECEIPTS -> sourceList.filter { it.type == DocumentType.RECEIPT }
            CategoryFilter.ID_CARDS -> sourceList.filter { it.type == DocumentType.ID_CARD || it.type == DocumentType.BUSINESS_CARD }
            CategoryFilter.BOOKS -> sourceList.filter { it.type == DocumentType.BOOK }
            CategoryFilter.FAVORITES -> sourceList.filter { it.isFavorite }
            CategoryFilter.VAULT -> sourceList
        }
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            filtered = filtered.filter { doc ->
                doc.title.lowercase().contains(q) ||
                doc.tags.any { it.lowercase().contains(q) } ||
                doc.type.name.lowercase().contains(q)
            }
        }
        filtered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val currentDocument = MutableStateFlow<Document?>(null)
    val currentPages = MutableStateFlow<List<DocumentPage>>(emptyList())
    init {
        refreshStorageUsage()
    }
    fun setCategory(category: CategoryFilter) {
        selectedCategory.value = category
    }
    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }
    fun loadDocument(docId: String) {
        viewModelScope.launch {
            val doc = repository.getDocument(docId)
            val pages = repository.getPagesList(docId)
            currentDocument.value = doc
            currentPages.value = pages
            schedulePendingOcr(docId, doc?.type ?: DocumentType.DOCUMENT, pages)
        }
    }

    /**
     * Pages saved by the scanner start without OCR (saving must be instant).
     * Recognize the missing text in the background and publish each result as
     * it lands, so the document opens immediately and text appears shortly after.
     */
    private fun schedulePendingOcr(docId: String, docType: DocumentType, pages: List<DocumentPage>) {
        val pending = pages.filter { it.ocrText.isNullOrBlank() }
        if (pending.isEmpty()) return
        ocrJobs.remove(docId)?.cancel()
        ocrJobs[docId] = viewModelScope.launch {
            for (page in pending) {
                val bitmap = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    BitmapFactory.decodeFile(page.processedPath)
                } ?: continue
                val result = try {
                    ocrEngine.recognizeText(bitmap)
                } catch (_: Throwable) {
                    bitmap.recycle()
                    continue
                }
                bitmap.recycle()
                val structuredJson = when (docType) {
                    DocumentType.RECEIPT -> {
                        val r = DocumentUnderstanding.extractReceipt(result.fullText)
                        org.json.JSONObject().apply {
                            put("merchant", r.merchant)
                            put("total", r.total)
                            put("date", r.date)
                        }.toString()
                    }
                    DocumentType.BUSINESS_CARD -> {
                        val c = DocumentUnderstanding.extractContact(result.fullText)
                        org.json.JSONObject().apply {
                            put("name", c.name)
                            put("company", c.company)
                            put("phone", c.phone)
                            put("email", c.email)
                        }.toString()
                    }
                    else -> null
                }
                val updated = page.copy(
                    ocrText = result.fullText,
                    ocrConfidence = result.confidence,
                    structuredJson = structuredJson ?: page.structuredJson
                )
                repository.updatePage(updated)
                if (currentDocument.value?.id == docId) {
                    currentPages.value = currentPages.value.map { if (it.id == page.id) updated else it }
                }
            }
        }
    }
    fun updateTitle(docId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.updateDocumentTitle(docId, newTitle.trim())
            currentDocument.value = currentDocument.value?.copy(title = newTitle.trim())
        }
    }
    fun toggleFavorite(doc: Document) {
        viewModelScope.launch {
            repository.toggleFavorite(doc.id, doc.isFavorite)
            if (currentDocument.value?.id == doc.id) {
                currentDocument.value = currentDocument.value?.copy(isFavorite = !doc.isFavorite)
            }
        }
    }
    fun togglePinned(doc: Document) {
        viewModelScope.launch {
            repository.togglePinned(doc.id, doc.isPinned)
            if (currentDocument.value?.id == doc.id) {
                currentDocument.value = currentDocument.value?.copy(isPinned = !doc.isPinned)
            }
        }
    }
    fun toggleVault(doc: Document) {
        viewModelScope.launch {
            repository.toggleVault(doc.id, doc.isVaultLocked)
            if (currentDocument.value?.id == doc.id) {
                currentDocument.value = currentDocument.value?.copy(isVaultLocked = !doc.isVaultLocked)
            }
        }
    }
    fun deleteDocument(docId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteDocument(docId)
            storageManager.deleteDocumentFiles(docId)
            refreshStorageUsage()
            onDeleted()
        }
    }
    fun deletePage(pageId: String) {
        val docId = currentDocument.value?.id ?: return
        viewModelScope.launch {
            repository.deletePage(pageId, docId)
            loadDocument(docId)
        }
    }
    fun updatePageOcr(pageId: String, newText: String) {
        val page = currentPages.value.find { it.id == pageId } ?: return
        val updated = page.copy(ocrText = newText)
        viewModelScope.launch {
            repository.updatePage(updated)
            currentPages.value = currentPages.value.map { if (it.id == pageId) updated else it }
        }
    }
    fun exportPdf(
        pageSize: PdfPageSize = PdfPageSize.A4,
        isSearchable: Boolean = true,
        quality: PdfQuality = PdfQuality.HIGH,
        onReady: (uri: Uri, file: File) -> Unit
    ) {
        val doc = currentDocument.value ?: return
        val pages = currentPages.value
        if (pages.isEmpty()) return
        isExporting.value = true
        exportProgress.value = 0f
        viewModelScope.launch(Dispatchers.IO) {
            val sanitizedName = doc.title.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            val outFile = storageManager.getExportFile("${sanitizedName}.pdf")
            pdfExporter.exportToPdf(
                pages = pages,
                outputFile = outFile,
                config = PdfExportConfig(
                    pageSize = pageSize,
                    isSearchable = isSearchable,
                    quality = quality
                ),
                onProgress = { cur, tot ->
                    exportProgress.value = cur.toFloat() / tot.toFloat()
                }
            )
            val uri = storageManager.getUriForFile(outFile)
            lastExportedUri.value = uri
            withContext(Dispatchers.Main) {
                isExporting.value = false
                refreshStorageUsage()
                onReady(uri, outFile)
            }
        }
    }
    fun exportDocx(onReady: (uri: Uri, file: File) -> Unit) {
        val doc = currentDocument.value ?: return
        val pages = currentPages.value
        if (pages.isEmpty()) return
        isExporting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val sanitizedName = doc.title.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            val outFile = storageManager.getExportFile("${sanitizedName}.docx")
            docxExporter.exportToDocx(doc.title, pages, outFile)
            val uri = storageManager.getUriForFile(outFile)
            withContext(Dispatchers.Main) {
                isExporting.value = false
                refreshStorageUsage()
                onReady(uri, outFile)
            }
        }
    }
    fun exportMarkdown(onReady: (uri: Uri, file: File) -> Unit) {
        val doc = currentDocument.value ?: return
        val pages = currentPages.value
        isExporting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val sanitizedName = doc.title.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            val outFile = storageManager.getExportFile("${sanitizedName}.md")
            textExporter.exportToMarkdown(doc, pages, emptyList(), outFile)
            val uri = storageManager.getUriForFile(outFile)
            withContext(Dispatchers.Main) {
                isExporting.value = false
                refreshStorageUsage()
                onReady(uri, outFile)
            }
        }
    }
    fun exportPlainText(onReady: (uri: Uri, file: File) -> Unit) {
        val doc = currentDocument.value ?: return
        val pages = currentPages.value
        isExporting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val sanitizedName = doc.title.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            val outFile = storageManager.getExportFile("${sanitizedName}.txt")
            textExporter.exportToPlainText(pages, outFile)
            val uri = storageManager.getUriForFile(outFile)
            withContext(Dispatchers.Main) {
                isExporting.value = false
                refreshStorageUsage()
                onReady(uri, outFile)
            }
        }
    }
    fun exportReceiptCsv(receipt: StructuredReceipt, onReady: (uri: Uri, file: File) -> Unit) {
        val doc = currentDocument.value ?: return
        isExporting.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val sanitizedName = doc.title.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            val outFile = storageManager.getExportFile("${sanitizedName}.csv")
            textExporter.exportReceiptToCsv(receipt, outFile)
            val uri = storageManager.getUriForFile(outFile)
            withContext(Dispatchers.Main) {
                isExporting.value = false
                refreshStorageUsage()
                onReady(uri, outFile)
            }
        }
    }
    fun saveNote(
        id: String? = null,
        title: String,
        content: String,
        isChecklist: Boolean = false,
        checklistItems: List<ChecklistItem> = emptyList(),
        tags: List<String> = emptyList(),
        documentId: String? = null
    ) {
        if (title.isBlank() && content.isBlank()) return
        val note = DocumentNote(
            id = id ?: UUID.randomUUID().toString(),
            documentId = documentId,
            title = if (title.isBlank()) "Untitled Note" else title,
            content = content,
            isChecklist = isChecklist,
            checklistItems = checklistItems,
            tags = tags
        )
        viewModelScope.launch {
            repository.insertNote(note)
        }
    }
    fun deleteNote(id: String) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }
    fun refreshStorageUsage() {
        viewModelScope.launch {
            storageBytes.value = storageManager.calculateStorageUsage()
        }
    }
    fun clearCache() {
        viewModelScope.launch {
            storageManager.clearExports()
            refreshStorageUsage()
        }
    }
}
