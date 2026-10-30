package com.nobody.repoi.platform

/**
 * 应用与设备信息（S8）。Android 实际经 PackageManager/Build；JVM 为桌面常量。
 */
expect object AppInfo {
    val versionName: String
    val versionCode: String
    val platform: String
    val brand: String
    val model: String
    val systemVersion: String
}
