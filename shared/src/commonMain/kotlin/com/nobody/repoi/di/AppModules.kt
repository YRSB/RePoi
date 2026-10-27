package com.nobody.repoi.di

import coil3.ImageLoader
import com.nobody.repoi.network.appJson
import com.nobody.repoi.network.createExHobbyClient
import com.nobody.repoi.network.createHpoiClient
import com.nobody.repoi.network.createImageClient
import com.nobody.repoi.platform.createImageLoader
import com.nobody.repoi.platform.platformContext
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * 网络模块：Json + 三个 HttpClient + Coil ImageLoader。
 *
 * Token 提供方在 Wave 1（TokenManager 迁移）后接入；Wave 0 返回空串占位。
 * `HpoiApi`/`ExHobbyApi` 在 Wave 3 挂到对应 client 上。
 */
val networkModule = module {
    single<Json> { appJson() }

    // TODO(Wave 1): tokenProvider 改为 { get<TokenManager>().currentToken }。
    single<() -> String>(named("tokenProvider")) { { "" } }

    single<HttpClient>(named("hpoi")) {
        createHpoiClient(json = get(), tokenProvider = get(named("tokenProvider")))
    }
    single<HttpClient>(named("image")) {
        createImageClient()
    }
    single<HttpClient>(named("exhobby")) {
        createExHobbyClient(json = get())
    }

    single<ImageLoader> {
        createImageLoader(
            context = platformContext(),
            imageHttpClient = get(named("image"))
        )
    }
}

/**
 * 数据模块：DataStore / Managers / Repositories。
 * Wave 0 为空壳，按波次陆续填充（Wave 1 起）。
 */
val dataModule = module {
    // Wave 1+: DataStore singles + TokenManager/ThemeManager/...
    // Wave 4+: Repositories
}

/**
 * 各端平台模块：PlatformActions、Context（Android）等。
 */
expect val platformModule: Module
