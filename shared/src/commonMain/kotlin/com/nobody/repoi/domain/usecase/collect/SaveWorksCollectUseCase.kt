package com.nobody.repoi.domain.usecase.collect

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.repository.CollectRepository

class SaveWorksCollectUseCase(
    private val collectRepository: CollectRepository,
) {
    suspend operator fun invoke(
        worksId: Long,
        state: Int,
        tags: List<String>,
        comment: String,
    ): Result<Unit> {
        if (state !in CollectConstants.WORKS_STATES) {
            return Result.failure(ApiException(0, "收藏状态不合法"))
        }
        return collectRepository.saveWorksCollect(
            worksId = worksId,
            state = state,
            tagStr = normalizeTags(tags),
            comment = comment,
        )
    }

    private fun normalizeTags(tags: List<String>): String = tags
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(",")
}
