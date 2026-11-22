package com.nobody.repoi.data.remote.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 评论模型数据清洗测试，覆盖评论与回复的纯文本清洗逻辑。
 */
class CommentModelsTest {

    @Test
    fun commentItem_escapedHtmlTag_cleanedProperly() {
        val comment = CommentItem(
            content = "&lt;a href=\"/user/224213\"&gt;@小猫咪能有什么坏心眼&lt;/a&gt; 测试评论"
        )
        assertEquals("@小猫咪能有什么坏心眼 测试评论", comment.contentCleanText)
    }

    @Test
    fun commentReplyItem_escapedHtmlTag_cleanedProperly() {
        val reply = CommentReplyItem(
            htmlContent = "&lt;a href=\"/user/224213\"&gt;@小猫咪能有什么坏心眼&lt;/a&gt; 回复测试"
        )
        assertEquals("@小猫咪能有什么坏心眼 回复测试", reply.contentCleanText)
    }

    @Test
    fun commentReplyItem_preferContentOverHtmlContent_stripsDuplicateReplyPrefix() {
        val reply = CommentReplyItem(
            content = "有推荐的产品链接吗，私信一个",
            htmlContent = "<span>回复<a target='_blank' href='user/58475'>死亡宣告: </a></span>有推荐的产品链接吗，私信一个",
            toUser = CommentUser(id = 58475L, nickname = "死亡宣告")
        )
        assertEquals("有推荐的产品链接吗，私信一个", reply.contentCleanText)
    }

    @Test
    fun commentReplyItem_htmlContentOnly_stripsServerTemplatePrefix() {
        val reply = CommentReplyItem(
            content = null,
            htmlContent = "<span>回复<a target='_blank' href='user/58475'>死亡宣告: </a></span>有推荐的产品链接吗，私信一个",
            toUser = CommentUser(id = 58475L, nickname = "死亡宣告")
        )
        assertEquals("有推荐的产品链接吗，私信一个", reply.contentCleanText)
    }

    @Test
    fun stripReplyPrefix_handlesVariousTemplateAndPlainTextPrefixes() {
        assertEquals(
            "有推荐的产品链接吗，私信一个",
            stripReplyPrefix("<span>回复<a target='_blank' href='user/58475'>死亡宣告: </a></span>有推荐的产品链接吗，私信一个", "死亡宣告")
        )
        assertEquals(
            "有推荐的产品链接吗，私信一个",
            stripReplyPrefix("<a href='/user/58475'>回复死亡宣告:</a> 有推荐的产品链接吗，私信一个", "死亡宣告")
        )
        assertEquals(
            "有推荐的产品链接吗，私信一个",
            stripReplyPrefix("回复死亡宣告: 有推荐的产品链接吗，私信一个", "死亡宣告")
        )
        assertEquals(
            "有推荐的产品链接吗，私信一个",
            stripReplyPrefix("回复 死亡宣告：有推荐的产品链接吗，私信一个", "死亡宣告")
        )
        assertEquals(
            "回复死亡宣告的问题：我也在找链接",
            stripReplyPrefix("回复死亡宣告的问题：我也在找链接", "死亡宣告")
        )
    }
}
