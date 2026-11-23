package com.irontrack.core.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

object FrenchFormat {
    val locale: Locale = Locale.FRANCE
    val date: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
    val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale)
    val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", locale)

    /** « 62,5 » ; « 60 » sans décimale inutile. */
    fun number(v: Double, maxDecimals: Int = 2): String {
        val factor = Math.pow(10.0, maxDecimals.toDouble())
        val rounded = (v * factor).roundToLong() / factor
        val s = if (rounded == Math.floor(rounded)) rounded.toLong().toString()
        else rounded.toBigDecimal().stripTrailingZeros().toPlainString()
        return s.replace('.', ',')
    }

    fun parseNumber(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    fun time(minutesOfDay: Int): String = "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)

    fun capitalize(s: String): String = s.replaceFirstChar { it.titlecase(locale) }

    fun formatLong(d: LocalDate): String = capitalize(d.format(longDate))
}
