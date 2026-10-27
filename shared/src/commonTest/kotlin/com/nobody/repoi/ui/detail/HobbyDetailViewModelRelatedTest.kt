package com.nobody.repoi.ui.detail

import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 相关推荐手办分支的 KMP 移植版（真实仓储 + MockEngine）。
 *
 * KMP 侧 `getRelatedHobbies` = 同款分组 + 推荐列表两路合并（当前条目按 nodeId/itemId 剔除，
 * 去重后取 15）；两路均抛异常才整体失败，单路失败静默降级。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HobbyDetailViewModelRelatedTest {

    private val env = HobbyDetailTestEnv()

    @BeforeTest
    fun setUp() = env.setUp()

    @AfterTest
    fun tearDown() = env.tearDown()

    @Test
    fun loadDetail_whenRelatedHobbiesSuccess_updatesUiState() = runTest(env.dispatchers.testDispatcher) {
        env.stubBasics(
            groupBody = """{"success":true,"code":200,"msg":"ok","data":{"groups":[{"typeName":"再版","items":[{"id":101,"itemId":101,"nameCN":"初音未来 再版"},{"id":102,"itemId":102,"nameCN":"初音未来 韶华"}]}]}}"""
        )

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.relatedHobbies.size)
        assertEquals(101L, state.relatedHobbies[0].actualItemId)
        assertEquals("再版", state.relatedHobbies[0].typeName)
        assertEquals("初音未来 再版", state.relatedHobbies[0].displayName)
        assertEquals(102L, state.relatedHobbies[1].actualItemId)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun loadDetail_whenRelatedHobbiesFails_silentlyDegradesToEmpty() = runTest(env.dispatchers.testDispatcher) {
        // 两路均 HTTP 500：仓储抛 ApiException，VM 静默降级为空（规则先注册优先命中）。
        env.mock.respondWhen(Regex.fromLiteral("api/hobby/group"), "", HttpStatusCode.InternalServerError)
        env.mock.respondWhen(Regex.fromLiteral("api/hobby/push/list/V2"), "", HttpStatusCode.InternalServerError)
        env.stubBasics()

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.relatedHobbies.isEmpty())
        viewModel.viewModelScope.cancel()
    }
}
