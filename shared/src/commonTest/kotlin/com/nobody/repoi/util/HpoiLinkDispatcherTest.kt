package com.nobody.repoi.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hpoi 链接解析与分流调度器单元测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `HpoiLinkDispatcherTest`（逐用例；JUnit4 → kotlin.test）。
 * 移植说明：原测试挂 `@RunWith(RobolectricTestRunner)`，但 `HpoiLinkDispatcher.parse`
 * 为纯字符串解析（shared/commonMain，无 Android 依赖），runner 属多余负担，
 * KMP commonTest 下直接去掉（与 M2 trio 的 JUnit4 → kotlin.test 惯例一致）。
 */
class HpoiLinkDispatcherTest {

    @Test
    fun parse_hobbyUrls_recognizesHobbyRoute() {
        val urls = listOf(
            "https://www.hpoi.net/hobby/59570",
            "https://www.hpoi.net.cn/hobby/59570/",
            "http://hpoi.net/item/59570?page=1#comments",
            "/hobby/59570",
            "hobby/59570",
            "/item/59570"
        )

        for (url in urls) {
            val route = HpoiLinkDispatcher.parse(url)
            assertTrue(route is HpoiRoute.Hobby, "Expected Hobby route for $url but was $route")
            assertEquals(59570L, (route as HpoiRoute.Hobby).hobbyId)
        }
    }

    @Test
    fun parse_albumUrls_recognizesAlbumRoute() {
        val urls = listOf(
            "https://www.hpoi.net/album/8888",
            "https://www.hpoi.net.cn/album/8888/",
            "http://hpoi.net/album/8888?sort=new",
            "/album/8888",
            "album/8888"
        )

        for (url in urls) {
            val route = HpoiLinkDispatcher.parse(url)
            assertTrue(route is HpoiRoute.Album, "Expected Album route for $url but was $route")
            assertEquals(8888L, (route as HpoiRoute.Album).albumId)
        }
    }

    @Test
    fun parse_characterUrls_recognizesCharacterRoute() {
        val urls = listOf(
            "https://www.hpoi.net/charactar/8110",
            "https://www.hpoi.net.cn/character/8110/",
            "http://hpoi.net/charactar/8110?page=1",
            "/charactar/8110",
            "character/8110"
        )

        for (url in urls) {
            val route = HpoiLinkDispatcher.parse(url)
            assertTrue(route is HpoiRoute.Character, "Expected Character route for $url but was $route")
            assertEquals(8110L, (route as HpoiRoute.Character).characterId)
        }
    }

    @Test
    fun parse_postUrls_recognizesPostRoute() {
        val urls = listOf(
            "https://www.hpoi.net/posts/12345",
            "https://www.hpoi.net.cn/post/12345/",
            "http://hpoi.net/posts/12345?page=1",
            "/posts/12345",
            "/post/12345",
            "posts/12345"
        )

        for (url in urls) {
            val route = HpoiLinkDispatcher.parse(url)
            assertTrue(route is HpoiRoute.Post, "Expected Post route for $url but was $route")
            assertEquals(12345L, (route as HpoiRoute.Post).postId)
        }
    }

    @Test
    fun parse_externalUrls_fallsBackToWebRoute() {
        val external = "https://www.bilibili.com/video/BV1xx"
        val route = HpoiLinkDispatcher.parse(external)
        assertTrue(route is HpoiRoute.Web)
        assertEquals(external, (route as HpoiRoute.Web).url)

        val otherSiteWithHobbyPath = "https://example.com/hobby/59570"
        val otherRoute = HpoiLinkDispatcher.parse(otherSiteWithHobbyPath)
        assertTrue(otherRoute is HpoiRoute.Web)
        assertEquals(otherSiteWithHobbyPath, (otherRoute as HpoiRoute.Web).url)
    }

    @Test
    fun parse_blankUrl_returnsEmptyWebRoute() {
        val route = HpoiLinkDispatcher.parse("   ")
        assertTrue(route is HpoiRoute.Web)
        assertTrue((route as HpoiRoute.Web).url.isEmpty())
    }
}
