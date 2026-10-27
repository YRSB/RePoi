package com.nobody.repoi.ui.detail

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
 * 发售情报时间轴分支的 KMP 移植版（真实仓储 + MockEngine）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HobbyDetailViewModelTimelineTest {

    private val env = HobbyDetailTestEnv()

    @BeforeTest
    fun setUp() = env.setUp()

    @AfterTest
    fun tearDown() = env.tearDown()

    @Test
    fun loadDetail_whenTimelineSuccess_updatesTimelineState() = runTest(env.dispatchers.testDispatcher) {
        env.stubBasics(
            timelineBody = """{"success":true,"code":200,"msg":"ok","data":{"list":[{"actionData":"delay","actionComment":"延期至7月","actionDateString":"2024-05-20"},{"actionData":"preorder","actionComment":"开启预订","actionDateString":"2024-01-10"}]}}"""
        )

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isTimelineLoading)
        assertEquals(2, state.timeline.size)
        assertEquals("出荷延期", state.timeline[0].displayTypeLabel)
        assertEquals("开订时间", state.timeline[1].displayTypeLabel)
        viewModel.close()
    }

    @Test
    fun loadDetail_whenTimelineFails_silentlyDegradesToEmpty() = runTest(env.dispatchers.testDispatcher) {
        env.stubBasics(timelineBody = env.failEnvelope())

        val viewModel = env.viewModel()
        viewModel.loadDetail(DetailNavParam(itemId = 59570L))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isTimelineLoading)
        assertTrue(state.timeline.isEmpty())
        viewModel.close()
    }
}
