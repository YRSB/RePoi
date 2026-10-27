package com.nobody.repoi.util

/**
 * 数字纯 Kotlin 格式化工具（替代 JVM-only 的 `String.format`）。
 */

/**
 * 实现 `"%.2f"` 语义：保留两位小数，四舍五入。
 */
fun formatFixed2(value: Double): String {
    val scaled = kotlin.math.round(value * 100)
    val negative = scaled < 0
    val abs = kotlin.math.abs(scaled)
    val intPart = abs / 100
    val fracPart = (abs % 100).toInt().toString().padStart(2, '0')
    return (if (negative) "-" else "") + "$intPart.$fracPart"
}

/**
 * 实现 `"%.1f"` 语义：保留一位小数，四舍五入。
 */
fun formatFixed1(value: Double): String {
    val scaled = kotlin.math.round(value * 10)
    val negative = scaled < 0
    val abs = kotlin.math.abs(scaled)
    val intPart = abs / 10
    val fracPart = (abs % 10).toInt()
    return (if (negative) "-" else "") + "$intPart.$fracPart"
}
