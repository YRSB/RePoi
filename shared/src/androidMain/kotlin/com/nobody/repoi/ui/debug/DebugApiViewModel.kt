package com.nobody.repoi.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.platform.nowMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class ApiEndpointType(val displayName: String, val description: String) {
    HOBBY_LIST("手办库查询", "POST api/hobby/query-v2"),
    ACTION_LIST("首页动态", "GET api/user-action/list-v3"),
    HOBBY_DETAIL("手办详情", "GET api/item/get-v2"),
    INFO_LIST("结构化属性", "POST api/item/infoList"),
    PIC_LIST("相关图片", "POST api/pic/list/relate-v2"),
    ALBUM_LIST("相册列表", "POST api/album/list"),
    ALBUM_DETAIL("相册详情", "POST api/album/detail"),
    ZONE_TABS("相册分区", "GET api/zone/home-tabs"),
    PRAISE_TOP("点赞榜", "GET api/item/praise/top"),
    USER_SELF("个人信息", "POST api/user/self"),
    LOGIN("账号登录", "POST api/user/loginV2")
}

data class DebugUiState(
    val selectedEndpoint: ApiEndpointType = ApiEndpointType.HOBBY_LIST,
    val paramInput1: String = "1",
    val paramInput2: String = "10000",
    val isLoading: Boolean = false,
    val elapsedTimeMs: Long = 0L,
    val statusSummary: String? = null,
    val jsonOutput: String = "点击下方「发送测试请求」按钮开始测试。"
)

