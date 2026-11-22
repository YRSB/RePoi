package com.nobody.repoi.domain.usecase.collect

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.session.UserSession

class BatchCancelCollectUseCase(
    private val collectRepository: CollectRepository,
    private val userSession: UserSession,
) {

    suspend operator fun invoke(ids: List<Long>, ownerUserId: Long): Result<Unit> {
        if (ownerUserId <= 0L || ownerUserId != userSession.currentUserId) {
            return Result.failure(ApiException(0, "仅能取消本人收藏"))
        }
        return collectRepository.cancelCollectBatch(ids)
    }
}
