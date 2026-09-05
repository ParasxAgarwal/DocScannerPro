package com.rebelroot.docscannerpro.core.database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.rebelroot.docscannerpro.core.model.ChecklistItem
import com.rebelroot.docscannerpro.core.model.DocumentType
import com.rebelroot.docscannerpro.core.model.FilterType
import org.json.JSONArray
import org.json.JSONObject
@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String = DocumentType.DOCUMENT.name,
    val folderId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isVaultLocked: Boolean = false,
    val tagsJson: String = "[]",
    val pageCount: Int = 0,
    val thumbnailPath: String? = null
)
@Entity(tableName = "pages")
data class PageEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int,
    val originalPath: String,
    val processedPath: String,
    val thumbnailPath: String,
    val rotation: Int = 0,
    val filterType: String = FilterType.AUTO_ENHANCE.name,
    val ocrText: String? = null,
    val ocrConfidence: Float = 0f,
    val width: Int = 0,
    val height: Int = 0,
    val structuredJson: String? = null
)
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val documentId: String? = null,
    val title: String,
    val content: String,
    val isChecklist: Boolean = false,
    val checklistJson: String = "[]",
    val isPinned: Boolean = false,
    val tagsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: String = "#0284C7",
    val iconName: String = "folder",
    val createdAt: Long = System.currentTimeMillis()
)
class DatabaseConverters {
    @TypeConverter
    fun fromStringList(list: List<String>?): String {
        if (list.isNullOrEmpty()) return "[]"
        val array = JSONArray()
        list.forEach { array.put(it) }
        return array.toString()
    }
    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(value)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
    @TypeConverter
    fun fromChecklist(list: List<ChecklistItem>?): String {
        if (list.isNullOrEmpty()) return "[]"
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("text", item.text)
                put("isChecked", item.isChecked)
            }
            array.put(obj)
        }
        return array.toString()
    }
    @TypeConverter
    fun toChecklist(value: String?): List<ChecklistItem> {
        if (value.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(value)
            val list = mutableListOf<ChecklistItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ChecklistItem(
                        id = obj.optString("id", i.toString()),
                        text = obj.optString("text", ""),
                        isChecked = obj.optBoolean("isChecked", false)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
