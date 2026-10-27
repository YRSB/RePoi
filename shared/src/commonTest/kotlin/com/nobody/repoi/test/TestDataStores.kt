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

    fun createDataStore(name: String): DataStore<Preferences> {
        // 文件名必须带 .preferences_pb 后缀（PreferenceDataStoreFactory 强制检查），
        // 且每个文件全局唯一（DataStore 禁止同文件多实例；JUnit 每个方法 new 一个
        // 测试类实例，实例计数器会重置，因此用进程级计数 + 时间戳）。
        val id = nextId.getAndIncrement()
        val file = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
            .resolve("repoi-test-${id}-${currentTimeMillis()}-$name.preferences_pb")
        created.add(file)
        return PreferenceDataStoreFactory.createWithPath(produceFile = { file })
    }

    fun cleanUp() {
        created.forEach { runCatching { FileSystem.SYSTEM.delete(it) } }
        created.clear()
    }

    companion object {
        private val nextId = kotlin.concurrent.atomics.AtomicInt(0)

        private fun currentTimeMillis(): Long =
            kotlin.time.Clock.System.now().toEpochMilliseconds()
    }
}
