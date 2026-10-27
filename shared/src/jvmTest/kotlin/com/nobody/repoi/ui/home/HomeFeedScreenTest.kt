package com.nobody.repoi.ui.home

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.network.appJson
import com.nobody.repoi.test.MockRepoEngine
import com.nobody.repoi.test.TestDataStores
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import com.nobody.repoi.ui.common.comment.FakePlatformActions
import com.nobody.repoi.ui.common.comment.startTestKoin
import com.nobody.repoi.ui.common.comment.stopTestKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HomeFeedScreen 的 KMP 移植版（jvmTest + 真 ViewModel + MockEngine，替代 Mockito + Robolectric）。
 *
 * `verify(vm)` 翻译为请求计数断言（点击重试/刷新必须发出新请求）。
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class HomeFeedScreenTest {

    private lateinit var mock: MockRepoEngine
    private lateinit var stores: TestDataStores
    private lateinit var viewModel: HomeFeedViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        startTestKoin(FakePlatformActions())
        mock = MockRepoEngine()
        stores = TestDataStores()
    }

    @AfterTest
    fun tearDown() {
        stopTestKoin()
        stores.cleanUp()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HomeFeedViewModel {
        val json = appJson()
        val client = io.ktor.client.HttpClient(mock.engine) { expectSuccess = true }
        val api = HpoiApi(client, json)
        val prefs = ViewPreferencesManager(stores.createDataStore("view_prefs"))
        viewModel = HomeFeedViewModel(HomeRepository(api), ZoneRepository(api, prefs), prefs)
        return viewModel
    }

    private fun stubZoneTabs() {
        mock.respondWhen(
            Regex.fromLiteral("api/zone/home-tabs"),
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true,"sort":"recent","hiddenZoneIds":[],"customOrder":[],"recentZoneIds":[]},"focusZones":[],"groups":[]}}"""
        )
    }

    private fun feedJson(vararg items: String): String =
        """{"success":true,"code":200,"msg":"ok","data":{"list":[${items.joinToString(",")}]}}"""

    @Test
    fun emptyErrorState_showsMessageAndRetriesInitialLoad() = runComposeUiTest {
        mock.enqueue("""{"success":false,"code":500,"msg":"网络连接失败","data":null}""")
        mock.enqueue("""{"success":false,"code":500,"msg":"网络连接失败","data":null}""")
        stubZoneTabs()
        val vm = createViewModel()
        runBlocking {
            withTimeout(10_000) {
                vm.uiState.first { !it.isInitialLoading }
            }
        }

        setContent { HomeFeedScreen(viewModel = vm) }

        onNodeWithText("网络连接失败").assertIsDisplayed()
        val countBefore = mock.requestCount
        onNodeWithText("重新加载").performClick()
        // 重试后 `isInitialLoading` 的 false→true→false 翻转可能快于 first{} 的订阅，
        // 导致 first{} 命中翻转前的 false 而漏等。用请求计数做收敛轮询。
        runBlocking {
            withTimeout(10_000) {
                while (mock.requestCount < countBefore + 1) {
                    delay(10)
                }
                vm.uiState.first { !it.isInitialLoading }
            }
        }

        assertEquals(countBefore + 1, mock.requestCount)
    }

    @Test
    fun emptyState_showsHintAndRefreshes() = runComposeUiTest {
        mock.enqueue(feedJson())
        mock.enqueue(feedJson())
        stubZoneTabs()
        val vm = createViewModel()
        runBlocking {
            withTimeout(10_000) {
                vm.uiState.first { !it.isInitialLoading }
            }
        }

        setContent { HomeFeedScreen(viewModel = vm) }

        onNodeWithText("暂无该分类下的动态情报").assertIsDisplayed()
        val countBefore = mock.requestCount
        onNodeWithText("刷新看看").performClick()
        // 同重试测试：先等请求计数+1 再等状态收敛，避免漏等翻转前的 isRefreshing=false。
        runBlocking {
            withTimeout(10_000) {
                while (mock.requestCount < countBefore + 1) {
                    delay(10)
                }
                vm.uiState.first { !it.isRefreshing }
            }
        }

        assertEquals(countBefore + 1, mock.requestCount)
    }

    @Test
    fun contentState_rendersActionCardAndDoesNotShowEmptyHint() = runComposeUiTest {
        mock.enqueue(
            feedJson(
                """{"id":1,"actionDate":"2026-10-21","actionComment":"这只手办涂装很稳","user":{"id":1,"nickname":"收藏家","level":3}}"""
            )
        )
        stubZoneTabs()
        val vm = createViewModel()
        runBlocking {
            withTimeout(10_000) {
                vm.uiState.first { it.items.isNotEmpty() }
            }
        }

        setContent { HomeFeedScreen(viewModel = vm) }

        onNodeWithText("这只手办涂装很稳").assertIsDisplayed()
        onNodeWithText("暂无该分类下的动态情报").assertDoesNotExist()
    }

    @Test
    fun initialLoadingState_keepsSkeletonWithoutEmptyOrErrorCards() = runComposeUiTest {
        // Freeze requests on virtual engine (never advanced): VM stays in initial loading.
        mock = MockRepoEngine(StandardTestDispatcher())
        stubZoneTabs()
        val vm = createViewModel()

        setContent { HomeFeedScreen(viewModel = vm) }

        onNodeWithText("暂无该分类下的动态情报").assertDoesNotExist()
        onNodeWithText("重新加载").assertDoesNotExist()
    }
}

