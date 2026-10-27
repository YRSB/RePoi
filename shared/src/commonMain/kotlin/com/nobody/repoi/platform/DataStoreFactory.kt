package com.nobody.repoi.platform

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

/**
 * 各端 DataStore 文件目录：Android = filesDir/datastore，JVM = `~/.repoi/`。
 */
expect fun appDataDir(): String

/**
 * 创建指定文件名的 Preferences DataStore。
 * 老项目 4 个 store（user_prefs/theme_prefs/search_history_prefs/view_prefs）在 Wave 1 接入。
 */
fun createDataStore(fileName: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { appDataDir().toPath().resolve(fileName) }
    )
