package com.nobody.repoi.domain.usecase.collect

import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.session.UserSession

/**
 * 收藏中心批量取消收藏。
 *
 * 原版 `isSelf` 门控原先散落在 `UserCollectViewModel`，现上移到本用例：
 * 非本人收藏直接失败且**不发网络请求**，杜绝越权提交。
 */
class BatchCancelCollectUseCase(
    private val collectRepository: CollectRepository,
    private val userSession: UserSession,
) {
    /**
     * @param ids 外层收藏节点 id（`UserCollectHobbyItem.id` / `UserCollectAlbumItem.id`），非真实条目 id
     * @param ownerUserId 列表归属用户 id；与当前登录用户不一致即视为非本人
     */
    suspend operator fun invoke(ids: List<Long>, ownerUserId: Long): Result<Unit> {
        if (ownerUserId <= 0L || ownerUserId != userSession.currentUserId) {
            return Result.failure(ApiException(0, "仅能取消本人收藏"))
        }
        return collectRepository.cancelCollectBatch(ids)
    }
}
