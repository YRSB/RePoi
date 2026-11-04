package com.nobody.repoi.platform

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider
import com.nobody.repoi.util.AppLogger
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
        clipData = ClipData.newRawUri("应用日志", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(intent, "分享应用日志").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        AppLogger.w("LogShare", "未找到可分享日志的应用：${e.message}")
    } catch (e: Exception) {
        AppLogger.e("LogShare", "调起系统日志分享失败：${e.message}", e)
        throw e
    }
}
