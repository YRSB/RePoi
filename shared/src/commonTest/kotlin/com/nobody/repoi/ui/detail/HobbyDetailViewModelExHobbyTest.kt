package com.nobody.repoi.ui.detail

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ExHobby 扩展图库分支的 KMP 移植版（真实仓储 + 双 MockEngine + 真 DataStore）。
 *
 * - `verify(exHobbyRepo)` 翻译为 `exMock.requestCount` 断言（ExHobby 有内存缓存，
 *   每个测试的 env 全新装配，不存在跨测试缓存污染）；
 * - 开关 mock 翻译为真 `ViewPreferencesManager.setExHobbyEnabled` + flow 收集语义，
 *   因此创建 VM 后需 `advanceUntilIdle()` 让 init 收集器先同步开关态再 `loadDetail`。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HobbyDetailViewModelExHobbyTest {

    private val env = HobbyDetailTestEnv()

    @BeforeTest
    fun setUp() = env.setUp()

    @AfterTest
    fun tearDown() = env.tearDown()

    @Test
    fun loadDetail_whenExHobbyEnabled_loadsAndUpdatesExHobbyPics() = runTest(env.dispatchers.testDispatcher) {
        env.stubBasics(exHobbyBody = """[{"path":"path/pic1.jpg"},{"path":"path/pic2.jpg"}]""")

        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExHobbyLoading)
        assertEquals(2, state.exHobbyPics.size)
        assertEquals("path/pic1.jpg", state.exHobbyPics[0].path)
        assertEquals(1, env.exMock.requestCount)
        viewModel.close()
    }

    @Test
    fun loadDetail_whenExHobbyDisabled_doesNotCallExHobbyRepo() = runTest(env.dispatchers.testDispatcher) {
        env.prefs.setExHobbyEnabled(false)
        env.stubBasics()

        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExHobbyLoading)
        assertTrue(state.exHobbyPics.isEmpty())
        assertEquals(0, env.exMock.requestCount)
        viewModel.close()
    }

    @Test
    fun loadDetail_whenExHobbyFails_silentlyDegradesToEmpty() = runTest(env.dispatchers.testDispatcher) {
        env.stubBasics()
        // HTTP 500：仓储 Result.failure，VM 静默降级为空。
        // 注意必须用 dynamicResponder 覆盖（规则按注册顺序命中，stubBasics 的空规则在先）。
        env.exMock.dynamicResponder = {
            "Internal Server Error" to HttpStatusCode.InternalServerError
        }

        val viewModel = env.viewModel()
        advanceUntilIdle()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExHobbyLoading)
        assertTrue(state.exHobbyPics.isEmpty())
        viewModel.close()
    }

    @Test
    fun whenExHobbyEnabledFlowChanges_dynamicallyUpdatesStateAndLoadsPictures() = runTest(env.dispatchers.testDispatcher) {
        env.prefs.setExHobbyEnabled(false)
        env.stubBasics(exHobbyBody = """[{"path":"path/pic1.jpg"}]""")

        val viewModel = env.viewModel()
        advanceUntilIdle()

        // 初始加载：禁用状态
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isExHobbyEnabled)
        assertTrue(viewModel.uiState.value.exHobbyPics.isEmpty())

        // 动态开启开关：触发自动拉取与状态翻转
        env.prefs.setExHobbyEnabled(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isExHobbyEnabled)
        assertEquals(1, viewModel.uiState.value.exHobbyPics.size)
        assertEquals("path/pic1.jpg", viewModel.uiState.value.exHobbyPics[0].path)

        // 动态关闭开关：状态翻转
        env.prefs.setExHobbyEnabled(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isExHobbyEnabled)
        viewModel.close()
    }
}
