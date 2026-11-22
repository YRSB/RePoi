package com.nobody.repoi.ui.navigation

import com.nobody.repoi.platform.AppMessageBus
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

internal class RootMessageBus : AppMessageBus {
    private val channel = Channel<String>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun show(message: String) {
        channel.trySend(message)
    }

    override suspend fun take(): String = channel.receive()
}
