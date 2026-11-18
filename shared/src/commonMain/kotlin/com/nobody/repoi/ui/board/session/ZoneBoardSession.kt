package com.nobody.repoi.ui.board.session

import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.ui.board.ZoneBoard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * 专区子板块会话控制器（深模块）。
 *
 * 封装专区 10 类子板块的生命周期调度、首屏懒加载、独立分页状态机、筛选不变性与滚动记忆。
 * 宿主（首页与专区详情）只需通过统一的 [activeSnapshot] 观察当前活跃板块的就绪状态，
 * 或通过 [snapshotFor] 提取特定板块投影，所有交互统一通过 [dispatch] 下发意图，
 * 并通过 [updateScope] 切换专区查询作用域。
 */
interface ZoneBoardSession {
    /** 当前活跃板块的响应式状态快照流。 */
    val activeSnapshot: StateFlow<ZoneBoardSnapshot>

    /**
     * 获取指定板块的当前状态快照。
     *
     * 具备首屏懒加载语义：若目标板块尚未发起过请求，将触发首屏懒加载。
     */
    fun snapshotFor(board: ZoneBoard): ZoneBoardSnapshot

    /**
     * 向会话控制器分发单向数据流意图。
     */
    fun dispatch(intent: ZoneBoardIntent)

    /**
     * 切换专区查询作用域。
     *
     * 具备原子取消与重置语义：取消所有后台在途协程，清空缓存条目与滚动记忆，
     * 并立即针对当前活跃板块发起新作用域的首屏加载。
     */
    fun updateScope(scope: ZoneQueryScope)

    /**
     * 会话控制器创建工厂。
     */
    interface Factory {
        fun create(
            coroutineScope: CoroutineScope,
            initialScope: ZoneQueryScope = ZoneQueryScope.All,
            initialBoard: ZoneBoard = ZoneBoard.Action
        ): ZoneBoardSession
    }
}
