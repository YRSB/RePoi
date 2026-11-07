package com.nobody.repoi.ui.navigation

import com.nobody.repoi.platform.AppMessageBus
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

/**
 * 根组合级轻提示总线（M6）。
 *
 * 30+ 屏经 `LocalAppMessages.current.show()` 上报一次性提示
 * （原 `Toast.makeText` 语义）；此前 commonMain 无任何提供方，
 * 首屏组合即崩溃。`RePoiApp` 提供本实现并经根 `SnackbarHost`
 * 消费 `take()`，形成完整链路。
 *
 * 丢弃语义：容量 1 + [BufferOverflow.DROP_OLDEST]，即「只保留最新一条」。
 * snackbar 串行展示期间到达的中间提示会被覆盖，不会排队逐条播放；
 * 若某类提示不允许被覆盖，应改用小容量有界队列而非调大本处容量。
 */
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
