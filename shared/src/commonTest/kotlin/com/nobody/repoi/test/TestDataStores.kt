package com.nobody.repoi.test

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * commonTest 用的 DataStore 内存替身：`MutableStateFlow` 背板 + `Mutex` 串行更新。
 *
 * 真 DataStore（[PreferenceDataStoreFactory] 文件版）的读写跑在自有 IO 作用域：
 * `edit` 的完成信号在真线程，`runTest` 的 `advanceUntilIdle()` 驱动不了——
 * VM 内 `launch { manager.setX() }` 的测试断言会读到旧值，且 IO 协程可能泄漏到
 * 下一个测试（`tearDown/resetMain` 后仍访问 Main，见 SearchViewModelTest r5 的
 * `UncaughtExceptionsBeforeTest`）。Fake 的 `updateData` 全程跑在调用者协程
 * （虚拟时钟），`advanceUntilIdle()` 可完整驱动，无竞态、无真 IO、无泄漏。
 *
 * 对标 [PodAura](https://github.com/SkyD666/PodAura) 的 DataStore 测试实践
 * （`MutableStateFlow` 假实现）。
 */
class FakeDataStore : DataStore<Preferences> {

    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        return mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
    }
}

/**
 * commonTest 用的 DataStore 测试构造：每个 store 独立内存实例，天然隔离，
 * 无需文件、无需清理（[cleanUp] 保留为 no-op，仅防调用方编译错）。
 */
class TestDataStores {

    fun createDataStore(name: String): DataStore<Preferences> {
        // name 仅保留作调试标识；内存 Fake 按实例隔离，无跨测试污染。
        return FakeDataStore()
    }

    fun cleanUp() {
        // 内存 Fake 无需清理。
    }
}
