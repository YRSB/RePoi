package com.nobody.repoi.domain.usecase.collect

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.repository.CollectRepository

class DeleteWorksCollectUseCase(
    private val collectRepository: CollectRepository,
) {
    suspend operator fun invoke(worksId: Long): Result<Unit> {
        if (worksId <= 0L) {
            return Result.failure(ApiException(0, "作品信息缺失"))
        }
        return collectRepository.deleteWorksCollect(worksId)
    }
}
