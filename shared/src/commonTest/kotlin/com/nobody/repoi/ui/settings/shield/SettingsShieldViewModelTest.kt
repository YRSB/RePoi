package com.nobody.repoi.ui.settings.shield

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.repository.FakeTokenManager
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsShieldViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun repositoryOf(mock: MockApi): SettingsRepository =
        SettingsRepository(mock.api(), FakeTokenManager("utoken-test"))

    @Test
    fun initialLoad_r18Shown_mapsVisibleTrue() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.ok("""{"userState":{"setting_r18":2}}"""))

        val viewModel = SettingsShieldViewModel(repositoryOf(mock))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        println("DIAG-7f3a shield nsfw=${state.nsfwVisible} loading=${state.isLoading} err=${state.errorMessage} reqCount=${mock.requestCount}")
        assertTrue(state.nsfwVisible)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun initialLoad_r18Hidden_mapsVisibleFalse() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.ok("""{"userState":{"setting_r18":1}}"""))

        val viewModel = SettingsShieldViewModel(repositoryOf(mock))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.nsfwVisible)
    }

    @Test
    fun initialLoad_missingKey_defaultsHidden() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.ok("""{"userState":{}}"""))

        val viewModel = SettingsShieldViewModel(repositoryOf(mock))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.nsfwVisible)
        assertFalse(state.isLoading)
    }

    @Test
    fun initialLoad_failureSetsError() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.failure(500, "网络异常"))

        val viewModel = SettingsShieldViewModel(repositoryOf(mock))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun setNsfwVisible_postsSettingR18Value() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.ok("""{"userState":{"setting_r18":2}}"""))
        mock.route("api/setting/mysql/upd", mock.ok("{}"))

        val viewModel = SettingsShieldViewModel(repositoryOf(mock))
        advanceUntilIdle()
        mock.takeRequest()

        viewModel.setNsfwVisible(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.nsfwVisible)
        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/setting/mysql/upd", recorded.encodedPath)
        assertEquals("setting_r18", recorded.formEntries()["key"])
        assertEquals("1", recorded.formEntries()["val"])
    }

    @Test
    fun setNsfwVisible_enablePostsValueTwo() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.ok("""{"userState":{"setting_r18":1}}"""))
        mock.route("api/setting/mysql/upd", mock.ok("{}"))

        val viewModel = SettingsShieldViewModel(repositoryOf(mock))
        advanceUntilIdle()
        mock.takeRequest()

        viewModel.setNsfwVisible(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.nsfwVisible)
        assertEquals("2", mock.takeRequest().formEntries()["val"])
    }

    @Test
    fun setNsfwVisible_failureRollsBackAndSetsError() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.ok("""{"userState":{"setting_r18":2}}"""))
        mock.route("api/setting/mysql/upd", mock.failure(500, "网络异常"))

        val viewModel = SettingsShieldViewModel(repositoryOf(mock))
        advanceUntilIdle()
        mock.takeRequest()

        viewModel.setNsfwVisible(false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.nsfwVisible)
        assertFalse(state.isSaving)
        assertNotNull(state.errorMessage)
    }

    @Test
    fun clearErrorMessage_dismissesError() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.failure(500, "网络异常"))

        val viewModel = SettingsShieldViewModel(repositoryOf(mock))
        advanceUntilIdle()
        viewModel.clearErrorMessage()

        assertNull(viewModel.uiState.value.errorMessage)
    }
}
