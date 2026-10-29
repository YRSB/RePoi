package com.nobody.poishelf.ui.detail

import com.nobody.poishelf.data.local.TestDataStore
import com.nobody.poishelf.data.local.ViewPreferencesManager
import com.nobody.poishelf.data.remote.api.ExHobbyApi
import com.nobody.poishelf.data.repository.CollectRepository
import com.nobody.poishelf.data.repository.CommentRepository
import com.nobody.poishelf.data.repository.ExHobbyRepository
import com.nobody.poishelf.data.repository.FakeTokenManager
import com.nobody.poishelf.data.repository.HobbyDetailRepository
import com.nobody.poishelf.data.repository.MockApi
import com.nobody.poishelf.data.repository.StickerRepository
import com.nobody.poishelf.data.repository.UserRepository
import com.nobody.poishelf.ui.common.comment.CommentInteractionDelegate
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
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
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 手办详情 ExHobby 图包链路 ViewModel 测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `HobbyDetailViewModelExHobbyTest`（逐用例；Mockito 仓储/开关桩 →
 * 真实 6 依赖 + MockEngine（ExHobby 引擎可配成功/失败响应）+ 真实 VPM
 * （[TestDataStore] 隔离目录；开关翻转驱动真实 `isExHobbyEnabledFlow`）；
 * `MainDispatcherRule` → `Dispatchers.setMain`；JUnit4 → kotlin.test + `runTest`）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HobbyDetailViewModelExHobbyTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private val json: Json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    private class ExHobbyConfig {
        var body: String = "[]"
        var failure: Throwable? = null
        var calls: Int = 0
        val engine = MockEngine {
            calls++
            failure?.let { throw it }
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        fun api(): ExHobbyApi = ExHobbyApi(HttpClient(engine), Json {
            ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true
        })
    }

    private class Env(val exhobby: ExHobbyConfig, enabled: Boolean) {
        val detailMock = MockApi()
        val collectMock = MockApi()
        val commentMock = MockApi()
        val userMock = MockApi()
        val tokenManager = FakeTokenManager("utoken-test")
        val preferences = ViewPreferencesManager(TestDataStore.viewPrefs())

        suspend fun build(): HobbyDetailViewModel {
            preferences.setExHobbyEnabled(enabled)
            val delegate = CommentInteractionDelegate(
                CommentRepository(commentMock.api(), tokenManager),
                StickerRepository(detailMock.api()),
                tokenManager,
                UserRepository(userMock.api(), tokenManager)
            )
            return HobbyDetailViewModel(
                HobbyDetailRepository(detailMock.api()),
                CollectRepository(collectMock.api(), collectMock.json),
                delegate,
                ExHobbyRepository(exhobby.api()),
                preferences,
                UserRepository(userMock.api(), tokenManager)
            )
        }

        fun stubBasics(detailNodeId: Long = 100L, itemId: Long = 59570L) {
            detailMock.enqueue(
                detailMock.ok("""{"itemData":{"id":$detailNodeId,"nodeId":$detailNodeId,"itemId":$itemId,"nameCN":"测试手办"}}""")
            )
            repeat(7) { detailMock.enqueue(detailMock.ok("""{"list":[]}""")) }
            collectMock.enqueue(collectMock.ok("""{}"""))
        }
    }

    @Test
    fun loadDetail_whenExHobbyEnabled_loadsAndUpdatesExHobbyPics() = runTest {
        val exhobby = ExHobbyConfig().apply {
            body = """[{"path":"path/pic1.jpg"},{"path":"path/pic2.jpg"}]"""
        }
        val env = Env(exhobby, enabled = true)
        env.stubBasics()

        val viewModel = env.build()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExHobbyLoading)
        assertEquals(2, state.exHobbyPics.size)
        assertEquals("path/pic1.jpg", state.exHobbyPics[0].path)
        assertEquals(1, exhobby.calls)
    }

    @Test
    fun loadDetail_whenExHobbyDisabled_doesNotCallExHobbyRepo() = runTest {
        val exhobby = ExHobbyConfig()
        val env = Env(exhobby, enabled = false)
        env.stubBasics()

        val viewModel = env.build()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExHobbyLoading)
        assertTrue(state.exHobbyPics.isEmpty())
        assertEquals(0, exhobby.calls)
    }

    @Test
    fun loadDetail_whenExHobbyFails_silentlyDegradesToEmpty() = runTest {
        val exhobby = ExHobbyConfig().apply {
            failure = RuntimeException("网络超时")
        }
        val env = Env(exhobby, enabled = true)
        env.stubBasics()

        val viewModel = env.build()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExHobbyLoading)
        assertTrue(state.exHobbyPics.isEmpty())
    }

    @Test
    fun whenExHobbyEnabledFlowChanges_dynamicallyUpdatesStateAndLoadsPictures() = runTest {
        val exhobby = ExHobbyConfig().apply {
            body = """[{"path":"path/pic1.jpg"}]"""
        }
        val env = Env(exhobby, enabled = false)
        env.stubBasics()

        val viewModel = env.build()

        // 初始加载：禁用状态
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isExHobbyEnabled)
        assertTrue(viewModel.uiState.value.exHobbyPics.isEmpty())

        // 动态开启开关：触发自动拉取与状态翻转
        env.preferences.setExHobbyEnabled(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isExHobbyEnabled)
        assertEquals(1, viewModel.uiState.value.exHobbyPics.size)
        assertEquals("path/pic1.jpg", viewModel.uiState.value.exHobbyPics[0].path)

        // 动态关闭开关：状态翻转
        env.preferences.setExHobbyEnabled(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isExHobbyEnabled)
    }
}