class DebugApiViewModel(
    private val api: HpoiApi
) : ViewModel() {

    private val prettyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    fun selectEndpoint(endpoint: ApiEndpointType) {
        val (p1, p2) = when (endpoint) {
            ApiEndpointType.HOBBY_LIST -> Pair("1", "10000")
            ApiEndpointType.ACTION_LIST -> Pair("0", "recommend")
            ApiEndpointType.HOBBY_DETAIL -> Pair("12345", "hobby")
            ApiEndpointType.INFO_LIST -> Pair("67890", "hobby")
            ApiEndpointType.PIC_LIST -> Pair("12345", "hobby")
            ApiEndpointType.ALBUM_DETAIL -> Pair("100", "")
            ApiEndpointType.ALBUM_LIST -> Pair("top", "")
            ApiEndpointType.ZONE_TABS -> Pair("", "")
            ApiEndpointType.PRAISE_TOP -> Pair("month", "")
            ApiEndpointType.USER_SELF -> Pair("", "")
            ApiEndpointType.LOGIN -> Pair("", "")
        }
        _uiState.update {
            it.copy(
                selectedEndpoint = endpoint,
                paramInput1 = p1,
                paramInput2 = p2,
                statusSummary = null,
                jsonOutput = "已选择: ${endpoint.displayName} (${endpoint.description})"
            )
        }
    }

    fun updateParam1(val1: String) {
        _uiState.update { it.copy(paramInput1 = val1) }
    }

    fun updateParam2(val2: String) {
        _uiState.update { it.copy(paramInput2 = val2) }
    }

    fun runApiTest() {
        val currentState = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusSummary = null) }
            val startTime = nowMillis()

            try {
                val (summary, resultJson) = when (currentState.selectedEndpoint) {
                    ApiEndpointType.HOBBY_LIST -> {
                        val page = currentState.paramInput1.toIntOrNull() ?: 1
                        val catId = currentState.paramInput2.toIntOrNull() ?: 10000
                        val res = api.getHobbyList(page = page, categoryId = catId)
                        Pair(
                            "Success: ${res.success}, Code: ${res.code}, Msg: ${res.msg}",
                            prettyJson.encodeToString(res)
                        )
                    }
                    ApiEndpointType.ACTION_LIST -> {
                        val catId = currentState.paramInput1.toIntOrNull() ?: 0
                        val subType = currentState.paramInput2.ifBlank { "recommend" }
                        val res = api.getActionList(categoryId = catId, subType = subType, pageSize = 20)
                        Pair(
                            "Success: ${res.success}, Code: ${res.code}, Msg: ${res.msg}",
                            prettyJson.encodeToString(res)
                        )
                    }
                    ApiEndpointType.HOBBY_DETAIL -> {
                        val itemId = currentState.paramInput1.toLongOrNull() ?: 12345L
                        val itemType = currentState.paramInput2.ifBlank { "hobby" }
                        val res = api.getHobbyDetail(itemId = itemId, itemType = itemType)
                        Pair(
                            "Success: ${res.success}, Code: ${res.code}, Msg: ${res.msg}",
                            prettyJson.encodeToString(res)
                        )
                    }
                    ApiEndpointType.INFO_LIST -> {
                        val nodeId = currentState.paramInput1.toLongOrNull() ?: 67890L
                        val itemType = currentState.paramInput2.ifBlank { "hobby" }
                        val res = api.getInfoList(nodeId = nodeId, itemType = itemType)
                        Pair(
                            "Success: ${res.success}, Code: ${res.code}, Msg: ${res.msg}",
                            prettyJson.encodeToString(res)
                        )
                    }
                    ApiEndpointType.PIC_LIST -> {
                        val itemId = currentState.paramInput1.toLongOrNull() ?: 12345L
                        val itemType = currentState.paramInput2.ifBlank { "hobby" }
                        val res = api.getPicList(itemId = itemId, itemType = itemType, picSubType = "official")
                        Pair(
                            "Success: ${res.success}, Code: ${res.code}, Msg: ${res.msg}",
                            prettyJson.encodeToString(res)
                        )
                    }
                    ApiEndpointType.ALBUM_DETAIL -> {
                        val albumId = currentState.paramInput1.toLongOrNull() ?: 100L
                        val res = api.getAlbumDetail(albumId = albumId)
                        Pair(
                            "Success: ${res.success}, Code: ${res.code}, Msg: ${res.msg}",
                            prettyJson.encodeToString(res)
                        )
                    }
                    ApiEndpointType.ALBUM_LIST -> {
                        val order = currentState.paramInput1.ifBlank { "top" }
                        val zones = currentState.paramInput2.ifBlank { null }
                        val res = api.getAlbumList(order = order, zones = zones)
                        Pair(
                            "Success: ${res.success}, Code: ${res.code}, Msg: ${res.msg}",
                            prettyJson.encodeToString(res)
                        )
                    }
                    ApiEndpointType.ZONE_TABS -> {
                        val res = api.getZoneHomeTabs()
                        Pair(
                            "Success: ${res.success}, Code: ${res.code}, Msg: ${res.msg}",
                            prettyJson.encodeToString(res)
                        )
                    }
                    ApiEndpointType.PRAISE_TOP -> {
                        val type = currentState.paramInput1.ifBlank { "month" }
                        val res = api.getPraiseTop(type = type)
                        Pair(
                            "Success: ${res.success}, Code: ${res.code}, Msg: ${res.msg}",
                            prettyJson.encodeToString(res)
                        )
                    }
                    ApiEndpointType.USER_SELF -> {
                        val res = api.getUserSelf()
                        Pair(
                            "Success: ${res.success}, Code: ${res.code}, Msg: ${res.msg}",
                            prettyJson.encodeToString(res)
                        )
                    }
                    ApiEndpointType.LOGIN -> {
                        val acc = currentState.paramInput1
                        val pwd = currentState.paramInput2
                        val res = api.login(account = acc, password = pwd)
                        Pair(
                            "Success: ${res.success}, Code: ${res.code}, Msg: ${res.msg}",
                            prettyJson.encodeToString(res)
                        )
                    }
                }

                val elapsed = nowMillis() - startTime
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        elapsedTimeMs = elapsed,
                        statusSummary = summary,
                        jsonOutput = resultJson
                    )
                }
            } catch (e: Exception) {
                val elapsed = nowMillis() - startTime
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        elapsedTimeMs = elapsed,
                        statusSummary = "Error: ${e::class.simpleName}",
                        jsonOutput = "请求异常:\n${e.message}\n\n堆栈信息:\n${e.stackTraceToString()}"
                    )
                }
            }
        }
    }
}
