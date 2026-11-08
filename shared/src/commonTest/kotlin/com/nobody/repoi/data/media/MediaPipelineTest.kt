package com.nobody.repoi.data.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [MediaPipeline] 契约测试（纯 JVM，不依赖 Compose / Android）。
 *
 * 覆盖四类用途 × 八个分类的变体映射、404 避坑闭环、缺省图判定、纵横比缓存截断，
 * 并以等价性用例集合逐条锁定过渡期 [CdnUrlUtils] 桥接与管道输出一致。
 */
class MediaPipelineTest {

    private val pipeline: MediaPipeline = MediaPipeline.Default

    private val relPath = "2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
    private val base = "https://rfx.hpoi.net/gk/"
    private val exBase = "https://res.e39x.com/pic/"

    // -------------------------------------------------- HOBBY：四类用途映射

    @Test
    fun resolveUrl_hobby_mapsAllPurposesToCoverVariants() {
        assertEquals("${base}cover/s/$relPath", pipeline.resolveUrl(relPath, MediaCategory.HOBBY, ImagePurpose.THUMBNAIL))
        assertEquals("${base}cover/sr/$relPath", pipeline.resolveUrl(relPath, MediaCategory.HOBBY, ImagePurpose.ADAPTIVE_GRID))
        assertEquals("${base}cover/n/$relPath", pipeline.resolveUrl(relPath, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD))
        assertEquals("${base}cover/raw/$relPath", pipeline.resolveUrl(relPath, MediaCategory.HOBBY, ImagePurpose.FULLSCREEN_RAW))
    }

    @Test
    fun resolveUrl_hobby_stripsExistingVariantPrefixBeforeRebuilding() {
        ImagePurpose.entries.forEach { purpose ->
            val expected = pipeline.resolveUrl(relPath, MediaCategory.HOBBY, purpose)
            val prefixed = listOf("cover/sr/", "cover/s/", "cover/n/", "cover/raw/", "/cover/n/")
            prefixed.forEach { prefix ->
                assertEquals(expected, pipeline.resolveUrl("$prefix$relPath", MediaCategory.HOBBY, purpose))
            }
        }
    }

    @Test
    fun resolveUrl_hobby_rewritesVariantSegmentOfAbsoluteUrl() {
        val absoluteSmall = "https://rfx.hpoi.net/gk/cover/s/$relPath"
        assertEquals("${base}cover/sr/$relPath", pipeline.resolveUrl(absoluteSmall, MediaCategory.HOBBY, ImagePurpose.ADAPTIVE_GRID))
        assertEquals("${base}cover/n/$relPath", pipeline.resolveUrl(absoluteSmall, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD))
        assertEquals("${base}cover/raw/$relPath", pipeline.resolveUrl(absoluteSmall, MediaCategory.HOBBY, ImagePurpose.FULLSCREEN_RAW))
    }

    @Test
    fun resolveUrl_blankAndNullPathsYieldEmptyString() {
        MediaCategory.entries.forEach { category ->
            ImagePurpose.entries.forEach { purpose ->
                assertEquals("", pipeline.resolveUrl(null, category, purpose), "$category/$purpose null")
                assertEquals("", pipeline.resolveUrl("", category, purpose), "$category/$purpose blank")
                assertEquals("", pipeline.resolveUrl("   ", category, purpose), "$category/$purpose spaces")
            }
        }
    }

    @Test
    fun resolveUrl_keepsQueryStringAfterVariantPrefix() {
        val withQuery = "$relPath?v=2"
        assertEquals("${base}cover/n/$withQuery", pipeline.resolveUrl(withQuery, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD))
        assertEquals("${base}cover/sr/$withQuery", pipeline.resolveUrl(withQuery, MediaCategory.ALBUM, ImagePurpose.CARD_STANDARD))
    }

    // -------------------------------------------------- ALBUM：全用途禁 cover/n/

