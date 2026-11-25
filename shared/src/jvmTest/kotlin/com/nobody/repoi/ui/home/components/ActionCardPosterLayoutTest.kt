package com.nobody.repoi.ui.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.serviceLoaderEnabled
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.RelateData
import com.nobody.repoi.data.remote.model.RelateItem
import com.nobody.repoi.data.remote.model.UserProfileSummary
import com.nobody.repoi.util.ImageAspectRatioCache
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, coil3.annotation.DelicateCoilApi::class)
class ActionCardPosterLayoutTest {

    @BeforeTest
    fun installOfflineImageLoader() {
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(PlatformContext.INSTANCE)
                .serviceLoaderEnabled(false)
                .build()
        )
    }

    @AfterTest
    fun clearAspectRatioCache() {
        ImageAspectRatioCache.clear()
    }

    @Test
    fun hobbyAction_posterStaysAtGoldenRatio() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.fillMaxWidth()) {
                ActionCard(actionItem = hobbyAction())
            }
        }

        val bounds = onNodeWithTag(ActionCardPosterTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(96f, bounds.width.value, 0.5f, "手办海报槽位宽度应恒为 96dp")
        assertEquals(96f * 13f / 9f, bounds.height.value, 0.5f, "手办海报槽位应保持 9:13 刚性定框")
    }

    @Test
    fun albumAction_withoutCachedRatio_usesFourThirdsPlaceholder() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.fillMaxWidth().testTag(CardContainerTag)) {
                ActionCard(actionItem = albumAction())
            }
        }

        val bounds = onNodeWithTag(ActionCardPosterTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(96f, bounds.width.value, 0.5f, "相册海报槽位宽度应恒为 96dp")
        assertEquals(72f, bounds.height.value, 0.5f, "相册首帧应按 4:3 占位（96×72）")
    }

    @Test
    fun albumAction_withTallCachedRatio_expandsToFullPhotoRatio() = runComposeUiTest {
        ImageAspectRatioCache[albumCoverRatioKey()] = 9f / 20f

        setContent {
            Box(modifier = Modifier.fillMaxWidth().testTag(CardContainerTag)) {
                ActionCard(actionItem = albumAction())
            }
        }

        val expectedHeight = 96f / (9f / 20f)
        val bounds = onNodeWithTag(ActionCardPosterTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(96f, bounds.width.value, 0.5f, "相册海报宽度应恒为 96dp")
        assertEquals(expectedHeight, bounds.height.value, 0.5f, "超长竖图高度应按真实比例完全撑开")

        val cardHeight = onNodeWithTag(CardContainerTag, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().height.value
        assertEquals(
            expectedHeight + 24f,
            cardHeight,
            1.5f,
            "卡片高度应由 9:20 竖图撑开，不得把图片裁到卡片外（实际 $cardHeight）"
        )
    }

    @Test
    fun vendorAction_posterUsesSquareSlot() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.fillMaxWidth()) {
                ActionCard(actionItem = vendorAction())
            }
        }

        val bounds = onNodeWithTag(ActionCardPosterTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(96f, bounds.width.value, 0.5f, "厂商海报槽位宽度应恒为 96dp")
        assertEquals(96f, bounds.height.value, 0.5f, "厂商海报槽位应为 1:1 正方形")
    }

    @Test
    fun characterAction_identifiedByCategoryRange_usesSquareSlot() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.fillMaxWidth()) {
                ActionCard(actionItem = characterAction())
            }
        }

        val bounds = onNodeWithTag(ActionCardPosterTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(96f, bounds.width.value, 0.5f, "角色海报槽位宽度应恒为 96dp")
        assertEquals(96f, bounds.height.value, 0.5f, "仅凭分类区间识别的角色海报也应为 1:1 正方形")
    }

    @Test
    fun hobbyAction_identifiedByCategoryRange_staysGolden() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.fillMaxWidth()) {
                ActionCard(actionItem = categoryHobbyAction())
            }
        }

        val bounds = onNodeWithTag(ActionCardPosterTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(96f, bounds.width.value, 0.5f, "仅凭分类区间识别的手办海报宽度应恒为 96dp")
        assertEquals(96f * 13f / 9f, bounds.height.value, 0.5f, "仅凭分类区间识别的手办仍应保持 9:13")
    }

    @Test
    fun albumAction_identifiedByCategoryRange_usesAdaptiveSlot() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.fillMaxWidth()) {
                ActionCard(actionItem = categoryAlbumAction())
            }
        }

        val bounds = onNodeWithTag(ActionCardPosterTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(96f, bounds.width.value, 0.5f, "相册海报槽位宽度应恒为 96dp")
        assertEquals(72f, bounds.height.value, 0.5f, "仅凭 70000 分类区间识别的相册应按 4:3 占位")
    }

    private fun hobbyAction() = ActionItem(
        id = 1L,
        actionType = 101,
        actionComment = "常规手办动态",
        user = UserProfileSummary(nickname = "测试玩家"),
        relateItem = RelateItem(
            id = 11L,
            itemType = "hobby",
            name = "测试手办",
            cover = "2024/hobby.jpg"
        )
    )

    private fun vendorAction() = ActionItem(
        id = 3L,
        actionType = 102,
        actionComment = "厂商更新",
        user = UserProfileSummary(nickname = "测试玩家"),
        relateItem = RelateItem(
            id = 31L,
            itemType = "company",
            categoryId = 40000,
            name = "测试厂商",
            cover = "2024/vendor.jpg"
        )
    )

    private fun characterAction() = ActionItem(
        id = 4L,
        actionType = 102,
        actionComment = "角色更新",
        user = UserProfileSummary(nickname = "测试玩家"),
        relateItem = RelateItem(
            id = 41L,
            categoryId = 40100,
            name = "测试角色",
            cover = "2024/character.jpg"
        )
    )

    private fun categoryHobbyAction() = ActionItem(
        id = 5L,
        actionType = 102,
        actionComment = "手办更新",
        user = UserProfileSummary(nickname = "测试玩家"),
        relateItem = RelateItem(
            id = 51L,
            categoryId = 10000,
            name = "分类手办",
            cover = "2024/hobby-category.jpg"
        )
    )

    private fun categoryAlbumAction() = ActionItem(
        id = 6L,
        actionType = 102,
        actionComment = "相册更新",
        user = UserProfileSummary(nickname = "测试玩家"),
        relateItem = RelateItem(
            id = 61L,
            categoryId = 70000,
            name = "分类相册",
            cover = "2024/album-category.jpg"
        )
    )

    private fun albumAction() = ActionItem(
        id = 2L,
        actionType = 103,
        actionComment = "玩家传图",
        user = UserProfileSummary(nickname = "测试玩家"),
        relateItem = RelateItem(
            id = 21L,
            itemType = "album",
            name = "测试相册",
            cover = "2024/album.jpg"
        ),

        relateData = listOf(
            RelateData(path = "2024/extra1.jpg"),
            RelateData(path = "2024/extra2.jpg"),
            RelateData(path = "2024/extra3.jpg")
        )
    )

    private fun albumCoverRatioKey(): String {
        val coverUrl = MediaPipeline.Default.resolveUrl(
            "2024/album.jpg",
            MediaCategory.ALBUM,
            ImagePurpose.ADAPTIVE_GRID
        )
        return "$coverUrl:raw:fit:adaptive"
    }

    private companion object {

        const val CardContainerTag = "action_card_container"
    }
}
