package com.nobody.repoi.platform

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

actual suspend fun shareLogFile(fileName: String, text: String) {
    val context = poiShelfAppContext()
    val dir = File(context.cacheDir, "poishelf-logs").apply { mkdirs() }
    val file = File(dir, fileName)
    file.writeText(text)
    // 仅保留最近 5 个
    dir.listFiles()
        ?.sortedByDescending { it.lastModified() }
        ?.drop(5)
        ?.forEach { runCatching { it.delete() } }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "分享应用日志"))
    } catch (_: ActivityNotFoundException) {
        // 无可分享目标：文件已落盘至缓存 poishelf-logs 目录，调用方经 onMessage 提示。
    }
}
