package com.nobody.repoi

import android.app.Application
import com.nobody.repoi.di.initKoin
import com.nobody.repoi.platform.initPoiShelfPlatformContext
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

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
