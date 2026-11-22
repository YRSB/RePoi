package com.nobody.repoi.platform

actual fun installUncaughtExceptionHook(onCrash: (threadName: String, throwable: Throwable) -> Unit) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        runCatching { onCrash(thread.name, throwable) }
        previous?.uncaughtException(thread, throwable)
    }
}
