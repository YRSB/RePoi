package com.nobody.repoi.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 平台共享网络引擎（Desktop JVM）：
 * 单例 OkHttpClient 承载连接池与调度线程池，供多命名 Ktor Client 共享复用。
 */
actual fun createSharedHttpClientEngine(): HttpClientEngine {
    val okHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
        .dispatcher(Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        })
        .build()

    return OkHttp.create {
        preconfigured = okHttpClient
    }
}
