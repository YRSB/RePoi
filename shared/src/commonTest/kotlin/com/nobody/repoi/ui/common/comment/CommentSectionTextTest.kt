package com.nobody.repoi.ui.common.comment

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import com.nobody.repoi.data.remote.model.CommentReplyItem
import com.nobody.repoi.data.remote.model.CommentUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 评论区富文本与回复关系格式化单测（纯 JVM commonTest）。
 */
class CommentSectionTextTest {

    @Test
    fun buildReplyRelationText_escapedAtUserLink_createsClickableLink() {
        val reply = CommentReplyItem(
            id = 1L,
            user = CommentUser(id = 10L, nickname = "测试路人"),
            toUser = CommentUser(id = 20L, nickname = "楼主"),
            htmlContent = "&lt;a href=\"/user/224213\"&gt;@小猫咪能有什么坏心眼&lt;/a&gt; 回复内容"
        )
        var clickedUrl: String? = null
        val annotated = buildReplyRelationText(
            reply = reply,
            primaryColor = Color.Blue,
            outlineColor = Color.Gray,
            onUrlClick = { clickedUrl = it }
        )

        assertEquals("测试路人 回复 楼主：@小猫咪能有什么坏心眼 回复内容", annotated.text)
        val links = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, links.size)
        val link = links.first().item
        assertTrue(link is LinkAnnotation.Clickable)
        assertEquals("https://www.hpoi.net/user/224213", link.tag)

        link.linkInteractionListener?.onClick(link)
        assertEquals("https://www.hpoi.net/user/224213", clickedUrl)
    }

    @Test
    fun buildReplyDetailContentText_escapedAtUserLink_createsClickableLink() {
        val reply = CommentReplyItem(
            id = 2L,
            user = CommentUser(id = 10L, nickname = "测试路人"),
            toUser = CommentUser(id = 20L, nickname = "楼主"),
            htmlContent = "&lt;a href=\"/user/224213\"&gt;@小猫咪能有什么坏心眼&lt;/a&gt; 详情回复"
        )
        var clickedUrl: String? = null
        val annotated = buildReplyDetailContentText(
            reply = reply,
            primaryColor = Color.Blue,
            outlineColor = Color.Gray,
            onUrlClick = { clickedUrl = it }
        )

        assertEquals("回复 楼主：@小猫咪能有什么坏心眼 详情回复", annotated.text)
        val links = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, links.size)
        val link = links.first().item
        assertTrue(link is LinkAnnotation.Clickable)
        assertEquals("https://www.hpoi.net/user/224213", link.tag)

        link.linkInteractionListener?.onClick(link)
        assertEquals("https://www.hpoi.net/user/224213", clickedUrl)
    }

    @Test
    fun buildReplyDetailContentText_withServerHtmlTemplate_noDuplicateReplyPrefix() {
        val reply = CommentReplyItem(
            id = 36143455L,
            user = CommentUser(id = 1L, nickname = "Deciso"),
            toUser = CommentUser(id = 58475L, nickname = "死亡宣告"),
            content = "有推荐的产品链接吗，私信一个",
            htmlContent = "<span>回复<a target='_blank' href='user/58475'>死亡宣告: </a></span>有推荐的产品链接吗，私信一个"
        )
        val annotated = buildReplyDetailContentText(
            reply = reply,
            primaryColor = Color.Blue,
            outlineColor = Color.Gray,
            onUrlClick = null
        )

        assertEquals("回复 死亡宣告：有推荐的产品链接吗，私信一个", annotated.text)
    }

    @Test
    fun buildReplyRelationText_withServerHtmlTemplate_noDuplicateReplyPrefix() {
        val reply = CommentReplyItem(
            id = 36143455L,
            user = CommentUser(id = 1L, nickname = "Deciso"),
            toUser = CommentUser(id = 58475L, nickname = "死亡宣告"),
            content = "有推荐的产品链接吗，私信一个",
            htmlContent = "<span>回复<a target='_blank' href='user/58475'>死亡宣告: </a></span>有推荐的产品链接吗，私信一个"
        )
        val annotated = buildReplyRelationText(
            reply = reply,
            primaryColor = Color.Blue,
            outlineColor = Color.Gray,
            onUrlClick = null
        )

        assertEquals("Deciso 回复 死亡宣告：有推荐的产品链接吗，私信一个", annotated.text)
    }

    @Test
    fun buildReplyDetailContentText_htmlContentFallback_stripsServerTemplate() {
        val reply = CommentReplyItem(
            id = 36143455L,
            user = CommentUser(id = 1L, nickname = "Deciso"),
            toUser = CommentUser(id = 58475L, nickname = "死亡宣告"),
            content = null,
            htmlContent = "<span>回复<a target='_blank' href='user/58475'>死亡宣告: </a></span>有推荐的产品链接吗，私信一个"
        )
        val annotated = buildReplyDetailContentText(
            reply = reply,
            primaryColor = Color.Blue,
            outlineColor = Color.Gray,
            onUrlClick = null
        )

        assertEquals("回复 死亡宣告：有推荐的产品链接吗，私信一个", annotated.text)
    }

    @Test
    fun buildReplyDetailContentText_contentContainsPlainTextPrefix_stripsDuplicate() {
        val reply = CommentReplyItem(
            id = 36143455L,
            user = CommentUser(id = 1L, nickname = "Deciso"),
            toUser = CommentUser(id = 58475L, nickname = "死亡宣告"),
            content = "回复死亡宣告: 有推荐的产品链接吗，私信一个"
        )
        val annotated = buildReplyDetailContentText(
            reply = reply,
            primaryColor = Color.Blue,
            outlineColor = Color.Gray,
            onUrlClick = null
        )

        assertEquals("回复 死亡宣告：有推荐的产品链接吗，私信一个", annotated.text)
    }
}