    @Test
    fun resolveUrl_album_neverEmitsCoverNormalDirectory() {
        ImagePurpose.entries.forEach { purpose ->
            assertEquals(
                pipeline.resolveUrl(relPath, MediaCategory.ALBUM, purpose).contains("cover/n/"),
                false,
                "相册用途 $purpose 命中 cover/n/ 会 404"
            )
        }
        assertEquals("${base}cover/sr/$relPath", pipeline.resolveUrl(relPath, MediaCategory.ALBUM, ImagePurpose.THUMBNAIL))
        assertEquals("${base}cover/sr/$relPath", pipeline.resolveUrl(relPath, MediaCategory.ALBUM, ImagePurpose.ADAPTIVE_GRID))
        assertEquals("${base}cover/sr/$relPath", pipeline.resolveUrl(relPath, MediaCategory.ALBUM, ImagePurpose.CARD_STANDARD))
        assertEquals("${base}cover/raw/$relPath", pipeline.resolveUrl(relPath, MediaCategory.ALBUM, ImagePurpose.FULLSCREEN_RAW))
    }

    @Test
    fun resolveUrl_album_rewritesCoverNormalAbsoluteUrlToSr() {
        val absoluteNormal = "https://rfx.hpoi.net/gk/cover/n/$relPath"
        assertEquals("${base}cover/sr/$relPath", pipeline.resolveUrl(absoluteNormal, MediaCategory.ALBUM, ImagePurpose.CARD_STANDARD))
    }

    // -------------------------------------------------- AVATAR / POST / BANNER

    @Test
    fun resolveUrl_avatar_mapsPurposesToHeadVariants() {
        assertEquals("${base}head/s/$relPath", pipeline.resolveUrl(relPath, MediaCategory.AVATAR, ImagePurpose.THUMBNAIL))
        assertEquals("${base}head/s/$relPath", pipeline.resolveUrl(relPath, MediaCategory.AVATAR, ImagePurpose.ADAPTIVE_GRID))
        assertEquals("${base}head/n/$relPath", pipeline.resolveUrl(relPath, MediaCategory.AVATAR, ImagePurpose.CARD_STANDARD))
        assertEquals("${base}head/raw/$relPath", pipeline.resolveUrl(relPath, MediaCategory.AVATAR, ImagePurpose.FULLSCREEN_RAW))
    }

    @Test
    fun resolveUrl_post_alwaysFallsBackToPostsNormal() {
        ImagePurpose.entries.forEach { purpose ->
            assertEquals("${base}posts/n/$relPath", pipeline.resolveUrl(relPath, MediaCategory.POST, purpose))
            assertEquals("${base}posts/n/$relPath", pipeline.resolveUrl("posts/raw/$relPath", MediaCategory.POST, purpose))
        }
        // 全屏原图必须降级：结果不含 /raw/
        assertFalse(pipeline.resolveRawUrl(relPath, MediaCategory.POST).contains("/raw/"))
    }

    @Test
    fun resolveUrl_banner_alwaysFallsBackToBnrNormal() {
        ImagePurpose.entries.forEach { purpose ->
            assertEquals("${base}bnr/n/$relPath", pipeline.resolveUrl(relPath, MediaCategory.BANNER, purpose))
            assertEquals("${base}bnr/n/$relPath", pipeline.resolveUrl("bnr/raw/$relPath", MediaCategory.BANNER, purpose))
        }
        assertFalse(pipeline.resolveRawUrl(relPath, MediaCategory.BANNER).contains("/raw/"))
    }

    @Test
    fun resolveUrl_postAndBanner_passAbsoluteUrlThroughUntouched() {
        val postAbsolute = "https://rfx.hpoi.net/gk/posts/n/$relPath"
        assertEquals(postAbsolute, pipeline.resolveUrl(postAbsolute, MediaCategory.POST, ImagePurpose.FULLSCREEN_RAW))
        assertEquals(postAbsolute, pipeline.resolveUrl(postAbsolute, MediaCategory.POST, ImagePurpose.THUMBNAIL))

        val bannerAbsolute = "https://custom.host/bnr.jpg"
        assertEquals(bannerAbsolute, pipeline.resolveUrl(bannerAbsolute, MediaCategory.BANNER, ImagePurpose.CARD_STANDARD))
        assertEquals(bannerAbsolute, pipeline.resolveRawUrl(bannerAbsolute, MediaCategory.BANNER))
    }

    // ------------------------------------------------------------ RESALE / ZONE

