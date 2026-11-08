package com.nobody.repoi.ui.common

import androidx.compose.ui.layout.ContentScale
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
 * 只覆盖基元内部抽出的纯函数（占位判定、比例截断、缓存键构造）；
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
    fun ratioKey_uses_file_name_plus_bucket() {
        val bucket = imageBucketKey(
            category = MediaCategory.ALBUM,
            purpose = ImagePurpose.ADAPTIVE_GRID,
            contentScaleLabel = "crop",
            adaptiveRatio = true
        )
        // 键基座取文件名，避免同一资源在不同变体目录下被误判为不同图片
        assertEquals(
            "abc.jpg:$bucket",
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
    }

    @Test
    fun ratioKey_is_stable_across_variants_of_same_file() {
        val bucket = "album:adaptive_grid:crop:adaptive"
        val small = ratioCacheKey("cover/s/abc.jpg", "https://rfx.hpoi.net/gk/cover/s/abc.jpg", bucket)
        val raw = ratioCacheKey("cover/raw/abc.jpg", "https://rfx.hpoi.net/gk/cover/raw/abc.jpg", bucket)
        // 同一张图的不同变体共享同一纵横比，键必须一致
        assertEquals(small, raw)
    }

    @Test
    fun ratioKey_differs_for_different_buckets() {
        assertTrue(
            ratioCacheKey("abc.jpg", "https://rfx.hpoi.net/gk/cover/sr/abc.jpg", "album:adaptive_grid:crop:adaptive") !=
                ratioCacheKey("abc.jpg", "https://rfx.hpoi.net/gk/cover/sr/abc.jpg", "album:thumbnail:crop:static")
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
                shouldLoadFromNetwork(sample, resolved),
                "路径 $sample 的请求判定应与管道占位语义一致"
            )
        }
    }
}
