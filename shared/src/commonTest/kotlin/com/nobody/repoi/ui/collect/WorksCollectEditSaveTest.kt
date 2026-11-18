package com.nobody.repoi.ui.collect

import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.RecordedHttpRequest
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.repository.WorksDetailRepository
import com.nobody.repoi.domain.usecase.collect.DeleteWorksCollectUseCase
import com.nobody.repoi.domain.usecase.collect.SaveWorksCollectUseCase
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import com.nobody.repoi.ui.works.WorksNavParam
import com.nobody.repoi.ui.works.WorksDetailViewModel
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 作品收藏编辑页写侧测试：保存委托 `SaveWorksCollectUseCase`（三态校验 + 标签串清洗），
 * 取消收藏委托 `DeleteWorksCollectUseCase`（worksId 守卫 + 仓储删除）。
 *
 * 类名刻意不含 `ViewModel`，避免受既有 ViewModel 测试过滤规则影响。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorksCollectEditSaveTest {

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
    fun saveCollect_delegatesToUseCaseWithNormalizedTags() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = WorksCollectEditViewModel(
            collectRepository,
            SaveWorksCollectUseCase(collectRepository),
            DeleteWorksCollectUseCase(collectRepository)
        )
        collectMock.route("user/glean/textTag/get", collectMock.ok("""[]"""))
        collectMock.route("works/glean/get", collectMock.ok("{}"))
        collectMock.route("works/glean/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.initData(WorksCollectEditNavParam(worksId = 66L))
        advanceUntilIdle()
        // 丢弃 initData 阶段已消费的请求，下面断言只针对保存写入
        collectMock.drainPaths()

        // 标签清洗由用例负责：trim 后写入
        assertTrue(viewModel.addTag("  热血  "))
        viewModel.saveCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.saveSuccess)
        val recorded = collectMock.takeRequest()
        assertEquals("/api/works/glean/add", recorded.encodedPath)
        val form = recorded.formEntries()
        assertEquals("66", form["itemId"])
        assertEquals("热血", form["tagStr"])
        assertEquals("1", form["state"])
    }

    @Test
    fun deleteCollect_stillDelegatesToRepository() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = WorksCollectEditViewModel(
            collectRepository,
            SaveWorksCollectUseCase(collectRepository),
            DeleteWorksCollectUseCase(collectRepository)
        )
        collectMock.route("user/glean/textTag/get", collectMock.ok("""[]"""))
        collectMock.route(
            "works/glean/get",
            collectMock.ok("""{"glean":{"state":2,"tagStr":"热血","comment":"不错"}}""")
        )
        collectMock.route("works/glean/del", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.initData(WorksCollectEditNavParam(worksId = 66L))
        advanceUntilIdle()
        collectMock.drainPaths()

        viewModel.deleteCollect()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.deleteSuccess)
        assertEquals("/api/works/glean/del", collectMock.takeRequest().encodedPath)
    }

    /** 三态依次切换：每次请求 state 参数真实变化，代数逐步 +1，手办状态池不被跨接口污染。 */
    @Test
    fun saveCollect_threeStatesInSequence_advancesGenerationPerSave() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = WorksCollectEditViewModel(
            collectRepository,
            SaveWorksCollectUseCase(collectRepository),
            DeleteWorksCollectUseCase(collectRepository)
        )
        collectMock.route("user/glean/textTag/get", collectMock.ok("""[]"""))
        collectMock.route("works/glean/get", collectMock.ok("{}"))
        collectMock.route("works/glean/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.initData(WorksCollectEditNavParam(worksId = 66L))
        advanceUntilIdle()
        assertEquals(WorksStateOption.WANT, viewModel.uiState.value.selectedState)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        assertNull(collectRepository.observeCollectState(66L).value)
        collectMock.drainPaths()

        listOf(WorksStateOption.WATCHING, WorksStateOption.WATCHED, WorksStateOption.WANT)
            .forEachIndexed { index, state ->
                viewModel.selectState(state)
                viewModel.saveCollect()
                advanceUntilIdle()

                // 写后还会拉一次 collectCount；按路径挑出写请求，不能依赖 FIFO 顺序
                val recorded = collectMock.drainRequests().first { it.encodedPath == "/api/works/glean/add" }
                val form = recorded.formEntries()
                assertEquals("Works", form["itemType"])
                assertEquals("66", form["itemId"])
                assertEquals(state.toString(), form["state"])
                assertTrue(viewModel.uiState.value.saveSuccess)
                assertEquals(state, viewModel.uiState.value.selectedState)
                assertTrue(viewModel.uiState.value.isAlreadyCollected)
                assertEquals((index + 1).toLong(), collectRepository.collectMutationGeneration.value)
                // 作品走 glean 独立协议，不写手办状态池
                assertNull(collectRepository.observeCollectState(66L).value)
            }
    }

    /** 改档后取消收藏：UI 三态与已收藏标记复位，代数累计 +2，手办状态池仍为空。 */
    @Test
    fun saveThenDeleteCollect_clearsCollectedFlagsAndAdvancesGenerationTwice() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = WorksCollectEditViewModel(
            collectRepository,
            SaveWorksCollectUseCase(collectRepository),
            DeleteWorksCollectUseCase(collectRepository)
        )
        collectMock.route("user/glean/textTag/get", collectMock.ok("""[]"""))
        collectMock.route("works/glean/get", collectMock.ok("""{"glean":{"state":2,"tagStr":"热血","comment":"不错"}}"""))
        collectMock.route("works/glean/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectMock.route("works/glean/del", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        viewModel.initData(WorksCollectEditNavParam(worksId = 66L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isAlreadyCollected)
        assertTrue(viewModel.uiState.value.hasDelete)
        assertEquals(WorksStateOption.WATCHING, viewModel.uiState.value.selectedState)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        collectMock.drainPaths()

        viewModel.selectState(WorksStateOption.WATCHED)
        viewModel.saveCollect()
        advanceUntilIdle()
        val add = collectMock.drainRequests().first { it.encodedPath == "/api/works/glean/add" }
        assertEquals("3", add.formEntries()["state"])
        assertEquals(1L, collectRepository.collectMutationGeneration.value)

        viewModel.deleteCollect()
        advanceUntilIdle()
        val del = collectMock.drainRequests().first { it.encodedPath == "/api/works/glean/del" }
        assertEquals("Works", del.formEntries()["itemType"])
        assertEquals("66", del.formEntries()["itemId"])
        assertTrue(viewModel.uiState.value.deleteSuccess)
        assertFalse(viewModel.uiState.value.isAlreadyCollected)
        assertFalse(viewModel.uiState.value.hasDelete)
        assertEquals(2L, collectRepository.collectMutationGeneration.value)
        assertNull(collectRepository.observeCollectState(66L).value)
    }

    /** 作品保存网络失败：代数不推进、saveSuccess 为假、errorMessage 有值。 */
    @Test
    fun saveCollect_onNetworkFailure_keepsGenerationAndReportsError() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = WorksCollectEditViewModel(
            collectRepository,
            SaveWorksCollectUseCase(collectRepository),
            DeleteWorksCollectUseCase(collectRepository)
        )
        collectMock.route("user/glean/textTag/get", collectMock.ok("""[]"""))
        collectMock.route("works/glean/get", collectMock.ok("{}"))
        collectMock.route("works/glean/add", collectMock.failure(500, "作品保存失败"))

        viewModel.initData(WorksCollectEditNavParam(worksId = 66L))
        advanceUntilIdle()
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        collectMock.drainPaths()

        viewModel.selectState(WorksStateOption.WATCHED)
        viewModel.saveCollect()
        advanceUntilIdle()

        assertEquals("/api/works/glean/add", collectMock.takeRequest().encodedPath)
        assertTrue(viewModel.uiState.value.errorMessage?.isNotBlank() == true)
        assertFalse(viewModel.uiState.value.saveSuccess)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(WorksStateOption.WATCHED, viewModel.uiState.value.selectedState)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        assertNull(collectRepository.observeCollectState(66L).value)
    }

    /** 作品取消网络失败：已收藏标记保留、代数不推进、errorMessage 有值。 */
    @Test
    fun deleteCollect_onNetworkFailure_keepsGenerationAndReportsError() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val viewModel = WorksCollectEditViewModel(
            collectRepository,
            SaveWorksCollectUseCase(collectRepository),
            DeleteWorksCollectUseCase(collectRepository)
        )
        collectMock.route("user/glean/textTag/get", collectMock.ok("""[]"""))
        collectMock.route("works/glean/get", collectMock.ok("""{"glean":{"state":2,"tagStr":"热血"}}"""))
        collectMock.route("works/glean/del", collectMock.failure(500, "作品取消失败"))

        viewModel.initData(WorksCollectEditNavParam(worksId = 66L))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isAlreadyCollected)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        collectMock.drainPaths()

        viewModel.deleteCollect()
        advanceUntilIdle()

        assertEquals("/api/works/glean/del", collectMock.takeRequest().encodedPath)
        assertTrue(viewModel.uiState.value.errorMessage?.isNotBlank() == true)
        assertFalse(viewModel.uiState.value.deleteSuccess)
        assertFalse(viewModel.uiState.value.isDeleting)
        assertTrue(viewModel.uiState.value.isAlreadyCollected)
        assertTrue(viewModel.uiState.value.hasDelete)
        assertEquals(0L, collectRepository.collectMutationGeneration.value)
        assertNull(collectRepository.observeCollectState(66L).value)
    }


    /**
     * 作品写后服务端基线：编辑页保存成功后刷新 `hobby/collectCount`，作品详情页按 worksId
     * 读取同一快照；作品详情无本地 delta，展示人数直接由服务端快照替换详情接口 collect 字段。
     */
    @Test
    fun saveCollect_refreshesWorksDetailCountBaseFromServer() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val (detail, _) = newWorksDetailViewModel(collectRepository)
        val edit = WorksCollectEditViewModel(
            collectRepository,
            SaveWorksCollectUseCase(collectRepository),
            DeleteWorksCollectUseCase(collectRepository)
        )
        collectMock.route("user/glean/textTag/get", collectMock.ok("""[]"""))
        collectMock.route("works/glean/get", collectMock.ok("{}"))
        collectMock.route("works/glean/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectMock.route(
            "collectCount",
            collectMock.ok("""{"collect":{"care":8,"want":1,"preorder":0,"buy":0,"resell":0}}""")
        )

        detail.loadDetail(WorksNavParam(worksId = 66L))
        advanceUntilIdle()
        assertEquals(5, detail.uiState.value.works?.collect)
        assertNull(detail.uiState.value.collectCountBase)

        edit.initData(WorksCollectEditNavParam(worksId = 66L))
        advanceUntilIdle()
        collectMock.drainPaths()

        // 写后服务端人数 12+2+0+1+0=15，作品详情展示基数改由服务端提供
        collectMock.replaceRoute(
            "collectCount",
            collectMock.ok("""{"collect":{"care":12,"want":2,"preorder":0,"buy":1,"resell":0}}""")
        )
        edit.saveCollect()
        advanceUntilIdle()

        assertTrue(edit.uiState.value.saveSuccess)
        assertEquals(15L, collectRepository.observeCollectCountSnapshot(66L).value?.total)
        assertEquals(15L, detail.uiState.value.collectCountBase)
        assertTrue(collectMock.drainPaths().any { it?.contains("hobby/collectCount") == true })
    }

    /** 计数请求失败：作品详情保留详情接口 collect 字段作为展示基数，写成功语义不变。 */
    @Test
    fun saveCollect_whenCountRequestFails_keepsWorksDetailFallback() = runTest(testDispatcher) {
        val collectMock = MockApi()
        val collectRepository = newRepository(collectMock)
        val (detail, _) = newWorksDetailViewModel(collectRepository)
        val edit = WorksCollectEditViewModel(
            collectRepository,
            SaveWorksCollectUseCase(collectRepository),
            DeleteWorksCollectUseCase(collectRepository)
        )
        collectMock.route("user/glean/textTag/get", collectMock.ok("""[]"""))
        collectMock.route("works/glean/get", collectMock.ok("{}"))
        collectMock.route("works/glean/add", """{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectMock.route(
            "collectCount",
            collectMock.ok("""{"collect":{"care":8,"want":1,"preorder":0,"buy":0,"resell":0}}""")
        )

        detail.loadDetail(WorksNavParam(worksId = 66L))
        advanceUntilIdle()

        edit.initData(WorksCollectEditNavParam(worksId = 66L))
        advanceUntilIdle()
        collectMock.drainPaths()

        collectMock.replaceRoute("collectCount", collectMock.failure(500, "计数失败"))
        edit.saveCollect()
        advanceUntilIdle()

        assertTrue(edit.uiState.value.saveSuccess)
        assertNull(collectRepository.observeCollectCountSnapshot(66L).value)
        assertNull(detail.uiState.value.collectCountBase)
        assertEquals(5, detail.uiState.value.works?.collect)
    }

    /** 作品详情测试替身：worksId 与 works.id 一致，便于快照按同一键观察。 */
    private fun newWorksDetailViewModel(collectRepository: CollectRepository): Pair<WorksDetailViewModel, MockApi> {
        val detailMock = MockApi()
        detailMock.route(
            "works/get",
            detailMock.ok("""{"result":{"works":{"id":66,"itemId":28,"nameCN":"作品A","collect":5},"worksSeries":null,"worksRatingAvg":0}}""")
        )
        detailMock.route("hobby/query-v2", detailMock.ok("""{"list":[]}"""))
        detailMock.route("works/album/get", detailMock.ok("""{"items":[]}"""))
        detailMock.route("works/character/home/get", detailMock.ok("""{"worksLinkCharacter":[]}"""))
        detailMock.route("comment/get", detailMock.ok("""{"list":[]}"""))
        val commentDelegate = CommentInteractionDelegate(
            CommentRepository(MockApi().api(testDispatcher)),
            StickerRepository(MockApi().api(testDispatcher)),
            FakeUserSession("utoken-test")
        )
        val viewModel = WorksDetailViewModel(
            WorksDetailRepository(detailMock.api(testDispatcher)),
            collectRepository,
            commentDelegate
        )
        return viewModel to detailMock
    }
    /** 消费并丢弃已排队的请求记录。 */
    private fun MockApi.drainPaths(): List<String?> = buildList {
        repeat(requestCount) { add(takeRequest().encodedPath) }
    }

    /** 消费全部排队请求并保留完整记录，供读后计数请求与写请求交错时按路径筛选。 */
    private fun MockApi.drainRequests(): List<RecordedHttpRequest> = buildList {
        repeat(requestCount) { add(takeRequest()) }
    }

    private fun newRepository(collectMock: MockApi): CollectRepository =
        CollectRepository(collectMock.api(testDispatcher), collectMock.json)
}
