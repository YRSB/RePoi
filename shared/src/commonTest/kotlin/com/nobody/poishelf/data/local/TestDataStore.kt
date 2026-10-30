package com.nobody.poishelf.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlin.random.Random
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * commonTest DataStore 测试脚手架（JVM/Android-host 双目标可用）。
 *
 * - 每个用例独占一个 `SYSTEM_TEMPORARY_DIRECTORY` 下的唯一子目录，
 *   背靠真实 `createDataStore(dirPath lambda)`（生产同款 `PreferenceDataStoreFactory`），
 *   不走 Robolectric/ApplicationProvider。
 * - 文件名沿用生产 `DataStoreFiles.*` 常量，保证 key 命名空间与线上一致。
 */
object TestDataStore {

    fun create(fileName: String): DataStore<Preferences> {
        val dir = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "repoi-test-datastore-${Random.nextLong()}").toString()
        return createDataStore(dirPath = { dir }, fileName = fileName)
    }

    fun userPrefs(): DataStore<Preferences> = create(DataStoreFiles.USER_PREFS)

    fun themePrefs(): DataStore<Preferences> = create(DataStoreFiles.THEME_PREFS)

    fun searchHistoryPrefs(): DataStore<Preferences> = create(DataStoreFiles.SEARCH_HISTORY_PREFS)

    fun viewPrefs(): DataStore<Preferences> = create(DataStoreFiles.VIEW_PREFS)
}

internal fun tempPathOf(name: String): String =
    (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "repoi-test-$name").toString().also {
        FileSystem.SYSTEM.createDirectories(it.toPath())
    }
