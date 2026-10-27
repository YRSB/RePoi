package com.nobody.repoi.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nobody.repoi.ui.common.comment.FakePlatformActions
import com.nobody.repoi.ui.common.comment.startTestKoin
import com.nobody.repoi.ui.common.comment.stopTestKoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * HobbyDetailScreen 错误态的 KMP 移植版（jvmTest + 真 ViewModel + MockEngine）。
 *
 * 装配复用 commonTest 的 [HobbyDetailTestEnv]；原 `verify(times(2))` 翻译为
 * 详情接口请求计数 == 2（初次加载 + 点击重试）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HobbyDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val env = HobbyDetailTestEnv()

    @Before
    fun setUp() {
        startTestKoin(FakePlatformActions())
        env.setUp()
    }

    @After
    fun tearDown() {
        env.tearDown()
        stopTestKoin()
    }

    @Test
    fun errorWithoutDetail_showsErrorStateAndRetriesLoad() {
        // 失败规则先注册优先命中（stubBasics 后注册的成功规则不会覆盖它）。
        env.mock.respondWhen(
            Regex.fromLiteral("api/item/get-v2"),
            """{"success":false,"code":500,"msg":"网络异常","data":null}"""
        )
        env.stubBasics()
        val vm = env.viewModel()

        composeRule.setContent {
            HobbyDetailScreen(
                navParam = DetailNavParam(itemId = 59570L),
                onBack = {},
                viewModel = vm
            )
        }
        runBlocking { vm.uiState.first { !it.isInitialLoading } }

        composeRule.onNodeWithText("条目加载失败").assertIsDisplayed()
        composeRule.onNodeWithText("网络异常").assertIsDisplayed()
        composeRule.onNodeWithText("点击重新加载").performClick()
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
