package com.nobody.repoi.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 应用内日志收集器：所有关键链路日志同时输出到平台日志与内存环形缓冲，
 * 用户可在设置页一键导出为文本文件并分享，便于排查线上问题。
 */
@OptIn(ExperimentalTime::class)
object AppLogger {

    private const val LOGCAT_TAG = "RePoi"
    private const val MAX_LINES = 800
    private const val DIR_NAME = "repoi-logs"
    private const val KEEP_FILES = 5

    private val lock = Any()
    private val buffer = ArrayDeque<String>(MAX_LINES)

    fun d(scope: String, message: String) {
        record("D", scope, message)
        platformLog(2, LOGCAT_TAG, "[$scope] $message")
    }

    fun i(scope: String, message: String) {
        record("I", scope, message)
        platformLog(3, LOGCAT_TAG, "[$scope] $message")
    }

    fun w(scope: String, message: String) {
        record("W", scope, message)
        platformLog(4, LOGCAT_TAG, "[$scope] $message")
    }

    fun e(scope: String, message: String, throwable: Throwable? = null) {
        record("E", scope, message + (throwable?.let { " 异常=${it.message}" } ?: ""))
        platformLog(5, LOGCAT_TAG, "[$scope] $message", throwable)
    }

    private fun record(level: String, scope: String, message: String) {
        val line = "${formatLogTime(now())} $level/[$scope] $message"
        synchronized(lock) {
            if (buffer.size >= MAX_LINES) buffer.removeFirst()
            buffer.addLast(line)
        }
    }

    /**
     * 安装全局崩溃捕获：先落盘崩溃堆栈再交还系统默认处理。
     */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stack = throwable.stackTraceToString().take(4000)
                record("E", "崩溃", "线程=${thread.name} ${throwable::class.simpleName}: ${throwable.message}\n$stack")
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * 将缓冲日志连同设备与版本头写入缓存目录文本文件并返回文件绝对路径。
     * 仅保留最近 [KEEP_FILES] 个文件避免占用空间。
     */
    fun exportToFile(): String {
        val dir = logCacheDir().toPath().resolve(DIR_NAME)
        FileSystem.SYSTEM.createDirectories(dir)
        val fileName = "repoi-log-${formatFileTime(now())}.log"
        val file = dir.resolve(fileName)
        val header = buildString {
            appendLine("RePoi 应用日志")
            appendLine("导出时间=${formatFileTime(now())}")
            appendLine("版本=${platformVersionName()}")
            appendLine("设备=${platformDeviceInfo()}")
            appendLine("=".repeat(40))
        }
        val body = synchronized(lock) { buffer.joinToString("\n") }
        FileSystem.SYSTEM.write(file) {
            writeUtf8(header + body.ifBlank { "（暂无日志记录）" } + "\n")
        }
        FileSystem.SYSTEM.listOrNull(dir)
            ?.sortedByDescending {
                runCatching { FileSystem.SYSTEM.metadata(it).lastModifiedAtMillis }.getOrDefault(0L)
            }
            ?.drop(KEEP_FILES)
            ?.forEach { runCatching { FileSystem.SYSTEM.delete(it) } }
        return file.toString()
    }

    internal fun now(): LocalDateTime =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    /** "MM-dd HH:mm:ss.SSS" */
    internal fun formatLogTime(time: LocalDateTime): String =
        "${time.monthNumber.toString().padStart(2, '0')}-${time.day.toString().padStart(2, '0')} " +
            "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}:" +
            "${time.second.toString().padStart(2, '0')}.${(time.nanosecond / 1_000_000).toString().padStart(3, '0')}"

    /** "yyyyMMdd-HHmmss" */
    internal fun formatFileTime(time: LocalDateTime): String =
        "${time.year}${time.monthNumber.toString().padStart(2, '0')}${time.day.toString().padStart(2, '0')}-" +
            "${time.hour.toString().padStart(2, '0')}${time.minute.toString().padStart(2, '0')}" +
            "${time.second.toString().padStart(2, '0')}"
}

/** 各端平台日志输出（android: Logcat；jvm: stderr）。 */
expect fun platformLog(priority: Int, tag: String, message: String, throwable: Throwable? = null)

/** 各端日志缓存目录绝对路径（android: cacheDir；jvm: ~/.repoi/cache）。 */
expect fun logCacheDir(): String

/** 各端版本名（android: PackageInfo；jvm: 固定版本号）。 */
expect fun platformVersionName(): String

/** 各端设备信息行（android: BRAND/MODEL/版本；jvm: OS 名/版本）。 */
expect fun platformDeviceInfo(): String
