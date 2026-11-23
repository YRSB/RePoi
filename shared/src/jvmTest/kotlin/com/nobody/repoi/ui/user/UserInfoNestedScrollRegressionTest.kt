package com.nobody.repoi.ui.user

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class UserInfoNestedScrollRegressionTest {

    private fun createSingleScrollConnection(
        getHeaderOffset: () -> Float,
        setHeaderOffset: (Float) -> Unit,
        maxCollapsePx: Float,
        childCanScrollBackward: () -> Boolean,
        coroutineScope: CoroutineScope? = null
    ): NestedScrollConnection {
        return object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val currentOffset = getHeaderOffset()
                if (delta < 0f && currentOffset > -maxCollapsePx) {
                    val newOffset = (currentOffset + delta).coerceIn(-maxCollapsePx, 0f)
                    val consumed = newOffset - currentOffset
                    setHeaderOffset(newOffset)
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y
                val currentOffset = getHeaderOffset()
                if (delta > 0f && !childCanScrollBackward() && currentOffset < 0f) {
                    val newOffset = (currentOffset + delta).coerceIn(-maxCollapsePx, 0f)
                    val consumedDelta = newOffset - currentOffset
                    setHeaderOffset(newOffset)
                    return Offset(0f, consumedDelta)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val deltaY = available.y
                val currentOffset = getHeaderOffset()
                if (deltaY < -50f && currentOffset > -maxCollapsePx) {
                    coroutineScope?.launch {
                        Animatable(currentOffset).animateTo(
                            targetValue = -maxCollapsePx,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) {
                            setHeaderOffset(value)
                        }
                    }
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                val deltaY = available.y
                val currentOffset = getHeaderOffset()
                if (deltaY > 50f && !childCanScrollBackward() && currentOffset < 0f) {
                    coroutineScope?.launch {
                        Animatable(currentOffset).animateTo(
                            targetValue = 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) {
                            setHeaderOffset(value)
                        }
                    }
                    return Velocity(0f, deltaY)
                }
                return Velocity.Zero
            }
        }
    }

    @Test
    fun scrollUp_shouldCollapseHeaderOffsetFirst() {
        var offset = 0f
        val connection = createSingleScrollConnection(
            getHeaderOffset = { offset },
            setHeaderOffset = { offset = it },
            maxCollapsePx = 200f,
            childCanScrollBackward = { false }
        )

        val consumed = connection.onPreScroll(Offset(0f, -80f), NestedScrollSource.UserInput)
        assertEquals(-80f, consumed.y)
        assertEquals(-80f, offset)

        val consumedMore = connection.onPreScroll(Offset(0f, -150f), NestedScrollSource.UserInput)
        assertEquals(-120f, consumedMore.y)
        assertEquals(-200f, offset)

        val consumedBeyond = connection.onPreScroll(Offset(0f, -50f), NestedScrollSource.UserInput)
        assertEquals(0f, consumedBeyond.y)
        assertEquals(-200f, offset)
    }

    @Test
    fun scrollDown_whenChildCannotScrollBackward_shouldExpandHeaderOffset() {
        var offset = -200f
        val connection = createSingleScrollConnection(
            getHeaderOffset = { offset },
            setHeaderOffset = { offset = it },
            maxCollapsePx = 200f,
            childCanScrollBackward = { false }
        )

        val preConsumed = connection.onPreScroll(Offset(0f, 50f), NestedScrollSource.UserInput)
        assertEquals(0f, preConsumed.y)

        val postConsumed = connection.onPostScroll(Offset.Zero, Offset(0f, 50f), NestedScrollSource.UserInput)
        assertEquals(50f, postConsumed.y)
        assertEquals(-150f, offset)
    }

    @Test
    fun scrollDown_whenChildCanScrollBackward_shouldNotExpandHeader() {
        var offset = -200f
        val connection = createSingleScrollConnection(
            getHeaderOffset = { offset },
            setHeaderOffset = { offset = it },
            maxCollapsePx = 200f,
            childCanScrollBackward = { true }
        )

        val postConsumed = connection.onPostScroll(Offset.Zero, Offset(0f, 50f), NestedScrollSource.UserInput)
        assertEquals(0f, postConsumed.y)
        assertEquals(-200f, offset)
    }

    @Test
    fun testRealGestureSingleScrollCollapsingHeader() = runComposeUiTest {
        var headerOffsetValue = 0f
        var innerState: LazyListState? = null

        setContent {
            var headerOffsetPx by remember { mutableFloatStateOf(0f) }
            val innerListState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            innerState = innerListState

            val connection = remember {
                createSingleScrollConnection(
                    getHeaderOffset = { headerOffsetPx },
                    setHeaderOffset = {
                        headerOffsetPx = it
                        headerOffsetValue = it
                    },
                    maxCollapsePx = 200f,
                    childCanScrollBackward = { innerListState.canScrollBackward },
                    coroutineScope = coroutineScope
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(connection)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, headerOffsetPx.roundToInt()) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color.Blue)
                            .testTag("collapsible_header")
                    ) {
                        Text("Collapsible Header")
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .background(Color.Yellow)
                            .testTag("sticky_tab")
                    ) {
                        Text("Sticky Tab")
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                    ) {
                        LazyColumn(
                            state = innerListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("inner_list")
                        ) {
                            items(40) { idx ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .background(if (idx % 2 == 0) Color.LightGray else Color.White)
                                ) {
                                    Text("Item $idx")
                                }
                            }
                        }
                    }
                }
            }
        }

        waitForIdle()
        assertEquals(0f, headerOffsetValue)

        onNodeWithTag("inner_list").performTouchInput {
            swipeUp()
        }
        waitForIdle()

        assertTrue(headerOffsetValue < 0f)

        onNodeWithTag("inner_list").performTouchInput {
            swipeDown()
        }
        waitForIdle()

        assertTrue(headerOffsetValue >= -200f)
    }
}
