package com.nobody.repoi.di

import com.nobody.repoi.platform.AndroidPlatformActions
import com.nobody.repoi.platform.ImagePickerLauncher
import com.nobody.repoi.platform.PlatformActions
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android 平台模块。Application Context 由壳在 startKoin 时经
 * Koin-Android 的 `androidContext()` 声明；图片选择 launcher 由
 * MainActivity 注册后经 [imagePickerModule] 注入。
 */
actual val platformModule: Module = module {
    single<PlatformActions> { AndroidPlatformActions(context = get(), imagePicker = get()) }
}

/**
 * 壳注入图片选择 launcher 的模块（MainActivity 注册后传入）。
 */
fun imagePickerModule(picker: ImagePickerLauncher): Module = module {
    single { picker }
}
