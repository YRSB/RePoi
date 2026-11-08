package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.AccountCheckResult
import com.nobody.repoi.data.remote.model.MaskItem
import com.nobody.repoi.data.remote.model.UserPrivacyPayload
import com.nobody.repoi.data.session.SessionState
import com.nobody.repoi.data.session.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
        get() = userSession.state
            .map { (it as? SessionState.Authenticated)?.token.orEmpty() }
            .distinctUntilChanged()

    val isLoggedIn: Boolean get() = userSession.isAuthenticated

    suspend fun addMask(itemNodeId: Long): Result<Unit> = runCatching {
        val response = api.addMask(itemNodeId = itemNodeId)
        response.unwrapUnit(userSession, "屏蔽失败")
    }

    suspend fun deleteMask(itemNodeId: Long): Result<Unit> = runCatching {
        val response = api.deleteMask(itemNodeId = itemNodeId)
        response.unwrapUnit(userSession, "解除屏蔽失败")
    }

    suspend fun getMaskList(
        page: Int = 1,
        pageSize: Int = 20,
        itemType: String
    ): Result<List<MaskItem>> = runCatching {
        val response = api.getMaskList(page = page, pageSize = pageSize, itemType = itemType)
        response.unwrapData(userSession, "获取屏蔽列表失败")?.blockList ?: emptyList()
    }

    suspend fun getMysqlSettings(): Result<Map<String, Int>> = runCatching {
        api.getMysqlSettings().unwrapData(userSession, "获取隐私设置失败").parseUserState()
    }

    suspend fun getRiakSettings(): Result<Map<String, Int>> = runCatching {
        api.getRiakSettings().unwrapData(userSession, "获取隐私设置失败").parseUserState()
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
            api.updateMysqlSetting(key = key, value = value).unwrapUnit(userSession, "更新隐私设置失败")
        } else {
            api.updateRiakSetting(key = key, value = value).unwrapUnit(userSession, "更新隐私设置失败")
        }
    }

    suspend fun updateMysqlSetting(key: String, value: Int): Result<Unit> = runCatching {
        api.updateMysqlSetting(key = key, value = value).unwrapUnit(userSession, "更新隐私设置失败")
    }

    suspend fun updateRiakSetting(key: String, value: Int): Result<Unit> = runCatching {
        api.updateRiakSetting(key = key, value = value).unwrapUnit(userSession, "更新隐私设置失败")
    }

    suspend fun checkAccount(): Result<AccountCheckResult> = runCatching {
        val response = api.checkAccount()
        response.unwrapData(userSession, "获取账号状态失败")?.result ?: AccountCheckResult()
    }

    suspend fun getUserPrivacy(): Result<UserPrivacyPayload> = runCatching {
        val response = api.getUserPrivacy()
        response.unwrapData(userSession, "获取账号信息失败") ?: UserPrivacyPayload()
    }

    private fun JsonElement?.parseUserState(): Map<String, Int> {
        val state = (this as? JsonObject)?.get("userState") as? JsonObject ?: return emptyMap()
        return state.entries.mapNotNull { (key, value) ->
            val intValue = (value as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            key to intValue
        }.toMap()
    }
}
