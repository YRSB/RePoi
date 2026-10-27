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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * ModalSideSheet 的 KMP 移植版（jvmTest 直接运行，无 Robolectric）。
 *
 * 原测试限定 `w840dp-h1200dp` 大屏；Desktop 测试窗口固定宽屏，等价覆盖。
 */
@OptIn(ExperimentalTestApi::class)
class ModalSideSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clickingScrim_triggersDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            ModalSideSheet(
                onDismissRequest = { dismissed = true }
            ) {
                Text("抽屉内容")
            }
        }

        composeTestRule.waitForIdle()

        // 语义点击遮罩空白处
        composeTestRule.onNodeWithTag("modal_side_sheet_scrim").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.waitForIdle()

        assertTrue(dismissed, "点击遮罩空白处应触发关闭")
    }

    @Test
    fun touchClickingScrim_triggersDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            ModalSideSheet(
                onDismissRequest = { dismissed = true }
            ) {
                Text("抽屉内容")
            }
        }

        // 触控手势点击遮罩空白处（左侧区域）
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("modal_side_sheet_scrim").performTouchInput {
            click(percentOffset(0.2f, 0.5f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.waitForIdle()

        assertTrue(dismissed, "触控点击遮罩空白处应触发关闭")
    }

    @Test
    fun draggingScrim_doesNotTriggerDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            ModalSideSheet(
                onDismissRequest = { dismissed = true }
            ) {
                Text("抽屉内容")
            }
        }

        // 在遮罩区域做一次明确的长距离拖拽手势：起点/终点都落在左侧遮罩区（避开右侧
        // 380dp 抽屉），位移远超 touch slop。语义等同老 Robolectric 版的 swipeDown()——
        // 后者在本机窗口尺寸下可能落在斜对角/过短位移，难以保证是"拖拽"而非"点击"。
        composeTestRule.onNodeWithTag("modal_side_sheet_scrim").performTouchInput {
            swipe(
                start = percentOffset(0.2f, 0.2f),
                end = percentOffset(0.2f, 0.8f),
                durationMillis = 500
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.waitForIdle()

        assertFalse(dismissed, "在遮罩区域拖动手势不应触发关闭")
    }

    @Test
    fun clickingContent_doesNotTriggerDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            ModalSideSheet(
                onDismissRequest = { dismissed = true }
            ) {
                Text(
                    text = "抽屉内部内容",
                    modifier = Modifier.testTag("drawer_content_text")
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.waitForIdle()

        // 点击抽屉内部内容
        composeTestRule.onNodeWithTag("drawer_content_text").performClick()
        composeTestRule.mainClock.advanceTimeBy(300)

        assertFalse(dismissed, "点击抽屉内部内容不应触发关闭")
    }

    @Test
    fun clickingFilterChipInsideDrawer_triggersOnClick() {
        var chipClicked = false
        var dismissed = false
        composeTestRule.setContent {
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

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.waitForIdle()

        // 点击抽屉内部的 FilterChip
        composeTestRule.onNodeWithTag("test_filter_chip").performClick()
        composeTestRule.waitForIdle()

        assertTrue(chipClicked, "抽屉内部的 FilterChip 点击应能成功触发 onClick")
        assertFalse(dismissed, "点击抽屉内部 FilterChip 不应误触发关闭抽屉")
    }

    @Test
    fun clickingSurfaceBlankArea_doesNotTriggerDismiss() {
        var dismissed = false
        composeTestRule.setContent {
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

        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(300)
        composeTestRule.waitForIdle()

        // 触控点击抽屉内部空白区域（无可点击项的面板表面）
        composeTestRule.onNodeWithTag("drawer_blank_area").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(300)

        assertFalse(dismissed, "点击抽屉右侧面板内部空白区域绝不应误触发关闭抽屉")
    }
}
