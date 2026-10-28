package com.nobody.poishelf.platform

/**
 * 退到后台（S9）JVM 实际：桌面无后台概念，返回 false
 *（M6 壳经窗口 `onCloseRequest` 关闭）。
 */
actual fun moveAppToBackground(): Boolean = false

/**
 * 全局未捕获异常钩子：`Thread.setDefaultUncaughtExceptionHandler` 语义，
 * 先执行 [onCrash] 再交还系统默认处理。
 */
actual fun installUncaughtExceptionHook(onCrash: (threadName: String, throwable: Throwable) -> Unit) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching { onCrash(thread.name, throwable) }
        previous?.uncaughtException(thread, throwable)
    }
}
