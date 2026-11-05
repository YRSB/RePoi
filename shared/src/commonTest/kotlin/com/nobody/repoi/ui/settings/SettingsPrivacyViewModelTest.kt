package com.nobody.repoi.ui.settings

import com.nobody.repoi.data.repository.FakeTokenManager
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPrivacyViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        mysqlBody: String = """{"userState":{"setting_collect_hobby":1,"setting_collect_album":0,"setting_collect_statistics":2,"setting_collect_other":1,"setting_privacy_friend":0}}""",
        riakBody: String = """{"userState":{"setting_own_action":2,"setting_privacy_posts":1,"setting_stranger_message":0}}"""
    ): Triple<MockApi, SettingsRepository, SettingsPrivacyViewModel> {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.ok(mysqlBody))
        mock.route("api/setting/riak/get", mock.ok(riakBody))
        val repository = SettingsRepository(mock.api(testDispatcher), FakeTokenManager("utoken-test"))
        return Triple(mock, repository, SettingsPrivacyViewModel(repository))
    }

    @Test
    fun initialLoad_mergesMysqlAndRiakState() = runTest(testDispatcher) {
        val (_, _, vm) = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(1, state.values["setting_collect_hobby"])
        assertEquals(0, state.values["setting_collect_album"])
        assertEquals(2, state.values["setting_collect_statistics"])
        assertEquals(1, state.values["setting_collect_other"])
        assertEquals(0, state.values["setting_privacy_friend"])
        assertEquals(2, state.values["setting_own_action"])
        assertEquals(1, state.values["setting_privacy_posts"])
        assertEquals(0, state.values["setting_stranger_message"])
    }

    @Test
    fun setMysqlOption_optimisticThenPersistsViaMysql() = runTest(testDispatcher) {
        val (mock, _, vm) = viewModel()
        advanceUntilIdle()
        mock.takeRequest()
        mock.takeRequest()
        mock.route("api/setting/mysql/upd", mock.ok("{}"))

        vm.setOption("setting_collect_hobby", 2)
        assertEquals(2, vm.uiState.value.values["setting_collect_hobby"])
        advanceUntilIdle()

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/setting/mysql/upd", recorded.encodedPath)
        assertEquals("setting_collect_hobby", recorded.formEntries()["key"])
        assertEquals("2", recorded.formEntries()["val"])
        assertTrue(vm.uiState.value.savingKeys.isEmpty())
    }

    @Test
    fun setRiakOption_persistsViaRiak() = runTest(testDispatcher) {
        val (mock, _, vm) = viewModel()
        advanceUntilIdle()
        mock.takeRequest()
        mock.takeRequest()
        mock.route("api/setting/riak/upd", mock.ok("{}"))

        vm.setOption("setting_stranger_message", 1)
        advanceUntilIdle()

        val recorded = mock.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/setting/riak/upd", recorded.encodedPath)
        assertEquals("setting_stranger_message", recorded.formEntries()["key"])
        assertEquals("1", recorded.formEntries()["val"])
        assertEquals(1, vm.uiState.value.values["setting_stranger_message"])
    }

    @Test
    fun setOption_failureRestoresPreviousValue() = runTest(testDispatcher) {
        val (mock, _, vm) = viewModel()
        advanceUntilIdle()
        mock.takeRequest()
        mock.takeRequest()

        mock.route("api/setting/mysql/upd", mock.failure(500, "写入失败"))
        vm.setOption("setting_privacy_friend", 2)
        advanceUntilIdle()

        assertEquals(0, vm.uiState.value.values["setting_privacy_friend"])
        assertTrue(vm.uiState.value.savingKeys.isEmpty())
    }

    @Test
    fun refresh_partialFailureKeepsLoadedKeysAndSurfacesMessage() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.ok("""{"userState":{"setting_collect_hobby":1}}"""))
        mock.route("api/setting/riak/get", mock.failure(500, "读取失败"))
        val repository = SettingsRepository(mock.api(testDispatcher), FakeTokenManager("utoken-test"))
        val vm = SettingsPrivacyViewModel(repository)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.values["setting_collect_hobby"])
        assertTrue(state.errorMessage?.isNotEmpty() == true)
    }

    @Test
    fun refresh_partialFailureAfterSuccessRetainsPreviousChannelValues() = runTest(testDispatcher) {
        val mock = MockApi()
        mock.route("api/setting/mysql/get", mock.ok("""{"userState":{"setting_collect_hobby":1}}"""))
        mock.route("api/setting/riak/get", mock.ok("""{"userState":{"setting_own_action":2}}"""))
        val repository = SettingsRepository(mock.api(testDispatcher), FakeTokenManager("utoken-test"))
        val vm = SettingsPrivacyViewModel(repository)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.values["setting_own_action"])

        mock.replaceRoute("api/setting/mysql/get", mock.failure(500, "抖动"))
        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.values["setting_collect_hobby"])
        assertEquals(2, state.values["setting_own_action"])
        assertTrue(state.errorMessage?.isNotEmpty() == true)
    }

    @Test
    fun setOption_concurrentSameKeySecondWriteIgnored() = runTest(testDispatcher) {
        val (mock, _, vm) = viewModel()
        advanceUntilIdle()
        mock.takeRequest()
        mock.takeRequest()
        val gate = CompletableDeferred<Unit>()
        mock.routeWhenSuspend(
            predicate = { path, _, _ -> path.contains("api/setting/mysql/upd") },
            body = { _, _, _ -> gate.await(); "{}" }
        )

        vm.setOption("setting_collect_hobby", 2)
        vm.setOption("setting_collect_hobby", 0)
        assertEquals(2, vm.uiState.value.values["setting_collect_hobby"])

        gate.complete(Unit)
        advanceUntilIdle()

        val recorded = mock.takeRequest()
        assertEquals("/api/setting/mysql/upd", recorded.encodedPath)
        assertEquals("2", recorded.formEntries()["val"])
        assertEquals(0, mock.requestCount)
        assertEquals(2, vm.uiState.value.values["setting_collect_hobby"])
        assertTrue(vm.uiState.value.savingKeys.isEmpty())
    }
}

