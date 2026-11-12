package com.nobody.repoi.ui.detail

import com.nobody.repoi.data.local.awaitPendingWrites
import com.nobody.repoi.data.repository.CoverFreshnessRepository
import com.nobody.repoi.data.repository.testCoverFreshnessRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 手办详情页封面沉淀测试。
 *
 * 契约：详情接口返回的 `cover` 必须写入 [CoverFreshnessRepository]，并以
 * `actualItemId` 为键，使历史页的按条回源直接命中、零额外请求。
 *
 * 调度器三件套：`Dispatchers.setMain` + `runTest(testDispatcher)` + `MockApi.api(testDispatcher)`。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HobbyDetailViewModelCoverSinkTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadDetail_sinksCoverForHistoryRepairWithoutExtraRequest() = runTest(testDispatcher) {
        val env = HobbyDetailEnv(backgroundScope, dispatcher = testDispatcher)
        env.stubBasics(nodeId = 100L, itemId = 59570L)
        env.collectMock.enqueue(env.collectMock.ok("""{}"""))

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val requestsAfterLoad = env.detailMock.requestCount
        val result = env.coverFreshnessRepository.freshCover(
            id = 59570L,
            type = CoverFreshnessRepository.EntityType.HOBBY,
        )

        assertEquals("cover/n/test.jpg", result.getOrThrow(), "详情页应沉淀当前真实封面")
        assertEquals(requestsAfterLoad, env.detailMock.requestCount, "命中沉淀后不应再请求详情接口")
    }

    /**
     * 工单 #117 AC：详情页沉淀必须经 500ms 防抖落盘，冷启动重建仓储后仍能命中。
     */
    @Test
    fun loadDetail_sinksCoverAndPersistsAfterDebounce() = runTest(testDispatcher) {
        val env = HobbyDetailEnv(backgroundScope, dispatcher = testDispatcher)
        env.coverFreshnessRepository.ensureWarmed()
        env.stubBasics(nodeId = 100L, itemId = 59570L)
        env.collectMock.enqueue(env.collectMock.ok("""{}"""))

        env.viewModel().loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        advanceTimeBy(501)
        advanceUntilIdle()
        env.coverFreshnessPreferences.awaitPendingWrites()

        val rebuilt = testCoverFreshnessRepository(
            scope = backgroundScope,
            preferences = env.coverFreshnessPreferences,
            dispatcher = testDispatcher,
        )
        rebuilt.ensureWarmed()
        assertEquals(
            "cover/n/test.jpg",
            rebuilt.getCachedCover(59570L, CoverFreshnessRepository.EntityType.HOBBY),
            "详情页沉淀应经防抖落盘，重建仓储后仍能命中",
        )
    }

}
