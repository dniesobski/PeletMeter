package com.example.ui.components

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val polishSymbols = DecimalFormatSymbols(Locale.forLanguageTag("pl-PL")).apply {
    decimalSeparator = ','
    groupingSeparator = ' '
}

private val oneDecimalFormatter = DecimalFormat("#,##0.#", polishSymbols)
private val twoDecimalFormatter = DecimalFormat("#,##0.00", polishSymbols)
private val zeroDecimalFormatter = DecimalFormat("#,##0", polishSymbols)

fun formatKg(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return oneDecimalFormatter.format(rounded)
}

fun formatNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return oneDecimalFormatter.format(rounded)
}

fun formatPln(value: Double): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return "${twoDecimalFormatter.format(rounded)} zł"
}

fun formatPlnCompact(value: Double): String {
    val rounded = kotlin.math.round(value)
    return "${zeroDecimalFormatter.format(rounded)} zł"
}
