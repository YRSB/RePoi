package com.nobody.repoi.domain.usecase.collect

import com.nobody.repoi.data.repository.CollectRepository

/**
 * 切换条目收藏状态（画廊 / 相册详情 / 角色详情等「一键收藏」入口）。
 *
 * 状态驱动架构下，写操作成功后由 [CollectRepository] 内部状态池推送新值，
 * 调用方无需再做手动广播或页面间回调。
 */
class ToggleItemCollectUseCase(
    private val collectRepository: CollectRepository,
) {
    /**
     * @param isCollected 当前是否已收藏；true → 取消，false → 收藏
     * @return 切换后的收藏状态（成功时恒为 `!isCollected`）；失败透传仓储异常
     */
    suspend operator fun invoke(nodeId: Long, isCollected: Boolean): Result<Boolean> {
        val result = if (isCollected) {
            collectRepository.collectItemDel(nodeId)
        } else {
            collectRepository.collectItemAdd(nodeId)
        }
        return result.map { !isCollected }
    }
}
