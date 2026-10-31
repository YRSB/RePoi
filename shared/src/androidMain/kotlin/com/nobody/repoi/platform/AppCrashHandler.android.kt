package com.nobody.repoi.platform

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
