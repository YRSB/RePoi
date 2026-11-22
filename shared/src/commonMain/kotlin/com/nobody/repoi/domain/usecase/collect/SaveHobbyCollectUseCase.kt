package com.nobody.repoi.domain.usecase.collect

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.CollectData
import com.nobody.repoi.data.repository.CollectRepository

class SaveHobbyCollectUseCase(
    private val collectRepository: CollectRepository,
) {
    suspend operator fun invoke(nodeId: Long, collectData: CollectData): Result<Unit> {
        if (nodeId <= 0L) {
            return Result.failure(ApiException(0, "条目信息缺失"))
        }
        val state = collectData.state
        if (state.isNullOrBlank() || state == CollectConstants.STATE_DELETE) {
            return Result.failure(ApiException(0, "请选择收藏状态"))
        }
        return collectRepository.saveCollect(nodeId, collectData)
    }
}
