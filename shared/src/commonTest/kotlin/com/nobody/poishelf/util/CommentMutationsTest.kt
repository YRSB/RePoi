package com.nobody.poishelf.util

import com.nobody.poishelf.data.remote.model.CommentItem
import com.nobody.poishelf.data.remote.model.CommentReplyItem
import com.nobody.poishelf.data.remote.model.CommentUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 评论点赞与回复纯列表变换测试（KMP commonTest 移植）。
 *
 * 对齐 PoiShelf `CommentMutationsTest`（逐用例，JUnit4 → kotlin.test）。
 */
class CommentMutationsTest {

    private fun user(nickname: String) = CommentUser(id = 1L, userId = 11L, nickname = nickname)

    private fun reply(id: Long, toUser: CommentUser? = null): CommentReplyItem {
        return CommentReplyItem(id = id, content = "回复$id", user = user("胶友$id"), toUser = toUser)
    }

    private fun comment(id: Long, replyCount: Int, replys: List<CommentReplyItem>): CommentItem {
        return CommentItem(id = id, content = "主楼", reply = replyCount, replys = replys)
    }

    @Test
    fun previewReplies_takesAtMostThreeAndSkipsMasked() {
        val item = comment(
            id = 1L,
            replyCount = 5,
            replys = listOf(
                reply(1L),
                CommentReplyItem(id = 2L, content = "(已屏蔽)", user = user("x")),
                reply(3L),
                reply(4L),
                reply(5L)
            )
        )
        val preview = CommentMutations.previewReplies(item)
        assertEquals(listOf(1L, 3L, 4L), preview.map { it.id })
    }

    @Test
    fun hasMoreReplies_usesReplyCountNotPreviewSize() {
        // 服务端只给了 3 条预览但总数 7 条：需要展示“共7条回复”
        val item = comment(id = 1L, replyCount = 7, replys = listOf(reply(1L), reply(2L), reply(3L)))
        assertTrue(CommentMutations.hasMoreReplies(item))
        // 总数恰好为 3 条：预览已完全覆盖，不展示“共 X 条回复”
        val three = comment(id = 2L, replyCount = 3, replys = listOf(reply(1L), reply(2L), reply(3L)))
        assertFalse(CommentMutations.hasMoreReplies(three))
        // 总数小于 3 条：不展示
        val full = comment(id = 3L, replyCount = 2, replys = listOf(reply(1L), reply(2L)))
        assertFalse(CommentMutations.hasMoreReplies(full))
        // 无回复：不展示
        assertFalse(CommentMutations.hasMoreReplies(comment(id = 4L, replyCount = 0, replys = emptyList())))
    }

    @Test
    fun applyPraise_togglesStateAndCount() {
        val comments = listOf(CommentItem(id = 1L, praiseCount = 3, praiseState = false))
        val praised = CommentMutations.applyPraise(comments, 1L)
        assertTrue(praised.first().praiseState)
        assertEquals(4, praised.first().praiseCount)
        // 再次取反即回滚
        val rolledBack = CommentMutations.applyPraise(praised, 1L)
        assertFalse(rolledBack.first().praiseState)
        assertEquals(3, rolledBack.first().praiseCount)
    }

    @Test
    fun applyReplyAppended_appendsAndIncrementsReply() {
        val comments = listOf(comment(id = 1L, replyCount = 1, replys = listOf(reply(1L))))
        val merged = CommentMutations.applyReplyAppended(comments, 1L, reply(2L, toUser = user("目标")))
        assertEquals(2, merged.first().reply)
        assertEquals(listOf(1L, 2L), merged.first().replys?.map { it.id })
        assertEquals("目标", merged.first().replys?.last()?.toUser?.nickname)
    }

    @Test
    fun applyExternalUpdate_replacesByIdOnly() {
        val comments = listOf(comment(id = 1L, replyCount = 1, replys = emptyList()))
        val updated = comment(id = 1L, replyCount = 2, replys = listOf(reply(9L)))
        val merged = CommentMutations.applyExternalUpdate(comments, updated)
        assertEquals(2, merged.first().reply)
        assertEquals(1, merged.first().replys?.size)
        // 未知 id 不改动原列表
        val untouched = CommentMutations.applyExternalUpdate(comments, comment(id = 99L, replyCount = 5, replys = emptyList()))
        assertEquals(comments, untouched)
    }

    @Test
    fun applyExternalUpdate_mergesRepliesWithoutDroppingPreview() {
        val comments = listOf(comment(id = 1L, replyCount = 3, replys = listOf(reply(1L), reply(2L), reply(3L))))
        // 服务端回写仅带总数与点赞态、子评论为空时，原有预览不得被冲掉
        val updated = comment(id = 1L, replyCount = 4, replys = emptyList())
            .copy(praiseState = true, praiseCount = 5)
        val merged = CommentMutations.applyExternalUpdate(comments, updated)
        assertEquals(4, merged.first().reply)
        assertTrue(merged.first().praiseState)
        assertEquals(listOf(1L, 2L, 3L), merged.first().replys?.map { it.id })
    }

    @Test
    fun resolveReplyToUserId_prefersIdThenUserId() {
        assertEquals(1L, CommentMutations.resolveReplyToUserId(reply(7L)))
        val userIdOnly = CommentReplyItem(
            id = 8L,
            content = "回复",
            user = CommentUser(id = 0L, userId = 22L, nickname = "胶友"),
            toUser = null
        )
        assertEquals(22L, CommentMutations.resolveReplyToUserId(userIdOnly))
        val noIds = CommentReplyItem(
            id = 9L,
            content = "回复",
            user = CommentUser(id = 0L, userId = 0L, nickname = "胶友"),
            toUser = null
        )
        assertEquals(null, CommentMutations.resolveReplyToUserId(noIds))
        assertEquals(null, CommentMutations.resolveReplyToUserId(null))
    }
}
