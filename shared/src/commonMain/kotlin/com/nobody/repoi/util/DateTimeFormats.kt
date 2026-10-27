package com.nobody.repoi.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 日期时间纯 Kotlin 格式化/解析工具（替代 JVM-only 的 `SimpleDateFormat`）。
 *
 * 服务端时间均为规范格式（"yyyy-MM-dd[ HH:mm[:ss]]"），手写严格解析即可等价；
 * 所有转换均使用系统默认时区（与老项目 `SimpleDateFormat` 默认行为一致）。
 */
@OptIn(ExperimentalTime::class)
object DateTimeFormats {

    /**
     * 纪元毫秒 → "yyyy-MM-dd"。
     */
    fun epochMillisToDateString(millis: Long): String {
        val time = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${time.year}-${time.monthNumber.toString().padStart(2, '0')}-" +
            time.day.toString().padStart(2, '0')
    }

    /**
     * 解析 "yyyy-MM-dd[ HH:mm[:ss]]" → 纪元毫秒；解析失败返回 null。
     */
    fun parseDateTimeToEpochMillis(content: String): Long? {
        val parts = content.trim().split(Regex("[^0-9]+")).filter { it.isNotEmpty() }
        if (parts.size < 3) return null
        return try {
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val day = parts[2].toInt()
            val hour = parts.getOrNull(3)?.toInt() ?: 0
            val minute = parts.getOrNull(4)?.toInt() ?: 0
            val second = parts.getOrNull(5)?.toInt() ?: 0
            LocalDateTime(year, month, day, hour, minute, second)
                .toInstant(TimeZone.currentSystemDefault())
                .toEpochMilliseconds()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * "yyyy-MM-dd HH:mm:ss" → 相对时间文案（刚刚/x分钟前/x小时前/x天前/x个月前/x年前）。
     * 解析失败返回日期部分（空格前）。
     */
    fun relativeTimeText(timeStr: String): String {
        val trimmed = timeStr.ifBlank { return "" }
        return try {
            val millis = parseDateTimeToEpochMillis(trimmed)
                ?: return trimmed.substringBefore(" ")
            val diffSeconds = (Clock.System.now().toEpochMilliseconds() - millis) / 1000
            when {
                diffSeconds < 60 -> "刚刚"
                diffSeconds < 3600 -> "${diffSeconds / 60}分钟前"
                diffSeconds < 86400 -> "${diffSeconds / 3600}小时前"
                diffSeconds < 2592000 -> "${kotlin.math.ceil(diffSeconds / 86400.0).toInt()}天前"
                diffSeconds < 31536000 -> "${diffSeconds / 2592000}个月前"
                else -> "${diffSeconds / 31536000}年前"
            }
        } catch (_: Exception) {
            trimmed.substringBefore(" ")
        }
    }

    /**
     * 纪元毫秒 → "MM-dd"（ActionCard 7 天外相对时间展示，与老项目 MONTH_DAY_FORMATTER 一致）。
     */
    fun epochMillisToMonthDay(millis: Long): String {
        val time = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault())
        return "${time.monthNumber.toString().padStart(2, '0')}-" +
            time.day.toString().padStart(2, '0')
    }
}
