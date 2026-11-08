package com.nobody.repoi.data.session

import com.nobody.repoi.data.remote.model.UserProfile

/**
 * 用户会话的强类型状态模型。
 *
 * 状态多态取代散落的布尔标志位：调用端只需订阅 [UserSession.state]，
 * 无需再感知底层凭据存储的异步冷启动时序。
 */
sealed interface SessionState {

    /** 冷启动预热中：本地凭据尚未读出，调用端可展示中性加载态而非访客态。 */
    data object Prewarming : SessionState

    /** 未认证：本地无有效凭据，或会话已登出 / 令牌失效被自愈清理。 */
    data object Unauthenticated : SessionState

    /**
     * 已认证：本地凭据可用即可进入，[profile] 由后台静默刷新增量填充。
     *
     * @param userId 用户 ID；冷启动仅恢复凭据时可能为 0，档案刷新后回填
     * @param token 当前活跃登录令牌（唯一凭据出口）
     * @param profile 用户档案；离线启动时为 null，联网后补齐
     */
    data class Authenticated(
        val userId: Long,
        val token: String,
        val profile: UserProfile? = null,
    ) : SessionState {
        /** 档案是否已完成至少一次网络刷新。 */
        val isProfileLoaded: Boolean get() = profile != null
    }
}
