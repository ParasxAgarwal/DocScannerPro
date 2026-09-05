package com.rebelroot.docscannerpro.core.export

import com.rebelroot.docscannerpro.core.model.DocumentPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxExporter {
    suspend fun exportToDocx(
        title: String,
        pages: List<DocumentPage>,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        val contentTypesXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
        """.trimIndent()
        val relsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent()
        val documentRelsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships" />
        """.trimIndent()

        val docBuilder = StringBuilder()
        docBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        docBuilder.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">")
        docBuilder.append("<w:body>")
        docBuilder.append("<w:p>")
        docBuilder.append("<w:pPr><w:jc w:val=\"center\"/></w:pPr>")
        docBuilder.append("<w:r><w:rPr><w:b/><w:sz w:val=\"40\"/><w:color w:val=\"0F172A\"/></w:rPr>")
        docBuilder.append("<w:t>${escapeXml(title)}</w:t>")
        docBuilder.append("</w:r></w:p>")
        docBuilder.append("<w:p/>")

        for ((index, page) in pages.withIndex()) {
            if (pages.size > 1) {
                docBuilder.append("<w:p>")
                docBuilder.append("<w:r><w:rPr><w:b/><w:sz w:val=\"26\"/><w:color w:val=\"0284C7\"/></w:rPr>")
                docBuilder.append("<w:t>Page ${index + 1}</w:t>")
                docBuilder.append("</w:r></w:p>")
            }

            val text = page.ocrText.orEmpty()
            val paragraphs = text.split("\n\n").filter(String::isNotBlank)
            if (paragraphs.isEmpty()) {
                text.lineSequence()
                    .filter(String::isNotBlank)
                    .forEach { line ->
                        appendParagraph(docBuilder, line)
                    }
            } else {
                paragraphs.forEach { paragraph ->
                    appendParagraph(docBuilder, paragraph.replace("\n", " "))
                }
            }

            if (index < pages.lastIndex) {
                docBuilder.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
            }
        }

        docBuilder.append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/></w:sectPr>")
        docBuilder.append("</w:body></w:document>")

        outputFile.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", contentTypesXml)
            writeZipEntry(zip, "_rels/.rels", relsXml)
            writeZipEntry(zip, "word/document.xml", docBuilder.toString())
            writeZipEntry(zip, "word/_rels/document.xml.rels", documentRelsXml)
        }
        outputFile
    }

    private fun appendParagraph(builder: StringBuilder, text: String) {
        builder.append("<w:p><w:r><w:rPr><w:sz w:val=\"22\"/></w:rPr>")
        builder.append("<w:t>${escapeXml(text)}</w:t>")
        builder.append("</w:r></w:p>")
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
