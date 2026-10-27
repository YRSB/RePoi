package com.nobody.repoi.di

import com.nobody.repoi.platform.AndroidPlatformActions
import com.nobody.repoi.platform.PlatformActions
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android 平台模块。Application Context 由壳在 startKoin 时经
 * Koin-Android 的 `androidContext()` 声明，此处仅绑定接口实现。
 */
actual val platformModule: Module = module {
    single<PlatformActions> { AndroidPlatformActions(context = get()) }
}
