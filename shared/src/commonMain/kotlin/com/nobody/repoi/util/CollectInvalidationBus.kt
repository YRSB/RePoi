package com.nobody.repoi.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 收藏数据失效事件总线（跨 Nav Entry）。
 *
 * 图片全屏画廊已升级为独立 Nav3 路由，取消收藏后无法直接触达仍存活的收藏页 ViewModel；
 * 通过该总线把「某个图片收藏项已被取消」广播给收藏页，由其在内存列表中就地剔除，
 * 避免返回收藏页后残留脏项、再次点击时按旧下标打开另一张图。
 */
class CollectInvalidationBus {

    private val _uncollected = MutableSharedFlow<Long>(extraBufferCapacity = 16)

    /** 已取消收藏的图片 id 流 */
    val uncollected: SharedFlow<Long> = _uncollected.asSharedFlow()

    /**
     * 广播图片取消收藏事件（幂等，nodeId 非正时忽略）。
     *
     * @param nodeId 被取消收藏的图片 id（与 `UserCollectPicItem.id` 同一字段）
     */
    fun notifyUncollected(nodeId: Long) {
        if (nodeId <= 0L) return
        _uncollected.tryEmit(nodeId)
    }
}
