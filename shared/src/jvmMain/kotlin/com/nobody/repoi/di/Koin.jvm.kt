package com.nobody.repoi.di

import com.nobody.repoi.data.local.DataStoreFiles
import com.nobody.repoi.data.local.createDataStore
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * 平台 DataStore 模块（jvm）：应用数据目录 `files/`（见 `AppDirs.jvm`），
 * 文件名与 Android 侧同名（桌面为新装，无迁移问题）。
 */
actual val dataStoreModule: Module
    get() = module {
        single(named("user_prefs")) {
            createDataStore(
                dirPath = { com.nobody.repoi.platform.filesDirPath() },
                fileName = DataStoreFiles.USER_PREFS,
            )
        }
        single(named("theme_prefs")) {
            createDataStore(
                dirPath = { com.nobody.repoi.platform.filesDirPath() },
                fileName = DataStoreFiles.THEME_PREFS,
            )
        }
        single(named("search_history_prefs")) {
            createDataStore(
                dirPath = { com.nobody.repoi.platform.filesDirPath() },
                fileName = DataStoreFiles.SEARCH_HISTORY_PREFS,
            )
        }
        single(named("view_prefs")) {
            createDataStore(
                dirPath = { com.nobody.repoi.platform.filesDirPath() },
                fileName = DataStoreFiles.VIEW_PREFS,
            )
        }
    }
