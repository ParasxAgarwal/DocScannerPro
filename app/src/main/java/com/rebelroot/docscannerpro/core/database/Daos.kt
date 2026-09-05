package com.rebelroot.docscannerpro.core.database
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE isVaultLocked = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getPublicDocuments(): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM documents WHERE isVaultLocked = 1 ORDER BY isPinned DESC, updatedAt DESC")
    fun getVaultDocuments(): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: String): DocumentEntity?
    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeDocumentById(id: String): Flow<DocumentEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)
    @Update
    suspend fun updateDocument(document: DocumentEntity)
    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)
    @Query("UPDATE documents SET isPinned = :isPinned WHERE id = :id")
    suspend fun setPinned(id: String, isPinned: Boolean)
    @Query("UPDATE documents SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)
    @Query("UPDATE documents SET isVaultLocked = :isVaultLocked WHERE id = :id")
    suspend fun setVaultLocked(id: String, isVaultLocked: Boolean)
    @Query("UPDATE documents SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, updatedAt: Long = System.currentTimeMillis())
    @Query("SELECT * FROM documents WHERE title LIKE '%' || :query || '%' OR tagsJson LIKE '%' || :query || '%'")
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>
}
@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    fun getPagesForDocument(documentId: String): Flow<List<PageEntity>>
    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getPagesListForDocument(documentId: String): List<PageEntity>
    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun getPageById(id: String): PageEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<PageEntity>)
    @Update
    suspend fun updatePage(page: PageEntity)
    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePage(id: String)
    @Query("DELETE FROM pages WHERE documentId = :documentId")
    suspend fun deletePagesForDocument(documentId: String)
    @Query("SELECT * FROM pages WHERE ocrText LIKE '%' || :query || '%'")
    suspend fun searchPagesByOcr(query: String): List<PageEntity>
}
@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes WHERE documentId = :documentId ORDER BY updatedAt DESC")
    fun getNotesForDocument(documentId: String): Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: String): NoteEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)
    @Update
    suspend fun updateNote(note: NoteEntity)
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: String)
    @Query("SELECT * FROM notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%'")
    fun searchNotes(query: String): Flow<List<NoteEntity>>
}
@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)
    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: String)
}
