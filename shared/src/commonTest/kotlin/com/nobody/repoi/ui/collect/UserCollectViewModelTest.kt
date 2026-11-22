package com.nobody.repoi.ui.collect

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.di.repositoryModule
import com.nobody.repoi.di.viewModelModule
import kotlinx.serialization.json.Json
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.nobody.repoi.data.remote.model.CollectData

@OptIn(ExperimentalCoroutinesApi::class)
class UserCollectViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val koinApps = mutableListOf<KoinApplication>()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        koinApps.forEach { it.close() }
        koinApps.clear()
        testDispatcher.cancelChildren()
        Dispatchers.resetMain()
    }

    private class Env(
        val mock: MockApi,
        val session: FakeUserSession,
        val collectRepository: CollectRepository,
        val viewPreferencesManager: ViewPreferencesManager,
        val koin: KoinApplication,
        val viewModel: UserCollectViewModel,
    )

    private fun buildEnv(
        mock: MockApi = MockApi(),
        loginUserId: Long = 999L,
        targetUserId: Long = 999L,
        viewPrefs: DataStore<Preferences> = TestDataStore.inMemory(),
        autoInit: Boolean = true,
        beforeVm: (KoinApplication) -> Unit = {},
    ): Env {
        val session = FakeUserSession("utoken-test", loginUserId)
        val app = koinApplication {
            modules(
                repositoryModule,
                viewModelModule,
                module {
                    single<Json> { mock.json }
                    single<HpoiApi> { mock.api(testDispatcher) }
                    single<UserSession> { session }
                    single { ViewPreferencesManager(get(named("view_prefs"))) }
                    single(named("view_prefs")) { viewPrefs }
                },
            )
        }
        koinApps += app
        beforeVm(app)
        val viewModel = app.koin.get<UserCollectViewModel>()

        if (autoInit) viewModel.initParam(UserCollectNavParam(userId = targetUserId))
        return Env(
            mock = mock,
            session = session,
            collectRepository = app.koin.get(),
            viewPreferencesManager = app.koin.get(),
            koin = app,
            viewModel = viewModel,
        )
    }

    private fun recreateViewModel(env: Env, targetUserId: Long = 999L): UserCollectViewModel =
        env.koin.koin.get<UserCollectViewModel>().also {
            it.initParam(UserCollectNavParam(userId = targetUserId))
        }

    private fun MockApi.routeCollectWrite() = route("api/collect/item/add", ok("{}"))

    private suspend fun TestScope.advanceMutationGeneration(collectRepository: CollectRepository) {
        assertTrue(collectRepository.collectItemAdd(nodeId = 9000L).isSuccess)
        advanceUntilIdle()
    }

    private suspend fun TestScope.awaitInitialized(viewModel: UserCollectViewModel) {
        repeat(200) {
            advanceUntilIdle()
            if (viewModel.uiState.value.isInitialized) return
            withContext(Dispatchers.Default) { delay(10) }
        }
        advanceUntilIdle()
        if (!viewModel.uiState.value.isInitialized) {
            throw AssertionError("ViewModel 初始化超时：排序记忆未能从 DataStore 读回")
        }
    }

    private fun listJson(items: String): String =
        """{"success":true,"code":200,"msg":"ok","data":{"list":[$items]}}"""

    private fun hobbyItem(id: Long): String =
        """{"id":$id,"itemId":${1000 + id},"nameCN":"手办$id"}"""

    private fun hobbies(count: Int, startId: Long = 1): String =
        listJson((0 until count).joinToString(",") { hobbyItem(startId + it) })

    private fun commonItem(id: Long): String =
        """{"id":$id,"itemId":${2000 + id},"nameCN":"条目$id"}"""

    private fun commons(count: Int, startId: Long = 1): String =
        listJson((0 until count).joinToString(",") { commonItem(startId + it) })

    private fun albumItem(id: Long): String =
        """{"id":$id,"name":"相册$id","title":"相册$id"}"""

    private fun albums(count: Int, startId: Long = 1): String =
        listJson((0 until count).joinToString(",") { albumItem(startId + it) })

    private fun picItem(id: Long): String =
        """{"id":$id,"path":"2026/09/${id}.jpg"}"""

    private fun pics(count: Int, startId: Long = 1): String =
        listJson((0 until count).joinToString(",") { picItem(startId + it) })

    private fun MockApi.routeHobby(body: (collectState: String?) -> String) {
        routeWhen(
            predicate = { path, _, _ -> path.contains("api/collect/hobby-v2") },
            body = { _, _, query -> body(query["collectState"]) }
        )
    }

    @Test
    fun initialSetup_matchesNavParameters() = runTest(testDispatcher) {
        val env = buildEnv(mock = MockApi().apply { enqueue(hobbies(2)) }, targetUserId = 888L)
        awaitInitialized(env.viewModel)

        val state = env.viewModel.uiState.value
        assertEquals(888L, env.viewModel.userId)
        assertEquals(UserCollectCategory.HOBBY, state.selectedCategory)
        assertFalse(state.hobbySelectionMode)
        assertTrue(state.hobbySelectedIds.isEmpty())
    }

    @Test
    fun initParam_sameParamTwice_loadsOnlyOnce() = runTest(testDispatcher) {
        val env = buildEnv(mock = MockApi().apply { enqueue(hobbies(2)) }, targetUserId = 888L)
        awaitInitialized(env.viewModel)
        val requestsAfterInit = env.mock.requestCount

        env.viewModel.initParam(
            UserCollectNavParam(
                userId = 888L,
                initialCollectState = CollectStateOption.DEFAULT,
                initialCategory = UserCollectCategory.HOBBY.key
            )
        )
        advanceUntilIdle()

        assertEquals(requestsAfterInit, env.mock.requestCount)
    }

    @Test
    fun switchCategory_updatesSelectedCategory() = runTest(testDispatcher) {
        val env = buildEnv(
            mock = MockApi().apply {
                enqueue(hobbies(1))
                enqueue(albums(1))
                enqueue(commons(1))
            }
        )
        awaitInitialized(env.viewModel)

        env.viewModel.onIntent(UserCollectIntent.SwitchCategory(UserCollectCategory.ALBUM))
        assertEquals(UserCollectCategory.ALBUM, env.viewModel.uiState.value.selectedCategory)
        advanceUntilIdle()
        assertEquals(1, env.viewModel.uiState.value.albumData.items.size)

        env.viewModel.onIntent(UserCollectIntent.SwitchCategory(UserCollectCategory.WORKS))
        assertEquals(UserCollectCategory.WORKS, env.viewModel.uiState.value.selectedCategory)
    }

    @Test
    fun loadFirstPage_publishesItemsAndHasMore() = runTest(testDispatcher) {
        val env = buildEnv(mock = MockApi().apply { enqueue(hobbies(UserCollectViewModel.PAGE_SIZE)) })
        awaitInitialized(env.viewModel)

        val hobby = env.viewModel.uiState.value.hobbyData
        assertEquals(UserCollectViewModel.PAGE_SIZE, hobby.items.size)

        assertTrue(hobby.hasMore)
        assertEquals(1, hobby.page)
        assertFalse(hobby.isLoading)
        assertNull(hobby.errorMessage)
    }

    @Test
    fun shortFirstPage_marksEndReached() = runTest(testDispatcher) {
        val env = buildEnv(mock = MockApi().apply { enqueue(hobbies(3)) })
        awaitInitialized(env.viewModel)

        val hobby = env.viewModel.uiState.value.hobbyData
        assertEquals(3, hobby.items.size)

        assertFalse(hobby.hasMore)
    }

    @Test
    fun loadNextPage_appendsDeduplicatedItemsAndAdvancesPage() = runTest(testDispatcher) {
        val env = buildEnv(
            mock = MockApi().apply {
                enqueue(hobbies(UserCollectViewModel.PAGE_SIZE))

                enqueue(hobbies(UserCollectViewModel.PAGE_SIZE, startId = 20))
            }
        )
        awaitInitialized(env.viewModel)

        env.viewModel.onIntent(UserCollectIntent.LoadNextPage(UserCollectCategory.HOBBY))
        advanceUntilIdle()

        val hobby = env.viewModel.uiState.value.hobbyData
        assertEquals(UserCollectViewModel.PAGE_SIZE * 2 - 1, hobby.items.size)
        assertEquals(2, hobby.page)
        assertTrue(hobby.hasMore)
        assertEquals(39L, hobby.items.last().id)
        assertFalse(hobby.isPaging)
    }

    @Test
    fun loadMore_failureKeepsPagingRetryable() = runTest(testDispatcher) {
        val env = buildEnv(
            mock = MockApi().apply {
                enqueue(hobbies(UserCollectViewModel.PAGE_SIZE))
                enqueue(failure(500, "网络开了小差"))
            }
        )
        awaitInitialized(env.viewModel)

        env.viewModel.onIntent(UserCollectIntent.LoadNextPage(UserCollectCategory.HOBBY))
        advanceUntilIdle()

        val hobby = env.viewModel.uiState.value.hobbyData

        assertEquals(UserCollectViewModel.PAGE_SIZE, hobby.items.size)
        assertEquals("网络开了小差", hobby.errorMessage)
        assertTrue(hobby.hasMore)
        assertFalse(hobby.isPaging)
    }

    @Test
    fun switchCollectState_reloadsWithRuntimeCondition() = runTest(testDispatcher) {
        val mock = MockApi().apply {
            routeHobby { collectState ->
                if (collectState == CollectStateOption.CARE) hobbies(1, startId = 501) else hobbies(2)
            }
        }
        val env = buildEnv(mock = mock)
        awaitInitialized(env.viewModel)
        assertEquals(2, env.viewModel.uiState.value.hobbyData.items.size)

        env.viewModel.onIntent(UserCollectIntent.SwitchCollectState(CollectStateOption.CARE))
        advanceUntilIdle()

        val hobby = env.viewModel.uiState.value.hobbyData
        assertEquals(CollectStateOption.CARE, env.viewModel.uiState.value.collectState)

        assertEquals(1, hobby.items.size)
        assertEquals(501L, hobby.items.first().id)
    }

    @Test
    fun switchWorksState_sendsWorksSpecificParams() = runTest(testDispatcher) {
        val mock = MockApi().apply {
            enqueue(hobbies(1))
            routeWhen(
                predicate = { path, _, _ -> path.contains("api/collect/common-v3") },
                body = { _, _, _ -> commons(1) }
            )
        }
        val env = buildEnv(mock = mock)
        awaitInitialized(env.viewModel)

        mock.takeRequest()

        env.viewModel.onIntent(UserCollectIntent.SwitchCategory(UserCollectCategory.WORKS))
        advanceUntilIdle()

        val firstWorksRequest = mock.takeRequest()
        assertEquals("works", firstWorksRequest.queryParameter("type"))
        assertEquals("1", firstWorksRequest.queryParameter("state"))
        assertEquals("false", firstWorksRequest.queryParameter("isAsc"))
        assertEquals(1, env.viewModel.uiState.value.worksData.items.size)

        env.viewModel.onIntent(UserCollectIntent.SwitchWorksState(WorksStateOption.WATCHED))
        advanceUntilIdle()

        val secondWorksRequest = mock.takeRequest()
        assertEquals("3", secondWorksRequest.queryParameter("state"))
        assertEquals(WorksStateOption.WATCHED, env.viewModel.uiState.value.worksState)
    }

    @Test
    fun firstPageFailure_showsErrorBannerUntilDismissed() = runTest(testDispatcher) {
        val mock = MockApi().apply { enqueue(failure(500, "服务端异常")) }
        val env = buildEnv(mock = mock)
        awaitInitialized(env.viewModel)

        assertEquals("服务端异常", env.viewModel.uiState.value.errorMessage)
        assertFalse(env.viewModel.uiState.value.isCurrentContentHidden)

        env.viewModel.onIntent(UserCollectIntent.DismissError(UserCollectCategory.HOBBY))

        assertNull(env.viewModel.uiState.value.errorMessage)
    }

    @Test
    fun hiddenContentFailure_marksContentHiddenForDedicatedEmptyState() = runTest(testDispatcher) {
        val mock = MockApi().apply { enqueue(failure(403, "用户已隐藏内容")) }
        val env = buildEnv(mock = mock)
        awaitInitialized(env.viewModel)

        assertTrue(env.viewModel.uiState.value.isCurrentContentHidden)
        assertNotNull(env.viewModel.uiState.value.errorMessage)
    }

    @Test
    fun confirmBatchCancel_refreshesCurrentCategoryAndConsumesGeneration() = runTest(testDispatcher) {
        val mock = MockApi().apply {
            enqueue(hobbies(3))
            enqueue(hobbies(1, startId = 91))
            route("api/collect/batch/cancel", ok("{}"))
        }
        val env = buildEnv(mock = mock)
        awaitInitialized(env.viewModel)
        val requestsBeforeCancel = mock.requestCount

        env.viewModel.onIntent(UserCollectIntent.ToggleSelectionMode(UserCollectCategory.HOBBY))
        env.viewModel.onIntent(UserCollectIntent.ToggleSelect(1L))
        env.viewModel.onIntent(UserCollectIntent.ToggleSelect(2L))
        assertEquals(setOf(1L, 2L), env.viewModel.uiState.value.hobbySelectedIds)

        env.viewModel.onIntent(UserCollectIntent.ConfirmBatchCancel)
        advanceUntilIdle()

        val state = env.viewModel.uiState.value

        assertEquals(requestsBeforeCancel + 2, mock.requestCount)
        assertEquals(listOf(91L), state.hobbyData.items.map { it.id })
        assertTrue(state.hobbySelectedIds.isEmpty())
        assertFalse(state.hobbySelectionMode)
        assertFalse(state.isCancellingBatch)
        assertNull(state.batchCancelError)
        assertEquals(1L, env.collectRepository.collectMutationGeneration.value)

        env.viewModel.onIntent(UserCollectIntent.OnResume)
        advanceUntilIdle()
        assertEquals(requestsBeforeCancel + 2, mock.requestCount)
    }

    @Test
    fun confirmBatchCancel_nonOwnerFailsFastWithoutRequest() = runTest(testDispatcher) {
        val mock = MockApi().apply { routeHobby { hobbies(2) } }

        val env = buildEnv(mock = mock, loginUserId = 777L, targetUserId = 888L)
        awaitInitialized(env.viewModel)
        val requestsBeforeCancel = mock.requestCount

        env.viewModel.onIntent(UserCollectIntent.ToggleSelectionMode(UserCollectCategory.HOBBY))
        env.viewModel.onIntent(UserCollectIntent.ToggleSelect(1L))
        env.viewModel.onIntent(UserCollectIntent.ConfirmBatchCancel)
        advanceUntilIdle()

        val state = env.viewModel.uiState.value
        assertEquals("仅能取消本人收藏", state.batchCancelError)
        assertFalse(state.isCancellingBatch)

        assertEquals(requestsBeforeCancel, mock.requestCount)
        assertEquals(2, state.hobbyData.items.size)

        env.viewModel.onIntent(UserCollectIntent.DismissBatchCancelError)
        assertNull(env.viewModel.uiState.value.batchCancelError)
    }

    @Test
    fun onResume_refreshesActiveCategoryImmediately() = runTest(testDispatcher) {
        val mock = MockApi().apply {
            enqueue(hobbies(1))
            enqueue(pics(1, startId = 11))
            enqueue(pics(2, startId = 11))
            routeCollectWrite()
        }
        val env = buildEnv(mock = mock)
        awaitInitialized(env.viewModel)
        env.viewModel.onIntent(UserCollectIntent.SwitchCategory(UserCollectCategory.PICTURE))
        advanceUntilIdle()
        assertEquals(1, env.viewModel.uiState.value.picData.items.size)
        advanceMutationGeneration(env.collectRepository)
        val requestsBeforeResume = mock.requestCount
        env.viewModel.onIntent(UserCollectIntent.OnResume)

        val refreshing = env.viewModel.uiState.value.picData
        assertEquals(1, refreshing.items.size)
        assertTrue(refreshing.isRefreshing)
        advanceUntilIdle()

        assertEquals(requestsBeforeResume + 1, mock.requestCount)
        assertEquals(2, env.viewModel.uiState.value.picData.items.size)
    }

    @Test
    fun onResume_refreshesActiveCategoryEvenWhenIdleAndEmpty() = runTest(testDispatcher) {
        val mock = MockApi().apply {
            enqueue(hobbies(0))
            enqueue(hobbies(1))
            routeCollectWrite()
        }
        val env = buildEnv(mock = mock)
        awaitInitialized(env.viewModel)

        assertEquals(0, env.viewModel.uiState.value.hobbyData.items.size)
        advanceMutationGeneration(env.collectRepository)
        val requestsBeforeResume = mock.requestCount
        env.viewModel.onIntent(UserCollectIntent.OnResume)
        advanceUntilIdle()

        assertEquals(requestsBeforeResume + 1, mock.requestCount)
        assertEquals(1, env.viewModel.uiState.value.hobbyData.items.size)
    }

    @Test
    fun onResume_stagesInactiveCategoriesUntilSwitched() = runTest(testDispatcher) {
        val mock = MockApi().apply {
            enqueue(hobbies(3))
            enqueue(albums(1))
            enqueue(pics(1, startId = 11))

            enqueue(hobbies(1, startId = 31))
            enqueue(albums(2, startId = 21))
            enqueue(pics(2, startId = 11))
            routeCollectWrite()
        }
        val env = buildEnv(mock = mock)
        awaitInitialized(env.viewModel)
        env.viewModel.onIntent(UserCollectIntent.SwitchCategory(UserCollectCategory.ALBUM))
        advanceUntilIdle()
        env.viewModel.onIntent(UserCollectIntent.SwitchCategory(UserCollectCategory.PICTURE))
        advanceUntilIdle()
        env.viewModel.onIntent(UserCollectIntent.SwitchCategory(UserCollectCategory.HOBBY))
        advanceUntilIdle()
        advanceMutationGeneration(env.collectRepository)
        val requestsBeforeResume = mock.requestCount
        env.viewModel.onIntent(UserCollectIntent.OnResume)
        advanceUntilIdle()

        assertEquals(requestsBeforeResume + 1, mock.requestCount)
        assertEquals(1, env.viewModel.uiState.value.hobbyData.items.size)
        assertEquals(1, env.viewModel.uiState.value.albumData.items.size)
        assertEquals(1, env.viewModel.uiState.value.picData.items.size)

        env.viewModel.onIntent(UserCollectIntent.SwitchCategory(UserCollectCategory.ALBUM))
        val stagedAlbum = env.viewModel.uiState.value.albumData
        assertEquals(1, stagedAlbum.items.size)
        assertTrue(stagedAlbum.isRefreshing)
        advanceUntilIdle()
        assertEquals(requestsBeforeResume + 2, mock.requestCount)
        assertEquals(listOf(21L, 22L), env.viewModel.uiState.value.albumData.items.map { it.id })

        assertEquals(requestsBeforeResume + 2, mock.requestCount)
        env.viewModel.onIntent(UserCollectIntent.SwitchCategory(UserCollectCategory.PICTURE))
        advanceUntilIdle()
        assertEquals(requestsBeforeResume + 3, mock.requestCount)
        assertEquals(2, env.viewModel.uiState.value.picData.items.size)
        env.viewModel.onIntent(UserCollectIntent.SwitchCategory(UserCollectCategory.HOBBY))
        env.viewModel.onIntent(UserCollectIntent.SwitchCategory(UserCollectCategory.ALBUM))
        advanceUntilIdle()
        assertEquals(requestsBeforeResume + 3, mock.requestCount)
    }

    @Test
    fun onResume_withoutGenerationGrowth_sendsNoRequest() = runTest(testDispatcher) {
        val mock = MockApi().apply {
            enqueue(hobbies(1))
            enqueue(pics(1, startId = 11))
            routeCollectWrite()
        }
        val env = buildEnv(mock = mock)
        awaitInitialized(env.viewModel)
        env.viewModel.onIntent(UserCollectIntent.SwitchCategory(UserCollectCategory.PICTURE))
        advanceUntilIdle()
        val requestsBeforeResume = mock.requestCount

        env.viewModel.onIntent(UserCollectIntent.OnResume)
        advanceUntilIdle()

        assertEquals(requestsBeforeResume, mock.requestCount)
    }

    @Test
    fun onResume_restoresConsumedCursorFromDataStore() = runTest(testDispatcher) {
        val mock = MockApi().apply {
            enqueue(hobbies(1))
            enqueue(hobbies(2, startId = 41))
            enqueue(hobbies(1))
            enqueue(hobbies(2, startId = 41))
            routeCollectWrite()
        }
        val env = buildEnv(mock = mock)
        awaitInitialized(env.viewModel)
        advanceMutationGeneration(env.collectRepository)
        env.viewModel.onIntent(UserCollectIntent.OnResume)
        advanceUntilIdle()
        advanceMutationGeneration(env.collectRepository)
        assertEquals(1L, env.viewPreferencesManager.getConsumedCollectMutationGeneration())

        val recreated = recreateViewModel(env)
        awaitInitialized(recreated)
        val requestsBeforeResume = mock.requestCount

        recreated.onIntent(UserCollectIntent.OnResume)
        advanceUntilIdle()

        assertEquals(requestsBeforeResume, mock.requestCount)
        assertEquals(1, recreated.uiState.value.hobbyData.items.size)
        assertEquals(2L, env.viewPreferencesManager.getConsumedCollectMutationGeneration())

        recreated.onIntent(UserCollectIntent.OnResume)
        advanceUntilIdle()
        assertEquals(requestsBeforeResume, mock.requestCount)
    }

    @Test
    fun onResume_afterProcessRestart_discardsStalePersistedCursor() = runTest(testDispatcher) {
        val viewPrefs = TestDataStore.inMemory()
        ViewPreferencesManager(viewPrefs).setConsumedCollectMutationGeneration(5L)
        val mock = MockApi().apply {
            enqueue(hobbies(1))
            enqueue(hobbies(2, startId = 71))
            routeCollectWrite()
        }

        val env = buildEnv(mock = mock, viewPrefs = viewPrefs)
        awaitInitialized(env.viewModel)
        advanceMutationGeneration(env.collectRepository)
        val requestsBeforeResume = mock.requestCount

        env.viewModel.onIntent(UserCollectIntent.OnResume)
        advanceUntilIdle()

        assertEquals(requestsBeforeResume + 1, mock.requestCount)
        assertEquals(2, env.viewModel.uiState.value.hobbyData.items.size)
    }

    @Test
    fun deleteCollectFromDetail_thenResume_dropsHobbyItem() = runTest(testDispatcher) {
        val mock = MockApi().apply {
            enqueue(hobbies(1))
            enqueue(listJson(""))
            route("api/collect/hobby/upd", ok("{}"))
        }
        val env = buildEnv(mock = mock)
        awaitInitialized(env.viewModel)
        assertEquals(1, env.viewModel.uiState.value.hobbyData.items.size)

        val generationBefore = env.collectRepository.collectMutationGeneration.value
        assertTrue(env.collectRepository.deleteCollect(1L, CollectData()).isSuccess)
        assertEquals(generationBefore + 1, env.collectRepository.collectMutationGeneration.value)

        val requestsBeforeResume = mock.requestCount
        env.viewModel.onIntent(UserCollectIntent.OnResume)
        advanceUntilIdle()

        assertEquals(requestsBeforeResume + 1, mock.requestCount)
        assertEquals(0, env.viewModel.uiState.value.hobbyData.items.size)
    }

    @Test
    fun initParam_firstLoadConsumesPendingGenerationWithoutExtraRefresh() = runTest(testDispatcher) {
        val viewPrefs = TestDataStore.inMemory()
        ViewPreferencesManager(viewPrefs).setConsumedCollectMutationGeneration(0L)
        val mock = MockApi().apply {
            enqueue(hobbies(1))
            routeCollectWrite()
        }
        val env = buildEnv(mock = mock, viewPrefs = viewPrefs, autoInit = false)
        assertTrue(env.collectRepository.collectItemAdd(9000L).isSuccess)
        assertEquals(1L, env.collectRepository.collectMutationGeneration.value)

        env.viewModel.initParam(UserCollectNavParam(userId = 999L))
        advanceUntilIdle()
        awaitInitialized(env.viewModel)

        val requestsBeforeResume = mock.requestCount
        env.viewModel.onIntent(UserCollectIntent.OnResume)
        advanceUntilIdle()

        assertEquals(requestsBeforeResume, mock.requestCount)
    }
}
