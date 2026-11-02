package com.nobody.repoi.ui.collect.subpage

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable

// ── 收藏子页滚动状态：统一的「排序 / 筛选」声明式键绑定 ──────────────
//
// 收藏页 8 个子页（周边 / 相册 / 图片 / 作品 / 厂商 / 系列 / 角色 / 人物）的列表滚动状态
// 一律经由下面三个入口创建，行为以周边页（[HobbyCollectSubPage]）为基准模型：
//
// 1. **键变化**（切换排序、收藏状态、子分类或标签筛选）：Compose 以
//    `initialFirstVisibleItemIndex = 0` 重建状态，列表自动回到顶部，不需要任何命令式回顶；
// 2. **键不变**（Nav3 从二级页返回、Entry 组合重建）：状态由 `SaveableStateRegistry`
//    恢复，滚动位置原样保留。
//
// 因此**禁止**再写 `LaunchedEffect(条件) { scrollToItem(0) }` 手动回顶：命令式回顶会在
// Entry 重建时重复执行，把刚恢复出来的滚动位置又清零，表现为「从详情页返回必定回顶」。
//
// 注意两条实现细节：
// - `rememberSaveable` 的 inputs 只参与「变化即重建」，**不参与**已保存状态的校验，
//   所以调用方必须把**所有**会改变列表内容 / 顺序的维度都传进来（漏传会让新条件沿用旧位置）；
// - 触底分页的 `derivedStateOf` 必须 `remember(状态实例)` 重新绑定，否则会一直盯着被丢弃的
//   旧状态实例，表现为「切过一次排序 / 筛选后触底分页永久失效」。

/**
 * 线性列表滚动状态入口（[LazyListState]），键为影响内容的排序 / 筛选维度。
 *
 * 参考实现见 [WorksCollectSubPage]。
 */
@Composable
internal fun rememberKeyedLazyListState(vararg keys: Any?): LazyListState =
    rememberSaveable(*keys, saver = LazyListState.Saver) { LazyListState() }

/**
 * 单列网格滚动状态入口（[LazyGridState]），键为影响内容的排序 / 筛选维度。
 *
 * 参考实现见 [HobbyCollectSubPage] 与 [CommonCollectSubPage]（列表形态）。
 */
@Composable
internal fun rememberKeyedLazyGridState(vararg keys: Any?): LazyGridState =
    rememberSaveable(*keys, saver = LazyGridState.Saver) { LazyGridState() }

/**
 * 瀑布流滚动状态入口（[LazyStaggeredGridState]），键为影响内容的排序 / 筛选维度。
 *
 * 参考实现见 [AlbumCollectSubPage] 与 [PictureCollectSubPage]。
 */
@Composable
internal fun rememberKeyedLazyStaggeredGridState(vararg keys: Any?): LazyStaggeredGridState =
    rememberSaveable(*keys, saver = LazyStaggeredGridState.Saver) { LazyStaggeredGridState() }
