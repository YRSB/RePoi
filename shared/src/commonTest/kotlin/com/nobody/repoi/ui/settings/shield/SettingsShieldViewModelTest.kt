package com.nobody.repoi.ui.settings.shield

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.data.session.SessionState
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun repositoryOf(mock: MockApi, token: String = "utoken-test"): SettingsRepository =
        SettingsRepository(mock.api(testDispatcher), FakeUserSession(token))

    @Test
    fun initialLoad_r18Shown_mapsVisibleTrue() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.ok("""{"userState":{"setting_r18":2}}"""))

        val viewModel = SettingsShieldViewModel(repositoryOf(mock))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.nsfwVisible)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun unauthenticated_skipsNetworkAndDefaultsHidden() = runTest(testDispatcher) {
        val mock = MockApi()
        val viewModel = SettingsShieldViewModel(repositoryOf(mock, token = ""))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.nsfwVisible)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(0, mock.requestCount)
    }

    @Test
    fun tokenChanges_triggersReloadAndReset() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.ok("""{"userState":{"setting_r18":2}}"""))
        val session = FakeUserSession()
        val repo = SettingsRepository(mock.api(testDispatcher), session)
        val viewModel = SettingsShieldViewModel(repo)
        advanceUntilIdle()
        assertEquals(0, mock.requestCount)

        session.setState(SessionState.Authenticated(userId = 0L, token = "new-token"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.nsfwVisible)

        session.setState(SessionState.Unauthenticated)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.nsfwVisible)
        assertFalse(viewModel.uiState.value.isLoading)
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

        val request = mock.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/setting/mysql/upd", request.encodedPath)
        assertEquals("setting_r18", request.formEntries()["key"])
        assertEquals("1", request.formEntries()["val"])
        assertFalse(viewModel.uiState.value.nsfwVisible)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun setNsfwVisible_failureRollsBackStateAndSurfacesError() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.ok("""{"userState":{"setting_r18":2}}"""))
        mock.route("api/setting/mysql/upd", mock.failure(500, "保存失败"))

        val viewModel = SettingsShieldViewModel(repositoryOf(mock))
        advanceUntilIdle()
        mock.takeRequest()

        viewModel.setNsfwVisible(false)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.nsfwVisible)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("保存失败", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun clearErrorMessage_clearsState() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.failure(500, "网络异常"))

        val viewModel = SettingsShieldViewModel(repositoryOf(mock))
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.clearErrorMessage()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun setNsfwVisible_optimisticStateBeforeCompletion() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.ok("""{"userState":{"setting_r18":1}}"""))
        val gate = CompletableDeferred<Unit>()
        mock.routeWhenSuspend(
            predicate = { path, _, _ -> path.contains("api/setting/mysql/upd") },
            body = { _, _, _ -> gate.await(); mock.ok("{}") }
        )

        val viewModel = SettingsShieldViewModel(repositoryOf(mock))
        advanceUntilIdle()
        mock.takeRequest()

        viewModel.setNsfwVisible(true)
        assertTrue(viewModel.uiState.value.nsfwVisible)
        assertTrue(viewModel.uiState.value.isSaving)

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.nsfwVisible)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun setNsfwVisible_concurrentToggleSecondWriteIgnored() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.ok("""{"userState":{"setting_r18":1}}"""))
        val gate = CompletableDeferred<Unit>()
        mock.routeWhenSuspend(
            predicate = { path, _, _ -> path.contains("api/setting/mysql/upd") },
            body = { _, _, _ -> gate.await(); mock.ok("{}") }
        )

        val viewModel = SettingsShieldViewModel(repositoryOf(mock))
        advanceUntilIdle()
        mock.takeRequest()

        viewModel.setNsfwVisible(true)
        viewModel.setNsfwVisible(false)
        assertTrue(viewModel.uiState.value.nsfwVisible)

        gate.complete(Unit)
        advanceUntilIdle()

        val recorded = mock.takeRequest()
        assertEquals("2", recorded.formEntries()["val"])
        assertEquals(0, mock.requestCount)
        assertTrue(viewModel.uiState.value.nsfwVisible)
        assertFalse(viewModel.uiState.value.isSaving)
    }
}
