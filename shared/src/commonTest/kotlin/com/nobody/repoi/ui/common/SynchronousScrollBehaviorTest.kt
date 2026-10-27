package com.nobody.repoi.ui.common

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalMaterial3Api::class)
class SynchronousScrollBehaviorTest {

    @Test
    fun testOnPreScrollUpdatesOffsetWithoutConsumingDelta() {
        val state = TopAppBarState(
            initialHeightOffsetLimit = -150f,
            initialHeightOffset = 0f,
            initialContentOffset = 0f
        )
        val behavior = SynchronousTopAppBarScrollBehavior(state = state)

        // 上滑 50px
        val consumed = behavior.nestedScrollConnection.onPreScroll(
            available = Offset(0f, -50f),
            source = NestedScrollSource.UserInput
        )

        // 验证 1:1 协同关键：不消费手势（位移 100% 留给子列表消费）
        assertEquals(0f, consumed.x, 0.001f)
        assertEquals(0f, consumed.y, 0.001f)

        // 验证顶栏高度 offset 同步移动 50px
        assertEquals(-50f, state.heightOffset, 0.001f)
        assertEquals(1f / 3f, state.collapsedFraction, 0.001f)
    }

    @Test
    fun testOnPreScrollDownwardExpandsSynchronously() {
        val state = TopAppBarState(
            initialHeightOffsetLimit = -150f,
            initialHeightOffset = -100f,
            initialContentOffset = 0f
        )
        val behavior = SynchronousTopAppBarScrollBehavior(state = state)

        // 下滑 30px
        val consumed = behavior.nestedScrollConnection.onPreScroll(
            available = Offset(0f, 30f),
            source = NestedScrollSource.UserInput
        )

        assertEquals(0f, consumed.x, 0.001f)
        assertEquals(0f, consumed.y, 0.001f)
        assertEquals(-70f, state.heightOffset, 0.001f)
    }

    @Test
    fun testCanScrollCondition() {
        var allowScroll = false
        val state = TopAppBarState(
            initialHeightOffsetLimit = -150f,
            initialHeightOffset = 0f,
            initialContentOffset = 0f
        )
        val behavior = SynchronousTopAppBarScrollBehavior(
            state = state,
            canScroll = { allowScroll }
        )

        // 当 canScroll 返回 false 时，上滑不生效
        val consumed1 = behavior.nestedScrollConnection.onPreScroll(
            available = Offset(0f, -50f),
            source = NestedScrollSource.UserInput
        )
        assertEquals(0f, consumed1.y, 0.001f)
        assertEquals(0f, state.heightOffset, 0.001f)

        // 当 canScroll 返回 true 时，正常响应位移
        allowScroll = true
        val consumed2 = behavior.nestedScrollConnection.onPreScroll(
            available = Offset(0f, -50f),
            source = NestedScrollSource.UserInput
        )
        assertEquals(0f, consumed2.y, 0.001f)
        assertEquals(-50f, state.heightOffset, 0.001f)
    }

    @Test
    fun testNoSnapAnimationOnFling() {
        val state = TopAppBarState(
            initialHeightOffsetLimit = -200f,
            initialHeightOffset = -80f,
            initialContentOffset = 0f
        )
        val behavior = SynchronousTopAppBarScrollBehavior(state = state)

        // snapAnimationSpec 必须恒为 null，杜绝强行吸附与弹跳
        assertNull(behavior.snapAnimationSpec)

        // onPostFling 不改变中间态
        runBlocking {
            behavior.nestedScrollConnection.onPostFling(
                consumed = Velocity.Zero,
                available = Velocity.Zero
            )
        }

        assertEquals(-80f, state.heightOffset, 0.001f)
    }

    @Test
    fun testExitUntilCollapsedConsumesPreScrollWhenScrollingUp() {
        val state = TopAppBarState(
            initialHeightOffsetLimit = -100f,
            initialHeightOffset = 0f,
            initialContentOffset = 0f
        )
        val behavior = ExitUntilCollapsedScrollBehavior(
            state = state,
            isAtTop = { true }
        )

        // 1. 上滑 -40px：顶部栏优先全额消费，子列表不滚动（返回 -40f）
        val consumed1 = behavior.nestedScrollConnection.onPreScroll(
            available = Offset(0f, -40f),
            source = NestedScrollSource.UserInput
        )
        assertEquals(0f, consumed1.x, 0.001f)
        assertEquals(-40f, consumed1.y, 0.001f)
        assertEquals(-40f, state.heightOffset, 0.001f)

        // 2. 继续上滑 -80px：只需 -60px 即可完全折叠（达到 limit -100px），多余 -20px 留给子列表消费
        val consumed2 = behavior.nestedScrollConnection.onPreScroll(
            available = Offset(0f, -80f),
            source = NestedScrollSource.UserInput
        )
        assertEquals(0f, consumed2.x, 0.001f)
        assertEquals(-60f, consumed2.y, 0.001f)
        assertEquals(-100f, state.heightOffset, 0.001f)
    }

    @Test
    fun testExitUntilCollapsedPassesFullDeltaWhenFullyCollapsed() {
        val state = TopAppBarState(
            initialHeightOffsetLimit = -100f,
            initialHeightOffset = -100f,
            initialContentOffset = 0f
        )
        val behavior = ExitUntilCollapsedScrollBehavior(
            state = state,
            isAtTop = { false }
        )

        // 完全折叠后继续上滑 -50px：消耗 0px，100% 位移留给子列表内部滚动
        val consumed = behavior.nestedScrollConnection.onPreScroll(
            available = Offset(0f, -50f),
            source = NestedScrollSource.UserInput
        )
        assertEquals(0f, consumed.x, 0.001f)
        assertEquals(0f, consumed.y, 0.001f)
        assertEquals(-100f, state.heightOffset, 0.001f)
    }

    @Test
    fun testExitUntilCollapsedDownwardExpandsImmediatelyAnywhere() {
        val state = TopAppBarState(
            initialHeightOffsetLimit = -100f,
            initialHeightOffset = -100f,
            initialContentOffset = 0f
        )
        val behavior = ExitUntilCollapsedScrollBehavior(
            state = state,
            isAtTop = { false } // 列表处于浏览中间态
        )

        // 列表在中间态下滑 50px：搜索框立即展开 50px，消费 50px 位移，避免子列表双倍速偏移
        val consumed = behavior.nestedScrollConnection.onPreScroll(
            available = Offset(0f, 50f),
            source = NestedScrollSource.UserInput
        )
        assertEquals(0f, consumed.x, 0.001f)
        assertEquals(50f, consumed.y, 0.001f)
        assertEquals(-50f, state.heightOffset, 0.001f)
    }

    @Test
    fun testExitUntilCollapsedDownwardAtTopExpandsSmoothly() {
        val state = TopAppBarState(
            initialHeightOffsetLimit = -100f,
            initialHeightOffset = -100f,
            initialContentOffset = 0f
        )
        val behavior = ExitUntilCollapsedScrollBehavior(
            state = state,
            isAtTop = { true } // 列表已处于最顶端
        )

        // 列表在顶部下滑 40px：搜索框平滑展开 40px，优先消费 40px 位移展开顶部栏
        val consumed = behavior.nestedScrollConnection.onPreScroll(
            available = Offset(0f, 40f),
            source = NestedScrollSource.UserInput
        )
        assertEquals(0f, consumed.x, 0.001f)
        assertEquals(40f, consumed.y, 0.001f)
        assertEquals(-60f, state.heightOffset, 0.001f)
    }
}
