package com.nobody.repoi.domain.usecase.collect

import com.nobody.repoi.data.remote.model.CollectData
import com.nobody.repoi.data.repository.CollectRepository

class DeleteHobbyCollectUseCase(
    private val collectRepository: CollectRepository,
) {

    suspend operator fun invoke(nodeId: Long, currentData: CollectData): Result<Unit> =
        collectRepository.deleteCollect(nodeId, currentData)
}
