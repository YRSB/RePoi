package com.nobody.repoi.ui.common

import com.nobody.repoi.data.media.DefaultMediaPipeline
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PoiAsyncImageTest {

    @Test
    fun placeholder_paths_do_not_request_network() {

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

    @Test
    fun placeholderUrl_album_never_uses_coverNormalDirectory() {

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

    @Test
    fun fallbackRatio_hobby_usesVerticalPosterRatio() {

        assertEquals(17f / 24f, fallbackRatioFor(MediaCategory.HOBBY, null), 0.0001f)
    }

    @Test
    fun fallbackRatio_albumAndOthers_useSquare() {

        listOf(MediaCategory.ALBUM, MediaCategory.POST, MediaCategory.ZONE, null).forEach { category ->
            assertEquals(1.0f, fallbackRatioFor(category, null), 0.0001f, "category=$category")
        }
    }

    @Test
    fun fallbackRatio_explicitValueWinsOverCategoryDefault() {
        assertEquals(0.75f, fallbackRatioFor(MediaCategory.HOBBY, 0.75f), 0.0001f)
        assertEquals(1.5f, fallbackRatioFor(MediaCategory.ALBUM, 1.5f), 0.0001f)
    }

    @Test
    fun clampRatio_bounds_to_pipeline_range() {

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

        assertEquals(1f, clampRatio(Float.NaN), 0.0001f)
        assertEquals(1f, clampRatio(0f), 0.0001f)
        assertEquals(1f, clampRatio(-1.5f), 0.0001f)

        assertEquals(2.5f, clampRatio(Float.POSITIVE_INFINITY), 0.0001f)
    }

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

        assertEquals(
            "cover/sr/2024/abc.jpg:$bucket",
            ratioCacheKey(
                path = "cover/sr/2024/abc.jpg",
                url = "https://rfx.hpoi.net/gk/cover/sr/2024/abc.jpg",
                bucket = bucket
            )
        )

        assertEquals(
            "https://rfx.hpoi.net/gk/cover/sr/abc.jpg:$bucket",
            ratioCacheKey(
                path = null,
                url = "https://rfx.hpoi.net/gk/cover/sr/abc.jpg",
                bucket = bucket
            )
        )

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
