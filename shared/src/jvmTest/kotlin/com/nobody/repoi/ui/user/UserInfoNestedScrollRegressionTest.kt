package com.nobody.repoi.ui.user

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class UserInfoNestedScrollRegressionTest {

    private fun createConnection(
        outerListState: LazyListState,
        childCanScrollBackward: () -> Boolean,
        outerHeaderItemCount: Int = 4
    ): NestedScrollConnection {
        return object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0) {
                    val tabsItem = outerListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == outerHeaderItemCount }
                    val canScrollOuter = if (tabsItem != null) {
                        tabsItem.offset > 0
                    } else {
                        outerListState.firstVisibleItemIndex < outerHeaderItemCount
                    }
                    if (canScrollOuter) {
                        val maxAllowedScroll = if (tabsItem != null) tabsItem.offset.toFloat() else Float.MAX_VALUE
                        val toScroll = min(-delta, maxAllowedScroll)
                        if (toScroll > 0f) {
                            val consumed = outerListState.dispatchRawDelta(toScroll)
                            return Offset(0f, -consumed)
                        }
                    }
                }

                if (delta > 0 && !childCanScrollBackward()) {
                    val canScrollOuter = outerListState.firstVisibleItemIndex > 0 || outerListState.firstVisibleItemScrollOffset > 0
                    if (canScrollOuter) {
                        val consumed = outerListState.dispatchRawDelta(-delta)
                        return Offset(0f, -consumed)
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                val delta = available.y
                if (delta > 0) {
                    val canScrollOuter = outerListState.firstVisibleItemIndex > 0 || outerListState.firstVisibleItemScrollOffset > 0
                    if (canScrollOuter) {
                        val consumedDelta = outerListState.dispatchRawDelta(-delta)
                        return Offset(0f, -consumedDelta)
                    }
                }
                return Offset.Zero
            }
        }
    }

    @Test
    fun swipeDown_shouldScrollOuterListBackwardTowardsTop() = runComposeUiTest {
        var outerState: LazyListState? = null
        setContent {
            val listState = rememberLazyListState()
            outerState = listState
            LazyColumn(state = listState, modifier = Modifier.height(300.dp)) {
                items(10) { _ ->
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp))
                }
            }
        }
        waitForIdle()
        val outerListState = outerState!!

        outerListState.dispatchRawDelta(200f)
        waitForIdle()
        val initialIndex = outerListState.firstVisibleItemIndex
        val initialOffset = outerListState.firstVisibleItemScrollOffset
        assertEquals(2, initialIndex)

        val connection = createConnection(
            outerListState = outerListState,
            childCanScrollBackward = { false }
        )

        val preConsumed = connection.onPreScroll(Offset(0f, 50f), NestedScrollSource.UserInput)
        waitForIdle()

        assertTrue(preConsumed.y > 0f)
        assertTrue(
            outerListState.firstVisibleItemIndex < initialIndex ||
                (outerListState.firstVisibleItemIndex == initialIndex && outerListState.firstVisibleItemScrollOffset < initialOffset)
        )
    }

    @Test
    fun swipeUp_shouldScrollOuterListForwardTowardsTabs() = runComposeUiTest {
        var outerState: LazyListState? = null
        setContent {
            val listState = rememberLazyListState()
            outerState = listState
            LazyColumn(state = listState, modifier = Modifier.height(300.dp)) {
                items(10) { _ ->
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp))
                }
            }
        }
        waitForIdle()
        val outerListState = outerState!!
        assertEquals(0, outerListState.firstVisibleItemIndex)

        val connection = createConnection(
            outerListState = outerListState,
            childCanScrollBackward = { false }
        )

        val preConsumed = connection.onPreScroll(Offset(0f, -50f), NestedScrollSource.UserInput)
        waitForIdle()

        assertTrue(preConsumed.y < 0f)
        assertTrue(
            outerListState.firstVisibleItemIndex > 0 || outerListState.firstVisibleItemScrollOffset > 0
        )
    }

    @Test
    fun testRealGestureFixedNestedScroll() = runComposeUiTest {
        var outerState: LazyListState? = null
        var innerState: LazyListState? = null

        setContent {
            val outerListState = rememberLazyListState()
            val innerListState = rememberLazyListState()
            outerState = outerListState
            innerState = innerListState

            val connection = createConnection(
                outerListState = outerListState,
                childCanScrollBackward = { innerListState.canScrollBackward },
                outerHeaderItemCount = 4
            )

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = outerListState,
                    modifier = Modifier.fillMaxSize().testTag("outer_list")
                ) {
                    items(4) { idx ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp)
                                .background(if (idx % 2 == 0) Color.LightGray else Color.Gray)
                        ) {
                            Text("Header item $idx")
                        }
                    }
                    stickyHeader {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .background(Color.Yellow)
                                .testTag("tab_row")
                        ) {
                            Text("Sticky Tab Row")
                        }
                    }
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxHeight()
                                .fillMaxWidth()
                                .nestedScroll(connection)
                                .testTag("pager_content")
                        ) {
                            LazyColumn(
                                state = innerListState,
                                modifier = Modifier.fillMaxSize().testTag("inner_list")
                            ) {
                                items(30) { idx ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(60.dp)
                                            .background(if (idx % 2 == 0) Color.Cyan else Color.Magenta)
                                    ) {
                                        Text("Inner item $idx")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        waitForIdle()
        val outer = outerState!!

        onNodeWithTag("inner_list").performTouchInput {
            swipeUp()
        }
        waitForIdle()
        assertTrue(outer.firstVisibleItemIndex > 0 || outer.firstVisibleItemScrollOffset > 0)

        onNodeWithTag("inner_list").performTouchInput {
            swipeDown()
        }
        waitForIdle()
    }
}
