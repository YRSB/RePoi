package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.AccountCheckResult
import com.nobody.repoi.data.remote.model.MaskItem
import com.nobody.repoi.data.remote.model.UserPrivacyPayload
import com.nobody.repoi.data.session.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

object PrivacyChannels {
    val MYSQL_KEYS: Set<String> = setOf(
        "setting_collect_hobby",
        "setting_collect_album",
        "setting_collect_statistics",
        "setting_collect_other",
        "setting_privacy_friend"
    )
}

class SettingsRepository(
    private val api: HpoiApi,
    private val userSession: UserSession
) {
    /** 兼容桥：由会话状态映射当前令牌；新代码应直接观察 [UserSession.state]。 */
    val tokenFlow: Flow<String>
        get() = userSession.tokenFlow

    val isLoggedIn: Boolean get() = userSession.isAuthenticated

    suspend fun addMask(itemNodeId: Long): Result<Unit> = runCatching {
        val snapshotToken = userSession.currentToken
        val response = api.addMask(itemNodeId = itemNodeId)
        response.unwrapUnit(userSession, "屏蔽失败", snapshotToken)
    }

    suspend fun deleteMask(itemNodeId: Long): Result<Unit> = runCatching {
        val snapshotToken = userSession.currentToken
        val response = api.deleteMask(itemNodeId = itemNodeId)
        response.unwrapUnit(userSession, "解除屏蔽失败", snapshotToken)
    }

    suspend fun getMaskList(
        page: Int = 1,
        pageSize: Int = 20,
        itemType: String
    ): Result<List<MaskItem>> = runCatching {
        val snapshotToken = userSession.currentToken
        val response = api.getMaskList(page = page, pageSize = pageSize, itemType = itemType)
        response.unwrapData(userSession, "获取屏蔽列表失败", snapshotToken)?.blockList ?: emptyList()
    }

    suspend fun getMysqlSettings(): Result<Map<String, Int>> = runCatching {
        val snapshotToken = userSession.currentToken
        api.getMysqlSettings().unwrapData(userSession, "获取隐私设置失败", snapshotToken).parseUserState()
    }

    suspend fun getRiakSettings(): Result<Map<String, Int>> = runCatching {
        val snapshotToken = userSession.currentToken
        api.getRiakSettings().unwrapData(userSession, "获取隐私设置失败", snapshotToken).parseUserState()
    }

    suspend fun getPrivacySettings(): Result<Map<String, Int>> {
        val mysql = getMysqlSettings()
        val riak = getRiakSettings()
        val merged = mutableMapOf<String, Int>()
        mysql.onSuccess { merged.putAll(it) }
        riak.onSuccess { merged.putAll(it) }
        val failure = if (mysql.isFailure) mysql.exceptionOrNull() else riak.exceptionOrNull()
        return if (failure != null && merged.isEmpty()) Result.failure(failure) else Result.success(merged)
    }

    suspend fun updatePrivacySetting(key: String, value: Int): Result<Unit> = runCatching {
        if (key in PrivacyChannels.MYSQL_KEYS) {
            val snapshotToken = userSession.currentToken
            api.updateMysqlSetting(key = key, value = value).unwrapUnit(userSession, "更新隐私设置失败", snapshotToken)
        } else {
            val snapshotToken = userSession.currentToken
            api.updateRiakSetting(key = key, value = value).unwrapUnit(userSession, "更新隐私设置失败", snapshotToken)
        }
    }

    suspend fun updateMysqlSetting(key: String, value: Int): Result<Unit> = runCatching {
        val snapshotToken = userSession.currentToken
        api.updateMysqlSetting(key = key, value = value).unwrapUnit(userSession, "更新隐私设置失败", snapshotToken)
    }

    suspend fun updateRiakSetting(key: String, value: Int): Result<Unit> = runCatching {
        val snapshotToken = userSession.currentToken
        api.updateRiakSetting(key = key, value = value).unwrapUnit(userSession, "更新隐私设置失败", snapshotToken)
    }

    suspend fun checkAccount(): Result<AccountCheckResult> = runCatching {
        val snapshotToken = userSession.currentToken
        val response = api.checkAccount()
        response.unwrapData(userSession, "获取账号状态失败", snapshotToken)?.result ?: AccountCheckResult()
    }

    suspend fun getUserPrivacy(): Result<UserPrivacyPayload> = runCatching {
        val snapshotToken = userSession.currentToken
        val response = api.getUserPrivacy()
        response.unwrapData(userSession, "获取账号信息失败", snapshotToken) ?: UserPrivacyPayload()
    }

    private fun JsonElement?.parseUserState(): Map<String, Int> {
        val state = (this as? JsonObject)?.get("userState") as? JsonObject ?: return emptyMap()
        return state.entries.mapNotNull { (key, value) ->
            val intValue = (value as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            key to intValue
        }.toMap()
    }
}
