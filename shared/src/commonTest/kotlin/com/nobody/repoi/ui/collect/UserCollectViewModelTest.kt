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

/**
 * 收藏中心 ViewModel 的 MVI 行为测试（只测公开接缝：`onIntent` + `uiState`）。
 *
 * 覆盖：Intent 发派、分类切换、分页 `hasMore` 与去重追加、条件变化后的重新装载、
 * 批量取消（含非本人零请求拦截）、错误横幅与「已隐藏内容」空态、恢复感知脏位刷新。
 *
 * 类名包含 `ViewModelTest`，因此 `shared/build.gradle.kts` 中同名的 quarantine 过滤必须同步摘除，
 * 否则本文件的断言永远不会在 CI 执行。
 */
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

    // ==================== 测试夹具 ====================

    private class Env(
        val mock: MockApi,
        val session: FakeUserSession,
        val collectRepository: CollectRepository,
        val viewPreferencesManager: ViewPreferencesManager,
        val koin: KoinApplication,
        val viewModel: UserCollectViewModel,
    )

    /**
     * 走生产 Koin 接线构造被测对象：`viewModelModule` / `repositoryModule` 原样装载，
     * 只把网络出口换成 [MockApi]、DataStore 换成内存实例。这样游标持久化与恢复覆盖的是
     * 真实生产路径，而不是测试里手工注入的恢复槽。
     *
     * 调度器三件套齐备：`setMain(testDispatcher)` + `runTest(testDispatcher)` +
     * `mock.api(testDispatcher)`，否则 MockEngine 跑真实线程，`advanceUntilIdle` 推不动。
     */
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
        // Screen 的 LaunchedEffect(param) 在生产中下发 NavKey 字段
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

    /** 模拟页面实例重建：从同一生产 Koin 容器再取一个 VM（仓储与 DataStore 保持不变） */
    private fun recreateViewModel(env: Env, targetUserId: Long = 999L): UserCollectViewModel =
        env.koin.koin.get<UserCollectViewModel>().also {
            it.initParam(UserCollectNavParam(userId = targetUserId))
        }

    /** 真实写操作替身：不再调用过渡标记方法，改由仓储写路径推进代数 */
    private fun MockApi.routeCollectWrite() = route("api/collect/item/add", ok("{}"))

    private suspend fun TestScope.advanceMutationGeneration(collectRepository: CollectRepository) {
        assertTrue(collectRepository.collectItemAdd(nodeId = 9000L).isSuccess)
        advanceUntilIdle()
    }

    /**
     * 等待 ViewModel 完成入口初始化。
     *
     * 排序记忆要真实读 DataStore（内部跑真实 IO 线程），虚拟时间推不动它；
     * 因此每轮推进后让出一小段**真实**时间，直到 `isInitialized` 置位或超时（最长约 2 秒）。
     */
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

    // ==================== 响应体构造 ====================

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

    // ==================== 初始状态与 Intent 发派 ====================

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

        // Nav3 重建 Entry 会重复下发同一 param：幂等门禁必须拦住，避免重复请求与浏览位置重置
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

    // ==================== 分页：首屏 / hasMore / 追加去重 ====================

    @Test
    fun loadFirstPage_publishesItemsAndHasMore() = runTest(testDispatcher) {
        val env = buildEnv(mock = MockApi().apply { enqueue(hobbies(UserCollectViewModel.PAGE_SIZE)) })
        awaitInitialized(env.viewModel)

        val hobby = env.viewModel.uiState.value.hobbyData
        assertEquals(UserCollectViewModel.PAGE_SIZE, hobby.items.size)
        // 满页 → 仍有下一页
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
        // 不满一页 → 没有下一页
        assertFalse(hobby.hasMore)
    }

    @Test
    fun loadNextPage_appendsDeduplicatedItemsAndAdvancesPage() = runTest(testDispatcher) {
        val env = buildEnv(
            mock = MockApi().apply {
                enqueue(hobbies(UserCollectViewModel.PAGE_SIZE))
                // 第 2 页与第 1 页在 id=20 处重叠：稳定键去重后只应追加 19 条
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
        // 追加失败只报错、不动 hasMore（可重试），已有条目不受影响
        assertEquals(UserCollectViewModel.PAGE_SIZE, hobby.items.size)
        assertEquals("网络开了小差", hobby.errorMessage)
        assertTrue(hobby.hasMore)
        assertFalse(hobby.isPaging)
    }

    // ==================== 条件变化后必须重读运行时状态 ====================

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
        // fetcher 闭包读的是运行时状态，而不是构造时的快照
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
        // 消费首屏周边请求，后续断言只看作品页请求
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

    // ==================== 错误横幅与「已隐藏内容」空态 ====================

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

    // ==================== 批量取消 ====================

    @Test
    fun confirmBatchCancel_refreshesCurrentCategoryAndConsumesGeneration() = runTest(testDispatcher) {
        val mock = MockApi().apply {
            enqueue(hobbies(3))                 // 首屏
            enqueue(hobbies(1, startId = 91))   // 成功后原位刷新
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
        // 批量取消一条 + 当前大类原位刷新一条；不靠逐行 removeItem 拼凑结果
        assertEquals(requestsBeforeCancel + 2, mock.requestCount)
        assertEquals(listOf(91L), state.hobbyData.items.map { it.id })
        assertTrue(state.hobbySelectedIds.isEmpty())
        assertFalse(state.hobbySelectionMode)
        assertFalse(state.isCancellingBatch)
        assertNull(state.batchCancelError)
        assertEquals(1L, env.collectRepository.collectMutationGeneration.value)

        // 出栈返回：游标已同步到最新代数，不得二次刷新
        env.viewModel.onIntent(UserCollectIntent.OnResume)
        advanceUntilIdle()
        assertEquals(requestsBeforeCancel + 2, mock.requestCount)
    }


    @Test
    fun confirmBatchCancel_nonOwnerFailsFastWithoutRequest() = runTest(testDispatcher) {
        val mock = MockApi().apply { routeHobby { hobbies(2) } }
        // 登录用户 777 浏览 888 的收藏：非本人
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
        // 验权失败必须零网络请求，且列表条目原样保留
        assertEquals(requestsBeforeCancel, mock.requestCount)
        assertEquals(2, state.hobbyData.items.size)

        env.viewModel.onIntent(UserCollectIntent.DismissBatchCancelError)
        assertNull(env.viewModel.uiState.value.batchCancelError)
    }

    // ==================== 恢复感知（收藏变更代数）====================

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
        // 立即进入原位刷新：旧条目仍在，不重置为空
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
        // 首屏拉空：激活大类闲置且空（对应首屏拉空或失败后的挂空态）
        assertEquals(0, env.viewModel.uiState.value.hobbyData.items.size)
        advanceMutationGeneration(env.collectRepository)
        val requestsBeforeResume = mock.requestCount
        env.viewModel.onIntent(UserCollectIntent.OnResume)
        advanceUntilIdle()
        // 激活大类处于当前视图：即使闲置且空也必须原位重试，不得静默跳过
        assertEquals(requestsBeforeResume + 1, mock.requestCount)
        assertEquals(1, env.viewModel.uiState.value.hobbyData.items.size)
    }

    @Test
    fun onResume_stagesInactiveCategoriesUntilSwitched() = runTest(testDispatcher) {
        val mock = MockApi().apply {
            enqueue(hobbies(3))
            enqueue(albums(1))
            enqueue(pics(1, startId = 11))
            // 恢复时激活大类刷新；惰性切入时其余两类各刷新一次
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
        // 只有当前激活的手办大类立刻请求，相册与图片仅入脏集合
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

        // 未切入的图片大类此前零请求；切入后原位刷新，随后重复切入不再请求
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

        // 代数未增长：不得白发一次刷新请求
        assertEquals(requestsBeforeResume, mock.requestCount)
    }

    @Test
    fun onResume_restoresConsumedCursorFromDataStore() = runTest(testDispatcher) {
        val mock = MockApi().apply {
            enqueue(hobbies(1))                      // 首实例首屏
            enqueue(hobbies(2, startId = 41))        // 首实例消费第 1 代的恢复刷新
            enqueue(hobbies(1))                      // 重建实例首屏
            enqueue(hobbies(2, startId = 41))        // 重建实例仅补刷第 2 代
            routeCollectWrite()
        }
        val env = buildEnv(mock = mock)
        awaitInitialized(env.viewModel)
        advanceMutationGeneration(env.collectRepository)
        env.viewModel.onIntent(UserCollectIntent.OnResume)
        advanceUntilIdle()
        advanceMutationGeneration(env.collectRepository)
        assertEquals(1L, env.viewPreferencesManager.getConsumedCollectMutationGeneration())

        // 生产接线：新建页面实例，游标从 DataStore 读回而非测试注入
        val recreated = recreateViewModel(env)
        awaitInitialized(recreated)
        val requestsBeforeResume = mock.requestCount

        recreated.onIntent(UserCollectIntent.OnResume)
        advanceUntilIdle()
        // 重建实例的首屏装载已消费第 2 代，OnResume 不再发冗余刷新
        assertEquals(requestsBeforeResume, mock.requestCount)
        assertEquals(1, recreated.uiState.value.hobbyData.items.size)
        assertEquals(2L, env.viewPreferencesManager.getConsumedCollectMutationGeneration())

        // 游标已写回持久化层，同一次恢复不得重复刷新
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
        // 新进程：仓储代数从 0 重启，旧进程的第 5 代游标不得阻塞新的写操作刷新
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

        // 首屏已拉取最新列表，恢复感知不得再发一次冗余刷新
        assertEquals(requestsBeforeResume, mock.requestCount)
    }
}