    @Test
    fun resolveUrl_resale_alwaysUsesResaleNormalAndTrims() {
        ImagePurpose.entries.forEach { purpose ->
            assertEquals("${base}resale/n/$relPath", pipeline.resolveUrl(relPath, MediaCategory.RESALE, purpose))
            assertEquals("${base}resale/n/$relPath", pipeline.resolveUrl("resale/raw/$relPath", MediaCategory.RESALE, purpose))
            assertEquals("${base}resale/n/$relPath", pipeline.resolveUrl("  $relPath  ", MediaCategory.RESALE, purpose))
        }
        assertEquals("https://cdn.example.com/a.jpg", pipeline.resolveRawUrl("https://cdn.example.com/a.jpg", MediaCategory.RESALE))
    }

    @Test
    fun resolveUrl_zone_routesByZonePrefixAndPurposeFallback() {
        // zone/ 前缀：所有用途都直连 CDN 根
        ImagePurpose.entries.forEach { purpose ->
            assertEquals("${base}zone/figure.png", pipeline.resolveUrl("zone/figure.png", MediaCategory.ZONE, purpose))
            assertEquals("${base}zone/figure.png", pipeline.resolveUrl("/zone/figure.png", MediaCategory.ZONE, purpose))
        }
        // 非 zone/ 前缀：图标回退 cover/s/，封面回退 cover/n/
        assertEquals("${base}cover/s/cover.png", pipeline.resolveUrl("cover.png", MediaCategory.ZONE, ImagePurpose.THUMBNAIL))
        assertEquals("${base}cover/s/cover.png", pipeline.resolveUrl("cover.png", MediaCategory.ZONE, ImagePurpose.ADAPTIVE_GRID))
        assertEquals("${base}cover/n/banner.jpg", pipeline.resolveUrl("banner.jpg", MediaCategory.ZONE, ImagePurpose.CARD_STANDARD))
        assertEquals("${base}cover/n/banner.jpg", pipeline.resolveUrl("banner.jpg", MediaCategory.ZONE, ImagePurpose.FULLSCREEN_RAW))
        // 绝对 URL 原样透传
        assertEquals("https://example.com/icon.png", pipeline.resolveUrl("https://example.com/icon.png", MediaCategory.ZONE, ImagePurpose.THUMBNAIL))
    }

    // --------------------------------------------------------------- EX_HOBBY

    @Test
    fun resolveUrl_exHobby_usesDedicatedDomainAndTwoVariants() {
        assertEquals("${exBase}s/$relPath", pipeline.resolveUrl(relPath, MediaCategory.EX_HOBBY, ImagePurpose.THUMBNAIL))
        assertEquals("${exBase}n/$relPath", pipeline.resolveUrl(relPath, MediaCategory.EX_HOBBY, ImagePurpose.ADAPTIVE_GRID))
        assertEquals("${exBase}n/$relPath", pipeline.resolveUrl(relPath, MediaCategory.EX_HOBBY, ImagePurpose.CARD_STANDARD))
        assertEquals("${exBase}n/$relPath", pipeline.resolveUrl(relPath, MediaCategory.EX_HOBBY, ImagePurpose.FULLSCREEN_RAW))
        // 前缀清洗（含残留前导斜杠）
        assertEquals("${exBase}s/$relPath", pipeline.resolveUrl("pic/s/$relPath", MediaCategory.EX_HOBBY, ImagePurpose.THUMBNAIL))
        assertEquals("${exBase}n/$relPath", pipeline.resolveUrl("/pic/s/$relPath", MediaCategory.EX_HOBBY, ImagePurpose.FULLSCREEN_RAW))
        // 外部绝对 URL 原样透传
        assertEquals("https://external.domain.com/pic.jpg", pipeline.resolveUrl("https://external.domain.com/pic.jpg", MediaCategory.EX_HOBBY, ImagePurpose.THUMBNAIL))
    }

    // ------------------------------------------------- 用户相片（pic/）家族桥接

    @Test
    fun resolvePictureUrl_mapsPurposesToPicVariants() {
        assertEquals("${base}pic/s/$relPath", pipeline.resolvePictureUrl(relPath, ImagePurpose.THUMBNAIL))
        assertEquals("${base}pic/n/$relPath", pipeline.resolvePictureUrl(relPath, ImagePurpose.ADAPTIVE_GRID))
        assertEquals("${base}pic/n/$relPath", pipeline.resolvePictureUrl(relPath, ImagePurpose.CARD_STANDARD))
        assertEquals("${base}pic/raw/$relPath", pipeline.resolvePictureUrl(relPath, ImagePurpose.FULLSCREEN_RAW))
        assertEquals("${base}pic/n/$relPath", pipeline.resolvePictureUrl("pic/raw/$relPath", ImagePurpose.CARD_STANDARD))
        assertEquals("${base}pic/raw/$relPath", pipeline.resolvePictureUrl("https://rfx.hpoi.net/gk/pic/n/$relPath", ImagePurpose.FULLSCREEN_RAW))
        assertEquals("", pipeline.resolvePictureUrl(null, ImagePurpose.THUMBNAIL))
    }

