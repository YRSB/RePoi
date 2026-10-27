package com.nobody.repoi.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * 启动 Koin 并加载全部模块。各端壳在入口处调用一次。
 *
 * Android 壳需在 [appDeclaration] 内调用 Koin-Android 的 `androidContext(...)`，
 * 以供给 `Context` 依赖。
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(
        networkModule,
        dataModule,
        platformModule,
        discoveryModule
    )
}
