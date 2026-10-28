package com.nobody.poishelf.data.remote.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val PersonJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * 人物主体响应 (api/person/get)
 */
@Serializable
data class PersonDetailPayload(
    val result: PersonDetailResult = PersonDetailResult()
)

@Serializable
data class PersonDetailResult(
    val careerType: Int = 0,
    val deputyCareerType: List<Int> = emptyList(),
    val person: PersonData = PersonData(),
    val isAddFav: Boolean = false
)

@Serializable
data class PersonData(
    val id: Long = 0L,
    val categoryId: Int = 30000,
    val itemId: Long = 0L,
    val itemType: String? = "person",
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null,
    val detail: String? = null,
    val commentCount: Int = 0,
    val collect: Int = 0,
    val sex: Int = 0,
    val birthdayMap: PersonBirthday? = null,
    val otherInfo: String? = null,
    val hobbyRating: Double = 0.0,
    val hobbyCount: Int = 0
) {
    /** 优先显示中文名，若无则显示日文/外文名 */
    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""

    /** 副标题（若有外文名且与主标题不同） */
    val subName: String?
        get() = name?.takeIf { it.isNotBlank() && it != displayName }

    /** 性别文案：1 男、2 女，其余未知 */
    val sexText: String
        get() = when (sex) {
            1 -> "男"
            2 -> "女"
            else -> "未知"
        }

    /** 生日文案，有效年月日组合展示，全无则未知 */
    val birthdayText: String
        get() {
            val map = birthdayMap ?: return "未知"
            val parts = buildList {
                val year = map.year
                if (year != null && year in 1..9999) add("${year}年")
                if (map.month in 1..12) add("${map.month}月")
                if (map.day in 1..31) add("${map.day}日")
            }
            return parts.joinToString("").ifBlank { "未知" }
        }

    /** 解析 otherInfo JSON 字符串获取结构化扩展信息 */
    val parsedOtherInfo: List<CompanyOtherInfoItem>
        get() {
            val jsonStr = otherInfo ?: return emptyList()
            return try {
                PersonJson.decodeFromString<List<CompanyOtherInfoItem>>(jsonStr)
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** 纯文本详情介绍（去除 HTML 标签） */
    val detailPlainText: String
        get() {
            if (detail.isNullOrBlank()) return ""
            return detail
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("(?i)</p>"), "\n\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()
        }
}

@Serializable
data class PersonBirthday(
    val year: Int? = null,
    val month: Int = 0,
    val day: Int = 0,
    val season: Int = 0
)

/**
 * 人物参与作品响应 (api/person/works/get)
 * 出演角色 (api/person/character/get) 同形复用
 */
@Serializable
data class PersonWorksPayload(
    val worksList: List<PersonWorksEntry> = emptyList()
)

@Serializable
data class PersonCharacterPayload(
    val characterList: List<PersonWorksEntry> = emptyList()
)

@Serializable
data class PersonWorksEntry(
    val w: PersonWorksItem = PersonWorksItem()
)

@Serializable
data class PersonWorksItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val itemType: String? = null,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null
) {
    /** 优先显示中文名，若无则显示原名 */
    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""
}

/**
 * 人物参与周边响应 (api/person/hobby/get)
 */
@Serializable
data class PersonHobbyPayload(
    val hobby: List<PersonHobbyEntry> = emptyList()
)

@Serializable
data class PersonHobbyEntry(
    val jobName: List<String> = emptyList(),
    val h: PersonHobbyItem = PersonHobbyItem()
)

@Serializable
data class PersonHobbyItem(
    val id: Long = 0L,
    val itemId: Long = 0L,
    val itemType: String? = null,
    val name: String? = null,
    val nameCN: String? = null,
    val cover: String? = null
) {
    /** 优先显示中文名，若无则显示原名 */
    val displayName: String
        get() = nameCN?.ifBlank { null } ?: name ?: ""
}
