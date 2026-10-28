package com.nobody.poishelf.platform

/**
 * 退到后台（S9）。Android 实际 `moveTaskToBack(true) || finish()`；
 * JVM 无后台概念，返回 false（M6 壳经 onCloseRequest 关闭窗口）。
 */
expect fun moveAppToBackground(): Boolean

/**
 * 安装全局未捕获异常钩子（AppLogger 崩溃捕获用；common 无 `Thread` API）。
 *
 * [onCrash] 收到线程名 + 异常；平台实际先执行它再交还系统默认处理。
 * Android/JVM 实际均为 `Thread.setDefaultUncaughtExceptionHandler` 语义。
 */
expect fun installUncaughtExceptionHook(onCrash: (threadName: String, throwable: Throwable) -> Unit)
