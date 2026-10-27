package com.nobody.repoi.ui.detail

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nobody.repoi.ui.common.comment.FakePlatformActions
import com.nobody.repoi.ui.common.comment.startTestKoin
import com.nobody.repoi.ui.common.comment.stopTestKoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * HobbyDetailScreen 错误态的 KMP 移植版（jvmTest + 真 ViewModel + MockEngine）。
 *
 * 装配复用 commonTest 的 [HobbyDetailTestEnv]；原 `verify(times(2))` 翻译为
 * 详情接口请求计数 == 2（初次加载 + 点击重试）。
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class HobbyDetailScreenTest {

    private val env = HobbyDetailTestEnv()

    @BeforeTest
    fun setUp() {
        // 注意：env.setUp() 先挂一个 StandardTestDispatcher 到 Main（commonTest 共享调度器
        // 铁律），但 jvmTest 用真实 runBlocking 等待、无人推进虚拟时钟——必须在此用
        // Desktop 真 Main 覆盖，否则 VM 协程永远挂起、测试 hang 到 CI 超时。
        startTestKoin(FakePlatformActions())
        // 真线程 engine：jvmTest 真 Main + runBlocking 等待，无人推进虚拟时钟。
        env.setUp(useVirtualEngine = false)
        Dispatchers.resetMain()
    }

    @AfterTest
    fun tearDown() {
        env.tearDown()
        stopTestKoin()
    }

    @Test
    fun errorWithoutDetail_showsErrorStateAndRetriesLoad() = runComposeUiTest {
        // 失败规则先注册优先命中（stubBasics 后注册的成功规则不会覆盖它）。
        env.mock.respondWhen(
            Regex.fromLiteral("api/item/get-v2"),
            """{"success":false,"code":500,"msg":"网络异常","data":null}"""
        )
        env.stubBasics()
        val vm = env.viewModel()

        setContent {
            HobbyDetailScreen(
                navParam = DetailNavParam(itemId = 59570L),
                onBack = {},
                viewModel = vm
            )
        }
        // jvmTest 铁律：任何 first{} 等待都必须包 withTimeout，宁可红不要 hang。
        runBlocking {
            withTimeout(10_000) {
                vm.uiState.first { !it.isInitialLoading }
            }
        }

        onNodeWithText("条目加载失败").assertIsDisplayed()
        onNodeWithText("网络异常").assertIsDisplayed()
        onNodeWithText("点击重新加载").performClick()
        // 点击后 `isInitialLoading` 的 false→true→false 翻转可能快于 first{} 订阅而漏等；
        // 先轮询详情请求计数==2 再等状态收敛。
        runBlocking {
            withTimeout(10_000) {
                while (env.mock.engine.requestHistory.count {
                        it.url.encodedPath.contains("api/item/get-v2")
                    } < 2
                ) {
                    delay(10)
                }
                vm.uiState.first { !it.isInitialLoading }
            }
        }

        val detailCalls = env.mock.engine.requestHistory.count {
            it.url.encodedPath.contains("api/item/get-v2")
        }
        assertEquals(2, detailCalls)
    }
}

