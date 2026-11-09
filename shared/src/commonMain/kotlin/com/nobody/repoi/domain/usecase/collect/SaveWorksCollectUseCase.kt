package com.nobody.repoi.domain.usecase.collect

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.repository.CollectRepository

/**
 * 保存作品收藏（作品收藏编辑页）。
 *
 * 承担两处领域职责：作品三态白名单校验（1 想看 / 2 在看 / 3 看过），
 * 以及标签串清洗（去空白 → 去空项 → 去重 → 英文逗号拼接），统一在用例层完成，
 * 仓储只接受协议层参数。
 */
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

    /** 标签串清洗：`split` 语义的 trim + 去空 + 保序去重。 */
    private fun normalizeTags(tags: List<String>): String = tags
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(",")
}
