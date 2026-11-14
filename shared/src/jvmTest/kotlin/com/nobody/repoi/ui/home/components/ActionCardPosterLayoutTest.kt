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

/**
 * [ActionCard] 左侧海报槽位的**真实布局约束**测试（桌面 JVM Compose UI）。
 *
 * 锁住 ADR-0016 / ADR-0021 的分类分流契约，这几条约束都无法由纯 JVM 单测或静态检查覆盖：
 * 1. 常规手办条目：9:13 刚性定框（96dp × 138.7dp），全生命周期零跳动；
 * 2. 厂商/角色等非手办实体：1:1 正方形（96dp × 96dp），贴合其方形封面；
 * 3. 相册传图条目未命中纵横比缓存：按 4:3 首帧占位（96dp × 72dp），不再被 9:13 拉满；
 * 4. 相册传图条目命中 9:20 超长比例：槽位高度不设上限，按真实比例撑到 96 / 0.45 ≈ 213.3dp，
 *    整张卡片随之撑高（`Row(IntrinsicSize.Min)` 的内在高度路径同样无上限）；
 * 5. 仅凭分类区间识别的条目：手办 10000 → 9:13，相册 70000 → 4:3 自适应。
 *
 * **零网络**：测试前把 Coil 单例换成不带任何 fetcher 的 `ImageLoader`，
 * 图片请求立即失败并落到破损图标分支，尺寸仍由 `Modifier` 链决定，与真实加载路径一致。
 * 比例缓存是全局单例，逐例清空，避免用例互相污染。
 */
@OptIn(ExperimentalTestApi::class, coil3.annotation.DelicateCoilApi::class)
class ActionCardPosterLayoutTest {

    @BeforeTest
    fun installOfflineImageLoader() {
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(PlatformContext.INSTANCE).build()
        )
    }

    @AfterTest
    fun clearAspectRatioCache() {
        ImageAspectRatioCache.clear()
    }

    /** 常规手办条目：宽度钉死 96dp，高度按 9:13 黄金比例算出 138.7dp。 */
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

    /** 相册传图未命中缓存：4:3 回退比例，96dp × 72dp。 */
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

    /** 相册传图命中 9:20 超长竖图：宽度 96dp，高度按比例完全撑开、不设上限。 */
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

        // 槽位高度必须被卡片真实包含（12dp×2 内边距），即卡片随图片撑高而非溢出
        val cardHeight = onNodeWithTag(CardContainerTag, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().height.value
        assertEquals(
            expectedHeight + 24f,
            cardHeight,
            1.5f,
            "卡片高度应由 9:20 竖图撑开，不得把图片裁到卡片外（实际 $cardHeight）"
        )
    }

    /** 厂商动态：封面普遍为正方形，槽位钉死 96dp × 96dp，不再拉成 9:13 竖框。 */
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

    /** 角色动态缺少 itemType 时走 1:1：验证真实组合路径；分类映射的证伪由 ActionPosterPolicyTest 负责。 */
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

    /** 缺少 itemType、仅靠 10000 分类区间识别的手办：仍走 9:13。 */
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

    /** 缺少 itemType、仅靠 70000 相册分类区间识别：走相册自适应，首帧 4:3 = 72dp。 */
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

    // ── 夹具 ──────────────────────────────────────────────────────────────

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

    /** 角色动态：故意不给 itemType，只靠 40100 分类区间识别。 */
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

    /** 手办动态：故意不给 itemType，只靠 10000 分类区间识别。 */
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

    /** 相册动态：itemType 缺失，只靠 70000 相册分类区间识别。 */
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
        // 玩家传图动态通常附图，右侧附图矩阵是卡片高度的天然锚点（ADR-0016 §1）
        relateData = listOf(
            RelateData(path = "2024/extra1.jpg"),
            RelateData(path = "2024/extra2.jpg"),
            RelateData(path = "2024/extra3.jpg")
        )
    )

    /**
     * 与 [ActionCard] 内部 `PoiAsyncImage` 通用重载生成的纵横比缓存键保持一致。
     *
     * 通用重载的缓存桶格式（`raw:fit:adaptive`）是其私有实现细节，没有 internal 构造函数可复用；
     * 一旦格式漂移，本用例会立即红，不会静默失效。
     */
    private fun albumCoverRatioKey(): String {
        val coverUrl = MediaPipeline.Default.resolveUrl(
            "2024/album.jpg",
            MediaCategory.ALBUM,
            ImagePurpose.ADAPTIVE_GRID
        )
        return "$coverUrl:raw:fit:adaptive"
    }

    private companion object {
        /** 测试外框探针：测量整卡高度（卡片自身没有语义节点）。 */
        const val CardContainerTag = "action_card_container"
    }
}
