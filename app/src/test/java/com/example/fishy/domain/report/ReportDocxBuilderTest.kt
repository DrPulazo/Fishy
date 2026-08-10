package com.example.fishy.domain.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ReportDocxBuilderTest {

    @Test
    fun fileNameUsesDdMmYyyy() {
        // 2026-08-04 00:00 UTC+0 → still 04082026 in US locale formatter on fixed millis
        val millis = java.util.GregorianCalendar(2026, java.util.Calendar.AUGUST, 4).timeInMillis
        assertEquals("Отчёт 04082026.docx", ReportDocxBuilder.reportDocxFileName(millis))
    }

    @Test
    fun shouldBoldTonnageAndExpenseLines() {
        assertTrue(ReportDocxBuilder.shouldBoldLine("Общий тоннаж: 50 кг"))
        assertTrue(ReportDocxBuilder.shouldBoldLine("общий тоннаж; 10"))
        assertTrue(ReportDocxBuilder.shouldBoldLine("Итого расход 1200"))
        assertTrue(ReportDocxBuilder.shouldBoldLine("заметки: Итого расход топлива"))
        assertTrue(ReportDocxBuilder.shouldBoldLine("Общий тоннаж за период: 52 000 кг"))
        assertFalse(ReportDocxBuilder.shouldBoldLine("Общий тоннаж за август 2026: 38 000 кг"))
        assertFalse(ReportDocxBuilder.shouldBoldLine("Фактический тоннаж брутто"))
        assertFalse(ReportDocxBuilder.shouldBoldLine("Обычная строка"))
    }

    @Test
    fun zipContainsRequiredPartsAndFormatting() {
        val text = """
            04.08.2026
            Контейнер

            Общий тоннаж: 50 кг

            Итого расход 100

            Сгенерировано приложением «Фишка».
        """.trimIndent()

        val bytes = ReportDocxBuilder.build(text)
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("_rels/.rels"))
        assertTrue(entries.containsKey("word/document.xml"))
        assertTrue(entries.containsKey("word/_rels/document.xml.rels"))
        assertTrue(entries.containsKey("word/styles.xml"))

        val styles = entries.getValue("word/styles.xml")
        assertTrue(styles.contains("""w:spacing w:line="360" w:lineRule="auto""""))
        assertTrue(styles.contains("Times New Roman"))
        assertTrue(entries.getValue("word/_rels/document.xml.rels").contains("styles.xml"))
        assertTrue(entries.getValue("[Content_Types].xml").contains("/word/styles.xml"))

        val doc = entries.getValue("word/document.xml")
        assertTrue(doc.contains("Times New Roman"))
        assertTrue(doc.contains("""w:sz w:val="24""""))
        assertTrue(doc.contains("""w:spacing w:line="360" w:lineRule="auto""""))
        assertTrue(doc.contains("Общий тоннаж: 50 кг"))
        assertTrue(doc.contains("Итого расход 100"))

        // No trailing empty paragraph after last content line
        assertFalse(doc.trim().endsWith("""<w:t xml:space="preserve"></w:t>"""))
        val lastText = Regex("""<w:t[^>]*>([^<]*)</w:t>""").findAll(doc).last().groupValues[1]
        assertTrue(lastText.isNotEmpty())

        // Bold appears near tonnage and expense paragraphs
        assertTrue(doc.contains("<w:b/>"))
        val tonnageIdx = doc.indexOf("Общий тоннаж: 50 кг")
        val expenseIdx = doc.indexOf("Итого расход 100")
        val normalIdx = doc.indexOf("Контейнер")
        assertTrue(tonnageIdx > 0 && expenseIdx > 0 && normalIdx > 0)

        fun boldBefore(textIdx: Int): Boolean {
            val window = doc.substring((textIdx - 200).coerceAtLeast(0), textIdx)
            return window.contains("<w:b/>")
        }
        assertTrue(boldBefore(tonnageIdx))
        assertTrue(boldBefore(expenseIdx))
        assertFalse(boldBefore(normalIdx))
    }

    @Test
    fun trimsTrailingBlankLines() {
        val doc = documentXmlOf("Строка\n\n")
        val texts = Regex("""<w:t[^>]*>(.*?)</w:t>""").findAll(doc).map { it.groupValues[1] }.toList()
        assertEquals(listOf("Строка"), texts)
    }

    @Test
    fun escapesXmlSpecialChars() {
        val doc = documentXmlOf("A & B <C> \"D\"")
        assertTrue(doc.contains("A &amp; B &lt;C&gt; &quot;D&quot;"))
    }

    private fun documentXmlOf(text: String): String {
        val bytes = ReportDocxBuilder.build(text)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        error("document.xml missing")
    }
}
