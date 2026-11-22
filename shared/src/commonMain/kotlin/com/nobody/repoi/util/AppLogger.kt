package com.nobody.repoi.util

import co.touchlab.kermit.Logger as KermitLogger
import com.nobody.repoi.platform.AppInfo
import com.nobody.repoi.platform.DateTimeFormats
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.nobody.repoi.platform.installUncaughtExceptionHook

object AppLogger {

    private const val LOGCAT_TAG = "PoiShelf"
    private const val MAX_LINES = 1200
    private const val DIR_NAME = "poishelf-logs"
    private const val KEEP_FILES = 5

    private val logger = KermitLogger.withTag(LOGCAT_TAG)
    private val bufferMutex = Mutex()
    private val buffer = ArrayDeque<String>(MAX_LINES)

    fun d(scope: String, message: String) {
        record("D", scope, message)
        logger.d { "[$scope] $message" }
    }

    fun i(scope: String, message: String) {
        record("I", scope, message)
        logger.i { "[$scope] $message" }
    }

    fun w(scope: String, message: String) {
        record("W", scope, message)
        logger.w { "[$scope] $message" }
    }

    fun e(scope: String, message: String, throwable: Throwable? = null) {
        record("E", scope, message, throwable)
        logger.e(throwable) { "[$scope] $message" }
    }

    private fun record(level: String, scope: String, message: String, throwable: Throwable? = null) {

        if (bufferMutex.tryLock()) {
            try {
                val suffix = throwable?.let { " 异常=${it.message}" } ?: ""
                val line = "${DateTimeFormats.logTimestamp()} $level/[$scope] $message$suffix"
                if (buffer.size >= MAX_LINES) buffer.removeFirst()
                buffer.addLast(line)
            } finally {
                bufferMutex.unlock()
            }
        }
    }

    fun installCrashHandler() {
        installUncaughtExceptionHook { threadName, throwable ->
            try {
                val stack = throwable.stackTraceToString().take(4000)
                record(
                    "E",
                    "崩溃",
                    "线程=$threadName ${throwable::class.simpleName}: ${throwable.message}\n$stack",
                )
            } catch (_: Exception) {
            }
        }
    }

    suspend fun exportLogBytes(): Pair<String, ByteArray> {
        val version = "${AppInfo.versionName}(${AppInfo.versionCode})"
        val header = buildString {
            appendLine("PoiShelf 应用日志")
            appendLine("导出时间=${DateTimeFormats.logTimestamp()}")
            appendLine("版本=$version")
            appendLine("设备=${AppInfo.brand} ${AppInfo.model} ${AppInfo.platform}(SDK=${AppInfo.systemVersion})")
            appendLine("=".repeat(40))
        }
        val body = bufferMutex.withLock { buffer.joinToString("\n") }
        val fileName = "poishelf-log-${DateTimeFormats.logFileTimestamp()}.log"
        return fileName to (header + body.ifBlank { "（暂无日志记录）" } + "\n").toByteArray()
    }

    suspend fun snapshot(): List<String> = bufferMutex.withLock { buffer.toList() }
}
