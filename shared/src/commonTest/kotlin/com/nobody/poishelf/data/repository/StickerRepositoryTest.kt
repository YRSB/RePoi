package com.nobody.poishelf.data.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 表情包仓储纯逻辑测试（W4-W5 pure-logic；KMP commonTest）。
 *
 * 对齐 W3 仓储测试形态（[MockApi] MockEngine + 真实 HpoiApi；JUnit4 → kotlin.test；
 * plain `runTest`，无 Main/DataStore）。
 * - buyTime（`api/sticker/pack/buyTime`，GET）+ packs（`api/sticker/pack/list`，
 *   GET）双均成功 → 按 `applyLocation <= 0 || == 1` 过滤。
 * - 购买时间一致的第二次调用走内存缓存，不再发 packs 请求。
 */
class StickerRepositoryTest {

    private fun buyTimeJson(buyTime: String?): String {
        val data = if (buyTime == null) "null" else """{"buyTime":$buyTime}"""
        return """{"success":true,"code":200,"msg":"ok","data":$data}"""
    }

    private fun packsJson(buyTime: String, packs: String): String =
        """{"success":true,"code":200,"msg":"ok","data":{"buyTime":$buyTime,"list":[$packs]}}"""

    private fun pack(name: String, applyLocation: Int): String =
        """{"name":"$name","applyLocation":$applyLocation,"items":[]}"""

    @Test
    fun getCommentPacks_filtersByApplyLocation() = runTest {
        val mock = MockApi()
        mock.enqueue(buyTimeJson("\"2024-01-01\""))
        mock.enqueue(packsJson("\"2024-01-01\"", "${pack("A", 0)},${pack("B", 2)},${pack("C", 1)}"))
        val repository = StickerRepository(mock.api())

        val result = repository.getCommentPacks()

        assertTrue(result.isSuccess)
        val names = result.getOrThrow().map { it.name }
        assertEquals(listOf("A", "C"), names)
    }

    @Test
    fun getCommentPacks_sameBuyTimeUsesCache() = runTest {
        val mock = MockApi()
        // M7 注：helper 每次调用都先拉 buyTime（比对缓存），故同一 buyTime 需入队两次；
        // 第 2 次命中内存缓存，不再发 packs 请求（3 = buyTime×2 + packs×1）。
        mock.enqueue(buyTimeJson("\"2024-05-05\""), buyTimeJson("\"2024-05-05\""))
        mock.enqueue(packsJson("\"2024-05-05\"", pack("A", 1)))
        val repository = StickerRepository(mock.api())

        val first = repository.getCommentPacks()
        assertTrue(first.isSuccess, "call-1 failed: ${first.exceptionOrNull()}")
        assertEquals(listOf("A"), first.getOrThrow().map { it.name })
        assertEquals(2, mock.requestCount, "call-1 should cost buyTime+packs")

        val second = repository.getCommentPacks()
        assertTrue(second.isSuccess, "call-2 failed: ${second.exceptionOrNull()}")

        // 命中缓存后仅多 1 次 buyTime。
        assertEquals(3, mock.requestCount)
    }
}
