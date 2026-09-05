package com.rebelroot.docscannerpro.core.database
import com.rebelroot.docscannerpro.core.model.ChecklistItem
import com.rebelroot.docscannerpro.core.model.Document
import com.rebelroot.docscannerpro.core.model.DocumentNote
import com.rebelroot.docscannerpro.core.model.DocumentPage
import com.rebelroot.docscannerpro.core.model.DocumentType
import com.rebelroot.docscannerpro.core.model.FilterType
import com.rebelroot.docscannerpro.core.model.Folder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
class DocumentRepository(
    private val database: AppDatabase
) {
    private val documentDao = database.documentDao()
    private val pageDao = database.pageDao()
    private val noteDao = database.noteDao()
    private val folderDao = database.folderDao()
    private val converters = DatabaseConverters()
    fun getPublicDocuments(): Flow<List<Document>> {
        return documentDao.getPublicDocuments().map { entities ->
            entities.map { it.toModel() }
        }.flowOn(Dispatchers.IO)
    }
    fun getVaultDocuments(): Flow<List<Document>> {
        return documentDao.getVaultDocuments().map { entities ->
            entities.map { it.toModel() }
        }.flowOn(Dispatchers.IO)
    }
    fun observeDocument(id: String): Flow<Document?> {
        return documentDao.observeDocumentById(id).map { it?.toModel() }.flowOn(Dispatchers.IO)
    }
    suspend fun getDocument(id: String): Document? = withContext(Dispatchers.IO) {
        documentDao.getDocumentById(id)?.toModel()
    }
    suspend fun insertDocument(doc: Document) = withContext(Dispatchers.IO) {
        documentDao.insertDocument(doc.toEntity())
    }
    suspend fun updateDocumentTitle(id: String, title: String) = withContext(Dispatchers.IO) {
        documentDao.updateTitle(id, title)
    }
    suspend fun togglePinned(id: String, current: Boolean) = withContext(Dispatchers.IO) {
        documentDao.setPinned(id, !current)
    }
    suspend fun toggleFavorite(id: String, current: Boolean) = withContext(Dispatchers.IO) {
        documentDao.setFavorite(id, !current)
    }
    suspend fun toggleVault(id: String, current: Boolean) = withContext(Dispatchers.IO) {
        documentDao.setVaultLocked(id, !current)
    }
    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        pageDao.deletePagesForDocument(id)
        documentDao.deleteDocument(id)
    }
    fun getPages(documentId: String): Flow<List<DocumentPage>> {
        return pageDao.getPagesForDocument(documentId).map { entities ->
            entities.map { it.toModel() }
        }.flowOn(Dispatchers.IO)
    }
    suspend fun getPagesList(documentId: String): List<DocumentPage> = withContext(Dispatchers.IO) {
        pageDao.getPagesListForDocument(documentId).map { it.toModel() }
    }
    suspend fun insertPages(pages: List<DocumentPage>) = withContext(Dispatchers.IO) {
        pageDao.insertPages(pages.map { it.toEntity() })
        if (pages.isNotEmpty()) {
            val docId = pages.first().documentId
            val allPages = pageDao.getPagesListForDocument(docId)
            val doc = documentDao.getDocumentById(docId)
            if (doc != null) {
                documentDao.insertDocument(
                    doc.copy(
                        pageCount = allPages.size,
                        thumbnailPath = allPages.firstOrNull()?.thumbnailPath ?: doc.thumbnailPath,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
    suspend fun updatePage(page: DocumentPage) = withContext(Dispatchers.IO) {
        pageDao.updatePage(page.toEntity())
        val doc = documentDao.getDocumentById(page.documentId)
        if (doc != null) {
            val allPages = pageDao.getPagesListForDocument(page.documentId)
            documentDao.insertDocument(
                doc.copy(
                    thumbnailPath = allPages.firstOrNull()?.thumbnailPath ?: doc.thumbnailPath,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }
    suspend fun deletePage(pageId: String, documentId: String) = withContext(Dispatchers.IO) {
        pageDao.deletePage(pageId)
        val remaining = pageDao.getPagesListForDocument(documentId)
        val doc = documentDao.getDocumentById(documentId)
        if (doc != null) {
            if (remaining.isEmpty()) {
                documentDao.deleteDocument(documentId)
            } else {
                val reindexed = remaining.mapIndexed { index, p -> p.copy(pageIndex = index) }
                pageDao.insertPages(reindexed)
                documentDao.insertDocument(
                    doc.copy(
                        pageCount = reindexed.size,
                        thumbnailPath = reindexed.first().thumbnailPath,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
    fun getAllNotes(): Flow<List<DocumentNote>> {
        return noteDao.getAllNotes().map { entities ->
            entities.map { it.toModel() }
        }.flowOn(Dispatchers.IO)
    }
    suspend fun insertNote(note: DocumentNote) = withContext(Dispatchers.IO) {
        noteDao.insertNote(note.toEntity())
    }
    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        noteDao.deleteNote(id)
    }
    fun getAllFolders(): Flow<List<Folder>> {
        return folderDao.getAllFolders().map { entities ->
            entities.map { it.toModel() }
        }.flowOn(Dispatchers.IO)
    }
    suspend fun insertFolder(folder: Folder) = withContext(Dispatchers.IO) {
        folderDao.insertFolder(
            FolderEntity(
                id = folder.id,
                name = folder.name,
                colorHex = folder.colorHex,
                iconName = folder.iconName,
                createdAt = folder.createdAt
            )
        )
    }
    suspend fun searchDocumentsAndPages(query: String): List<Document> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val pagesMatching = pageDao.searchPagesByOcr(query)
        val docIdsFromOcr = pagesMatching.map { it.documentId }.toSet()
        val allDocs = mutableMapOf<String, DocumentEntity>()
        docIdsFromOcr.forEach { docId ->
            documentDao.getDocumentById(docId)?.let { allDocs[it.id] = it }
        }
        return@withContext allDocs.values.map { it.toModel() }
    }
    private fun DocumentEntity.toModel(): Document {
        return Document(
            id = id,
            title = title,
            type = try { DocumentType.valueOf(type) } catch (_: Exception) { DocumentType.DOCUMENT },
            folderId = folderId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isPinned = isPinned,
            isFavorite = isFavorite,
            isVaultLocked = isVaultLocked,
            tags = converters.toStringList(tagsJson),
            pageCount = pageCount,
            thumbnailPath = thumbnailPath
        )
    }
    private fun Document.toEntity(): DocumentEntity {
        return DocumentEntity(
            id = id,
            title = title,
            type = type.name,
            folderId = folderId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isPinned = isPinned,
            isFavorite = isFavorite,
            isVaultLocked = isVaultLocked,
            tagsJson = converters.fromStringList(tags),
            pageCount = pageCount,
            thumbnailPath = thumbnailPath
        )
    }
    private fun PageEntity.toModel(): DocumentPage {
        return DocumentPage(
            id = id,
            documentId = documentId,
            pageIndex = pageIndex,
            originalPath = originalPath,
            processedPath = processedPath,
            thumbnailPath = thumbnailPath,
            rotation = rotation,
            filterType = try { FilterType.valueOf(filterType) } catch (_: Exception) { FilterType.AUTO_ENHANCE },
            ocrText = ocrText,
            ocrConfidence = ocrConfidence,
            width = width,
            height = height,
            structuredJson = structuredJson
        )
    }
    private fun DocumentPage.toEntity(): PageEntity {
        return PageEntity(
            id = id,
            documentId = documentId,
            pageIndex = pageIndex,
            originalPath = originalPath,
            processedPath = processedPath,
            thumbnailPath = thumbnailPath,
            rotation = rotation,
            filterType = filterType.name,
            ocrText = ocrText,
            ocrConfidence = ocrConfidence,
            width = width,
            height = height,
            structuredJson = structuredJson
        )
    }
    private fun NoteEntity.toModel(): DocumentNote {
        return DocumentNote(
            id = id,
            documentId = documentId,
            title = title,
            content = content,
            isChecklist = isChecklist,
            checklistItems = converters.toChecklist(checklistJson),
            isPinned = isPinned,
            tags = converters.toStringList(tagsJson),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    private fun DocumentNote.toEntity(): NoteEntity {
        return NoteEntity(
            id = id,
            documentId = documentId,
            title = title,
            content = content,
            isChecklist = isChecklist,
            checklistJson = converters.fromChecklist(checklistItems),
            isPinned = isPinned,
            tagsJson = converters.fromStringList(tags),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    private fun FolderEntity.toModel(): Folder {
        return Folder(
            id = id,
            name = name,
            colorHex = colorHex,
            iconName = iconName,
            createdAt = createdAt
        )
    }
}
