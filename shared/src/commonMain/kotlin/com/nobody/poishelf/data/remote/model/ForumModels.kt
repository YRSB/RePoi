package com.nobody.poishelf.data.remote.model

import com.nobody.poishelf.platform.nowMillis
import kotlinx.serialization.Serializable

/**
 * 版块列表返回外层结构（api/forum/focus/list 与 api/forum/not/focus/list）。
 */
@Serializable
data class ForumListPayload(
    val forumList: List<ForumItem>? = emptyList()
)

/**
 * 讨论版块条目数据模型。
 * 字段键反推自原版 PlateSubscribeAdapter 读取键（name/detail/focusUserCount/plateState/levelLimit/addTime）。
 */
@Serializable
data class ForumItem(
    val id: Long = 0L,
    val name: String = "",
    val detail: String? = null,
    @Serializable(with = LenientIntSerializer::class)
    val focusUserCount: Int? = null,
    val plateState: Int = 0,
    val levelLimit: Int = 0,
    @Serializable(with = LenientDateTimeToEpochSerializer::class)
    val addTime: Long = 0L,
    val postsCount: Int = 0
) {
    /** 有效关注人数（服务端可能下发数字或字符串，解析失败回退为 0） */
    val effectiveFocusCount: Int
        get() = focusUserCount ?: 0

    /** 是否已关注（plateState 非 0 即已关注） */
    val isFollowed: Boolean
        get() = plateState != 0

    /**
     * 是否新版块：创建于 7 天内。
     * 原版仅在未关注 Tab 展示 NEW 徽标，调用方需结合所在 Tab 判定。
     */
    val isNewPlate: Boolean
        get() = addTime > 0L && nowMillis() < addTime + NEW_PLATE_WINDOW_MS

    companion object {
        /** 新版块判定窗口：7 天（对齐原版 BaseConstants.Time.WEEK） */
        const val NEW_PLATE_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    }
}

/**
 * 版块详情返回外层结构（GET api/forum/{id}）。
 */
@Serializable
data class ForumDetailPayload(
    val forum: ForumDetailData? = null
)

/**
 * 版块详情数据主体，用于讨论板 forum Tab 顶部版块头卡。
 */
@Serializable
data class ForumDetailData(
    val id: Long = 0L,
    val name: String = "",
    val postsCount: Int = 0,
    val adminList: List<ForumAdmin>? = emptyList()
)

/**
 * 版块管理员（版主）简档。
 */
@Serializable
data class ForumAdmin(
    val header: String? = null,
    val nickname: String? = null
)
