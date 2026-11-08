package com.nobody.repoi.ui.common

import com.nobody.repoi.data.media.DefaultMediaPipeline
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [PoiAsyncImage] 可脱离 Compose 运行时验证的纯逻辑单测。
 *
 * 只覆盖基元内部抽出的纯函数（占位判定、分类缺省图、语义缺省比例、比例截断与缓存键构造）；
 * 涉及测量/绘制/点击的部分交由 UI 层人工验证，避免为此引入 Compose 测试运行时。
 */
class PoiAsyncImageTest {

    // ------------------------------------------------------------------ 占位判定

    @Test
    fun placeholder_paths_do_not_request_network() {
        // 官方缺省吉祥物：无论解析结果多像真 URL，都不应发请求
        assertFalse(
            shouldLoadFromNetwork(
                path = "none.jpg",
                resolvedUrl = "https://rfx.hpoi.net/gk/cover/sr/none.jpg"
            )
        )
        assertFalse(shouldLoadFromNetwork(path = null, resolvedUrl = ""))
        assertFalse(shouldLoadFromNetwork(path = "   ", resolvedUrl = "https://rfx.hpoi.net/gk/cover/sr/"))
        assertFalse(
            shouldLoadFromNetwork(
                path = "cover/sr/null",
                resolvedUrl = "https://rfx.hpoi.net/gk/cover/sr/null"
            )
        )
        // 解析结果为空串时即便路径非空也不请求（绝对 URL 家族缺档的兜底）
        assertFalse(shouldLoadFromNetwork(path = "abc.jpg", resolvedUrl = ""))
    }

    @Test
    fun real_paths_request_network() {
        assertTrue(
            shouldLoadFromNetwork(
                path = "cover/sr/2024/abc.jpg",
                resolvedUrl = "https://rfx.hpoi.net/gk/cover/sr/2024/abc.jpg"
            )
        )
        assertTrue(
            shouldLoadFromNetwork(
                path = "https://rfx.hpoi.net/gk/cover/sr/abc.jpg",
                resolvedUrl = "https://rfx.hpoi.net/gk/cover/sr/abc.jpg"
            )
        )
    }

    // -------------------------------------------------------------- 分类缺省占位图

