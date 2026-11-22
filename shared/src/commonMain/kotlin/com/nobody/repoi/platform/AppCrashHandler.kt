package com.nobody.repoi.platform

expect fun installUncaughtExceptionHook(onCrash: (threadName: String, throwable: Throwable) -> Unit)
