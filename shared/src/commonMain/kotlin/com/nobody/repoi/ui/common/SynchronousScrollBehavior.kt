package com.nobody.repoi.ui.common

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
