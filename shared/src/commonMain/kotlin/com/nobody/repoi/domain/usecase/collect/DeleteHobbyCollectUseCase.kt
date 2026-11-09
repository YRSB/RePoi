package com.nobody.repoi.domain.usecase.collect

import com.nobody.repoi.data.remote.model.CollectData
import com.nobody.repoi.data.repository.CollectRepository

/**
 * 取消手办收藏（收藏管理页「取消收藏」按钮），成功后状态池置空。
 */
class DeleteHobbyCollectUseCase(
    private val collectRepository: CollectRepository,
) {
    /**
     * @param currentData 当前收藏详情，服务端据此提交 `state = "delete"` 的完整载荷
     */
    suspend operator fun invoke(nodeId: Long, currentData: CollectData): Result<Unit> =
        collectRepository.deleteCollect(nodeId, currentData)
}
