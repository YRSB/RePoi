package com.nobody.repoi.util

import com.nobody.repoi.data.remote.model.CommentItem
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.CommentUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

        val item = comment(id = 1L, replyCount = 7, replys = listOf(reply(1L), reply(2L), reply(3L)))
        assertTrue(CommentMutations.hasMoreReplies(item))

        val three = comment(id = 2L, replyCount = 3, replys = listOf(reply(1L), reply(2L), reply(3L)))
        assertFalse(CommentMutations.hasMoreReplies(three))

        val full = comment(id = 3L, replyCount = 2, replys = listOf(reply(1L), reply(2L)))
        assertFalse(CommentMutations.hasMoreReplies(full))

        assertFalse(CommentMutations.hasMoreReplies(comment(id = 4L, replyCount = 0, replys = emptyList())))

        val itemWithShielded = comment(
            id = 5L,
            replyCount = 2,
            replys = listOf(
                reply(1L),
                CommentReplyItem(id = 2L, content = "(已屏蔽)"),
                CommentReplyItem(id = 3L, content = "(已屏蔽)"),
                reply(4L)
            )
        )
        assertFalse(CommentMutations.hasMoreReplies(itemWithShielded))
    }

    @Test
    fun applyPraise_togglesStateAndCount() {
        val comments = listOf(CommentItem(id = 1L, praiseCount = 3, praiseState = false))
        val praised = CommentMutations.applyPraise(comments, 1L)
        assertTrue(praised.first().praiseState)
        assertEquals(4, praised.first().praiseCount)

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

        val untouched = CommentMutations.applyExternalUpdate(comments, comment(id = 99L, replyCount = 5, replys = emptyList()))
        assertEquals(comments, untouched)
    }

    @Test
    fun applyExternalUpdate_mergesRepliesWithoutDroppingPreview() {
        val comments = listOf(comment(id = 1L, replyCount = 3, replys = listOf(reply(1L), reply(2L), reply(3L))))

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

    @Test
    fun isShielded_detectsMaskTextAndIsMaskFlag() {
        val maskedTextComment = CommentItem(id = 1L, content = "(已屏蔽)")
        val htmlMaskedComment = CommentItem(id = 2L, content = "<p>(已屏蔽)</p>")
        val isMaskComment = CommentItem(id = 3L, content = "正常内容", isMask = 1)
        val normalComment = CommentItem(id = 4L, content = "正常内容", isMask = 0)

        assertTrue(CommentMutations.isShielded(maskedTextComment))
        assertTrue(CommentMutations.isShielded(htmlMaskedComment))
        assertTrue(CommentMutations.isShielded(isMaskComment))
        assertFalse(CommentMutations.isShielded(normalComment))
        assertTrue(maskedTextComment.isShielded)
        assertTrue(isMaskComment.isShielded)
        assertFalse(normalComment.isShielded)

        val maskedReply = CommentReplyItem(id = 5L, content = "(已屏蔽)")
        val htmlMaskedReply = CommentReplyItem(id = 6L, htmlContent = "<span>(已屏蔽)</span>")
        val normalReply = CommentReplyItem(id = 7L, content = "正常回复")

        assertTrue(CommentMutations.isShielded(maskedReply))
        assertTrue(CommentMutations.isShielded(htmlMaskedReply))
        assertFalse(CommentMutations.isShielded(normalReply))
        assertTrue(maskedReply.isShielded)
        assertFalse(normalReply.isShielded)
    }

    @Test
    fun filterShieldedComments_removesShieldedRootsAndFiltersSubReplies() {
        val comments = listOf(

            CommentItem(
                id = 1L,
                content = "(已屏蔽)",
                reply = 2,
                replys = listOf(reply(11L), reply(12L))
            ),

            CommentItem(
                id = 2L,
                content = "正常文字",
                isMask = 1,
                reply = 1,
                replys = listOf(reply(21L))
            ),

            CommentItem(
                id = 3L,
                content = "正常主楼内容",
                reply = 3,
                replys = listOf(
                    reply(31L),
                    CommentReplyItem(id = 32L, content = "(已屏蔽)"),
                    reply(33L)
                )
            )
        )

        val filtered = CommentMutations.filterShieldedComments(comments)

        assertEquals(1, filtered.size)
        val remaining = filtered.single()
        assertEquals(3L, remaining.id)
        assertEquals(listOf(31L, 33L), remaining.replys?.map { it.id })
    }
}
