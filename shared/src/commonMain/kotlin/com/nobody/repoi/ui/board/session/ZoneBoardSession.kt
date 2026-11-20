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

    /** 动态（Action）基础板块的响应式状态快照流（供宿主快速响应专区动态更新）。 */
    val actionSnapshot: StateFlow<ZoneBoardSnapshot>

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
     * 并立即针对当前活跃板块发起新作用域的首屏加载（首屏延迟门闩未解除时只重置不加载，
     * 由 [resumeInitialLoad] 统一触发）。
     */
    fun updateScope(scope: ZoneQueryScope)

    /**
     * 解除首屏加载延迟门闩（配合 [Factory.create] 的 `deferInitialLoad`）。
     *
     * 防坑契约：宿主冷启动需要异步读取落盘偏好（专区 / 板块 / 筛选）才能确定首屏请求参数时，
     * 必须以 `deferInitialLoad = true` 创建会话，落定后调用本方法；否则会话会在构造时按
     * 默认作用域乐观发出注定作废的首屏请求，落定切换作用域后重发一次，
     * 用户看到加载指示器转两轮且信息流内容闪清（issue #198）。
     *
     * 门闩期间所有快照投影为「首屏加载中」；懒加载、重试、刷新与筛选重载全部挂起，
     * 筛选意图只登记值不触发请求。幂等：未延迟或已解除后调用无副作用。
     */
    fun resumeInitialLoad()


    /**
     * 会话控制器创建工厂。
     */
    interface Factory {
        fun create(
            coroutineScope: CoroutineScope,
            initialScope: ZoneQueryScope = ZoneQueryScope.All,
            initialBoard: ZoneBoard = ZoneBoard.Action,
            /**
             * 是否延迟首屏加载：`true` 时构造不发起任何请求，
             * 等宿主冷启动落定后调用 [resumeInitialLoad]（issue #198 双加载门闩）。
             */
            deferInitialLoad: Boolean = false
        ): ZoneBoardSession
    }
}
