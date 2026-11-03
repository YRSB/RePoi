package com.nobody.repoi.ui.collect

import com.nobody.repoi.ui.collect.subpage.hobbyScrollKeyInputs
import com.nobody.repoi.ui.collect.subpage.scrollStateSaveableKey
import com.nobody.repoi.ui.collect.subpage.worksScrollKeyInputs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * 收藏信息流「条件即键」的纯逻辑测试（KMP commonTest）。
 *
 * 守护两个不变量：
 * 1. 同一组筛选条件 → 同一个键（从二级页返回时才能按同一键恢复滚动位置）；
 * 2. 任一影响内容的维度变化 → 不同的键（切换条件时才能重建状态、自动回顶）。
 *
 * 类名刻意不含 `ViewModel`：`shared/build.gradle.kts` 的 Test 任务过滤了 `*ViewModel*Test`，
 * 按既定决策该过滤保留不动，因此守护测试必须落在会被执行的命名范围内。
 */
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

        // 收藏状态 / 排序 / 升降序 / 细分类 / 标签：任一变化都必须换键
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
        // 同一页面的列表态与网格态靠 scope 隔离，位置互不覆盖
        assertNotEquals(
            key("common_list", "actionDate"),
            key("common_grid", "actionDate")
        )
    }

    @Test
    fun nullValue_doesNotCollideWithLiteralNullString() {
        // 标签是用户可自定义的字符串，"null" 是合法标签值
        assertNotEquals(key("hobby_grid", null), key("hobby_grid", "null"))
    }

    @Test
    fun separatorInsideTag_doesNotCollideWithMultipleDimensions() {
        // 长度前缀编码：单值里出现 ':' 不能被拆成两个维度
        assertNotEquals(key("hobby_grid", "a:b"), key("hobby_grid", "a", "b"))
    }

    @Test
    fun subCategoryAllKey_normalizesToNull() {
        // 「全部」在请求参数里是 10000，在 UI 态里统一为 null，保证同内容同键
        assertNull(HobbySubCategoryOption.normalize(null))
        assertNull(HobbySubCategoryOption.normalize(UserCollectViewModel.DEFAULT_HOBBY_KEY))
        assertEquals(103, HobbySubCategoryOption.normalize(103))
    }

    @Test
    fun hobbyOrder_fallsBackFromOrderTimeWhenNotBuy() {
        // 入手时间仅在「已入」档合法，其余档必须回落收藏时间，否则键与请求参数不一致
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
