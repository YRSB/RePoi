package com.nobody.repoi.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

/**
 * Koin 启动入口（PodAura `Koin.kt` 同形）。
 *
 * - common 侧装配全部平台无关模块；
 * - `dataStoreModule` 为平台 expect（4 具名 DataStore，文件名与 Android 侧一致）；
 * - shell 以 `appDeclaration` 注入平台扩展：
 *   androidApp → `androidLogger() + androidContext(...)`（koin-android），
 *   desktopApp → 空声明。
 */
fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(
        networkModule,
        dataStoreModule,
        managerModule,
        repositoryModule,
        viewModelModule,
        imageModule,
    )
}

/** 平台 DataStore 模块：4 具名 `DataStore<Preferences>`（见 `ManagerModule`）。 */
expect val dataStoreModule: Module
