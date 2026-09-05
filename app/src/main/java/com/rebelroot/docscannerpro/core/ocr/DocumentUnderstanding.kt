package com.rebelroot.docscannerpro.core.ocr
import com.rebelroot.docscannerpro.core.model.DocumentType
import com.rebelroot.docscannerpro.core.model.StructuredContact
import com.rebelroot.docscannerpro.core.model.StructuredId
import com.rebelroot.docscannerpro.core.model.StructuredReceipt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
object DocumentUnderstanding {
    private val EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val PHONE_PATTERN = Pattern.compile("(\\+?[0-9]{1,3}[-.\\s]?)?\\(?([0-9]{3})\\)?[-.\\s]?([0-9]{3})[-.\\s]?([0-9]{4})")
    private val URL_PATTERN = Pattern.compile("(https?://)?(www\\.)?[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/[a-zA-Z0-9._~:/?#\\[\\]@!$&'()*+,;=-]*)?")
    private val TOTAL_PATTERN = Pattern.compile("(?i)(?:total|amount|subtotal|balance|due)[^0-9$€£₹]*([$€£₹]?\\s*[0-9]+[.,][0-9]{2})")
    private val DATE_PATTERN = Pattern.compile("\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}|[A-Za-z]{3,9}\\s+\\d{1,2},?\\s+\\d{4})\\b")
    fun extractReceipt(ocrText: String): StructuredReceipt {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val merchant = lines.firstOrNull { it.length > 2 && !it.contains("$") && !it.contains("Total", true) }
        var total: String? = null
        var date: String? = null
        var tax: String? = null
        val items = mutableListOf<String>()
        for (line in lines) {
            val totalMatch = TOTAL_PATTERN.matcher(line)
            if (totalMatch.find()) {
                total = totalMatch.group(1)?.trim()
            }
            if (date == null) {
                val dateMatch = DATE_PATTERN.matcher(line)
                if (dateMatch.find()) {
                    date = dateMatch.group(1)?.trim()
                }
            }
            if (line.contains("tax", ignoreCase = true) && tax == null) {
                val numMatch = Pattern.compile("[$€£₹]?\\s*[0-9]+[.,][0-9]{2}").matcher(line)
                if (numMatch.find()) {
                    tax = numMatch.group(0)?.trim()
                }
            }
            if (line.contains("$") || line.contains("€") || line.contains("£") || line.contains("₹")) {
                items.add(line)
            }
        }
        return StructuredReceipt(
            merchant = merchant,
            date = date,
            total = total,
            tax = tax,
            items = items.take(10)
        )
    }
    fun extractContact(ocrText: String): StructuredContact {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        var email: String? = null
        var phone: String? = null
        var website: String? = null
        val emailMatch = EMAIL_PATTERN.matcher(ocrText)
        if (emailMatch.find()) email = emailMatch.group(0)
        val phoneMatch = PHONE_PATTERN.matcher(ocrText)
        if (phoneMatch.find()) phone = phoneMatch.group(0)
        val urlMatch = URL_PATTERN.matcher(ocrText)
        while (urlMatch.find()) {
            val candidate = urlMatch.group(0)
            if (candidate != null && !candidate.contains("@")) {
                website = candidate
                break
            }
        }
        val name = lines.firstOrNull { line ->
            line != email && line != phone && line != website && line.length in 3..35 && !line.any { it.isDigit() }
        }
        val company = lines.drop(1).firstOrNull { line ->
            line != name && line != email && line != phone && line != website && line.length in 3..40
        }
        return StructuredContact(
            name = name,
            company = company,
            phone = phone,
            email = email,
            website = website
        )
    }
    fun extractId(ocrText: String): StructuredId {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val idRegex = Pattern.compile("(?i)(?:id|no|licence|license|dl|card)[^a-zA-Z0-9]*([A-Z0-9]{5,15})")
        var idNumber: String? = null
        for (line in lines) {
            val m = idRegex.matcher(line)
            if (m.find()) {
                idNumber = m.group(1)
                break
            }
        }
        val nameCandidate = lines.firstOrNull { it.length in 4..30 && !it.any { c -> c.isDigit() } }
        return StructuredId(
            idNumber = idNumber,
            fullName = nameCandidate
        )
    }
    fun suggestFilename(type: DocumentType, ocrText: String? = null): String {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val sanitized = when (type) {
            DocumentType.RECEIPT -> {
                val receipt = if (!ocrText.isNullOrBlank()) extractReceipt(ocrText) else null
                val merchant = receipt?.merchant?.replace(Regex("[^a-zA-Z0-9]"), "")?.take(12) ?: "Store"
                val amount = receipt?.total?.replace(Regex("[^0-9.]"), "") ?: ""
                if (amount.isNotBlank()) "Receipt_${merchant}_${amount}" else "Receipt_${merchant}"
            }
            DocumentType.BOOK -> "Book_Scan"
            DocumentType.BUSINESS_CARD -> {
                val contact = if (!ocrText.isNullOrBlank()) extractContact(ocrText) else null
                val name = contact?.name?.replace(Regex("[^a-zA-Z0-9]"), "")?.take(15) ?: "Contact"
                "Card_${name}"
            }
            DocumentType.ID_CARD -> "ID_Card"
            DocumentType.NOTE -> "Note"
            DocumentType.WHITEBOARD -> "Whiteboard"
            DocumentType.QR_BARCODE -> "Code"
            DocumentType.DOCUMENT -> {
                val firstLine = ocrText?.lines()?.firstOrNull { it.trim().isNotBlank() }
                    ?.replace(Regex("[^a-zA-Z0-9]"), "")?.take(15)
                if (!firstLine.isNullOrBlank()) "Doc_${firstLine}" else "Document"
            }
        }
        return "${dateStr}_${sanitized}.pdf"
    }
}
