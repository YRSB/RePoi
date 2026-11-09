package com.nobody.repoi.ui.collect

import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.domain.usecase.collect.SaveWorksCollectUseCase
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
import kotlin.test.assertTrue

/**
 * 作品收藏编辑页写侧测试：保存委托 `SaveWorksCollectUseCase`（三态校验 + 标签串清洗），
 * 取消收藏仍走仓储 `deleteWorksCollect`。
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
            SaveWorksCollectUseCase(collectRepository)
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
            SaveWorksCollectUseCase(collectRepository)
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

    /** 消费并丢弃已排队的请求记录。 */
    private fun MockApi.drainPaths(): List<String?> = buildList {
        repeat(requestCount) { add(takeRequest().encodedPath) }
    }

    private fun newRepository(collectMock: MockApi): CollectRepository =
        CollectRepository(collectMock.api(testDispatcher), collectMock.json, FakeUserSession("utoken-test"))
}
