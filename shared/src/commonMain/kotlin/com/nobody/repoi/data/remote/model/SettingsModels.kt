package com.nobody.repoi.data.remote.model

import kotlinx.serialization.Serializable

@Serializable
data class MaskListPayload(
    val blockList: List<MaskItem> = emptyList()
)

@Serializable
data class MaskItem(
    val id: Long = 0L,
    val itemType: String = "",
    val name: String? = null,
    val title: String? = null,
    val image: String? = null,
    val header: String? = null
)

@Serializable
data class AccountCheckPayload(
    val result: AccountCheckResult = AccountCheckResult()
)

@Serializable
data class AccountCheckResult(
    val verify: Boolean = false,
    val password: Boolean = false,
    val phone: Boolean = false,
    val email: Boolean = false,
    val wechat: Boolean = false
)

@Serializable
data class UserPrivacyPayload(
    val phone: String = "",
    val email: String = ""
)
