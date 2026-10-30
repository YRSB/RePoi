package com.nobody.repoi.util

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 价格与货币展示格式化工具，严格对齐 Hpoi 2.39 NumberUtil.c / strings.xml 规范。
 */
object PriceFormatter {

    /**
     * 格式化货币代码为中文标签，对齐原版 Hpoi 的 NumberUtil.b / strings.xml
     */
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

    /**
     * 统一价格展示格式化，严格对齐 Hpoi 2.39 NumberUtil.c：
     * - 若 money == 0.0 或接近 0，显示 "价格待定"
     * - 若 money > 0.0，格式化金额 + 货币名称 + (if (tax > 0) "(含税）" else "")
     * - 若 money 无值但有 explicit 字符串（price / moneyString），则使用 explicit（若为纯数字则追加货币与税标）
     */
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
                // 对齐 DecimalFormat("#.##")：保留至多两位小数，去尾零。
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

    /**
     * 定长小数格式化（`String.format(Locale, "%.Nf", v)` 的 KMP 等价实现）。
     *
     * - 四舍五入（half-up，符号感知），小数位补零到 [decimals] 位，无千分位分隔；
     * - 行为对齐 `java.util.Formatter("%.Nf")`（HALF_UP），浮点表示误差同级（~1e-15）。
     */
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
