package com.nobody.repoi.data.remote.model

enum class ActionSubType(val apiValue: String, val label: String) {

    Recommend("recommend", "推荐"),

    Comment("comment", "评论"),

    AllInfo("allInfo", "全部情报"),

    Confirm("confirm", "制作"),

    OfficialPic("official_pic", "更图"),

    Preorder("preorder", "开订"),

    Delay("delay", "延期"),

    Release("release", "出荷"),

    Reorder("reorder", "再版");

    companion object {

        val DEFAULT: ActionSubType = Recommend

        val ordered: List<ActionSubType> = entries

        private val LEGACY_KEYS: Map<String, ActionSubType> = mapOf("all" to AllInfo)

        fun fromApi(raw: String?): ActionSubType? = entries.firstOrNull { it.apiValue == raw }

        fun fromPersisted(raw: String?): ActionSubType =
            fromApi(raw) ?: raw?.let { LEGACY_KEYS[it] } ?: DEFAULT

        fun normalizeKey(raw: String?): String = fromPersisted(raw).apiValue
    }
}