    // ------------------------------------------------------------- 缺省图判定

    @Test
    fun isPlaceholder_coversAllPlaceholderBranches() {
        listOf(null, "", "   ", "null", "NULL", "none", "none.jpg", "None.Jpg", "/none.jpg").forEach {
            assertTrue(pipeline.isPlaceholder(it), "应判为占位符：$it")
        }
        listOf(
            "cover/n/none.jpg",
            "/cover/sr/none.jpg",
            "https://rfx.hpoi.net/gk/cover/n/none.jpg",
            "https://rfx.hpoi.net/gk/cover/sr/none.jpg",
            "https://rfx.hpoi.net/gk/cover/raw/none.jpg",
            "2026/08/none.jpg/",
            "pic/n/none.jpg?v=1",
            "posts/n/none#frag"
        ).forEach { assertTrue(pipeline.isPlaceholder(it), "应判为占位符：$it") }

        listOf("2026/08/sample.jpg", "cover/n/2026/08/sample.jpg", "cover/n/none_cover.jpg", "pic/raw/none2.jpg")
            .forEach { assertFalse(pipeline.isPlaceholder(it), "不应判为占位符：$it") }
    }

    @Test
    fun getDefaultCoverUrl_coversAllPurposes() {
        assertEquals("${base}cover/s/none.jpg", pipeline.getDefaultCoverUrl(ImagePurpose.THUMBNAIL))
        assertEquals("${base}cover/sr/none.jpg", pipeline.getDefaultCoverUrl(ImagePurpose.ADAPTIVE_GRID))
        assertEquals("${base}cover/n/none.jpg", pipeline.getDefaultCoverUrl(ImagePurpose.CARD_STANDARD))
        assertEquals("${base}cover/raw/none.jpg", pipeline.getDefaultCoverUrl(ImagePurpose.FULLSCREEN_RAW))
    }

    @Test
    fun resolveCoverUrlOrDefault_fallsBackOnlyWhenPlaceholder() {
        ImagePurpose.entries.forEach { purpose ->
            val default = pipeline.getDefaultCoverUrl(purpose)
            assertEquals(default, equivalentCoverUrlOrDefault(null, purpose))
            assertEquals(default, equivalentCoverUrlOrDefault("  ", purpose))
            assertEquals(default, equivalentCoverUrlOrDefault("none.jpg", purpose))
            assertEquals(default, equivalentCoverUrlOrDefault("cover/n/none.jpg", purpose))
            assertEquals(
                pipeline.resolveUrl("2026/08/real.jpg", MediaCategory.HOBBY, purpose),
                equivalentCoverUrlOrDefault("2026/08/real.jpg", purpose)
            )
        }
    }

    // ------------------------------------------------------------ 纵横比缓存

    @Test
    fun recordAspectRatio_clampsIntoRange() {
        val keyLow = "media-pipeline-test/low"
        val keyHigh = "media-pipeline-test/high"
        val keyMid = "media-pipeline-test/mid"

        pipeline.recordAspectRatio(keyLow, 0.1f)
        pipeline.recordAspectRatio(keyHigh, 5f)
        pipeline.recordAspectRatio(keyMid, 1.5f)

        assertEquals(0.4f, pipeline.getAspectRatio(keyLow))
        assertEquals(2.5f, pipeline.getAspectRatio(keyHigh))
        assertEquals(1.5f, pipeline.getAspectRatio(keyMid))
    }

    @Test
    fun getAspectRatio_returnsNullForUnknownKey() {
        assertNull(pipeline.getAspectRatio("media-pipeline-test/never-recorded"))
    }

    // -------------------------------------------------------------- 契约细节

    @Test
    fun resolveRawUrl_equalsFullscreenRawForEveryCategory() {
        MediaCategory.entries.forEach { category ->
            assertEquals(
                pipeline.resolveUrl(relPath, category, ImagePurpose.FULLSCREEN_RAW),
                pipeline.resolveRawUrl(relPath, category),
                "category=$category"
            )
        }
    }

