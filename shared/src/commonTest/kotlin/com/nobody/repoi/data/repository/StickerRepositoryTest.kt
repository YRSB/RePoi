package com.nobody.repoi.data.repository

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

        mock.enqueue(buyTimeJson("\"2024-05-05\""))
        mock.enqueue(packsJson("\"2024-05-05\"", pack("A", 1)))
        mock.enqueue(buyTimeJson("\"2024-05-05\""))
        val repository = StickerRepository(mock.api())

        val first = repository.getCommentPacks()
        assertTrue(first.isSuccess, "call-1 failed: ${first.exceptionOrNull()}")
        assertEquals(listOf("A"), first.getOrThrow().map { it.name })
        assertEquals(2, mock.requestCount, "call-1 should cost buyTime+packs")

        val second = repository.getCommentPacks()
        assertTrue(second.isSuccess, "call-2 failed: ${second.exceptionOrNull()}")

        assertEquals(3, mock.requestCount)
    }
}
