package com.nobody.repoi.util

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

object PriceFormatter {

    fun formatCurrency(currency: String?): String {
        return when (currency?.lowercase()?.trim()) {
            "yen", "jpy" -> "日元"
            "rmb", "cny" -> "人民币"
            "dollar", "usd" -> "美元"
            "euro", "eur" -> "欧元"
            "hkd" -> "港元"
            else -> currency?.trim().orEmpty()
        }
    }

    fun format(
        money: Double?,
        currency: String?,
        tax: Int? = null,
        price: String? = null,
        moneyString: String? = null
    ): String {
        val explicit = price?.trim()?.ifBlank { null } ?: moneyString?.trim()?.ifBlank { null }
        if (explicit != null) {
            val isPureNumber = explicit.toDoubleOrNull() != null
            if (isPureNumber && !currency.isNullOrBlank()) {
                val currencyLabel = formatCurrency(currency)
                val taxLabel = if ((tax ?: 0) > 0) "(含税）" else ""
                return "$explicit$currencyLabel$taxLabel"
            }
            return explicit
        }

        if (money != null) {
            if (abs(money) < 1e-5) {
                return "价格待定"
            }
            val formattedVal = if (money % 1.0 == 0.0) {
                money.toLong().toString()
            } else {

                val rounded = (money * 100).roundToInt() / 100.0
                if (rounded % 1.0 == 0.0) {
                    rounded.toLong().toString()
                } else {
                    val two = rounded.toString()
                    two.trimEnd('0').trimEnd('.')
                }
            }
            val currencyLabel = formatCurrency(currency)
            val taxLabel = if ((tax ?: 0) > 0) "(含税）" else ""
            return "$formattedVal$currencyLabel$taxLabel"
        }

        return ""
    }

    fun formatFixed(value: Double, decimals: Int): String {
        require(decimals >= 0) { "decimals must be >= 0, was $decimals" }
        if (value.isNaN()) return "NaN"
        if (value.isInfinite()) return if (value > 0) "∞" else "-∞"
        val factor = 10.0.pow(decimals)
        val shifted = value * factor
        val rounded = if (shifted >= 0) (shifted + 0.5).toLong() else (shifted - 0.5).toLong()
        val negative = rounded < 0
        val magnitude = abs(rounded)
        val intPart = magnitude / factor.toLong()
        return buildString {
            if (negative) append('-')
            append(intPart)
            if (decimals > 0) {
                append('.')
                append((magnitude % factor.toLong()).toString().padStart(decimals, '0'))
            }
        }
    }
}
