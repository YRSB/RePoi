package com.nobody.poishelf.platform

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import android.os.Build

actual object AppInfo {
    actual val versionName: String = runCatching {
        val context = poiShelfAppContext()
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "dev"
    }.getOrDefault("dev")
    actual val versionCode: String = runCatching {
        val context = poiShelfAppContext()
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        PackageInfoCompat.getLongVersionCode(info).toString()
    }.getOrDefault("0")
    actual val platform: String = "android"
    actual val brand: String = Build.BRAND ?: "unknown"
    actual val model: String = Build.MODEL ?: "unknown"
    actual val systemVersion: String = "Android ${Build.VERSION.RELEASE}(SDK=${Build.VERSION.SDK_INT})"
}
