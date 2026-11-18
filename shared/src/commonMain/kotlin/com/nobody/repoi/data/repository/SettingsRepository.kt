package com.nobody.repoi.data.repository

import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.AccountCheckResult
import com.nobody.repoi.data.remote.model.MaskItem
import com.nobody.repoi.data.remote.model.UserPrivacyPayload
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
) {

    suspend fun addMask(itemNodeId: Long): Result<Unit> = runCatching {
        api.addMask(itemNodeId = itemNodeId)
    }

    suspend fun deleteMask(itemNodeId: Long): Result<Unit> = runCatching {
        api.deleteMask(itemNodeId = itemNodeId)
    }

    suspend fun getMaskList(
        page: Int = 1,
        pageSize: Int = 20,
        itemType: String
    ): Result<List<MaskItem>> = runCatching {
        api.getMaskList(page = page, pageSize = pageSize, itemType = itemType)?.blockList ?: emptyList()
    }

    suspend fun getMysqlSettings(): Result<Map<String, Int>> = runCatching {
        api.getMysqlSettings().parseUserState()
    }

    suspend fun getRiakSettings(): Result<Map<String, Int>> = runCatching {
        api.getRiakSettings().parseUserState()
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
            api.updateMysqlSetting(key = key, value = value)
        } else {
            api.updateRiakSetting(key = key, value = value)
        }
    }

    suspend fun updateMysqlSetting(key: String, value: Int): Result<Unit> = runCatching {
        api.updateMysqlSetting(key = key, value = value)
    }

    suspend fun updateRiakSetting(key: String, value: Int): Result<Unit> = runCatching {
        api.updateRiakSetting(key = key, value = value)
    }

    suspend fun checkAccount(): Result<AccountCheckResult> = runCatching {
        api.checkAccount()?.result ?: AccountCheckResult()
    }

    suspend fun getUserPrivacy(): Result<UserPrivacyPayload> = runCatching {
        api.getUserPrivacy() ?: UserPrivacyPayload()
    }

    private fun JsonElement?.parseUserState(): Map<String, Int> {
        val state = (this as? JsonObject)?.get("userState") as? JsonObject ?: return emptyMap()
        return state.entries.mapNotNull { (key, value) ->
            val intValue = (value as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
            key to intValue
        }.toMap()
    }
}
