package com.nobody.repoi.ui.collect.subpage

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable

// ── 收藏子页滚动状态：统一的「排序 / 筛选」保存键绑定 ──────────────────
//
// 收藏页 8 个子页（周边 / 相册 / 图片 / 作品 / 厂商 / 系列 / 角色 / 人物）的列表滚动状态
// 一律经由下面三个入口创建，行为以周边页（[HobbyCollectSubPage]）为基准模型：
//
// 1. **条件变化**（切换排序、收藏状态、子分类或标签筛选）：inputs 变化 → 立即以
//    `initialFirstVisibleItemIndex = 0` 重建状态，列表从首项开始，不需要任何命令式回顶；
// 2. **条件不变**（Nav3 从二级页返回、Entry 组合重建）：条件串一致 → 由
//    `SaveableStateRegistry` 按同一位置键恢复，滚动位置原样保留。
//
// 为什么必须在 inputs 之外再控制位置作用域：
// 只传 inputs 时，恢复路径会先用 `consumeRestored(位置键)` 取回保存值，再用**当前** inputs
// 构造 holder 做「inputs 是否变化」校验——校验恒成立，于是**恢复值优先于 inputs 变化**。
// 结果：列表状态先被销毁、之后条件才变化时（典型：Pager 换 Tab 后本页被销毁，在别的通用页
// 改了共享的 `commonOrder`，再切回来），旧排序下的旧下标会被恢复，并套到已清空重载的新列表上。
// 用 `key(条件串)` 包裹后，条件不同就进入新的组合组，取不到旧保存值；销毁 / 恢复边界上同样保证回顶。
//
// 这里用单个计算键调用 `key(...)`，不展开 vararg：Compose 编译器对「展开 vararg 传入 key」的
// IR lowering 会崩溃（`irJoinKeyChain: Empty collection can't be reduced`）。单键调用既避开该问题，
// 也不使用已弃用的 `rememberSaveable(key = ...)` 重载。
//
// 另有两处配套约定：
// - 触底分页的 `derivedStateOf` 必须 `remember(状态实例)` 重新绑定，否则会一直盯着被丢弃的
//   旧状态实例，表现为「切过一次排序 / 筛选后触底分页永久失效」；
// - 传入的键必须覆盖**所有**会改变列表内容 / 顺序的维度，否则新条件下仍可能沿用旧位置；
//   同时 `scope` 必须在同一个 `SaveableStateRegistry` 内唯一（同一子页的列表 / 网格各一个）。

/**
 * 拼接显式保存键：`scope` + 各维度值。
 *
 * 每个维度值用「长度前缀」编码，避免用户自定义标签里出现分隔符时把不同条件拼成同一个键；
 * `null` 用独立的 `:n` 标记，避免与字符串 "null" 撞键（标签可由用户自定义）。
 * 键只需在一次保存 / 恢复周期内稳定，不要求可读。
 *
 * 可见性为 internal：commonTest 需要直接断言键的相等 / 不等不变量。
 */
internal fun scrollStateSaveableKey(scope: String, keys: Array<out Any?>): String =
    buildString {
        append(scope)
        for (key in keys) {
            if (key == null) {
                append(":n")
            } else {
                val text = key.toString()
                append(':').append(text.length).append(':').append(text)
            }
        }
    }

/**
 * 线性列表滚动状态入口（[LazyListState]），按影响内容的排序 / 筛选维度绑定保存键。
 *
 * 参考实现见 [WorksCollectSubPage]。
 */
@Composable
internal fun rememberKeyedLazyListState(scope: String, vararg keys: Any?): LazyListState =
    key(scrollStateSaveableKey(scope, keys)) {
        rememberSaveable(saver = LazyListState.Saver) {
            LazyListState()
        }
    }

/**
 * 单列网格滚动状态入口（[LazyGridState]），按影响内容的排序 / 筛选维度绑定保存键。
 *
 * 参考实现见 [HobbyCollectSubPage] 与 [CommonCollectSubPage]（列表形态）。
 */
@Composable
internal fun rememberKeyedLazyGridState(scope: String, vararg keys: Any?): LazyGridState =
    key(scrollStateSaveableKey(scope, keys)) {
        rememberSaveable(saver = LazyGridState.Saver) {
            LazyGridState()
        }
    }

/**
 * 瀑布流滚动状态入口（[LazyStaggeredGridState]），按影响内容的排序 / 筛选维度绑定保存键。
 *
 * 参考实现见 [AlbumCollectSubPage] 与 [PictureCollectSubPage]。
 */
@Composable
internal fun rememberKeyedLazyStaggeredGridState(scope: String, vararg keys: Any?): LazyStaggeredGridState =
    key(scrollStateSaveableKey(scope, keys)) {
        rememberSaveable(saver = LazyStaggeredGridState.Saver) {
            LazyStaggeredGridState()
        }
    }
