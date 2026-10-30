package com.nobody.repoi.ui.item.list.components

/**
 * 高级多维筛选参数数据类，消除参数泥团 (Data Clumps)。
 *
 * 原 `HobbyFilterAdaptiveSheet.kt:53-58` 逐字移植（纯数据、无 Compose 依赖）；
 * M2 先行移植以满足 `HobbyListViewModel.applyAdvancedFilters`，
 * 完整筛选抽屉 UI（M4/M5）届时直接复用本类。
 */
data class HobbyAdvancedFilterParam(
    val sex: Int? = null,
    val specs: String? = null,
    val scale: String? = null,
    val r18: Int? = null
)