    @Test
    fun placeholderUrl_album_never_uses_coverNormalDirectory() {
        // 相册在 CDN 上没有 cover/n/ 目录：缺省吉祥物图也必须绕开这一档
        ImagePurpose.entries.forEach { purpose ->
            val placeholder = placeholderUrlFor(MediaPipeline.Default, MediaCategory.ALBUM, purpose)
            assertFalse(placeholder.contains("cover/n/"), "相册缺省图 $purpose 命中 cover/n/ 会 404")
            assertEquals(
                MediaPipeline.Default.resolveUrl(DefaultMediaPipeline.DEFAULT_COVER_PATH, MediaCategory.ALBUM, purpose),
                placeholder
            )
        }
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/sr/none.jpg",
            placeholderUrlFor(MediaPipeline.Default, MediaCategory.ALBUM, ImagePurpose.CARD_STANDARD)
        )
    }

    @Test
    fun placeholderUrl_otherCategories_keepDefaultCoverUrl() {
        // 其它分类保持真实部署的 cover/ 吉祥物图，不因相册特判被连带改写
        listOf(MediaCategory.HOBBY, MediaCategory.POST, MediaCategory.BANNER, MediaCategory.RESALE).forEach { category ->
            ImagePurpose.entries.forEach { purpose ->
                assertEquals(
                    MediaPipeline.Default.getDefaultCoverUrl(purpose),
                    placeholderUrlFor(MediaPipeline.Default, category, purpose),
                    "$category/$purpose"
                )
            }
        }
    }

    // ------------------------------------------------------------ 语义缺省比例

    @Test
    fun fallbackRatio_hobby_usesVerticalPosterRatio() {
        // ADR-0007 §3：内置手办 17:24 语义缺省
        assertEquals(17f / 24f, fallbackRatioFor(MediaCategory.HOBBY, null), 0.0001f)
    }

    @Test
    fun fallbackRatio_albumAndOthers_useSquare() {
        // 相册及其余分类（含通用重载的无分类上下文）一律 1:1
        listOf(MediaCategory.ALBUM, MediaCategory.POST, MediaCategory.ZONE, null).forEach { category ->
            assertEquals(1.0f, fallbackRatioFor(category, null), 0.0001f, "category=$category")
        }
    }

    @Test
    fun fallbackRatio_explicitValueWinsOverCategoryDefault() {
        assertEquals(0.75f, fallbackRatioFor(MediaCategory.HOBBY, 0.75f), 0.0001f)
        assertEquals(1.5f, fallbackRatioFor(MediaCategory.ALBUM, 1.5f), 0.0001f)
    }

    // ---------------------------------------------------------------- 比例截断

    @Test
    fun clampRatio_bounds_to_pipeline_range() {
        // 过窄长条图抬到 0.4f，过宽全景图压到 2.5f
        assertEquals(0.4f, clampRatio(0.1f), 0.0001f)
        assertEquals(2.5f, clampRatio(5f), 0.0001f)
        assertEquals(0.4f, clampRatio(0.4f), 0.0001f)
        assertEquals(2.5f, clampRatio(2.5f), 0.0001f)
    }

    @Test
    fun clampRatio_keeps_normal_values() {
        assertEquals(1f, clampRatio(1f), 0.0001f)
        assertEquals(0.75f, clampRatio(0.75f), 0.0001f)
        assertEquals(17f / 24f, clampRatio(17f / 24f), 0.0001f)
    }

    @Test
    fun clampRatio_falls_back_for_invalid_values() {
        // 矢量图与未指定尺寸的 painter 会给出 NaN；0 与负数同样非法，统一回落正方形
        assertEquals(1f, clampRatio(Float.NaN), 0.0001f)
        assertEquals(1f, clampRatio(0f), 0.0001f)
        assertEquals(1f, clampRatio(-1.5f), 0.0001f)
        // 无穷大是极端全景图的产物，按上界截断而非回落
        assertEquals(2.5f, clampRatio(Float.POSITIVE_INFINITY), 0.0001f)
    }

    // --------------------------------------------------------------- 缓存键构造

    @Test
    fun bucketKey_distinguishes_category_purpose_and_adaptive() {
        val adaptive = imageBucketKey(
            category = MediaCategory.ALBUM,
            purpose = ImagePurpose.ADAPTIVE_GRID,
            contentScaleLabel = "crop",
            adaptiveRatio = true
        )
        val static = imageBucketKey(
            category = MediaCategory.ALBUM,
            purpose = ImagePurpose.ADAPTIVE_GRID,
            contentScaleLabel = "crop",
            adaptiveRatio = false
        )
        val thumbnail = imageBucketKey(
            category = MediaCategory.ALBUM,
            purpose = ImagePurpose.THUMBNAIL,
            contentScaleLabel = "crop",
            adaptiveRatio = true
        )

        assertEquals("album:adaptive_grid:crop:adaptive", adaptive)
        assertEquals("album:adaptive_grid:crop:static", static)
        assertEquals("album:thumbnail:crop:adaptive", thumbnail)
        // 三个维度任一不同都必须落到不同桶，防止缩略图与原图缓存串味
        assertEquals(3, setOf(adaptive, static, thumbnail).size)
    }

    @Test
    fun bucketKey_matches_spec_shape_for_hobby_card() {
        assertEquals(
            "hobby:card_standard:crop:static",
            imageBucketKey(
                category = MediaCategory.HOBBY,
                purpose = ImagePurpose.CARD_STANDARD,
                contentScaleLabel = "crop",
                adaptiveRatio = false
            )
        )
    }

    @Test
    fun ratioKey_uses_fullPath_plus_bucket() {
        val bucket = imageBucketKey(
            category = MediaCategory.ALBUM,
            purpose = ImagePurpose.ADAPTIVE_GRID,
            contentScaleLabel = "crop",
            adaptiveRatio = true
        )
        // 键基座取完整路径：仅凭文件名会让不同目录下的同名图共用同一个比例
        assertEquals(
            "cover/sr/2024/abc.jpg:$bucket",
            ratioCacheKey(
                path = "cover/sr/2024/abc.jpg",
                url = "https://rfx.hpoi.net/gk/cover/sr/2024/abc.jpg",
                bucket = bucket
            )
        )
        // 无路径（通用重载的 URL 直通）时回落完整 URL 作为基座
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/sr/abc.jpg:$bucket",
            ratioCacheKey(
                path = null,
                url = "https://rfx.hpoi.net/gk/cover/sr/abc.jpg",
                bucket = bucket
            )
        )
        // 空白路径同样回落 URL
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/sr/abc.jpg:$bucket",
            ratioCacheKey(
                path = "   ",
                url = "https://rfx.hpoi.net/gk/cover/sr/abc.jpg",
                bucket = bucket
            )
        )
    }

    @Test
    fun ratioKey_differsForSameFileNameInDifferentDirectories() {
        val bucket = "album:adaptive_grid:crop:adaptive"
        val first = ratioCacheKey("cover/sr/2024/abc.jpg", "https://rfx.hpoi.net/gk/cover/sr/2024/abc.jpg", bucket)
        val second = ratioCacheKey("cover/sr/2025/abc.jpg", "https://rfx.hpoi.net/gk/cover/sr/2025/abc.jpg", bucket)
        assertTrue(first != second, "不同目录同名文件必须使用不同比例键：$first / $second")
    }

    @Test
    fun ratioKey_differsForDifferentBuckets() {
        assertTrue(
            ratioCacheKey("abc.jpg", "https://rfx.hpoi.net/gk/cover/sr/abc.jpg", "album:adaptive_grid:crop:adaptive") !=
                ratioCacheKey("abc.jpg", "https://rfx.hpoi.net/gk/cover/sr/abc.jpg", "album:thumbnail:crop:static")
        )
    }

    @Test
    fun ratioKey_placeholder_doesNotCollideAcrossCategories() {
        // 所有缺省图都指向 none.jpg：键必须靠分类桶区分，否则相册方图会污染手办竖图
        val albumBucket = imageBucketKey(
            MediaCategory.ALBUM, ImagePurpose.ADAPTIVE_GRID, "crop", true
        )
        val hobbyBucket = imageBucketKey(
            MediaCategory.HOBBY, ImagePurpose.ADAPTIVE_GRID, "crop", true
        )
        val albumPlaceholder = placeholderUrlFor(MediaPipeline.Default, MediaCategory.ALBUM, ImagePurpose.ADAPTIVE_GRID)
        val hobbyPlaceholder = placeholderUrlFor(MediaPipeline.Default, MediaCategory.HOBBY, ImagePurpose.ADAPTIVE_GRID)

        assertTrue(
            ratioCacheKey(null, albumPlaceholder, albumBucket) !=
                ratioCacheKey(null, hobbyPlaceholder, hobbyBucket)
        )
    }

    // ------------------------------------------------------------- 与管道的一致性

    @Test
    fun placeholder_detection_agrees_with_pipeline() {
        val pipeline = MediaPipeline.Default
        val samples = listOf(null, "", "  ", "none", "none.jpg", "cover/sr/none.jpg", "cover/sr/abc.jpg")
        for (sample in samples) {
            val resolved = pipeline.resolveUrl(sample, MediaCategory.ALBUM, ImagePurpose.ADAPTIVE_GRID)
            assertEquals(
                !pipeline.isPlaceholder(sample) && resolved.isNotBlank(),
                shouldLoadFromNetwork(sample, resolved, pipeline),
                "路径 $sample 的请求判定应与管道占位语义一致"
            )
        }
    }
}
