package com.nobody.poishelf.ui.common

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * 全局统一无缝同步滚动行为。
 *
 * 区别于 Material 3 默认在 onPreScroll 中全额拦截/消费位移的二态割裂模式，
 * 本组件更新 TopAppBarState 的 heightOffset 但返回 Offset.Zero，
 * 使得列表与顶部操作区/搜索栏能够在同一次手势中 1:1 协同位移：
 * 1. 上滑时：顶栏收起与子列表滚动完全同步，紧密衔接，消除空白断层；
 * 2. 下滑时：列表向下滚动与顶栏向下展开同步响应，列表无需等待顶栏完全展开；
 * 3. 任意中间位置释放手势：不触发 snap 弹簧强制吸附，自然稳定悬停在半开状态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberSynchronousTopAppBarScrollBehavior(
    initialHeightOffsetLimit: Float = -Float.MAX_VALUE,
    state: TopAppBarState = rememberTopAppBarState(initialHeightOffsetLimit = initialHeightOffsetLimit),
    canScroll: () -> Boolean = { true }
): TopAppBarScrollBehavior {
    return remember(state, canScroll) {
        SynchronousTopAppBarScrollBehavior(
            state = state,
            canScroll = canScroll
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class SynchronousTopAppBarScrollBehavior(
    override val state: TopAppBarState,
    val canScroll: () -> Boolean = { true }
) : TopAppBarScrollBehavior {
    override val isPinned: Boolean = false
    override val snapAnimationSpec: AnimationSpec<Float>? = null
    override val flingAnimationSpec: DecayAnimationSpec<Float>? = null

    override var nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!canScroll()) return Offset.Zero

            val newOffset = state.heightOffset + available.y
            state.heightOffset = newOffset.coerceIn(state.heightOffsetLimit, 0f)

            // 返回 Offset.Zero：位移完全留给子组件（LazyGrid / LazyColumn）同时消费，避免列表被冻结
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (!canScroll()) return Offset.Zero
            state.contentOffset += consumed.y
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            return Velocity.Zero
        }
    }
}

/**
 * 具有首项折叠优先 + 下滑即时回现的嵌套滚动行为。
 * 专用于带有常驻吸顶次级栏（如搜索结果分类 TabRow / 筛选栏）的页面结构：
 * [ 可折叠操作栏 (SearchBar) ]
 * [ 常驻吸顶栏 (TabRow) ]
 * [ 结果列表 (LazyGrid / LazyColumn) ]
 *
 * 核心交互机制：
 * 1. 列表置顶上滑（从首项向下滚动浏览）：
 *    - 当处于列表最顶部且搜索框尚未完全收折时，在 onPreScroll 中全额消费手势位移收折搜索框；
 *    - 此时子列表不发生内部 item 滚动，保持首项贴在吸顶栏下方，随 topContentPadding 刚体平移上浮；
 *    - 待搜索框完全退出、TabRow 吸顶后，后续手势位移才移交给子列表内部滚动。
 * 2. 任意位置下滑（向上滚动页面）：
 *    - 无论列表处于何处，下滑手势均即时展开搜索框，搜索框与列表 1:1 协同位移，无需等待回到首项；
 *    - 列表回到最顶部且搜索框完全展开后，才允许触发下拉刷新 (PullToRefresh) 等顶层手势。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberExitUntilCollapsedScrollBehavior(
    initialHeightOffsetLimit: Float = -Float.MAX_VALUE,
    state: TopAppBarState = rememberTopAppBarState(initialHeightOffsetLimit = initialHeightOffsetLimit),
    canScroll: () -> Boolean = { true },
    isAtTop: () -> Boolean = { true }
): TopAppBarScrollBehavior {
    return remember(state, canScroll, isAtTop) {
        ExitUntilCollapsedScrollBehavior(
            state = state,
            canScroll = canScroll,
            isAtTop = isAtTop
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
class ExitUntilCollapsedScrollBehavior(
    override val state: TopAppBarState,
    val canScroll: () -> Boolean = { true },
    val isAtTop: () -> Boolean = { true }
) : TopAppBarScrollBehavior {
    override val isPinned: Boolean = false
    override val snapAnimationSpec: AnimationSpec<Float>? = null
    override val flingAnimationSpec: DecayAnimationSpec<Float>? = null

    override var nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!canScroll()) return Offset.Zero

            // 上滑优先折叠顶部栏 (available.y < 0)；下滑在任意位置即时展开顶部栏 (available.y > 0)
            val prevOffset = state.heightOffset
            val newOffset = (state.heightOffset + available.y).coerceIn(state.heightOffsetLimit, 0f)
            state.heightOffset = newOffset
            val consumedY = newOffset - prevOffset

            return Offset(0f, consumedY)
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (!canScroll()) return Offset.Zero
            state.contentOffset += consumed.y
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            return Velocity.Zero
        }
    }
}

