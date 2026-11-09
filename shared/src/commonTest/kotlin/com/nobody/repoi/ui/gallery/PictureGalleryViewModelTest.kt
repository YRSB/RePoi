package com.nobody.repoi.ui.gallery

import com.nobody.repoi.data.remote.model.UserCollectPicItem
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
import kotlin.test.assertTrue

/**
 * 大图画廊收藏联动测试（规格 #86「Testing Decisions」要求的画廊套件）。
 *
 * 覆盖三件事：
 * 1. 写侧正确区分收藏（`collect/item/add`）与取消（`collect/del`）；
 * 2. 成功后不再手动广播——整条链路只有一次写请求，跨页刷新由仓储内生事件流驱动；
 * 3. 网络失败回滚星标与收藏人数，并给出提示。
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
        return Fixture(mock, viewModel)
    }

    @Test
    fun toggleCollect_whenUncollected_sendsAddAndRollsForward() = runTest(testDispatcher) {
        val (mock, viewModel) = fixture()
        mock.route("api/collect/item/add", mock.ok("{}"))

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
    }

    @Test
    fun toggleCollect_whenCollected_sendsDel() = runTest(testDispatcher) {
        val (mock, viewModel) = fixture()
        val item = picItem(id = 9002L)
        // 预置已收藏态：切页回填 collect/state
        mock.route("api/collect/state", mock.ok("""{"collect":{"id":9002,"state":"care","type":2}}"""))
        mock.route("api/pic/relate", mock.ok("""{"list":[]}"""))
        mock.route("api/collect/del", mock.ok("{}"))
        viewModel.onPageChanged(item, 0)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCollected, "预置：当前图应处于已收藏态")

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
    }

    @Test
    fun toggleCollect_whenNetworkFails_rollsBackAndShowsMessage() = runTest(testDispatcher) {
        val (mock, viewModel) = fixture()
        mock.route("api/collect/item/add", mock.failure(500, "网络异常"))

        viewModel.toggleCollect(picItem(id = 9003L))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCollected, "失败必须回滚星标")
        assertEquals(0, viewModel.uiState.value.collectCount, "失败必须回滚计数")
        assertEquals("网络异常", viewModel.uiState.value.toastMessage)
    }

    @Test
    fun toggleCollect_success_emitsExactlyOneWriteRequest() = runTest(testDispatcher) {
        val (mock, viewModel) = fixture()
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

    private data class Fixture(val mock: MockApi, val viewModel: PictureGalleryViewModel)
}
