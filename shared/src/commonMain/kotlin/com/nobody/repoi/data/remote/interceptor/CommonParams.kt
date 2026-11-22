package com.nobody.repoi.data.remote.interceptor

object CommonParams {

    const val REFERER_URL = "https://www.hpoi.net/android"
    const val USER_AGENT = "okhttp/4.12.0"

    const val API_APP_VERSION = "200133"

    const val API_SYSTEM_VERSION = "34"

    const val API_MACHINE = "SM-S9280"

    const val BASE_URL = "https://www.hpoi.net.cn/"

    const val WEB_BASE_URL = "https://www.hpoi.net/"
    const val EXHOBBY_BASE_URL = "https://www.exhobby.net/"
    const val EXHOBBY_COOKIE = "is18=yes"
    const val EXHOBBY_USER_AGENT = "okhttp/4.9.0"

    fun build(token: String): Map<String, String> = mapOf(
        "utoken" to token,
        "platform" to "android",
        "appVersion" to API_APP_VERSION,
        "channel" to "official",
        "machine" to API_MACHINE,
        "system" to API_SYSTEM_VERSION,
    )

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
