package com.nobody.repoi.platform

/**
 * 安装全局未捕获异常钩子（AppLogger 崩溃捕获用；common 无 `Thread` API）。
 *
 * [onCrash] 收到线程名 + 异常；平台实际先执行它再交还系统默认处理。
 * Android/JVM 实际均为 `Thread.setDefaultUncaughtExceptionHandler` 语义。
 */
expect fun installUncaughtExceptionHook(onCrash: (threadName: String, throwable: Throwable) -> Unit)
