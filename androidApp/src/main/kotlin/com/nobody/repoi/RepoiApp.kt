package com.nobody.repoi

import android.app.Application
import com.nobody.repoi.di.initKoin
import com.nobody.repoi.platform.initPoiShelfPlatformContext
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

/**
 * M2 thin shell：初始化平台上下文 + Koin（common `initKoin` + android 扩展）。
 * 无业务逻辑；`MainActivity` 保持调用 demo `App()`（M6 替换为共用根组合）。
 */
class RepoiApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initPoiShelfPlatformContext(this)
        initKoin {
            androidLogger()
            androidContext(this@RepoiApp)
        }
    }
}
