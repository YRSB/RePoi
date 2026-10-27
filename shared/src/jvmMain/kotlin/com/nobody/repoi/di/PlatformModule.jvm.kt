package com.nobody.repoi.di

import com.nobody.repoi.platform.JvmPlatformActions
import com.nobody.repoi.platform.PlatformActions
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<PlatformActions> { JvmPlatformActions() }
}
