package com.nobody.repoi.test

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.FileSystem
import okio.Path

/**
 * commonTest 用的 DataStore 测试构造：每个 store 独占临时目录下的唯一文件，
 * 测试结束后删除，避免跨测试/跨 target 污染。
 */
class TestDataStores {

    private val created = mutableListOf<Path>()
    private var counter = 0

    fun createDataStore(name: String): DataStore<Preferences> {
        val file = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
            .resolve("repoi-test-${(counter++).toString()}-$name")
        created.add(file)
        return PreferenceDataStoreFactory.createWithPath(produceFile = { file })
    }

    fun cleanUp() {
        created.forEach { runCatching { FileSystem.SYSTEM.delete(it) } }
        created.clear()
    }
}
