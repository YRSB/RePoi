package com.nobody.repoi.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 收藏状态变更事件（跨 Nav Entry）。
 *
 * @param nodeId 图片收藏项 id（与 `UserCollectPicItem.id` 同一字段）
 * @param collected true 表示重新收藏，false 表示取消收藏
 */
data class CollectChange(
    val nodeId: Long,
    val collected: Boolean,
)

/**
 * 收藏数据失效事件总线（跨 Nav Entry）。
 *
 * 图片全屏画廊已升级为独立 Nav3 路由，收藏状态变更后无法直接触达仍存活的收藏页 ViewModel；
 * 通过该总线把「某个图片收藏项已被取消/重新收藏」广播给收藏页，由其在内存列表中就地剔除或刷新。
 */
class CollectInvalidationBus {

    private val _changes = MutableSharedFlow<CollectChange>(extraBufferCapacity = 16)

    /** 收藏状态变更流 */
    val changes: SharedFlow<CollectChange> = _changes.asSharedFlow()

    /** 广播图片取消收藏事件（nodeId 非正时忽略） */
    fun notifyUncollected(nodeId: Long) = notify(nodeId, collected = false)

    /** 广播图片重新收藏事件（nodeId 非正时忽略） */
    fun notifyCollected(nodeId: Long) = notify(nodeId, collected = true)

    private fun notify(nodeId: Long, collected: Boolean) {
        if (nodeId <= 0L) return
        _changes.tryEmit(CollectChange(nodeId = nodeId, collected = collected))
    }
}
