package com.nobody.repoi.ui.board

import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.nobody.repoi.platform.isLandscapeOrientation

/**
 * 二手（`resale_item`）板块的交易类型与成色选项：首页、专区详情与二手市场**唯一**共享定义（issue #160）。
 *
 * 协议参数（`POST api/resale/search`，匿名实测见 `docs/research/aggregate-api-verification.md` §3/§5）：
 * - `type`：`1` 出售 / `2` 求购；
 * - `puritys`：成色多选键（`11/9/8/7`），空表示全部成色（省略参数）；
 * - `zones`：单专区为 `nodeId` 字符串；「全部」聚合省略；关注聚合因逗号串按**交集**处理而不可用。
 *
 * 禁止在首页/详情/市场各写一份（与 [AlbumBoardOptions] 同属「共享常量收敛」）。
 */
object ResaleBoardOptions {
    /** 默认交易类型：出售（与专区详情历史默认值一致）。 */
    const val DEFAULT_TYPE: Int = 1

    /** 游标分页页大小（与专区详情、二手市场既有实现一致）。 */
    const val PAGE_SIZE: Int = 24

    /** 交易类型：`key` = 协议 `type` 值，`value` = 界面文案；顺序即渲染顺序。 */
    val TYPES: List<Pair<Int, String>> = listOf(
        1 to "出售",
        2 to "求购"
    )

    /**
     * 成色选项：`key` = 协议 `puritys` 值（`null` = 全部成色，省略参数），`value` = 界面文案。
     *
     * 「全部成色」由共享定义直接给出，调用方渲染筛选条时无需再自行追加。
     */
    val PURITIES: List<Pair<String?, String>> = listOf(
        null to "全部成色",
        "11" to "全新未拆",
        "9" to "仅拆封",
        "8" to "二手好相",
        "7" to "瑕疵品"
    )

    /** 合法交易类型集合（宿主机切换入口的边界校验）。 */
    val TYPE_KEYS: Set<Int> = TYPES.map { it.first }.toSet()

    /** 合法成色键集合（不含 `null`「全部成色」）。 */
    val PURITY_KEYS: Set<String> = PURITIES.mapNotNull { it.first }.toSet()

    /** 类型是否合法。 */
    fun isValidType(type: Int): Boolean = type in TYPE_KEYS

    /** 成色是否合法（`null` 表示全部成色，恒合法）。 */
    fun isValidPurity(purity: String?): Boolean = purity == null || purity in PURITY_KEYS
}

/**
 * 二手列表列数策略：竖屏 1 列（列表形态），横屏 2 列、超宽（≥1200dp）3 列（沿用专区详情历史规则）。
 *
 * 首页与专区详情共用，避免两屏各写一份导致同一板块视觉漂移。
 */
@Composable
fun rememberResaleStaggeredGridColumns(): StaggeredGridCells {
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val isLandscape = isLandscapeOrientation()
    return remember(windowSizeClass, isLandscape) {
        when {
            !isLandscape -> StaggeredGridCells.Fixed(1)
            windowSizeClass.isWidthAtLeastBreakpoint(1200) -> StaggeredGridCells.Fixed(3)
            else -> StaggeredGridCells.Fixed(2)
        }
    }
}
