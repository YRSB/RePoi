package com.nobody.poishelf.platform

/**
 * 应用与设备信息（S8）JVM 实际：桌面常量（D1 默认与 Android 共用线值）。
 *
 * `platform` 保持 `"android"`（服务端兼容确认前不变，见 D1）；
 * 版本取桌面常量（无 BuildKonfig；M6 打包时按需注入）。
 */
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
