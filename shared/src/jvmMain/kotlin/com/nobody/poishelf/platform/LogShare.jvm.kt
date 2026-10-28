package com.nobody.poishelf.platform

import java.io.File

/**
 * 日志文件分享（S3）JVM 实际：桌面无系统分享面板，落盘到用户主目录
 * `PoiShelf/logs/` 并仅保留最近 5 个；调用方经 `onMessage` 提示路径。
 */
actual suspend fun shareLogFile(fileName: String, text: String) {
    val dir = File(System.getProperty("user.home"), "PoiShelf/logs").apply { mkdirs() }
    runCatching {
        File(dir, fileName).writeText(text)
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(5)
            ?.forEach { runCatching { it.delete() } }
    }
}
