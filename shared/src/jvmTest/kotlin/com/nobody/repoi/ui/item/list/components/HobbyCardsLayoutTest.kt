package com.nobody.repoi.ui.item.list.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.serviceLoaderEnabled
import com.nobody.repoi.data.remote.model.HobbyItemSummary
import com.nobody.repoi.data.remote.model.UserCollectHobbyItem
import com.nobody.repoi.ui.collect.subpage.HobbyCardField
import com.nobody.repoi.ui.collect.subpage.HobbyCollectListCard
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, coil3.annotation.DelicateCoilApi::class)
class HobbyCardsLayoutTest {

    @BeforeTest
    fun installOfflineImageLoader() {
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(PlatformContext.INSTANCE)
                .serviceLoaderEnabled(false)
                .build()
        )
    }

    @Test
    fun hobbyListCard_alignsSecondRowBadgeBottomWithThumbnailBottom_andMaintains4dpSpacing() = runComposeUiTest {
        val item = HobbyItemSummary(
            id = 100L,
            name = "测试手办模型",
            nameCN = "测试手办模型",
            companyName = "Good Smile Company",
            releaseDate = "2026-05",
            price = "15000日元",
            rating = 8.5
        )

        setContent {
            Box(modifier = Modifier.fillMaxWidth().testTag("container")) {
                HobbyListCard(
                    item = item,
                    onClick = {}
                )
            }
        }

        val cardBounds = onNodeWithTag("container", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val thumbBounds = onNodeWithContentDescription("测试手办模型", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val row1BadgeBounds = onNodeWithText("厂商", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val row2BadgeBounds = onNodeWithText("定价", useUnmergedTree = true).getUnclippedBoundsInRoot()

        val expectedThumbBottom = thumbBounds.bottom.value
        val rowSpacing = row2BadgeBounds.top.value - row1BadgeBounds.bottom.value

        assertEquals(
            expectedThumbBottom,
            row2BadgeBounds.bottom.value,
            1.5f,
            "第 2 行微标签底部应与缩略图底部齐平（期望 $expectedThumbBottom, 实际 ${row2BadgeBounds.bottom.value}）"
        )

        assertEquals(
            6f,
            rowSpacing,
            0.5f,
            "2 行微标签之间间距应紧凑（对应 4dp 容器间距，实际文本间距 $rowSpacing）"
        )
    }

    @Test
    fun hobbyCollectListCard_alignsSecondRowBadgeBottomWithThumbnailBottom_andMaintains4dpSpacing() = runComposeUiTest {
        val item = UserCollectHobbyItem(
            id = 200L,
            name = "收藏手办",
            nameCN = "收藏手办",
            companyName = "Max Factory",
            releaseDate = "2026-06",
            price = "18000日元",
            rating = 9.0
        )

        val fields = listOf(
            HobbyCardField.COMPANY,
            HobbyCardField.RELEASE,
            HobbyCardField.PRICE,
            HobbyCardField.RATING
        )

        setContent {
            Box(modifier = Modifier.fillMaxWidth().testTag("container")) {
                HobbyCollectListCard(
                    item = item,
                    fields = fields,
                    isSelectionEnabled = false,
                    selectionMode = false,
                    isSelected = false,
                    onClick = {},
                    onLongClick = {}
                )
            }
        }

        val cardBounds = onNodeWithTag("container", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val thumbBounds = onNodeWithContentDescription("收藏手办", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val row1BadgeBounds = onNodeWithText("厂商", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val row2BadgeBounds = onNodeWithText("定价", useUnmergedTree = true).getUnclippedBoundsInRoot()

        val expectedThumbBottom = thumbBounds.bottom.value

        assertEquals(
            expectedThumbBottom,
            row2BadgeBounds.bottom.value,
            1.5f,
            "收藏卡第 2 行微标签底部应与缩略图底部齐平（期望 $expectedThumbBottom, 实际 ${row2BadgeBounds.bottom.value}）"
        )

        val rowSpacing = row2BadgeBounds.top.value - row1BadgeBounds.bottom.value
        assertEquals(
            6f,
            rowSpacing,
            0.5f,
            "收藏卡 2 行微标签之间间距应紧凑（对应 4dp 容器间距，实际文本间距 $rowSpacing）"
        )
    }
}
