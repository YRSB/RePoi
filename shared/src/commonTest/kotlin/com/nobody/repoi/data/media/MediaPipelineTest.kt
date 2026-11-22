package com.nobody.repoi.data.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaPipelineTest {

    private val pipeline: MediaPipeline = MediaPipeline.Default

    private val relPath = "2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
    private val base = "https://rfx.hpoi.net/gk/"
    private val exBase = "https://res.e39x.com/pic/"

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
    fun resolveUrl_hobby_canonicalizesLegacyHpoiAbsoluteUrl() {

        val plainHttp = "http://rfx.hpoi.net/gk/cover/n/$relPath"
        assertEquals("${base}cover/n/$relPath", pipeline.resolveUrl(plainHttp, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD))

        val legacyHost = "https://www.hpoi.net/gk/cover/n/$relPath"
        assertEquals("${base}cover/n/$relPath", pipeline.resolveUrl(legacyHost, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD))
        assertEquals("${base}cover/raw/$relPath", pipeline.resolveRawUrl(legacyHost, MediaCategory.HOBBY))

        val withQuery = "http://rfx.hpoi.net/gk/cover/n/$relPath?v=2"
        assertEquals("${base}cover/n/$relPath?v=2", pipeline.resolveUrl(withQuery, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD))

        assertEquals(
            "http://cdn.example.com/cover/n/a.jpg",
            pipeline.resolveUrl("http://cdn.example.com/cover/n/a.jpg", MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD)
        )
        assertEquals(
            "http://rfx.hpoi.net/other/cover/n/a.jpg",
            pipeline.resolveUrl("http://rfx.hpoi.net/other/cover/n/a.jpg", MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD)
        )

        assertEquals(
            "https://not-hpoi.net/gk/cover/n/$relPath",
            pipeline.resolveUrl("https://not-hpoi.net/gk/cover/n/$relPath", MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD)
        )
        assertEquals(
            "https://cdn.example.com/hpoi.net/gk/cover/n/$relPath",
            pipeline.resolveUrl(
                "https://cdn.example.com/hpoi.net/gk/cover/n/$relPath",
                MediaCategory.HOBBY,
                ImagePurpose.CARD_STANDARD
            )
        )

        val gkOnlyInQuery = "https://www.hpoi.net/view?src=gk/cover/n/a.jpg"
        assertEquals(
            gkOnlyInQuery,
            pipeline.resolveUrl(gkOnlyInQuery, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD)
        )

        assertEquals(
            "${base}cover/n/$relPath",
            pipeline.resolveUrl("http://WWW.HPOI.NET/GK/cover/n/$relPath", MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD)
        )
    }

    @Test
    fun resolveUrl_keepsQueryStringAfterVariantPrefix() {
        val withQuery = "$relPath?v=2"
        assertEquals("${base}cover/n/$withQuery", pipeline.resolveUrl(withQuery, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD))
        assertEquals("${base}cover/sr/$withQuery", pipeline.resolveUrl(withQuery, MediaCategory.ALBUM, ImagePurpose.CARD_STANDARD))
    }

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
    fun resolveUrl_postAndBanner_downgradeRawSegmentOfAbsoluteUrl() {

        val postRaw = "https://rfx.hpoi.net/gk/posts/raw/$relPath"
        val postNormal = "https://rfx.hpoi.net/gk/posts/n/$relPath"
        assertEquals(postNormal, pipeline.resolveUrl(postRaw, MediaCategory.POST, ImagePurpose.FULLSCREEN_RAW))
        assertEquals(postNormal, pipeline.resolveRawUrl(postRaw, MediaCategory.POST))
        assertEquals(postNormal, pipeline.resolveUrl(postNormal, MediaCategory.POST, ImagePurpose.THUMBNAIL))

        val bannerRaw = "https://rfx.hpoi.net/gk/bnr/raw/$relPath"
        val bannerNormal = "https://rfx.hpoi.net/gk/bnr/n/$relPath"
        assertEquals(
            bannerNormal,
            pipeline.resolveUrl(bannerRaw, MediaCategory.BANNER, ImagePurpose.FULLSCREEN_RAW)
        )
        assertEquals(bannerNormal, pipeline.resolveRawUrl(bannerRaw, MediaCategory.BANNER))
        assertFalse(pipeline.resolveRawUrl(postRaw, MediaCategory.POST).contains("/raw/"))
        assertFalse(pipeline.resolveRawUrl(bannerRaw, MediaCategory.BANNER).contains("/raw/"))
    }

    @Test
    fun resolveUrl_postAndBanner_leaveCustomDomainAbsoluteUrlUntouched() {

        val bannerAbsolute = "https://custom.host/bnr.jpg"
        assertEquals(bannerAbsolute, pipeline.resolveUrl(bannerAbsolute, MediaCategory.BANNER, ImagePurpose.CARD_STANDARD))
        assertEquals(bannerAbsolute, pipeline.resolveRawUrl(bannerAbsolute, MediaCategory.BANNER))

        val postAbsolute = "https://custom.host/pics/abc.jpg"
        assertEquals(postAbsolute, pipeline.resolveUrl(postAbsolute, MediaCategory.POST, ImagePurpose.FULLSCREEN_RAW))
    }

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

        ImagePurpose.entries.forEach { purpose ->
            assertEquals("${base}zone/figure.png", pipeline.resolveUrl("zone/figure.png", MediaCategory.ZONE, purpose))
            assertEquals("${base}zone/figure.png", pipeline.resolveUrl("/zone/figure.png", MediaCategory.ZONE, purpose))
        }

        assertEquals("${base}cover/s/cover.png", pipeline.resolveUrl("cover.png", MediaCategory.ZONE, ImagePurpose.THUMBNAIL))
        assertEquals("${base}cover/s/cover.png", pipeline.resolveUrl("cover.png", MediaCategory.ZONE, ImagePurpose.ADAPTIVE_GRID))
        assertEquals("${base}cover/n/banner.jpg", pipeline.resolveUrl("banner.jpg", MediaCategory.ZONE, ImagePurpose.CARD_STANDARD))
        assertEquals("${base}cover/n/banner.jpg", pipeline.resolveUrl("banner.jpg", MediaCategory.ZONE, ImagePurpose.FULLSCREEN_RAW))

        assertEquals("https://example.com/icon.png", pipeline.resolveUrl("https://example.com/icon.png", MediaCategory.ZONE, ImagePurpose.THUMBNAIL))
    }

    @Test
    fun resolveUrl_exHobby_usesDedicatedDomainAndTwoVariants() {
        assertEquals("${exBase}s/$relPath", pipeline.resolveUrl(relPath, MediaCategory.EX_HOBBY, ImagePurpose.THUMBNAIL))
        assertEquals("${exBase}n/$relPath", pipeline.resolveUrl(relPath, MediaCategory.EX_HOBBY, ImagePurpose.ADAPTIVE_GRID))
        assertEquals("${exBase}n/$relPath", pipeline.resolveUrl(relPath, MediaCategory.EX_HOBBY, ImagePurpose.CARD_STANDARD))
        assertEquals("${exBase}n/$relPath", pipeline.resolveUrl(relPath, MediaCategory.EX_HOBBY, ImagePurpose.FULLSCREEN_RAW))

        assertEquals("${exBase}s/$relPath", pipeline.resolveUrl("pic/s/$relPath", MediaCategory.EX_HOBBY, ImagePurpose.THUMBNAIL))
        assertEquals("${exBase}n/$relPath", pipeline.resolveUrl("/pic/s/$relPath", MediaCategory.EX_HOBBY, ImagePurpose.FULLSCREEN_RAW))

        assertEquals("https://external.domain.com/pic.jpg", pipeline.resolveUrl("https://external.domain.com/pic.jpg", MediaCategory.EX_HOBBY, ImagePurpose.THUMBNAIL))
    }

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

    @Test
    fun resolvePictureUrl_defaultImplementationCanBeOverriddenByCustomPipeline() {

        val custom = object : MediaPipeline by MediaPipeline.Default {
            override fun resolvePictureUrl(path: String?, purpose: ImagePurpose): String =
                "custom://${path ?: "null"}/${purpose.name.lowercase()}"
        }

        assertEquals("custom://$relPath/thumbnail", custom.resolvePictureUrl(relPath, ImagePurpose.THUMBNAIL))

        assertEquals(
            "${base}cover/n/$relPath",
            custom.resolveUrl(relPath, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD)
        )

        assertEquals(
            "${base}pic/n/$relPath",
            MediaPipeline.Default.resolvePictureUrl(relPath, ImagePurpose.CARD_STANDARD)
        )
    }

    @Test
    fun resolveUrl_albumPlaceholder_neverEmitsCoverNormalDirectory() {

        ImagePurpose.entries.forEach { purpose ->
            val placeholder = pipeline.resolveUrl(
                DefaultMediaPipeline.DEFAULT_COVER_PATH,
                MediaCategory.ALBUM,
                purpose
            )
            assertFalse(placeholder.contains("cover/n/"), "相册缺省图 $purpose 命中 cover/n/ 会 404")
            assertTrue(placeholder.endsWith("/none.jpg"), "相册缺省图应落在 none.jpg：$placeholder")
        }
        assertEquals(
            "${base}cover/sr/none.jpg",
            pipeline.resolveUrl(DefaultMediaPipeline.DEFAULT_COVER_PATH, MediaCategory.ALBUM, ImagePurpose.CARD_STANDARD)
        )
        assertEquals(
            "${base}cover/raw/none.jpg",
            pipeline.resolveUrl(DefaultMediaPipeline.DEFAULT_COVER_PATH, MediaCategory.ALBUM, ImagePurpose.FULLSCREEN_RAW)
        )

        assertEquals("${base}cover/n/none.jpg", pipeline.getDefaultCoverUrl(ImagePurpose.CARD_STANDARD))
    }

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

    @Test
    fun pipeline_matchesCdnUrlUtilsRegressionExpectations() {
        val rel = "2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        val srUrl = "${base}cover/sr/$rel"
        val sUrl = "${base}cover/s/$rel"
        val nUrl = "${base}cover/n/$rel"
        val rawUrl = "${base}cover/raw/$rel"

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

        val sample = "2026/08/sample.png"
        listOf("/$sample", "cover/sr/$sample", "cover/s/$sample", "cover/n/$sample", "cover/raw/$sample", "/cover/sr/$sample")
            .forEach { assertEquals(sample, DefaultMediaPipeline.cleanCoverPath(it), "cleanCoverPath 等价性失败：$it") }

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

    private fun equivalentCoverUrlOrDefault(path: String?, purpose: ImagePurpose): String =
        if (pipeline.isPlaceholder(path)) {
            pipeline.getDefaultCoverUrl(purpose)
        } else {
            pipeline.resolveUrl(path, MediaCategory.HOBBY, purpose)
        }
}
