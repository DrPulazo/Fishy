package com.example.fishy.domain.stats

import com.example.fishy.data.local.entity.ShipmentEntity
import com.example.fishy.domain.format.QuantityFormatters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Plain-text statistics export for the selected period.
 * Lists every product (no «Прочее» bucket); chart legend may still use Other.
 */
object StatisticsExportGenerator {

    private const val EN_DASH = '–'

    fun generate(
        entities: List<ShipmentEntity>,
        fromMonthStart: Long,
        toMonthStart: Long,
        portFilter: String = "",
        productFilter: String = "",
        thousandsSeparator: Boolean = true,
        generatedAtMillis: Long = System.currentTimeMillis(),
        locale: Locale = Locale("ru", "RU")
    ): String {
        val from = minOf(fromMonthStart, toMonthStart)
        val to = maxOf(fromMonthStart, toMonthStart)
        val monthStarts = StatisticsAggregator.monthsInclusivePublic(from, to).toSet()
        val contributions = entities
            .flatMap { StatisticsBreakdown.extractContributions(it) }
            .let { StatisticsBreakdown.filterContributionsByPort(it, portFilter) }
            .let { StatisticsBreakdown.filterContributionsByProduct(it, productFilter) }
            .filter { it.monthStartMillis in monthStarts }

        val byMonth = contributions
            .groupBy { it.monthStartMillis }
            .toSortedMap()

        val dateTimeFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", locale)
        return buildString {
            var periodTotal = 0.0
            byMonth.forEach { (monthStart, rows) ->
                val monthKg = rows.sumOf { it.weightKg }
                if (monthKg <= 0.0) return@forEach
                periodTotal += monthKg
                val monthTitle = formatMonthHeading(monthStart, locale)
                val monthLower = monthTitle.replaceFirstChar { ch ->
                    if (ch.isUpperCase()) ch.lowercase(locale) else ch.toString()
                }
                append(monthTitle)
                append(":\n")
                rows.groupBy { it.productKey to it.productLabel }
                    .map { (keyLabel, list) -> keyLabel.second to list.sumOf { it.weightKg } }
                    .filter { it.second > 0.0 }
                    .sortedByDescending { it.second }
                    .forEach { (label, kg) ->
                        append(label)
                        append(' ')
                        append(EN_DASH)
                        append(' ')
                        append(formatKg(kg, thousandsSeparator, locale))
                        append(" кг\n")
                    }
                append('\n')
                append("Общий тоннаж за ")
                append(monthLower)
                append(": ")
                append(formatKg(monthKg, thousandsSeparator, locale))
                append(" кг\n\n")
            }
            // One month: month total already said everything — no duplicate «за период».
            if (monthStarts.size > 1) {
                append("Общий тоннаж за период: ")
                append(formatKg(periodTotal, thousandsSeparator, locale))
                append(" кг\n\n")
            }
            append("Сгенерировано приложением «Фишка».\n")
            append(dateTimeFmt.format(Date(generatedAtMillis)))
        }
    }

    fun statsDocxFileName(generatedAtMillis: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("ddMMyyyy", Locale.US)
        return "Статистика ${fmt.format(Date(generatedAtMillis))}.docx"
    }

    fun formatMonthHeading(monthStartMillis: Long, locale: Locale): String {
        val raw = SimpleDateFormat("LLLL yyyy", locale).format(Date(monthStartMillis))
        return raw.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString()
        }
    }

    fun formatKg(kg: Double, thousandsSeparator: Boolean, locale: Locale = Locale.getDefault()): String {
        val whole = kg.roundToLong().coerceAtLeast(0L)
        return if (whole <= Int.MAX_VALUE) {
            QuantityFormatters.formatInteger(whole.toInt(), thousandsSeparator, locale)
        } else {
            QuantityFormatters.formatWeight(whole.toDouble(), thousandsSeparator, locale)
        }
    }
}
