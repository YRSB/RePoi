package com.nobody.repoi.platform

import androidx.compose.runtime.compositionLocalOf

val LocalAppMessages = compositionLocalOf<AppMessageBus> {
    error("LocalAppMessages 未提供：根组合需提供 AppMessageBus")
}

interface AppMessageBus {
    fun show(message: String)
    suspend fun take(): String
}
