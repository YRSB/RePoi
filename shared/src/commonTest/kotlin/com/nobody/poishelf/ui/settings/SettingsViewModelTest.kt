package com.nobody.poishelf.ui.settings

import com.nobody.poishelf.data.local.TestDataStore
import com.nobody.poishelf.data.local.ViewPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 设置 ViewModel 测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `SettingsViewModelTest`（逐用例；Mockito `mock(ViewPreferencesManager)` →
 * 真实 ViewPreferencesManager（[TestDataStore] 隔离目录）；`verify set` →
 * 真实持久化断言；`MainDispatcherRule` → `Dispatchers.setMain`；
 * JUnit4 → kotlin.test + `runTest`）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun preferences(): ViewPreferencesManager =
        ViewPreferencesManager(TestDataStore.viewPrefs())

    @Test
    fun initialValue_readsSynchronousPreference() = runTest(testDispatcher) {
        val prefs = preferences()
        prefs.setExHobbyEnabled(true)
        val viewModel = SettingsViewModel(prefs)

        assertTrue(viewModel.isExHobbyEnabled.value)
    }

    @Test
    fun setExHobbyEnabled_persistsPreference() = runTest(testDispatcher) {
        val prefs = preferences()
        val viewModel = SettingsViewModel(prefs)

        viewModel.setExHobbyEnabled(false)
        advanceUntilIdle()

        assertFalse(prefs.isExHobbyEnabled)
        assertFalse(prefs.isExHobbyEnabledFlow.first())
    }

    @Test
    fun upstreamFlow_updatesExposedState() = runTest(testDispatcher) {
        val prefs = preferences()
        prefs.setExHobbyEnabled(true)
        val viewModel = SettingsViewModel(prefs)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.isExHobbyEnabled.collect {}
        }

        prefs.setExHobbyEnabled(false)
        advanceUntilIdle()

        assertFalse(viewModel.isExHobbyEnabled.value)
        collector.cancel()
    }

    @Test
    fun firstCollection_startsWhileSubscribedFlow() = runTest(testDispatcher) {
        val prefs = preferences()
        prefs.setExHobbyEnabled(true)
        val viewModel = SettingsViewModel(prefs)

        val value = viewModel.isExHobbyEnabled.first()

        assertTrue(value)
    }
}
