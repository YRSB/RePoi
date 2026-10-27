package com.nobody.repoi.util

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
            if (kotlin.math.abs(money) < 1e-5) {
                return "价格待定"
            }
            val formattedVal = if (money % 1.0 == 0.0) {
                money.toLong().toString()
            } else {
                formatTwoDecimals(money)
            }
            val currencyLabel = formatCurrency(currency)
            val taxLabel = if ((tax ?: 0) > 0) "(含税）" else ""
            return "$formattedVal$currencyLabel$taxLabel"
        }

        return ""
    }

    /**
     * 纯 Kotlin 实现 `DecimalFormat("#.##")` 语义：最多保留两位小数，
     * 四舍五入，去除末尾多余的零与小数点。
     */
    internal fun formatTwoDecimals(value: Double): String {
        val rounded = kotlin.math.round(value * 100) / 100.0
        // 整数部分 + 两位小数，再去尾零
        val scaled = kotlin.math.round(rounded * 100).toLong()
        val negative = scaled < 0
        val abs = kotlin.math.abs(scaled)
        val intPart = abs / 100
        val fracPart = (abs % 100).toInt()
        val fracText = fracPart.toString().padStart(2, '0').trimEnd('0')
        return buildString {
            if (negative) append('-')
            append(intPart)
            if (fracText.isNotEmpty()) {
                append('.')
                append(fracText)
            }
        }
    }
}
