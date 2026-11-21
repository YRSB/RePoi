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
}
