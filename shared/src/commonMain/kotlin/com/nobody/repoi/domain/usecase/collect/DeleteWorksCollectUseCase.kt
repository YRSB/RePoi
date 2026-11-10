package com.nobody.repoi.domain.usecase.collect

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.repository.CollectRepository

/**
 * 取消作品收藏（作品收藏编辑页 / 作品详情的写侧收口）。
 *
 * 只承担一处领域守卫：`worksId` 缺失（`<= 0`）时**不发网络请求**直接失败，
 * 避免把无效 id 提交给 `api/works/glean/del`；其余错误文案沿用仓储。
 */
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