    @Test
    fun defaultCompanionInstanceIsTheSamePipeline() {
        assertEquals(DefaultMediaPipeline, MediaPipeline.Default)
        assertEquals("${base}cover/n/$relPath", MediaPipeline.Default.resolveUrl(relPath, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD))
    }

    // ------------------------------------- 等价性：与既有 CdnUrlUtils 回归用例逐条一致

    /**
     * 对 [com.nobody.repoi.util.CdnUrlUtilsTest] 覆盖过的每条输入/期望，断言管道输出一致。
     * 桥接映射固定：`getCoverSrUrl` → HOBBY/ADAPTIVE_GRID，`getCoverSmallUrl` → HOBBY/THUMBNAIL，
     * `getCoverUrl` → HOBBY/CARD_STANDARD，`getRawCoverUrl` → resolveRawUrl(HOBBY)，
     * `getBannerUrl` → BANNER/CARD_STANDARD，`getRawBannerUrl` → resolveRawUrl(BANNER)。
     */
    @Test
    fun pipeline_matchesCdnUrlUtilsRegressionExpectations() {
        val rel = "2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        val srUrl = "${base}cover/sr/$rel"
        val sUrl = "${base}cover/s/$rel"
        val nUrl = "${base}cover/n/$rel"
        val rawUrl = "${base}cover/raw/$rel"

        // 期望值全部直接取自 CdnUrlUtilsTest 的字面量，不复用被测实现。
        val coverSrCases = listOf<Pair<String?, String>>(
            rel to srUrl,
            "cover/sr/$rel" to srUrl,
            sUrl to srUrl,
            nUrl to srUrl,
            rawUrl to srUrl,
            null to "",
            "  " to ""
        )
        val coverSmallCases = listOf<Pair<String?, String>>(
            rel to sUrl,
            "cover/n/$rel" to sUrl,
            "cover/sr/$rel" to sUrl,
            srUrl to sUrl,
            nUrl to sUrl,
            rawUrl to sUrl,
            null to "",
            "  " to ""
        )
        val coverNormalCases = listOf<Pair<String?, String>>(
            rel to nUrl,
            sUrl to nUrl,
            srUrl to nUrl,
            rawUrl to nUrl,
            null to ""
        )
        val coverRawCases = listOf<Pair<String?, String>>(
            rel to rawUrl,
            srUrl to rawUrl,
            nUrl to rawUrl,
            sUrl to rawUrl,
            null to ""
        )

        coverSrCases.forEach { (path, expected) ->
            assertEquals(expected, pipeline.resolveUrl(path, MediaCategory.HOBBY, ImagePurpose.ADAPTIVE_GRID), "cover/sr：$path")
        }
        coverSmallCases.forEach { (path, expected) ->
            assertEquals(expected, pipeline.resolveUrl(path, MediaCategory.HOBBY, ImagePurpose.THUMBNAIL), "cover/s：$path")
        }
        coverNormalCases.forEach { (path, expected) ->
            assertEquals(expected, pipeline.resolveUrl(path, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD), "cover/n：$path")
        }
        coverRawCases.forEach { (path, expected) ->
            assertEquals(expected, pipeline.resolveRawUrl(path, MediaCategory.HOBBY), "cover/raw：$path")
        }

        // 缺省封面（OrDefault）语义：占位符才回退官方缺省图，否则走标准变体（字面量来自 CdnUrlUtilsTest）
        val defaultNormal = "${base}cover/n/none.jpg"
        listOf(null, "", "   ", "null", "none.jpg", "cover/n/none.jpg").forEach { path ->
            assertEquals(
                defaultNormal,
                equivalentCoverUrlOrDefault(path, ImagePurpose.CARD_STANDARD),
                "OrDefault 缺省回退失败：$path"
            )
        }
        assertEquals(
            defaultNormal,
            pipeline.getDefaultCoverUrl(ImagePurpose.CARD_STANDARD)
        )
        assertEquals(
            "${base}cover/n/2026/08/real.jpg",
            equivalentCoverUrlOrDefault("2026/08/real.jpg", ImagePurpose.CARD_STANDARD)
        )
        assertEquals(
            "${base}cover/s/2026/08/real.jpg",
            equivalentCoverUrlOrDefault("2026/08/real.jpg", ImagePurpose.THUMBNAIL)
        )
        assertEquals(
            "${base}cover/sr/2026/08/real.jpg",
            equivalentCoverUrlOrDefault("2026/08/real.jpg", ImagePurpose.ADAPTIVE_GRID)
        )

        // 路径清理（字面量来自 CdnUrlUtilsTest.cleanCoverPath_cleansAllPrefixes）
        val sample = "2026/08/sample.png"
        listOf("/$sample", "cover/sr/$sample", "cover/s/$sample", "cover/n/$sample", "cover/raw/$sample", "/cover/sr/$sample")
            .forEach { assertEquals(sample, DefaultMediaPipeline.cleanCoverPath(it), "cleanCoverPath 等价性失败：$it") }

        // 横幅（字面量来自 CdnUrlUtilsTest.getBannerUrl_and_getRawBannerUrl_resolvesToNormalVariant）
        val bannerRel = "2026/08/12d4b839597948b7b6444810137795e6.jpg"
        val bannerExpected = "${base}bnr/n/$bannerRel"
        listOf(bannerRel, "/$bannerRel", "bnr/n/$bannerRel", "bnr/raw/$bannerRel").forEach {
            assertEquals(bannerExpected, pipeline.resolveUrl(it, MediaCategory.BANNER, ImagePurpose.CARD_STANDARD))
            assertEquals(bannerExpected, pipeline.resolveRawUrl(it, MediaCategory.BANNER))
        }
        assertEquals("", pipeline.resolveUrl(null, MediaCategory.BANNER, ImagePurpose.CARD_STANDARD))
        assertEquals("", pipeline.resolveUrl("   ", MediaCategory.BANNER, ImagePurpose.CARD_STANDARD))
        assertEquals("", pipeline.resolveRawUrl(null, MediaCategory.BANNER))
        assertEquals("https://custom.host/bnr.jpg", pipeline.resolveUrl("https://custom.host/bnr.jpg", MediaCategory.BANNER, ImagePurpose.CARD_STANDARD))
        assertEquals("https://custom.host/bnr.jpg", pipeline.resolveRawUrl("https://custom.host/bnr.jpg", MediaCategory.BANNER))

        // ExHobby（字面量来自 CdnUrlUtilsTest.getExHobbyUrls_handlesPrefixesAndBlanks）
        val exRel = "2024/05/abc12345.jpg"
        val exThumb = "${exBase}s/$exRel"
        val exFull = "${exBase}n/$exRel"
        listOf(exRel, "pic/s/$exRel", "/pic/n/$exRel").forEach {
            assertEquals(exThumb, pipeline.resolveUrl(it, MediaCategory.EX_HOBBY, ImagePurpose.THUMBNAIL))
        }
        listOf(exRel, "pic/n/$exRel", "/pic/s/$exRel").forEach {
            assertEquals(exFull, pipeline.resolveUrl(it, MediaCategory.EX_HOBBY, ImagePurpose.FULLSCREEN_RAW))
        }
        assertEquals("https://external.domain.com/pic.jpg", pipeline.resolveUrl("https://external.domain.com/pic.jpg", MediaCategory.EX_HOBBY, ImagePurpose.THUMBNAIL))
        assertEquals("https://external.domain.com/pic.jpg", pipeline.resolveUrl("https://external.domain.com/pic.jpg", MediaCategory.EX_HOBBY, ImagePurpose.FULLSCREEN_RAW))
        assertEquals("", pipeline.resolveUrl(null, MediaCategory.EX_HOBBY, ImagePurpose.THUMBNAIL))
        assertEquals("", pipeline.resolveUrl("  ", MediaCategory.EX_HOBBY, ImagePurpose.THUMBNAIL))
        assertEquals("", pipeline.resolveUrl(null, MediaCategory.EX_HOBBY, ImagePurpose.FULLSCREEN_RAW))
        assertEquals("", pipeline.resolveUrl("", MediaCategory.EX_HOBBY, ImagePurpose.FULLSCREEN_RAW))
    }

    /** `getCoverUrlOrDefault` 的契约级等价组合（仅使用公开契约方法）。 */
    private fun equivalentCoverUrlOrDefault(path: String?, purpose: ImagePurpose): String =
        if (pipeline.isPlaceholder(path)) {
            pipeline.getDefaultCoverUrl(purpose)
        } else {
            pipeline.resolveUrl(path, MediaCategory.HOBBY, purpose)
        }
}
