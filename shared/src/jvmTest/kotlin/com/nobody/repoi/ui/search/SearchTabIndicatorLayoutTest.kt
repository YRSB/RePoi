package com.nobody.repoi.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [SearchTabIndicator] 与 Material 3 `ScrollableTabRow` 的布局契约测试（Compose UI，桌面 JVM）。
 *
 * 锁住一个真实踩过的坑：M3 用指示器 Placeable 的**宽度**计算页签内居中偏移
 * `(tabWidth - placeable.width) / 2`，且该偏移允许为负。若指示器返回整行宽度，偏移会变成
 * 大负数，把指示器整体推出屏幕——编译、纯 JVM 单测都发现不了，只有真实布局一次才能暴露。
 * 因此这里断言最高优先级契约：布局宽度必须等于选中页签的内容宽，而不是整行宽度。
 */
@OptIn(ExperimentalTestApi::class)
class SearchTabIndicatorLayoutTest {

    @Test
    fun indicatorLayoutWidthMatchesSelectedTabContent() = runComposeUiTest {
        val page = mutableStateOf(3)
        setContent {
            MaterialTheme {
                Column(Modifier.fillMaxWidth()) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = page.value,
                        edgePadding = 16.dp,
                        indicator = {
                            SearchTabIndicator(
                                page = { page.value },
                                pageOffsetFraction = { 0f }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("周边", "相册", "厂商", "角色/要素", "作品", "系列", "人物", "用户")
                            .forEachIndexed { index, title ->
                                Tab(
                                    selected = page.value == index,
                                    onClick = { page.value = index },
                                    text = { Text(title) }
                                )
                            }
                    }
                }
            }
        }

        val selectedText = onNodeWithText("角色/要素", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val indicator = onNodeWithTag(SearchTabIndicatorTestTag).getUnclippedBoundsInRoot()
        assertTrue(
            indicator.width <= selectedText.width + 8.dp,
            "指示器宽度 ${indicator.width} 超出选中页签内容宽 ${selectedText.width}；" +
                "整行宽度会让 M3 的居中偏移变为大负数并把指示器推出屏幕"
        )

        runOnIdle { page.value = 1 }
        waitForIdle()
        val albumText = onNodeWithText("相册", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val moved = onNodeWithTag(SearchTabIndicatorTestTag).getUnclippedBoundsInRoot()
        assertTrue(
            moved.width <= albumText.width + 8.dp,
            "切换页签后指示器宽度 ${moved.width} 未跟随内容宽 ${albumText.width}"
        )
    }
}
