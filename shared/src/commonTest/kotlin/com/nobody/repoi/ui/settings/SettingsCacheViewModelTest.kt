package com.nobody.repoi.ui.settings

import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.nobody.repoi.ui.settings.panes.formatBytes

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsCacheViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private class FakeDiskCache(var sizeBytes: Long = 12_582_912L) : DiskCache {
        var cleared = false
        var failClear = false

        override val size: Long get() = sizeBytes
        override val maxSize: Long get() = 250L * 1024L * 1024L
        override val directory: Path get() = "/tmp".toPath()
        override val fileSystem: FileSystem get() = FileSystem.SYSTEM

        override fun openSnapshot(key: String): DiskCache.Snapshot? = null
        override fun openEditor(key: String): DiskCache.Editor? = null
        override fun remove(key: String): Boolean = true
        override fun clear() {
            if (failClear) throw IllegalStateException("io")
            cleared = true
            sizeBytes = 0L
        }
        override fun shutdown() {}
    }

    @Test
    fun initialState_readsCacheSize() = runTest(testDispatcher) {
        val cache = FakeDiskCache()
        val viewModel = SettingsCacheViewModel(cache, testDispatcher)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(12_582_912L, viewModel.uiState.value.sizeBytes)
    }

    @Test
    fun clearCache_clearsAndReloadsSize() = runTest(testDispatcher) {
        val cache = FakeDiskCache()
        val viewModel = SettingsCacheViewModel(cache, testDispatcher)
        advanceUntilIdle()

        viewModel.clearCache()
        advanceUntilIdle()

        assertTrue(cache.cleared)
        assertEquals(0L, viewModel.uiState.value.sizeBytes)
        assertFalse(viewModel.uiState.value.isClearing)
        assertEquals("图片缓存已清理", viewModel.uiState.value.actionMessage)
    }

    @Test
    fun clearCache_whenFails_reportsMessage() = runTest(testDispatcher) {
        val cache = FakeDiskCache().apply { failClear = true }
        val viewModel = SettingsCacheViewModel(cache, testDispatcher)
        advanceUntilIdle()

        viewModel.clearCache()
        advanceUntilIdle()

        assertEquals("缓存清理失败请重试", viewModel.uiState.value.actionMessage)
    }

    @Test
    fun refresh_preservesUnconsumedActionMessage() = runTest(testDispatcher) {
        val cache = FakeDiskCache()
        val viewModel = SettingsCacheViewModel(cache, testDispatcher)
        advanceUntilIdle()
        viewModel.clearCache()
        advanceUntilIdle()
        assertEquals("图片缓存已清理", viewModel.uiState.value.actionMessage)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals("图片缓存已清理", viewModel.uiState.value.actionMessage)
    }

    @Test
    fun clearCache_whenSizeReadFailsAfterSuccess_reportsZero() = runTest(testDispatcher) {
        val cache = object : DiskCache by FakeDiskCache() {
            override val size: Long get() = throw IllegalStateException("stat")
        }
        val viewModel = SettingsCacheViewModel(cache, testDispatcher)
        advanceUntilIdle()
        viewModel.clearCache()
        advanceUntilIdle()

        assertEquals(0L, viewModel.uiState.value.sizeBytes)
        assertEquals("图片缓存已清理", viewModel.uiState.value.actionMessage)
    }

    @Test
    fun upstreamClear_updatesExposedState() = runTest(testDispatcher) {
        val cache = FakeDiskCache()
        val viewModel = SettingsCacheViewModel(cache, testDispatcher)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        cache.sizeBytes = 1024L
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1024L, viewModel.uiState.value.sizeBytes)
        collector.cancel()
    }

    @Test
    fun consumeMessage_clearsActionMessage() = runTest(testDispatcher) {
        val cache = FakeDiskCache()
        val viewModel = SettingsCacheViewModel(cache, testDispatcher)
        advanceUntilIdle()
        viewModel.clearCache()
        advanceUntilIdle()

        viewModel.consumeMessage()

        assertNull(viewModel.uiState.value.actionMessage)
    }

    @Test
    fun updateMaxSizeBytes_updatesState() = runTest(testDispatcher) {
        val cache = FakeDiskCache()
        val viewModel = SettingsCacheViewModel(cache, testDispatcher)
        advanceUntilIdle()

        viewModel.updateMaxSizeBytes(1024L * 1024L * 1024L)
        advanceUntilIdle()

        assertEquals(1024L * 1024L * 1024L, viewModel.uiState.value.maxSizeBytes)
    }

    @Test
    fun formatBytes_formatsUnitsCorrectly() {
        assertEquals("未知", formatBytes(-1))
        assertEquals("500 B", formatBytes(500))
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("10.0 MB", formatBytes(10L * 1024L * 1024L))
        assertEquals("500.0 MB", formatBytes(500L * 1024L * 1024L))
        assertEquals("1.50 GB", formatBytes((1.5 * 1024 * 1024 * 1024).toLong()))
    }
}
