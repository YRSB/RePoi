package com.nobody.poishelf.data.remote.interceptor

/**
 * Hpoi API 公共请求参数（OkHttp 拦截器 → Ktor 插件移植）。
 *
 * 来源：PoiShelf `CommonParamsInterceptor` companion。Ktor 侧由
 * `CommonParamsPlugin`（HttpSend）在发包前合并，本文件只保留常量与
 * 纯参数合并逻辑以便单测。
 */
object CommonParams {

    const val REFERER_URL = "https://www.hpoi.net/android"
    const val USER_AGENT = "okhttp/4.12.0"

    /** Hpoi 2.42 对应客户端构建版本号 (versionCode) */
    const val API_APP_VERSION = "200133"

    /** 模拟现代 Android API Level (Android 14 API 34，对应 Hpoi 2.42 targetSdk) */
    const val API_SYSTEM_VERSION = "34"

    /** 模拟现代机型设备标示 (Galaxy S24 Ultra) */
    const val API_MACHINE = "SM-S9280"

    const val BASE_URL = "https://www.hpoi.net.cn/"
    const val EXHOBBY_BASE_URL = "https://www.exhobby.net/"
    const val EXHOBBY_COOKIE = "is18=yes"
    const val EXHOBBY_USER_AGENT = "okhttp/4.9.0"

    /** 全部 6 个公共参数；M2 桌面端与 Android 共用同一套（D1 默认）。 */
    fun build(token: String): Map<String, String> = mapOf(
        "utoken" to token,
        "platform" to "android",
        "appVersion" to API_APP_VERSION,
        "channel" to "official",
        "machine" to API_MACHINE,
        "system" to API_SYSTEM_VERSION,
    )

    /**
     * 合并公共参数：保留调用方已有的同名参数（不覆盖），跳过 null 值。
     * 对应 Retrofit `@Field nullable` 省略 null 的行为。
     */
    fun merge(existing: Map<String, String?>, token: String): Map<String, String> {
        val merged = LinkedHashMap<String, String>()
        for ((key, value) in existing) {
            if (value != null) merged[key] = value
        }
        for ((key, value) in build(token)) {
            merged.putIfAbsent(key, value)
        }
        return merged
    }
}
