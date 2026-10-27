package com.nobody.repoi.ui.search.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ModalSideSheet 的 KMP 移植版（jvmTest 直接运行，无 Robolectric）。
 *
 * 原测试限定 `w840dp-h1200dp` 大屏；Desktop 测试窗口固定宽屏，等价覆盖。
 */
@OptIn(ExperimentalTestApi::class)
class ModalSideSheetTest {

    @Test
    fun clickingScrim_triggersDismiss() = runComposeUiTest {
        var dismissed = false
        setContent {
            ModalSideSheet(
                onDismissRequest = { dismissed = true }
            ) {
                Text("抽屉内容")
            }
        }

        waitForIdle()

        // 语义点击遮罩空白处
        onNodeWithTag("modal_side_sheet_scrim").performClick()
        waitForIdle()
        mainClock.advanceTimeBy(300)
        waitForIdle()

        assertTrue(dismissed, "点击遮罩空白处应触发关闭")
    }

    @Test
    fun touchClickingScrim_triggersDismiss() = runComposeUiTest {
        var dismissed = false
        setContent {
            ModalSideSheet(
                onDismissRequest = { dismissed = true }
            ) {
                Text("抽屉内容")
            }
        }

        // 触控手势点击遮罩空白处（左侧区域）
        waitForIdle()
        onNodeWithTag("modal_side_sheet_scrim").performTouchInput {
            click(percentOffset(0.2f, 0.5f))
        }
        waitForIdle()
        mainClock.advanceTimeBy(300)
        waitForIdle()

        assertTrue(dismissed, "触控点击遮罩空白处应触发关闭")
    }

    @Test
    fun draggingScrim_doesNotTriggerDismiss() = runComposeUiTest {
        var dismissed = false
        setContent {
            ModalSideSheet(
                onDismissRequest = { dismissed = true }
            ) {
                Text("抽屉内容")
            }
        }

        // 在遮罩区域上下拖拽手势（与老 Robolectric 版一字不差的 swipeDown()：
        // 位移大到可触发滚动语义，"无需精确落点"正是原测试通过的关键——不要改成
        // 起终点都落在遮罩内的"精确拖拽"，那会被当成 tap 触发 dismiss）。
        onNodeWithTag("modal_side_sheet_scrim").performTouchInput {
            swipeDown()
        }
        waitForIdle()
        mainClock.advanceTimeBy(300)
        waitForIdle()

        assertFalse(dismissed, "在遮罩区域拖动手势不应触发关闭")
    }

    @Test
    fun clickingContent_doesNotTriggerDismiss() = runComposeUiTest {
        var dismissed = false
        setContent {
            ModalSideSheet(
                onDismissRequest = { dismissed = true }
            ) {
                Text(
                    text = "抽屉内部内容",
                    modifier = Modifier.testTag("drawer_content_text")
                )
            }
        }

        waitForIdle()
        mainClock.advanceTimeBy(300)
        waitForIdle()

        // 点击抽屉内部内容
        onNodeWithTag("drawer_content_text").performClick()
        mainClock.advanceTimeBy(300)

        assertFalse(dismissed, "点击抽屉内部内容不应触发关闭")
    }

    @Test
    fun clickingFilterChipInsideDrawer_triggersOnClick() = runComposeUiTest {
        var chipClicked = false
        var dismissed = false
        setContent {
            ModalSideSheet(
                onDismissRequest = { dismissed = true }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    FilterChip(
                        selected = false,
                        onClick = { chipClicked = true },
                        label = { Text("手办") },
                        modifier = Modifier.testTag("test_filter_chip")
                    )
                }
            }
        }

        waitForIdle()
        mainClock.advanceTimeBy(300)
        waitForIdle()

        // 点击抽屉内部的 FilterChip
        onNodeWithTag("test_filter_chip").performClick()
        waitForIdle()

        assertTrue(chipClicked, "抽屉内部的 FilterChip 点击应能成功触发 onClick")
        assertFalse(dismissed, "点击抽屉内部 FilterChip 不应误触发关闭抽屉")
    }

    @Test
    fun clickingSurfaceBlankArea_doesNotTriggerDismiss() = runComposeUiTest {
        var dismissed = false
        setContent {
            ModalSideSheet(
                onDismissRequest = { dismissed = true }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("drawer_blank_area")
                )
            }
        }

        waitForIdle()
        mainClock.advanceTimeBy(300)
        waitForIdle()

        // 触控点击抽屉内部空白区域（无可点击项的面板表面）
        onNodeWithTag("drawer_blank_area").performClick()
        waitForIdle()
        mainClock.advanceTimeBy(300)

        assertFalse(dismissed, "点击抽屉右侧面板内部空白区域绝不应误触发关闭抽屉")
    }
}

