package com.nobody.repoi.platform

import androidx.compose.runtime.compositionLocalOf

/**
 * 轻提示总线（S6）：替代 94 处 `Toast.makeText`。
 * 各 dispatcher/VM 经 `onMessage` 回调上报，根组合经 SnackbarHost 消费。
 */
val LocalAppMessages = compositionLocalOf<AppMessageBus> {
    error("LocalAppMessages 未提供：根组合需提供 AppMessageBus")
}

/**
 * 最小的消息总线接口；M3/M4 的 SnackbarHost 装配本接口的具体实现。
 */
interface AppMessageBus {
    fun show(message: String)
    suspend fun take(): String
}
