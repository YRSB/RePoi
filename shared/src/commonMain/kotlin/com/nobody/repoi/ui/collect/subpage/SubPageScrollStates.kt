package com.nobody.repoi.ui.collect.subpage

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable

// ── 收藏子页滚动状态：统一的「排序 / 筛选」key() 作用域 ──────────────
//
// 收藏页 8 个子页（周边 / 相册 / 图片 / 作品 / 厂商 / 系列 / 角色 / 人物）的列表滚动状态
// 一律经由下面三个入口创建，行为以周边页（[HobbyCollectSubPage]）为基准模型：
//
// 1. **条件变化**（切换排序、收藏状态、子分类或标签筛选）：`key(*keys)` 改变组合键，
//    旧作用域整体离开组合、新作用域重新 `init()`，列表从首项开始，不需要任何命令式回顶；
// 2. **条件不变**（Nav3 从二级页返回、Entry 组合重建）：组合键稳定，状态由
//    `SaveableStateRegistry` 按同一键恢复，滚动位置原样保留。
//
// 为什么必须是 `key()` 而不是只给 `rememberSaveable` 传 inputs：
// `rememberSaveable` 的恢复路径先 `consumeRestored(finalKey)`，再用**当前** inputs 构造
// holder 并做「inputs 是否变化」校验——校验恒成立，于是**恢复值优先于 inputs 变化**。
// 也就是说 inputs 只能在「同一次组合内」触发回顶；一旦列表状态先被销毁、之后条件才变化
// （典型：Pager 换 Tab 后本页被销毁，在别的通用页改了共享的 `commonOrder`，再切回来），
// 旧排序下的旧下标会被恢复并套到已清空重载的新列表上。`key()` 把条件并进组合键，
// 恢复键随条件改变而改变，销毁 / 恢复边界上同样保证回顶。
//
// 因此**禁止**再写 `LaunchedEffect(条件) { scrollToItem(0) }` 手动回顶：命令式回顶会在
// Entry 重建时重复执行，把刚恢复出来的滚动位置又清零，表现为「从详情页返回必定回顶」。
//
// 另有两处配套约定：
// - 触底分页的 `derivedStateOf` 必须 `remember(状态实例)` 重新绑定，否则会一直盯着被丢弃的
//   旧状态实例，表现为「切过一次排序 / 筛选后触底分页永久失效」；
// - 传入的键必须覆盖**所有**会改变列表内容 / 顺序的维度，否则新条件下仍可能沿用旧位置。

/**
 * 线性列表滚动状态入口（[LazyListState]），按影响内容的排序 / 筛选维度建立 `key()` 作用域。
 *
 * 参考实现见 [WorksCollectSubPage]。
 */
@Composable
internal fun rememberKeyedLazyListState(vararg keys: Any?): LazyListState =
    key(*keys) {
        rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    }

/**
 * 单列网格滚动状态入口（[LazyGridState]），按影响内容的排序 / 筛选维度建立 `key()` 作用域。
 *
 * 参考实现见 [HobbyCollectSubPage] 与 [CommonCollectSubPage]（列表形态）。
 */
@Composable
internal fun rememberKeyedLazyGridState(vararg keys: Any?): LazyGridState =
    key(*keys) {
        rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    }

/**
 * 瀑布流滚动状态入口（[LazyStaggeredGridState]），按影响内容的排序 / 筛选维度建立 `key()` 作用域。
 *
 * 参考实现见 [AlbumCollectSubPage] 与 [PictureCollectSubPage]。
 */
@Composable
internal fun rememberKeyedLazyStaggeredGridState(vararg keys: Any?): LazyStaggeredGridState =
    key(*keys) {
        rememberSaveable(saver = LazyStaggeredGridState.Saver) { LazyStaggeredGridState() }
    }
