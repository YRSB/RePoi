package com.nobody.repoi.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * commonTest DataStore 测试脚手架（JVM/Android-host 双目标可用）。
 *
 * 两类实例，按测试目的选择：
 * - **真实文件**（[create] 及 `xxxPrefs()`）：每个用例独占 `SYSTEM_TEMPORARY_DIRECTORY` 下的唯一子目录，
 *   背靠生产同款 `PreferenceDataStoreFactory`，用于冷启动恢复、防抖落盘、跨实例预热等持久化契约；
 *   文件名沿用 `DataStoreFiles.*`，保证 key 命名空间与线上一致。
 * - **内存实例**（[inMemory]）：无文件、无内部协程作用域，`edit` 在调用方协程内完成。
 *   ViewModel 测试默认用它，避免真实 I/O 的续体在 `resetMain()` 之后恢复而污染后续测试。
 *
 * 真实文件实例可显式传入 [CoroutineScope]：该 scope 归测试所有，`@AfterTest` 取消它即可回收
 * DataStore 的内部写协程；不传时沿用 DataStore 默认 IO scope。
 */
object TestDataStore {

    fun create(fileName: String, scope: CoroutineScope? = null): DataStore<Preferences> {
        val dir = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "repoi-test-datastore-${Random.nextLong()}").toString()
        val produceFile = { dir.toPath().resolve(fileName) }
        return if (scope == null) {
            PreferenceDataStoreFactory.createWithPath(produceFile = produceFile)
        } else {
            PreferenceDataStoreFactory.createWithPath(produceFile = produceFile, scope = scope)
        }
    }

    fun userPrefs(scope: CoroutineScope? = null): DataStore<Preferences> =
        create(DataStoreFiles.USER_PREFS, scope)

    fun themePrefs(scope: CoroutineScope? = null): DataStore<Preferences> =
        create(DataStoreFiles.THEME_PREFS, scope)

    fun searchHistoryPrefs(scope: CoroutineScope? = null): DataStore<Preferences> =
        create(DataStoreFiles.SEARCH_HISTORY_PREFS, scope)

    fun viewPrefs(scope: CoroutineScope? = null): DataStore<Preferences> =
        create(DataStoreFiles.VIEW_PREFS, scope)

    fun coverFreshnessPrefs(scope: CoroutineScope? = null): DataStore<Preferences> =
        create(DataStoreFiles.COVER_FRESHNESS, scope)

    /** 纯内存 DataStore：`edit` 立即在调用方协程内生效，不产生真实 I/O 与后台写协程。 */
    fun inMemory(): DataStore<Preferences> = InMemoryPreferencesDataStore()
}

/**
 * 等待 DataStore 处理完已排队的真实文件写入。
 *
 * `data.first()` 可能直接命中内存缓存，看不到「已入队、尚未落定」的写入；空 `edit` 会排在
 * 所有在途写入之后，作为可观测的同步屏障。`runTest` 的虚拟时间推不动 DataStore 的真实 I/O，
 * 因此断言落盘前必须先过这道屏障。纯内存实例不需要它。
 */
suspend fun DataStore<Preferences>.awaitPendingWrites() {
    edit { }
}

private class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)
    private val guard = Mutex()

    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        guard.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
}

internal fun tempPathOf(name: String): String =
    (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "repoi-test-$name").toString().also {
        FileSystem.SYSTEM.createDirectories(it.toPath())
    }
