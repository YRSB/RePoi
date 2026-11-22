package com.nobody.repoi.ui.collect

import com.nobody.repoi.ui.collect.subpage.hobbyScrollKeyInputs
import com.nobody.repoi.ui.collect.subpage.scrollStateSaveableKey
import com.nobody.repoi.ui.collect.subpage.worksScrollKeyInputs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CollectFeedQueryKeyTest {

    private fun key(scope: String, vararg keys: Any?): String =
        scrollStateSaveableKey(scope, keys)

    @Test
    fun sameConditions_produceIdenticalKey() {
        assertEquals(
            key("hobby_grid", "", "actionDate", 2, null, null),
            key("hobby_grid", "", "actionDate", 2, null, null)
        )
    }

    @Test
    fun everyContentDimension_changesKey() {
        val base = key("hobby_grid", *hobbyScrollKeyInputs(UserCollectUiState()))

        val variants = listOf(
            UserCollectUiState(collectState = CollectStateOption.WANT),
            UserCollectUiState(order = HobbyOrderOption.RELEASE),
            UserCollectUiState(sortType = UserCollectUiState.SORT_ASC),
            UserCollectUiState(subCategoryId = 103),
            UserCollectUiState(selectedTag = "手办"),
        )
        variants.forEach { variant ->
            assertNotEquals(base, key("hobby_grid", *hobbyScrollKeyInputs(variant)))
        }
    }

    @Test
    fun worksContentDimensions_changeKey() {
        val base = key("works_list", *worksScrollKeyInputs(WorksStateOption.WANT, false, null))

        assertNotEquals(
            base,
            key("works_list", *worksScrollKeyInputs(WorksStateOption.WATCHING, false, null))
        )
        assertNotEquals(
            base,
            key("works_list", *worksScrollKeyInputs(WorksStateOption.WANT, true, null))
        )
        assertNotEquals(
            base,
            key("works_list", *worksScrollKeyInputs(WorksStateOption.WANT, false, "手办"))
        )
    }

    @Test
    fun differentScope_producesDifferentKey() {

        assertNotEquals(
            key("common_list", "actionDate"),
            key("common_grid", "actionDate")
        )
    }

    @Test
    fun nullValue_doesNotCollideWithLiteralNullString() {

        assertNotEquals(key("hobby_grid", null), key("hobby_grid", "null"))
    }

    @Test
    fun separatorInsideTag_doesNotCollideWithMultipleDimensions() {

        assertNotEquals(key("hobby_grid", "a:b"), key("hobby_grid", "a", "b"))
    }

    @Test
    fun subCategoryAllKey_normalizesToNull() {

        assertNull(HobbySubCategoryOption.normalize(null))
        assertNull(HobbySubCategoryOption.normalize(UserCollectViewModel.DEFAULT_HOBBY_KEY))
        assertEquals(103, HobbySubCategoryOption.normalize(103))
    }

    @Test
    fun hobbyOrder_fallsBackFromOrderTimeWhenNotBuy() {

        assertEquals(
            HobbyOrderOption.ORDER_TIME,
            HobbyOrderOption.effective(CollectStateOption.BUY, HobbyOrderOption.ORDER_TIME)
        )
        assertEquals(
            HobbyOrderOption.ACTION_DATE,
            HobbyOrderOption.effective(CollectStateOption.WANT, HobbyOrderOption.ORDER_TIME)
        )
        assertEquals(
            HobbyOrderOption.ACTION_DATE,
            HobbyOrderOption.effective(CollectStateOption.ALL, HobbyOrderOption.ACTION_DATE)
        )
    }
}
