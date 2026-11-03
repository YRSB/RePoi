package com.nobody.repoi.platform

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 跨平台日期与时间格式化工具单元测试（DateTimeFormats）。
 *
 * 验证对齐 kotlinx-datetime 的系统时区计算、多模式服务端日期解析、
 * 秒级与毫秒级时间戳自适应转换，以及零填充格式稳定性。
 */
class DateTimeFormatsTest {

    @Test
    fun todayYmd_matchesStandardDateFormat() {
        val today = DateTimeFormats.todayYmd(TimeZone.UTC)
        today.shouldMatch(Regex("""^\d{4}-\d{2}-\d{2}$"""))
    }

    @Test
    fun nowYmdHms_matchesFullDateTimeFormat() {
        val now = DateTimeFormats.nowYmdHms(TimeZone.UTC)
        now.shouldMatch(Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$"""))
    }

    @Test
    fun nowHm_matchesHourMinuteFormat() {
        val hm = DateTimeFormats.nowHm(TimeZone.UTC)
        hm.shouldMatch(Regex("""^\d{2}:\d{2}$"""))
    }

    @Test
    fun logFileTimestamp_matchesCompactFormat() {
        val logTs = DateTimeFormats.logFileTimestamp(TimeZone.UTC)
        logTs.shouldMatch(Regex("""^\d{8}-\d{6}$"""))
    }

    @Test
    fun logTimestamp_matchesLogLineFormat() {
        val logTs = DateTimeFormats.logTimestamp(TimeZone.UTC)
        logTs.shouldMatch(Regex("""^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}$"""))
    }

    @Test
    fun ymdFromMillis_formatsEpochMillisCorrectly() {
        // 2024-01-15 00:00:00 UTC = 1705276800000 ms
        val formatted = DateTimeFormats.ymdFromMillis(1705276800000L, TimeZone.UTC)
        formatted shouldBe "2024-01-15"
    }

    @Test
    fun ymdFromMillis_autoConvertsSecondsTimestamp() {
        // 秒级时间戳（< 1e11）: 1705276800 s
        val formatted = DateTimeFormats.ymdFromMillis(1705276800L, TimeZone.UTC)
        formatted shouldBe "2024-01-15"
    }

    @Test
    fun hmFromMillis_formatsHourAndMinute() {
        // 2024-01-15 14:05:00 UTC = 1705327500000 ms
        val formatted = DateTimeFormats.hmFromMillis(1705327500000L, TimeZone.UTC)
        formatted shouldBe "14:05"
    }

    @Test
    fun parseServerDateTime_parsesVariousValidFormats() {
        // ymdHms
        val ts1 = DateTimeFormats.parseServerDateTime("2026-11-03 15:30:45")
        ts1.shouldNotBeNull()

        // ymdHm
        val ts2 = DateTimeFormats.parseServerDateTime("2026-11-03 15:30")
        ts2.shouldNotBeNull()

        // ymd
        val ts3 = DateTimeFormats.parseServerDateTime("2026-11-03")
        ts3.shouldNotBeNull()

        // slash ymdHms
        val ts4 = DateTimeFormats.parseServerDateTime("2026/11/03 15:30:45")
        ts4.shouldNotBeNull()
        ts4 shouldBe ts1

        // 字符串形式的毫秒数值
        val ts5 = DateTimeFormats.parseServerDateTime("1705276800000")
        ts5 shouldBe 1705276800000L

        // 字符串形式的秒数值（自动 * 1000）
        val ts6 = DateTimeFormats.parseServerDateTime("1705276800")
        ts6 shouldBe 1705276800000L
    }

    @Test
    fun parseServerDateTime_returnsNullForInvalidFormats() {
        DateTimeFormats.parseServerDateTime("").shouldBeNull()
        DateTimeFormats.parseServerDateTime("   ").shouldBeNull()
        DateTimeFormats.parseServerDateTime("not-a-date").shouldBeNull()
        DateTimeFormats.parseServerDateTime("2026-99-99").shouldBeNull()
    }

    @Test
    fun nowMillis_returnsPositiveCurrentTime() {
        val millis = nowMillis()
        assertTrue(millis > 1_700_000_000_000L, "nowMillis 应返回有效的当前纪元毫秒数")
    }
}
