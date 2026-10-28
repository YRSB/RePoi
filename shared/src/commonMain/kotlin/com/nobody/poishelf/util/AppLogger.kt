package com.nobody.poishelf.util

import co.touchlab.kermit.Logger as KermitLogger
import com.nobody.poishelf.platform.AppInfo
import com.nobody.poishelf.platform.DateTimeFormats
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.nobody.poishelf.platform.installUncaughtExceptionHook

/**
 * 应用内日志收集器（KMP 移植）：内存环形缓冲 + Kermit 输出。
 *
 * - `android.util.Log` → Kermit（Logcat 仍可见，JVM 侧走 stdout 无崩溃）；
 * - SimpleDateFormat → [DateTimeFormats]（不可变，无线程安全问题）；
 * - 设备/版本头 → [AppInfo] expect；
 * - 导出目录 → `cacheDirPath()/poishelf-logs`（见 `AppDirs`）。
 */
object AppLogger {

    private const val LOGCAT_TAG = "PoiShelf"
    private const val MAX_LINES = 800
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
        record("E", scope, message + (throwable?.let { " 异常=${it.message}" } ?: ""))
        logger.e(throwable) { "[$scope] $message" }
    }

    private fun record(level: String, scope: String, message: String) {
        val line = "${DateTimeFormats.logTimestamp()} $level/[$scope] $message"
        // record 允许同步调用；用 tryLock 快速路径，失败则丢弃（日志不阻塞业务）。
        if (bufferMutex.tryLock()) {
            try {
                if (buffer.size >= MAX_LINES) buffer.removeFirst()
                buffer.addLast(line)
            } finally {
                bufferMutex.unlock()
            }
        }
    }

    /**
     * 安装全局崩溃捕获：先记入缓冲再交还系统默认处理。
     *
     * 平台钩子见 `installUncaughtExceptionHook`（common 无 `Thread` API）。
     */
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

    /**
     * 将缓冲日志连同设备与版本头写入缓存目录文本并返回内容。
     * 仅保留最近 [KEEP_FILES] 个文件避免占用空间。
     *
     * KMP 版返回文件字节 + 文件名（调用方经 FileKit 落盘/分享），
     * 避免 common 持有 java.io.File。
     */
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
