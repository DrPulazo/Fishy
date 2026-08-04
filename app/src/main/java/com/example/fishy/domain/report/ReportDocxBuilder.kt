package com.example.fishy.domain.report

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal .docx (OOXML) builder — no Apache POI.
 * Times New Roman 12pt; whole line bold when it contains tonnage / expense markers.
 */
object ReportDocxBuilder {

    private val fileDateFmt = SimpleDateFormat("ddMMyyyy", Locale.US)

    fun reportDocxFileName(epochMillis: Long): String =
        "Отчёт ${fileDateFmt.format(Date(epochMillis))}.docx"

    fun build(text: String): ByteArray {
        val documentXml = buildDocumentXml(text)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putUtf8("[Content_Types].xml", CONTENT_TYPES)
            zip.putUtf8("_rels/.rels", ROOT_RELS)
            zip.putUtf8("word/document.xml", documentXml)
            zip.putUtf8("word/_rels/document.xml.rels", DOCUMENT_RELS)
            zip.putUtf8("word/styles.xml", STYLES)
        }
        return out.toByteArray()
    }

    fun shouldBoldLine(line: String): Boolean {
        val lower = line.lowercase(Locale.getDefault())
        return lower.contains("общий тоннаж:") ||
            lower.contains("общий тоннаж;") ||
            lower.contains("итого расход")
    }

    private fun buildDocumentXml(text: String): String {
        // Drop trailing blank lines so the last page is not an empty sheet.
        val lines = text.trimEnd('\n', '\r').split('\n')
        val body = buildString {
            for (line in lines) {
                append(paragraphXml(line, shouldBoldLine(line)))
            }
            if (lines.isEmpty()) {
                append(paragraphXml("", bold = false))
            }
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
$body
    <w:sectPr>
      <w:pgSz w:w="11906" w:h="16838"/>
      <w:pgMar w:top="1134" w:right="850" w:bottom="1134" w:left="1701" w:header="708" w:footer="708" w:gutter="0"/>
      <w:cols w:space="708"/>
      <w:docGrid w:linePitch="360"/>
    </w:sectPr>
  </w:body>
</w:document>
"""
    }

    private fun paragraphXml(line: String, bold: Boolean): String {
        val boldXml = if (bold) "<w:b/><w:bCs/>" else ""
        return """
    <w:p>
      <w:pPr>
        <w:spacing w:line="360" w:lineRule="auto"/>
      </w:pPr>
      <w:r>
        <w:rPr>
          <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:cs="Times New Roman"/>
          <w:sz w:val="24"/>
          <w:szCs w:val="24"/>
          $boldXml
        </w:rPr>
        <w:t xml:space="preserve">${escapeXml(line)}</w:t>
      </w:r>
    </w:p>
"""
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    private fun ZipOutputStream.putUtf8(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private const val CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>
"""

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>
"""

    private const val DOCUMENT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>
"""

    /** Defaults help mail/preview apps that ignore per-paragraph spacing. */
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:cs="Times New Roman"/>
        <w:sz w:val="24"/>
        <w:szCs w:val="24"/>
      </w:rPr>
    </w:rPrDefault>
    <w:pPrDefault>
      <w:pPr>
        <w:spacing w:line="360" w:lineRule="auto"/>
      </w:pPr>
    </w:pPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:qFormat/>
    <w:pPr>
      <w:spacing w:line="360" w:lineRule="auto"/>
    </w:pPr>
    <w:rPr>
      <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:cs="Times New Roman"/>
      <w:sz w:val="24"/>
      <w:szCs w:val="24"/>
    </w:rPr>
  </w:style>
</w:styles>
"""
}
