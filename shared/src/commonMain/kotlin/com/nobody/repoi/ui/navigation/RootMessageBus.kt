package com.nobody.repoi.ui.navigation

import com.nobody.repoi.platform.AppMessageBus
import kotlinx.coroutines.channels.Channel

/**
 * 根组合级轻提示总线（M6）。
 *
 * 30+ 屏经 `LocalAppMessages.current.show()` 上报一次性提示
 * （原 `Toast.makeText` 语义）；此前 commonMain 无任何提供方，
 * 首屏组合即崩溃。`PoiShelfApp` 提供本实现并经根 `SnackbarHost`
 * 消费 `take()`，形成完整链路。
 */
internal class RootMessageBus : AppMessageBus {
    private val channel = Channel<String>(Channel.UNLIMITED)

    override fun show(message: String) {
        channel.trySend(message)
    }

    override suspend fun take(): String = channel.receive()
}
