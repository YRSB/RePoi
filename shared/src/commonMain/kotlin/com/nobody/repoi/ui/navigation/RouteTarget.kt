package com.nobody.repoi.ui.navigation

/**
 * 统一路由目标：动态流动作与富文本超链接解析后的唯一强类型出口。
 *
 * - [Internal] 直接承载 Navigation 3 的 [AppNavKey]（站内页面压入全局根栈）；
 * - [External] 交由系统浏览器/外部应用打开；
 * - [Notice] 无可跳转目标时的用户提示。
 *
 * 本模型与解析器（[AppRouteResolver]）均为纯计算产物，零 Compose 与平台副作用依赖。
 */
sealed interface RouteTarget {
    data class Internal(val key: AppNavKey) : RouteTarget

    data class External(val url: String) : RouteTarget

    data class Notice(val message: String) : RouteTarget
}
