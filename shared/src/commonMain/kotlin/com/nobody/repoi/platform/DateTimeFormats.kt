package com.nobody.repoi.platform

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * M2 common datetime helpers (kotlinx-datetime 0.8.0).
 *
 * Replaces SimpleDateFormat/Calendar call sites in ported data-layer code.
 * All formatting is manual (zero-padded) so behavior is identical on both targets.
 */
@OptIn(ExperimentalTime::class)
object DateTimeFormats {

    fun todayYmd(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val today: LocalDate = Clock.System.now().toLocalDateTime(timeZone).date
        return ymd(today.year, today.month.number, today.day)
    }

    fun nowYmdHms(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val dt: LocalDateTime = Clock.System.now().toLocalDateTime(timeZone)
        return "${ymd(dt.year, dt.month.number, dt.day)} ${hms(dt.hour, dt.minute, dt.second)}"
    }

    fun nowHm(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val dt: LocalDateTime = Clock.System.now().toLocalDateTime(timeZone)
        return hms(dt.hour, dt.minute, null).substring(0, 5)
    }

    fun logTimestamp(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val now = Clock.System.now()
        val dt: LocalDateTime = now.toLocalDateTime(timeZone)
        val millis = (now.toEpochMilliseconds() % 1000).toString().padStart(3, '0')
        return "${dt.month.number.toString().padStart(2, '0')}-${dt.day.toString().padStart(2, '0')} " +
            "${hms(dt.hour, dt.minute, dt.second)}.$millis"
    }

    fun logFileTimestamp(timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val dt: LocalDateTime = Clock.System.now().toLocalDateTime(timeZone)
        return "${dt.year}${dt.month.number.toString().padStart(2, '0')}${dt.day.toString().padStart(2, '0')}-" +
            hms(dt.hour, dt.minute, dt.second).replace(":", "")
    }

    /**
     * Epoch 毫秒 → "yyyy-MM-dd"（系统时区）。
     *
     * 替换 `SimpleDateFormat("yyyy-MM-dd").format(Date(millis))` 调用点；
     * 秒级时间戳（< 1e11）按秒换算，与原版 `ts < 1e11 → * 1000` 语义一致。
     */
    fun ymdFromMillis(millis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val ms = if (millis < 100_000_000_000L) millis * 1000L else millis
        val date: LocalDate = Instant.fromEpochMilliseconds(ms).toLocalDateTime(timeZone).date
        return ymd(date.year, date.month.number, date.day)
    }

    /**
     * Epoch 毫秒 → "HH:mm"（系统时区）。
     *
     * 替换 `SimpleDateFormat("HH:mm").format(Date(millis))` 调用点（消息时间戳等）；
     * 秒级时间戳（< 1e11）按秒换算，与 `ymdFromMillis` 语义一致。
     * 注意：与 `nowHm` 的差异在于本函数格式化给定时刻而非当前时刻。
     */
    fun hmFromMillis(millis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val ms = if (millis < 100_000_000_000L) millis * 1000L else millis
        val dt: LocalDateTime = Instant.fromEpochMilliseconds(ms).toLocalDateTime(timeZone)
        return hms(dt.hour, dt.minute, null)
    }

    /**
     * Multi-pattern parse for server date strings ("yyyy-MM-dd HH:mm:ss",
     * "yyyy-MM-dd HH:mm", "yyyy-MM-dd", "yyyy/MM/dd HH:mm:ss").
     * Returns epoch millis, or null when nothing matches (same fallback as before).
     */
    fun parseServerDateTime(content: String): Long? {
        val text = content.trim()
        if (text.isEmpty()) return null
        text.toLongOrNull()?.let { num ->
            return when {
                num > 100_000_000_000L -> num
                num > 1_000_000_000L -> num * 1000L
                else -> num
            }
        }
        parseYmdHms(text)?.let { return it }
        parseYmdHm(text)?.let { return it }
        parseYmd(text)?.let { return it }
        parseSlashYmdHms(text)?.let { return it }
        return null
    }

    private fun ymd(y: Int, m: Int, d: Int): String =
        "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"

    private fun hms(h: Int, m: Int, s: Int?): String {
        val base = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"
        return if (s == null) base else "$base:${s.toString().padStart(2, '0')}"
    }

    private fun epochOf(
        y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): Long? = runCatching {
        LocalDateTime(y, mo, d, h, mi, s).toInstant(timeZone).toEpochMilliseconds()
    }.getOrNull()

    private val ymdHms = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})[ T](\d{1,2}):(\d{1,2}):(\d{1,2})$""")
    private val ymdHm = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})[ T](\d{1,2}):(\d{1,2})$""")
    private val ymd = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})$""")
    private val slashYmdHms = Regex("""^(\d{4})/(\d{1,2})/(\d{1,2})[ T](\d{1,2}):(\d{1,2}):(\d{1,2})$""")

    private fun parseYmdHms(t: String): Long? {
        val m = ymdHms.matchEntire(t) ?: return null
        val (y, mo, d, h, mi, s) = m.destructured
        return epochOf(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt())
    }

    private fun parseYmdHm(t: String): Long? {
        val m = ymdHm.matchEntire(t) ?: return null
        val (y, mo, d, h, mi) = m.destructured
        return epochOf(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt())
    }

    private fun parseYmd(t: String): Long? {
        val m = ymd.matchEntire(t) ?: return null
        val (y, mo, d) = m.destructured
        return epochOf(y.toInt(), mo.toInt(), d.toInt())
    }

    private fun parseSlashYmdHms(t: String): Long? {
        val m = slashYmdHms.matchEntire(t) ?: return null
        val (y, mo, d, h, mi, s) = m.destructured
        return epochOf(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt())
    }
}

/** Current time as epoch millis (SystemClock.uptimeMillis replacement for debounce logic). */
@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
