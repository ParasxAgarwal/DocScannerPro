package com.rebelroot.docscannerpro.core.model
import android.graphics.PointF
import java.io.Serializable
enum class DocumentType {
    DOCUMENT,
    RECEIPT,
    BUSINESS_CARD,
    ID_CARD,
    NOTE,
    WHITEBOARD,
    BOOK,
    QR_BARCODE
}
enum class FilterType {
    ORIGINAL,
    AUTO_ENHANCE,
    DOCUMENT_BW,
    GRAYSCALE,
    COLOR_BOOST,
    RECEIPT_CONTRAST
}
data class QuadCorners(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF
) : Serializable {
    fun toFloatArray(): FloatArray {
        return floatArrayOf(
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomRight.x, bottomRight.y,
            bottomLeft.x, bottomLeft.y
        )
    }
    companion object {
        fun defaultQuad(width: Float, height: Float, insetRatio: Float = 0.08f): QuadCorners {
            val insetX = width * insetRatio
            val insetY = height * insetRatio
            return QuadCorners(
                topLeft = PointF(insetX, insetY),
                topRight = PointF(width - insetX, insetY),
                bottomRight = PointF(width - insetX, height - insetY),
                bottomLeft = PointF(insetX, height - insetY)
            )
        }
    }
}
data class Document(
    val id: String,
    val title: String,
    val type: DocumentType = DocumentType.DOCUMENT,
    val folderId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isVaultLocked: Boolean = false,
    val tags: List<String> = emptyList(),
    val pageCount: Int = 0,
    val thumbnailPath: String? = null
)
data class DocumentPage(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    val originalPath: String,
    val processedPath: String,
    val thumbnailPath: String,
    val rotation: Int = 0,
    val filterType: FilterType = FilterType.AUTO_ENHANCE,
    val ocrText: String? = null,
    val ocrConfidence: Float = 0f,
    val width: Int = 0,
    val height: Int = 0,
    val structuredJson: String? = null
)
data class DocumentNote(
    val id: String,
    val documentId: String? = null,
    val title: String,
    val content: String,
    val isChecklist: Boolean = false,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val isPinned: Boolean = false,
    val tags: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
data class ChecklistItem(
    val id: String,
    val text: String,
    val isChecked: Boolean = false
)
data class Folder(
    val id: String,
    val name: String,
    val colorHex: String = "#0284C7",
    val iconName: String = "folder",
    val createdAt: Long = System.currentTimeMillis()
)
data class OcrResult(
    val fullText: String,
    val blocks: List<OcrBlock> = emptyList(),
    val confidence: Float = 1.0f,
    val language: String = "en"
)
data class OcrBlock(
    val text: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val lines: List<OcrLine> = emptyList()
)
data class OcrLine(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)
data class StructuredReceipt(
    val merchant: String? = null,
    val date: String? = null,
    val total: String? = null,
    val subtotal: String? = null,
    val tax: String? = null,
    val currency: String = "$",
    val items: List<String> = emptyList()
)
data class StructuredContact(
    val name: String? = null,
    val company: String? = null,
    val jobTitle: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val website: String? = null,
    val address: String? = null
)
data class StructuredId(
    val cardType: String = "ID Card",
    val idNumber: String? = null,
    val fullName: String? = null,
    val dateOfBirth: String? = null,
    val expiryDate: String? = null
)
enum class BarcodeType {
    URL, WIFI, CONTACT, EMAIL, PHONE, TEXT
}
data class BarcodeItem(
    val rawValue: String,
    val displayValue: String,
    val formatName: String,
    val type: BarcodeType
)
data class AnnotationItem(
    val type: String,
    val colorHex: String,
    val strokeWidth: Float,
    val pointsJson: String? = null,
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val text: String? = null
)
