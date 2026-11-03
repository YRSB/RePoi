package com.nobody.repoi.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Hpoi 资源 CDN URL 静态补全工具测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `CdnUrlUtilsTest`（逐用例，JUnit4 → kotlin.test）。
 */
class CdnUrlUtilsTest {

    @Test
    fun getCoverSrUrl_handlesRelativeAndAbsolutePaths() {
        val relPath = "2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/sr/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getCoverSrUrl(relPath)
        )

        val withPrefixSr = "cover/sr/2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/sr/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getCoverSrUrl(withPrefixSr)
        )

        val fullUrlS = "https://rfx.hpoi.net/gk/cover/s/2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/sr/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getCoverSrUrl(fullUrlS)
        )

        val fullUrlN = "https://rfx.hpoi.net/gk/cover/n/2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/sr/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getCoverSrUrl(fullUrlN)
        )

        val fullUrlRaw = "https://rfx.hpoi.net/gk/cover/raw/2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/sr/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getCoverSrUrl(fullUrlRaw)
        )

        assertEquals("", CdnUrlUtils.getCoverSrUrl(null))
        assertEquals("", CdnUrlUtils.getCoverSrUrl("  "))
    }

    @Test
    fun getCoverSmallUrl_handlesRelativeAndAbsolutePaths() {
        val relPath = "2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/s/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getCoverSmallUrl(relPath)
        )

        val withPrefix = "cover/n/2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/s/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getCoverSmallUrl(withPrefix)
        )

        val withPrefixSr = "cover/sr/2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/s/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getCoverSmallUrl(withPrefixSr)
        )

        val fullUrlSr = "https://rfx.hpoi.net/gk/cover/sr/2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/s/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getCoverSmallUrl(fullUrlSr)
        )

        val fullUrlN = "https://rfx.hpoi.net/gk/cover/n/2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/s/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getCoverSmallUrl(fullUrlN)
        )

        val fullUrlRaw = "https://rfx.hpoi.net/gk/cover/raw/2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/s/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getCoverSmallUrl(fullUrlRaw)
        )

        assertEquals("", CdnUrlUtils.getCoverSmallUrl(null))
        assertEquals("", CdnUrlUtils.getCoverSmallUrl("  "))
    }

    @Test
    fun getCoverUrl_handlesRelativeAndAbsolutePaths() {
        val relPath = "2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/n/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getCoverUrl(relPath)
        )

        val fullUrlS = "https://rfx.hpoi.net/gk/cover/s/2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/n/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getCoverUrl(fullUrlS)
        )

        val fullUrlSr = "https://rfx.hpoi.net/gk/cover/sr/2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/n/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getCoverUrl(fullUrlSr)
        )

        val fullUrlRaw = "https://rfx.hpoi.net/gk/cover/raw/2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/n/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getCoverUrl(fullUrlRaw)
        )

        assertEquals("", CdnUrlUtils.getCoverUrl(null))
    }

    @Test
    fun getRawCoverUrl_handlesRelativeAndAbsolutePaths() {
        val relPath = "2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/raw/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getRawCoverUrl(relPath)
        )

        val fullUrlSr = "https://rfx.hpoi.net/gk/cover/sr/2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/raw/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getRawCoverUrl(fullUrlSr)
        )

        val fullUrlN = "https://rfx.hpoi.net/gk/cover/n/2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/raw/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getRawCoverUrl(fullUrlN)
        )

        val fullUrlS = "https://rfx.hpoi.net/gk/cover/s/2026/08/6e29fa23068f4f289179586c28c0a7b7.png"
        assertEquals(
            "https://rfx.hpoi.net/gk/cover/raw/2026/08/6e29fa23068f4f289179586c28c0a7b7.png",
            CdnUrlUtils.getRawCoverUrl(fullUrlS)
        )

        assertEquals("", CdnUrlUtils.getRawCoverUrl(null))
    }

    @Test
    fun isNoCover_identifiesEmptyAndPlaceholderPaths() {
        kotlin.test.assertTrue(CdnUrlUtils.isNoCover(null))
        kotlin.test.assertTrue(CdnUrlUtils.isNoCover(""))
        kotlin.test.assertTrue(CdnUrlUtils.isNoCover("   "))
        kotlin.test.assertTrue(CdnUrlUtils.isNoCover("null"))
        kotlin.test.assertTrue(CdnUrlUtils.isNoCover("none.jpg"))
        kotlin.test.assertTrue(CdnUrlUtils.isNoCover("/none.jpg"))
        kotlin.test.assertTrue(CdnUrlUtils.isNoCover("cover/n/none.jpg"))
        kotlin.test.assertTrue(CdnUrlUtils.isNoCover("/cover/sr/none.jpg"))

        kotlin.test.assertFalse(CdnUrlUtils.isNoCover("2026/08/sample.jpg"))
        kotlin.test.assertFalse(CdnUrlUtils.isNoCover("cover/n/2026/08/sample.jpg"))
    }

    @Test
    fun getCoverUrlOrDefault_fallsBackToNoneJpgWhenEmptyOrPlaceholder() {
        val expectedDefault = "https://rfx.hpoi.net/gk/cover/n/none.jpg"
        assertEquals(expectedDefault, CdnUrlUtils.getCoverUrlOrDefault(null))
        assertEquals(expectedDefault, CdnUrlUtils.getCoverUrlOrDefault(""))
        assertEquals(expectedDefault, CdnUrlUtils.getCoverUrlOrDefault("   "))
        assertEquals(expectedDefault, CdnUrlUtils.getCoverUrlOrDefault("null"))
        assertEquals(expectedDefault, CdnUrlUtils.getCoverUrlOrDefault("none.jpg"))
        assertEquals(expectedDefault, CdnUrlUtils.getCoverUrlOrDefault("cover/n/none.jpg"))

        assertEquals(
            "https://rfx.hpoi.net/gk/cover/n/2026/08/real.jpg",
            CdnUrlUtils.getCoverUrlOrDefault("2026/08/real.jpg")
        )
    }

    @Test
    fun getCoverSmallUrlOrDefault_fallsBackToNoneJpgSmall() {
        val expectedDefault = "https://rfx.hpoi.net/gk/cover/s/none.jpg"
        assertEquals(expectedDefault, CdnUrlUtils.getCoverSmallUrlOrDefault(null))
        assertEquals(expectedDefault, CdnUrlUtils.getCoverSmallUrlOrDefault(""))
        assertEquals(expectedDefault, CdnUrlUtils.getCoverSmallUrlOrDefault("none.jpg"))

        assertEquals(
            "https://rfx.hpoi.net/gk/cover/s/2026/08/real.jpg",
            CdnUrlUtils.getCoverSmallUrlOrDefault("2026/08/real.jpg")
        )
    }

    @Test
    fun getCoverSrUrlOrDefault_fallsBackToNoneJpgSr() {
        val expectedDefault = "https://rfx.hpoi.net/gk/cover/sr/none.jpg"
        assertEquals(expectedDefault, CdnUrlUtils.getCoverSrUrlOrDefault(null))
        assertEquals(expectedDefault, CdnUrlUtils.getCoverSrUrlOrDefault(""))
        assertEquals(expectedDefault, CdnUrlUtils.getCoverSrUrlOrDefault("none.jpg"))

        assertEquals(
            "https://rfx.hpoi.net/gk/cover/sr/2026/08/real.jpg",
            CdnUrlUtils.getCoverSrUrlOrDefault("2026/08/real.jpg")
        )
    }

    @Test
    fun cleanCoverPath_cleansAllPrefixes() {
        val rel = "2026/08/sample.png"
        assertEquals("2026/08/sample.png", CdnUrlUtils.cleanCoverPath("/$rel"))
        assertEquals("2026/08/sample.png", CdnUrlUtils.cleanCoverPath("cover/sr/$rel"))
        assertEquals("2026/08/sample.png", CdnUrlUtils.cleanCoverPath("cover/s/$rel"))
        assertEquals("2026/08/sample.png", CdnUrlUtils.cleanCoverPath("cover/n/$rel"))
        assertEquals("2026/08/sample.png", CdnUrlUtils.cleanCoverPath("cover/raw/$rel"))
        assertEquals("2026/08/sample.png", CdnUrlUtils.cleanCoverPath("/cover/sr/$rel"))
    }

    @Test
    fun getExHobbyUrls_handlesPrefixesAndBlanks() {
        val rel = "2024/05/abc12345.jpg"
        assertEquals("https://res.e39x.com/pic/s/2024/05/abc12345.jpg", CdnUrlUtils.getExHobbyThumbUrl(rel))
        assertEquals("https://res.e39x.com/pic/s/2024/05/abc12345.jpg", CdnUrlUtils.getExHobbyThumbUrl("pic/s/$rel"))
        assertEquals("https://res.e39x.com/pic/s/2024/05/abc12345.jpg", CdnUrlUtils.getExHobbyThumbUrl("/pic/n/$rel"))

        assertEquals("https://res.e39x.com/pic/n/2024/05/abc12345.jpg", CdnUrlUtils.getExHobbyFullUrl(rel))
        assertEquals("https://res.e39x.com/pic/n/2024/05/abc12345.jpg", CdnUrlUtils.getExHobbyFullUrl("pic/n/$rel"))
        assertEquals("https://res.e39x.com/pic/n/2024/05/abc12345.jpg", CdnUrlUtils.getExHobbyFullUrl("/pic/s/$rel"))

        // http / blanks
        assertEquals("https://external.domain.com/pic.jpg", CdnUrlUtils.getExHobbyThumbUrl("https://external.domain.com/pic.jpg"))
        assertEquals("https://external.domain.com/pic.jpg", CdnUrlUtils.getExHobbyFullUrl("https://external.domain.com/pic.jpg"))
        assertEquals("", CdnUrlUtils.getExHobbyThumbUrl(null))
        assertEquals("", CdnUrlUtils.getExHobbyThumbUrl("  "))
        assertEquals("", CdnUrlUtils.getExHobbyFullUrl(null))
        assertEquals("", CdnUrlUtils.getExHobbyFullUrl(""))
    }

    @Test
    fun getBannerUrl_and_getRawBannerUrl_resolvesToNormalVariant() {
        val relPath = "2026/08/12d4b839597948b7b6444810137795e6.jpg"
        val expected = "https://rfx.hpoi.net/gk/bnr/n/2026/08/12d4b839597948b7b6444810137795e6.jpg"

        assertEquals(expected, CdnUrlUtils.getBannerUrl(relPath))
        assertEquals(expected, CdnUrlUtils.getBannerUrl("/$relPath"))
        assertEquals(expected, CdnUrlUtils.getBannerUrl("bnr/n/$relPath"))
        assertEquals(expected, CdnUrlUtils.getBannerUrl("bnr/raw/$relPath"))

        // getRawBannerUrl 应该同样解析为 bnr/n/，防止请求 404 的 bnr/raw/ 目录
        assertEquals(expected, CdnUrlUtils.getRawBannerUrl(relPath))
        assertEquals(expected, CdnUrlUtils.getRawBannerUrl("bnr/raw/$relPath"))

        // 空值与绝对路径安全处理
        assertEquals("", CdnUrlUtils.getBannerUrl(null))
        assertEquals("", CdnUrlUtils.getBannerUrl("   "))
        assertEquals("", CdnUrlUtils.getRawBannerUrl(null))
        assertEquals("https://custom.host/bnr.jpg", CdnUrlUtils.getBannerUrl("https://custom.host/bnr.jpg"))
        assertEquals("https://custom.host/bnr.jpg", CdnUrlUtils.getRawBannerUrl("https://custom.host/bnr.jpg"))
    }
}
