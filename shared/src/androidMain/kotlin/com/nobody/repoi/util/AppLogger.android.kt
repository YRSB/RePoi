package com.nobody.repoi.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import org.koin.mp.KoinPlatform.getKoin

actual fun platformLog(priority: Int, tag: String, message: String, throwable: Throwable?) {
    try {
        when (priority) {
            2 -> Log.d(tag, message, throwable)
            3 -> Log.i(tag, message, throwable)
            4 -> Log.w(tag, message, throwable)
            else -> Log.e(tag, message, throwable)
        }
    } catch (_: RuntimeException) {
        // 纯 JVM 单测环境没有 android.util.Log 实现，吞掉异常
    }
}

actual fun logCacheDir(): String {
    val context: Context = getKoin().get()
    return context.cacheDir.absolutePath
}

actual fun platformVersionName(): String {
    val context: Context = getKoin().get()
    return try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        "${info.versionName}($code)"
    } catch (_: PackageManager.NameNotFoundException) {
        "未知"
    }
}

actual fun platformDeviceInfo(): String =
    "${Build.BRAND} ${Build.MODEL} Android=${Build.VERSION.RELEASE}(SDK=${Build.VERSION.SDK_INT})"
