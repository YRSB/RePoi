package com.nobody.repoi.util

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.data.remote.model.RelateData
import com.nobody.repoi.data.remote.model.RelateItem
import com.nobody.repoi.data.remote.model.UserProfileSummary
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class FeedContentResolverTest {

    @Test
    fun trade105_resolvesDetailAsMainCommentAndSellVerb() {
        val action = ActionItem(
            id = 1L,
            actionType = 105,
            user = UserProfileSummary(nickname = "出物君"),
            relateItem = RelateItem(itemType = "hobby", nameCN = "自由高达"),
            relateData = listOf(
                RelateData(price = "200", purityStr = "有瑕疵", detail = "板件未开过，盒子有些变形")
            )
        )

        assertTrue(FeedContentResolver.isTradeAction(action))
        assertEquals("板件未开过，盒子有些变形", FeedContentResolver.resolveMainComment(action))
        assertEquals("出物", FeedContentResolver.resolveActionVerb(action))
    }

    @Test
    fun trade106_resolvesBuyVerb() {
        val action = ActionItem(
            id = 2L,
            actionType = 106,
            user = UserProfileSummary(nickname = "求购君"),
            relateItem = RelateItem(itemType = "hobby", nameCN = "狂三 白无垢"),
            relateData = listOf(
                RelateData(price = "500", purityStr = "收购", detail = "")
            )
        )

        assertTrue(FeedContentResolver.isTradeAction(action))
        assertEquals("求购", FeedContentResolver.resolveActionVerb(action))
        assertNull(FeedContentResolver.resolveMainComment(action))
    }

    @Test
    fun comment101_emptyContentAndEmoticon_resolvesNullButVerbIsComment() {
        val action = ActionItem(
            id = 3L,
            actionType = 101,
            actionComment = "",
            user = UserProfileSummary(nickname = "表情君"),
            relateItem = RelateItem(itemType = "album", nameCN = "返图转载"),
            relateData = listOf(
                RelateData(content = null, emoticon = JsonPrimitive(""))
            )
        )

        assertFalse(FeedContentResolver.isTradeAction(action))
        assertNull(FeedContentResolver.resolveMainComment(action))
        assertEquals("动态", FeedContentResolver.resolveActionVerb(action))
    }

    @Test
    fun comment101_stringEmoticon_resolvesText() {
        val action = ActionItem(
            id = 4L,
            actionType = 101,
            user = UserProfileSummary(nickname = "表情君"),
            relateData = listOf(
                RelateData(content = "  ", emoticon = JsonPrimitive("[doge]"))
            )
        )

        assertEquals("[doge]", FeedContentResolver.resolveMainComment(action))
        assertEquals("评论", FeedContentResolver.resolveActionVerb(action))
    }

    @Test
    fun update102_keepsUpdateVerb() {
        val action = ActionItem(id = 5L, actionType = 102)

        assertEquals("更新", FeedContentResolver.resolveActionVerb(action))
    }

    @Test
    fun official401_keepsOfficialPicVerb() {
        val action = ActionItem(id = 6L, actionType = 401, actionData = "official_pic")

        assertEquals("官图", FeedContentResolver.resolveActionVerb(action))
    }

    @Test
    fun hobbyWithoutComment_resolvesDynamicVerb() {
        val action = ActionItem(
            id = 7L,
            actionType = 1,
            relateItem = RelateItem(itemType = "hobby", nameCN = "初音未来")
        )

        assertNull(FeedContentResolver.resolveMainComment(action))
        assertEquals("动态", FeedContentResolver.resolveActionVerb(action))
    }
}
