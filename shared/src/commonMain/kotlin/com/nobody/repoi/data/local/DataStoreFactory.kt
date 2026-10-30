package com.nobody.repoi.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

/**
 * DataStore 路径 lambda 工厂（PodAura `createDataStore` 同形）。
 *
 * Android 实际传入 `filesDir`；JVM 传入应用数据目录（见 `AppDirs`）。
 * 文件名保持 `<name>.preferences_pb`（AGP `preferencesDataStore` 约定），
 * 保证 Android 端升级后数据连续。
 */
fun createDataStore(dirPath: () -> String, fileName: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { dirPath().toPath().resolve(fileName) },
    )

object DataStoreFiles {
    const val USER_PREFS = "user_prefs.preferences_pb"
    const val THEME_PREFS = "theme_prefs.preferences_pb"
    const val SEARCH_HISTORY_PREFS = "search_history_prefs.preferences_pb"
    const val VIEW_PREFS = "view_prefs.preferences_pb"
}
