package com.nobody.repoi.ui.gallery

import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserCollectPicItem
import com.nobody.repoi.data.repository.CollectEvent
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.FollowRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.UserFinanceRepository
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.data.session.FakeUserSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 大图画廊收藏联动测试（规格 #86「Testing Decisions」要求的画廊套件）。
 *
 * 覆盖四件事：
 * 1. 写侧正确区分收藏（`collect/item/add`）与取消（`collect/del`）；
 * 2. 成功后不再手动广播——整条链路只有一次写请求，跨页刷新由仓储内生事件流驱动；
 * 3. 写侧透传图片分类，成功后仓储发射带 PICTURE 分类的统一事件；
 * 4. 收藏/取消的网络失败均回滚星标与收藏人数，并给出提示。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PictureGalleryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private fun fixture(): Fixture {
        val mock = MockApi()
        val session = FakeUserSession(token = "test-token", userId = 999L)
        val userRepo = UserRepository(
            mock.api(testDispatcher),
            session,
            FollowRepository(mock.api(testDispatcher), mock.json, session),
            UserFinanceRepository(mock.api(testDispatcher))
        )
        val collectRepo = CollectRepository(mock.api(testDispatcher), mock.json)
        // 下载客户端不参与收藏链路，空 MockEngine 占位即可
        val downloadClient = HttpClient(
            MockEngine { respond("", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg")) }
        )
        val viewModel = PictureGalleryViewModel(
            collectRepository = collectRepo,
            userRepository = userRepo,
            downloadClient = downloadClient
        )
        return Fixture(mock, collectRepo, viewModel)
    }

    /** 订阅收集器：后台收集 collectEvents，调用方按序取事件（随 runTest 结束自动取消）。 */
    private fun kotlinx.coroutines.test.TestScope.collectEventsOf(
        repository: CollectRepository
    ): MutableList<CollectEvent> {
        val events = mutableListOf<CollectEvent>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED, context = UnconfinedTestDispatcher()) {
            repository.collectEvents.collect { events.add(it) }
        }
        return events
    }

    /** 预置已收藏态：切页回填 collect/state（500ms 延迟由 advanceUntilIdle 推进）。 */
    private suspend fun kotlinx.coroutines.test.TestScope.presetCollected(
        mock: MockApi,
        viewModel: PictureGalleryViewModel,
        item: UserCollectPicItem
    ) {
        mock.route("api/collect/state", mock.ok("""{"collect":{"id":${item.id},"state":"care","type":2}}"""))
        mock.route("api/pic/relate", mock.ok("""{"list":[]}"""))
        viewModel.onPageChanged(item, 0)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCollected, "预置：当前图应处于已收藏态")
    }

    @Test
    fun toggleCollect_whenUncollected_sendsAddAndRollsForward() = runTest(testDispatcher) {
        val (mock, collectRepo, viewModel) = fixture()
        mock.route("api/collect/item/add", mock.ok("{}"))
        val events = collectEventsOf(collectRepo)

        val nowCollected = viewModel.toggleCollect(picItem(id = 9001L))
        advanceUntilIdle()

        assertTrue(nowCollected, "返回值供 UI 触发缩放动画，应为切换后的收藏态")
        assertTrue(viewModel.uiState.value.isCollected)
        assertEquals(1, viewModel.uiState.value.collectCount, "乐观更新应立即 +1")
        // 成功链只有一次写请求：画廊不再手动广播
        assertEquals(1, mock.requestCount)

        val request = mock.takeRequest()
        assertTrue(request.encodedPath!!.contains("api/collect/item/add"), "收藏应走 api/collect/item/add")
        assertTrue(request.bodyContains("id=9001"))
        // 分类不进网络请求：断言仓储发射带 PICTURE 分类的统一事件，回归分类误传
        runCurrent()
        assertEquals(
            listOf(CollectEvent.ItemCollectChanged(9001L, UserCollectCategory.PICTURE, true)),
            events
        )
    }

    @Test
    fun toggleCollect_whenCollected_sendsDel() = runTest(testDispatcher) {
        val (mock, collectRepo, viewModel) = fixture()
        val item = picItem(id = 9002L)
        mock.route("api/collect/del", mock.ok("{}"))
        val events = collectEventsOf(collectRepo)
        presetCollected(mock, viewModel, item)

        val nowCollected = viewModel.toggleCollect(item)
        advanceUntilIdle()

        assertFalse(nowCollected)
        assertFalse(viewModel.uiState.value.isCollected)

        // 写请求必须是 del，且带原图 id
        var write = mock.takeRequest()
        while (write.encodedPath!!.contains("api/collect/state") || write.encodedPath!!.contains("api/pic/relate")) {
            write = mock.takeRequest()
        }
        assertTrue(write.encodedPath!!.contains("api/collect/del"), "取消收藏应走 api/collect/del")
        assertTrue(write.bodyContains("id=9002"))
        runCurrent()
        assertEquals(
            listOf(CollectEvent.ItemCollectChanged(9002L, UserCollectCategory.PICTURE, false)),
            events
        )
    }

    @Test
    fun toggleCollect_whenNetworkFails_rollsBackAndShowsMessage() = runTest(testDispatcher) {
        val (mock, _, viewModel) = fixture()
        mock.route("api/collect/item/add", mock.failure(500, "网络异常"))

        viewModel.toggleCollect(picItem(id = 9003L))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCollected, "失败必须回滚星标")
        assertEquals(0, viewModel.uiState.value.collectCount, "失败必须回滚计数")
        assertEquals("网络异常", viewModel.uiState.value.toastMessage)
    }

    @Test
    fun toggleCollect_whenDelFails_rollsBackToCollected() = runTest(testDispatcher) {
        val (mock, _, viewModel) = fixture()
        val item = picItem(id = 9005L)
        mock.route("api/collect/del", mock.failure(500, "网络异常"))
        presetCollected(mock, viewModel, item)
        val collectedCount = viewModel.uiState.value.collectCount

        viewModel.toggleCollect(item)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isCollected, "取消失败必须回滚到已收藏态")
        assertEquals(collectedCount, viewModel.uiState.value.collectCount, "取消失败必须回滚计数")
        assertEquals("网络异常", viewModel.uiState.value.toastMessage)
    }

    @Test
    fun toggleCollect_success_emitsExactlyOneWriteRequest() = runTest(testDispatcher) {
        val (mock, _, viewModel) = fixture()
        mock.route("api/collect/item/add", mock.ok("{}"))

        viewModel.toggleCollect(picItem(id = 9004L))
        advanceUntilIdle()

        // 手动广播已删除：整条链路只有一次写请求
        assertEquals(1, mock.requestCount)
    }

    private fun picItem(id: Long) = UserCollectPicItem(
        id = id,
        itemId = 4235086L,
        path = "2026/09/a.jpg",
        name = "测试图",
        collect = 2
    )

    private data class Fixture(
        val mock: MockApi,
        val collectRepo: CollectRepository,
        val viewModel: PictureGalleryViewModel
    )
}
