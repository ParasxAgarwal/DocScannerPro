package com.rebelroot.docscannerpro.core.export
import com.rebelroot.docscannerpro.core.model.Document
import com.rebelroot.docscannerpro.core.model.DocumentNote
import com.rebelroot.docscannerpro.core.model.DocumentPage
import com.rebelroot.docscannerpro.core.model.StructuredReceipt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
class TextExporter {
    suspend fun exportToMarkdown(
        document: Document,
        pages: List<DocumentPage>,
        notes: List<DocumentNote>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.append("# ${document.title}\n\n")
        sb.append("**Type:** ${document.type}  \n")
        sb.append("**Pages:** ${pages.size}  \n")
        sb.append("**Date:** ${java.util.Date(document.updatedAt)}  \n\n")
        if (notes.isNotEmpty()) {
            sb.append("## Notes\n\n")
            for (n in notes) {
                sb.append("### ${n.title}\n")
                sb.append("${n.content}\n\n")
                if (n.isChecklist && n.checklistItems.isNotEmpty()) {
                    for (item in n.checklistItems) {
                        val mark = if (item.isChecked) "[x]" else "[ ]"
                        sb.append("- $mark ${item.text}\n")
                    }
                    sb.append("\n")
                }
            }
        }
        sb.append("## Scanned Content\n\n")
        for ((i, page) in pages.withIndex()) {
            sb.append("### Page ${i + 1}\n\n")
            sb.append(page.ocrText ?: "*(No text recognized)*")
            sb.append("\n\n---\n\n")
        }
        FileOutputStream(outputFile).use { out ->
            out.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
        }
        outputFile
    }
    suspend fun exportToPlainText(
        pages: List<DocumentPage>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        for ((i, page) in pages.withIndex()) {
            if (pages.size > 1) {
                sb.append("--- PAGE ${i + 1} ---\n\n")
            }
            sb.append(page.ocrText ?: "")
            sb.append("\n\n")
        }
        FileOutputStream(outputFile).use { out ->
            out.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
        }
        outputFile
    }
    suspend fun exportReceiptToCsv(
        receipt: StructuredReceipt,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.append("Field,Value\n")
        sb.append("Merchant,\"${receipt.merchant ?: ""}\"\n")
        sb.append("Date,\"${receipt.date ?: ""}\"\n")
        sb.append("Subtotal,\"${receipt.subtotal ?: ""}\"\n")
        sb.append("Tax,\"${receipt.tax ?: ""}\"\n")
        sb.append("Total,\"${receipt.total ?: ""}\"\n")
        if (receipt.items.isNotEmpty()) {
            sb.append("\nLine Items\n")
            for (item in receipt.items) {
                sb.append("\"${item.replace("\"", "\"\"")}\"\n")
            }
        }
        FileOutputStream(outputFile).use { out ->
            out.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
        }
        outputFile
    }
    suspend fun exportToJson(
        document: Document,
        pages: List<DocumentPage>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("id", document.id)
        root.put("title", document.title)
        root.put("type", document.type.name)
        root.put("createdAt", document.createdAt)
        root.put("updatedAt", document.updatedAt)
        val pagesArr = JSONArray()
        for (p in pages) {
            val pageObj = JSONObject()
            pageObj.put("pageIndex", p.pageIndex)
            pageObj.put("filter", p.filterType.name)
            pageObj.put("ocrText", p.ocrText ?: "")
            pageObj.put("ocrConfidence", p.ocrConfidence.toDouble())
            if (!p.structuredJson.isNullOrBlank()) {
                pageObj.put("structuredData", JSONObject(p.structuredJson))
            }
            pagesArr.put(pageObj)
        }
        root.put("pages", pagesArr)
        FileOutputStream(outputFile).use { out ->
            out.write(root.toString(2).toByteArray(StandardCharsets.UTF_8))
        }
        outputFile
    }
}
