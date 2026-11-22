package com.nobody.repoi.platform

actual object AppInfo {
    actual val versionName: String = "dev"
    actual val versionCode: String = "0"
    actual val platform: String = "android"
    actual val brand: String =
        System.getenv("COMPUTERNAME") ?: System.getProperty("user.name") ?: "desktop"
    actual val model: String = System.getProperty("os.arch") ?: "unknown"
    actual val systemVersion: String =
        "${System.getProperty("os.name")}(${System.getProperty("os.version")})"
}
