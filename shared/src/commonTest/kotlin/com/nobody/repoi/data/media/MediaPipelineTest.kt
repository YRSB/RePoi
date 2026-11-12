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
    fun resolveUrl_hobby_canonicalizesLegacyHpoiAbsoluteUrl() {
        // 历史快照类列表可能存下多年前的绝对地址：http 明文在 Android 直接被拒，旧域名也可能下线
        val plainHttp = "http://rfx.hpoi.net/gk/cover/n/$relPath"
        assertEquals("${base}cover/n/$relPath", pipeline.resolveUrl(plainHttp, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD))

        val legacyHost = "https://www.hpoi.net/gk/cover/n/$relPath"
        assertEquals("${base}cover/n/$relPath", pipeline.resolveUrl(legacyHost, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD))
        assertEquals("${base}cover/raw/$relPath", pipeline.resolveRawUrl(legacyHost, MediaCategory.HOBBY))

        // query 必须保留（服务端以 query 区分版本时不能丢）
        val withQuery = "http://rfx.hpoi.net/gk/cover/n/$relPath?v=2"
        assertEquals("${base}cover/n/$relPath?v=2", pipeline.resolveUrl(withQuery, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD))

        // 非 hpoi 域名与非 CDN 路径原样透传，避免误改外部资源
        assertEquals(
            "http://cdn.example.com/cover/n/a.jpg",
            pipeline.resolveUrl("http://cdn.example.com/cover/n/a.jpg", MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD)
        )
        assertEquals(
            "http://rfx.hpoi.net/other/cover/n/a.jpg",
            pipeline.resolveUrl("http://rfx.hpoi.net/other/cover/n/a.jpg", MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD)
        )

        // 域名按 authority 精确匹配：形似域名、或在 path 中出现 hpoi.net 的外部 URL 不得被改写
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

        // gk/ 只在 query 里时不能当成 CDN 根截断路径
        val gkOnlyInQuery = "https://www.hpoi.net/view?src=gk/cover/n/a.jpg"
        assertEquals(
            gkOnlyInQuery,
            pipeline.resolveUrl(gkOnlyInQuery, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD)
        )

        // 旧快照可能原样保存大小写混合的绝对地址，marker 匹配忽略大小写但保留原始路径
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
    fun resolveUrl_postAndBanner_downgradeRawSegmentOfAbsoluteUrl() {
        // 服务端没有 posts/raw/ 与 bnr/raw/ 目录：绝对 URL 也必须改写，否则线上必然 404
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
        // 自定义域名不含任何已知变体段，替换规则自然不命中
        val bannerAbsolute = "https://custom.host/bnr.jpg"
        assertEquals(bannerAbsolute, pipeline.resolveUrl(bannerAbsolute, MediaCategory.BANNER, ImagePurpose.CARD_STANDARD))
        assertEquals(bannerAbsolute, pipeline.resolveRawUrl(bannerAbsolute, MediaCategory.BANNER))

        val postAbsolute = "https://custom.host/pics/abc.jpg"
        assertEquals(postAbsolute, pipeline.resolveUrl(postAbsolute, MediaCategory.POST, ImagePurpose.FULLSCREEN_RAW))
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

    @Test
    fun resolvePictureUrl_defaultImplementationCanBeOverriddenByCustomPipeline() {
        // 接口默认实现必须真的被覆写方法接管：旧实现的顶层扩展函数会让 receiver 完全失效
        val custom = object : MediaPipeline by MediaPipeline.Default {
            override fun resolvePictureUrl(path: String?, purpose: ImagePurpose): String =
                "custom://${path ?: "null"}/${purpose.name.lowercase()}"
        }

        assertEquals("custom://$relPath/thumbnail", custom.resolvePictureUrl(relPath, ImagePurpose.THUMBNAIL))
        // 未覆写的成员仍委托默认实现
        assertEquals(
            "${base}cover/n/$relPath",
            custom.resolveUrl(relPath, MediaCategory.HOBBY, ImagePurpose.CARD_STANDARD)
        )
        // 默认实现自身保持 pic/ 家族规则
        assertEquals(
            "${base}pic/n/$relPath",
            MediaPipeline.Default.resolvePictureUrl(relPath, ImagePurpose.CARD_STANDARD)
        )
    }

    // ------------------------------------------------------ 相册缺省占位图（R2-3）

    @Test
    fun resolveUrl_albumPlaceholder_neverEmitsCoverNormalDirectory() {
        // 缺省图走 resolveUrl(DEFAULT_COVER_PATH, ALBUM, purpose)：相册无 cover/n/ 目录，全用途不得命中
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
        // 对照：默认缺省封面（非相册路径）确实是 cover/n/none.jpg，正是相册必须绕开的那一档
        assertEquals("${base}cover/n/none.jpg", pipeline.getDefaultCoverUrl(ImagePurpose.CARD_STANDARD))
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
