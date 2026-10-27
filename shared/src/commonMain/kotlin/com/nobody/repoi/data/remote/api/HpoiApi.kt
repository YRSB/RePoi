package com.nobody.repoi.data.remote.api

import com.nobody.repoi.data.remote.model.ActionListData
import com.nobody.repoi.data.remote.model.AlbumDetailPayload
import com.nobody.repoi.data.remote.model.AlbumListPayload
import com.nobody.repoi.data.remote.model.AttributeListPayload
import com.nobody.repoi.data.remote.model.AutoFillPayload
import com.nobody.repoi.data.remote.model.BlockListPayload
import com.nobody.repoi.data.remote.model.CategoryNode
import com.nobody.repoi.data.remote.model.CategoryTreeData
import com.nobody.repoi.data.remote.model.CharacterAlbumPayload
import com.nobody.repoi.data.remote.model.CharacterDetailPayload
import com.nobody.repoi.data.remote.model.CharacterRelevantPayload
import com.nobody.repoi.data.remote.model.CharacterWorksPayload
import com.nobody.repoi.data.remote.model.ChatMessagePayload
import com.nobody.repoi.data.remote.model.CheckInStatusPayload
import com.nobody.repoi.data.remote.model.CollectCountPayload
import com.nobody.repoi.data.remote.model.CollectSimilarityPayload
import com.nobody.repoi.data.remote.model.CollectStatePayload
import com.nobody.repoi.data.remote.model.CollectTagsPayload
import com.nobody.repoi.data.remote.model.CollectTopPayload
import com.nobody.repoi.data.remote.model.CommentPayload
import com.nobody.repoi.data.remote.model.CommentReplyAddPayload
import com.nobody.repoi.data.remote.model.CommentReplyPayload
import com.nobody.repoi.data.remote.model.CommonResponse
import com.nobody.repoi.data.remote.model.CompanyActionPayload
import com.nobody.repoi.data.remote.model.CompanyDetailPayload
import com.nobody.repoi.data.remote.model.CompanyMyVotesData
import com.nobody.repoi.data.remote.model.CompanySeriesPayload
import com.nobody.repoi.data.remote.model.CompanyTagPayload
import com.nobody.repoi.data.remote.model.DiscussListPayload
import com.nobody.repoi.data.remote.model.DiscussReplyListPayload
import com.nobody.repoi.data.remote.model.FellowCountPayload
import com.nobody.repoi.data.remote.model.FellowTypePayload
import com.nobody.repoi.data.remote.model.ForumDetailPayload
import com.nobody.repoi.data.remote.model.ForumListPayload
import com.nobody.repoi.data.remote.model.HobbyAlbumPayload
import com.nobody.repoi.data.remote.model.HobbyDetailPayload
import com.nobody.repoi.data.remote.model.HobbyGroupPayload
import com.nobody.repoi.data.remote.model.HobbyListData
import com.nobody.repoi.data.remote.model.HobbyPushListPayload
import com.nobody.repoi.data.remote.model.HobbyTimelinePayload
import com.nobody.repoi.data.remote.model.HotwordPayload
import com.nobody.repoi.data.remote.model.InfoListPayload
import com.nobody.repoi.data.remote.model.ItemLikeInfoPayload
import com.nobody.repoi.data.remote.model.ItemTagsPayload
import com.nobody.repoi.data.remote.model.LatestMsgIdPayload
import com.nobody.repoi.data.remote.model.LoginData
import com.nobody.repoi.data.remote.model.MessageContactPayload
import com.nobody.repoi.data.remote.model.OrderStaticPayload
import com.nobody.repoi.data.remote.model.PersonCharacterPayload
import com.nobody.repoi.data.remote.model.PersonDetailPayload
import com.nobody.repoi.data.remote.model.PersonHobbyPayload
import com.nobody.repoi.data.remote.model.PersonWorksPayload
import com.nobody.repoi.data.remote.model.PicListPayload
import com.nobody.repoi.data.remote.model.PictureRelatePayload
import com.nobody.repoi.data.remote.model.PostDetailPayload
import com.nobody.repoi.data.remote.model.PostListPayload
import com.nobody.repoi.data.remote.model.PraiseTopPayload
import com.nobody.repoi.data.remote.model.PushMessagePayload
import com.nobody.repoi.data.remote.model.RelatedAlbumPayload
import com.nobody.repoi.data.remote.model.RelatedHobbyPayload
import com.nobody.repoi.data.remote.model.ResaleDetailPayload
import com.nobody.repoi.data.remote.model.ResaleSearchPayload
import com.nobody.repoi.data.remote.model.SearchResponsePayload
import com.nobody.repoi.data.remote.model.SeriesAlbumPayload
import com.nobody.repoi.data.remote.model.SeriesDetailPayload
import com.nobody.repoi.data.remote.model.SeriesForumPayload
import com.nobody.repoi.data.remote.model.SeriesGoodsPayload
import com.nobody.repoi.data.remote.model.SeriesLinkPayload
import com.nobody.repoi.data.remote.model.StickerBuyTimePayload
import com.nobody.repoi.data.remote.model.StickerPackPayload
import com.nobody.repoi.data.remote.model.UnreadPushCountPayload
import com.nobody.repoi.data.remote.model.UserCollectAlbumPayload
import com.nobody.repoi.data.remote.model.UserCollectCommonPayload
import com.nobody.repoi.data.remote.model.UserCollectHobbyPayload
import com.nobody.repoi.data.remote.model.UserCollectPicPayload
import com.nobody.repoi.data.remote.model.UserFellowPayload
import com.nobody.repoi.data.remote.model.UserHistoryPayload
import com.nobody.repoi.data.remote.model.UserMpPayload
import com.nobody.repoi.data.remote.model.UserPraisePayload
import com.nobody.repoi.data.remote.model.UserSearchPayload
import com.nobody.repoi.data.remote.model.UserSelfPayload
import com.nobody.repoi.data.remote.model.WorksAlbumPayload
import com.nobody.repoi.data.remote.model.WorksCharacterPayload
import com.nobody.repoi.data.remote.model.WorksDetailPayload
import com.nobody.repoi.data.remote.model.ZoneFocusPayload
import com.nobody.repoi.data.remote.model.ZoneHomeTabsPayload
import com.nobody.repoi.data.remote.model.ZoneInfoPayload
import com.nobody.repoi.data.remote.model.ZoneItemPayload
import com.nobody.repoi.data.remote.model.ZoneItemsPayload
import com.nobody.repoi.network.bodyAsJsonRef
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.Json

private fun toParameters(map: Map<String, String>): Parameters = Parameters.build {
    map.forEach { (k, v) -> append(k, v) }
}

/**
 * Hpoi 核心 API Ktor client（由 Retrofit `HpoiApiService` 机械翻译，136 方法）。
 * 公共参数/头由 `CommonParamsPlugin` 统一注入；响应经 `bodyAsJsonRef` 解码。
 */
class HpoiApi(
    private val client: HttpClient,
    private val json: Json
) {
/**
     * 获取首页动态/信息流 (api/user-action/list-v3, GET)
     * 注意：subType 不能为空（如 "recommend" 或 "all"），pageSize 不能低于 5（默认 20）。
     */
    suspend fun getActionList(
        categoryId: Int = 0,
        lastTime: Long = 0L,
        pageSize: Int = 20,
        subType: String? = "recommend",
        zoneNodeId: Long? = null,
        zoneIds: String? = null
    ): CommonResponse<ActionListData> = client.get("api/user-action/list-v3") {
        url {
            parameters.append("categoryId", categoryId.toString())
            parameters.append("lastTime", lastTime.toString())
            parameters.append("pageSize", pageSize.toString())
            subType?.let { parameters.append("subType", it.toString()) }
            zoneNodeId?.let { parameters.append("zoneNodeId", it.toString()) }
            zoneIds?.let { parameters.append("zoneIds", it.toString()) }
        }
    }.bodyAsJsonRef(json)

/**
     * 获取关注动态（条目/用户）(api/user-action/collect/care/list-v2, GET)
     * 注意：条目动态按筛选映射 actionType/subType；用户动态仅传 type=user。
     */
    suspend fun getFollowDynamics(
        page: Int? = null,
        lastId: Long = 0L,
        pageSize: Int = 20,
        actionType: Int? = null,
        subType: String? = null,
        type: String = "item",
        isHome: Int? = null
    ): CommonResponse<JsonObject> = client.get("api/user-action/collect/care/list-v2") {
        url {
            page?.let { parameters.append("page", it.toString()) }
            parameters.append("lastId", lastId.toString())
            parameters.append("pageSize", pageSize.toString())
            actionType?.let { parameters.append("actionType", it.toString()) }
            subType?.let { parameters.append("subType", it.toString()) }
            parameters.append("type", type.toString())
            isHome?.let { parameters.append("isHome", it.toString()) }
        }
    }.bodyAsJsonRef(json)

/**
     * 查询手办/周边列表 (api/hobby/query-v2, POST Form)
     */
    suspend fun getHobbyList(
        page: Int = 1,
        pageSize: Int = 20,
        categoryId: Int = 10000,
        order: String? = "hits7Day"
    ): CommonResponse<HobbyListData> = client.post("api/hobby/query-v2") {
        setBody(FormDataContent(Parameters.build {
            append("page", page.toString())
            append("pageSize", pageSize.toString())
            append("categoryId", categoryId.toString())
            order?.let { append("order", it.toString()) }
        }))
    }.bodyAsJsonRef(json)

/**
     * 多维查询手办/周边列表 (api/hobby/query-v2, POST Form)
     * 支持 category, order, page, pageSize, releaseYear, releaseMonth, releaseStart, releaseEnd, hasShopItem, keyword, sex, specs, scale
     */
    suspend fun queryHobbyList(
        params: Map<String, String>
    ): CommonResponse<HobbyListData> = client.post("api/hobby/query-v2") {
        setBody(FormDataContent(Parameters.build {
            appendAll(toParameters(params))
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取全部分类树结构 (api/category/web/all, POST Form 空参)
     */
    suspend fun getAllCategories(): CommonResponse<CategoryTreeData> =
        client.post("api/category/web/all").bodyAsJsonRef(json)

/**
     * 获取条目详情 (api/item/get-v2, GET)
     */
    suspend fun getHobbyDetail(
        itemId: Long,
        itemType: String
    ): CommonResponse<HobbyDetailPayload> = client.get("api/item/get-v2") {
        url {
            parameters.append("itemId", itemId.toString())
            parameters.append("itemType", itemType.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取条目结构化属性列表 (api/item/infoList, POST Form)
     */
    suspend fun getInfoList(
        nodeId: Long,
        itemType: String
    ): CommonResponse<InfoListPayload> = client.post("api/item/infoList") {
        setBody(FormDataContent(Parameters.build {
            append("id", nodeId.toString())
            append("itemType", itemType.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取手办发售情报时间轴 (api/user-action/item-info/list, GET)
     */
    suspend fun getTimeline(
        nodeId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): CommonResponse<HobbyTimelinePayload> = client.get("api/user-action/item-info/list") {
        url {
            parameters.append("nodeId", nodeId.toString())
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取手办关联同款/再版分组列表 (api/hobby/group, POST Form)
     */
    suspend fun getHobbyGroup(
        id: Long
    ): CommonResponse<HobbyGroupPayload> = client.post("api/hobby/group") {
        setBody(FormDataContent(Parameters.build {
            append("id", id.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取推荐手办列表 (api/hobby/push/list/V2, POST Form)
     */
    suspend fun getHobbyPushList(
        page: Int = 1,
        pageSize: Int = 10
    ): CommonResponse<HobbyPushListPayload> = client.post("api/hobby/push/list/V2") {
        setBody(FormDataContent(Parameters.build {
            append("page", page.toString())
            append("pageSize", pageSize.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取关联图片列表 (api/pic/list/relate-v2, POST Form)
     */
    suspend fun getPicList(
        itemId: Long,
        itemType: String,
        picSubType: String? = null,
        page: Int = 1,
        pageSize: Int = 20,
        order: String? = null,
        nodeId: Long? = null
    ): CommonResponse<PicListPayload> = client.post("api/pic/list/relate-v2") {
        setBody(FormDataContent(Parameters.build {
            append("itemId", itemId.toString())
            append("itemType", itemType.toString())
            picSubType?.let { append("subType", it.toString()) }
            append("page", page.toString())
            append("pageSize", pageSize.toString())
            order?.let { append("order", it.toString()) }
            nodeId?.let { append("nodeId", it.toString()) }
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取相册详情 (api/album/detail, POST Form)
     */
    suspend fun getAlbumDetail(
        albumId: Long,
        itemId: Long? = null
    ): CommonResponse<AlbumDetailPayload> = client.post("api/album/detail") {
        setBody(FormDataContent(Parameters.build {
            append("id", albumId.toString())
            itemId?.let { append("itemId", it.toString()) }
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取相关相册列表 (api/album/relate/album, POST Form)
     */
    suspend fun getRelatedAlbums(
        albumId: Long,
        page: Int = 1,
        pageSize: Int = 8
    ): CommonResponse<RelatedAlbumPayload> = client.post("api/album/relate/album") {
        setBody(FormDataContent(Parameters.build {
            append("nodeId", albumId.toString())
            append("page", page.toString())
            append("pageSize", pageSize.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取相册关联手办列表 (api/item/relate/hobby, POST Form)
     */
    suspend fun getRelatedHobbies(
        albumId: Long,
        itemType: String = "album",
        pageSize: Int = 8
    ): CommonResponse<RelatedHobbyPayload> = client.post("api/item/relate/hobby") {
        setBody(FormDataContent(Parameters.build {
            append("id", albumId.toString())
            append("itemType", itemType.toString())
            append("pageSize", pageSize.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取条目/相册标签列表 (api/item/tags, POST Form)
     */
    suspend fun getTags(
        albumId: Long
    ): CommonResponse<ItemTagsPayload> = client.post("api/item/tags") {
        setBody(FormDataContent(Parameters.build {
            append("id", albumId.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取相册频道分区 tabs (api/zone/home-tabs, GET)
     */
    suspend fun getZoneHomeTabs(): CommonResponse<ZoneHomeTabsPayload> =
        client.get("api/zone/home-tabs").bodyAsJsonRef(json)

/**
     * 获取专区详情资料 (api/zone/info, GET)
     */
    suspend fun getZoneInfo(
        nodeId: Long
    ): CommonResponse<ZoneInfoPayload> = client.get("api/zone/info") {
        url {
            parameters.append("nodeId", nodeId.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取专区分类关联实体条目列表 (api/zone/items, GET)
     * itemType: company(厂商) / series(系列) / works(作品) / charactar(角色) / person(人物)
     */
    suspend fun getZoneItems(
        nodeId: Long,
        itemType: String,
        category: Int = 0,
        page: Int = 1,
        pageSize: Int = 20
    ): CommonResponse<ZoneItemsPayload> = client.get("api/zone/items") {
        url {
            parameters.append("nodeId", nodeId.toString())
            parameters.append("itemType", itemType.toString())
            parameters.append("category", category.toString())
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 检索二手交易列表 (api/resale/search, POST Form)
     * 支持 type(1出售/2求购), zones, keyword, category, puritys, regionNodeId, lastTime, pageSize
     */
    suspend fun searchResale(
        params: Map<String, String>
    ): CommonResponse<ResaleSearchPayload> = client.post("api/resale/search") {
        setBody(FormDataContent(Parameters.build {
            appendAll(toParameters(params))
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取二手交易详情 (api/resale/data/get, GET)
     *
     * @param nodeId 二手交易条目 ID (对应 ResaleSearchItem.id)
     */
    suspend fun getResaleDetail(
        nodeId: Long
    ): CommonResponse<ResaleDetailPayload> = client.get("api/resale/data/get") {
        url {
            parameters.append("nodeId", nodeId.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 关注 / 取消关注专区 (api/zone/focus, POST Form)
     */
    suspend fun toggleZoneFocus(
        nodeId: Long
    ): CommonResponse<ZoneFocusPayload> = client.post("api/zone/focus") {
        setBody(FormDataContent(Parameters.build {
            append("nodeId", nodeId.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 更新专区首页设置 (api/zone/home-tabs/settings, POST Form)
     */
    suspend fun updateZoneSettings(
        showAll: Boolean,
        sort: String,
        hiddenZoneIds: String,
        customOrder: String
    ): CommonResponse<JsonObject> = client.post("api/zone/home-tabs/settings") {
        setBody(FormDataContent(Parameters.build {
            append("showAll", showAll.toString())
            append("sort", sort.toString())
            append("hiddenZoneIds", hiddenZoneIds.toString())
            append("customOrder", customOrder.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 专区访问记录上报 (api/zone/home-tabs/visited, POST Form)
     */
    suspend fun reportZoneVisited(
        nodeId: Long
    ): CommonResponse<JsonObject> = client.post("api/zone/home-tabs/visited") {
        setBody(FormDataContent(Parameters.build {
            append("nodeId", nodeId.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 查询讨论贴列表 (api/posts/search, GET)
     */
    suspend fun searchPosts(
        zoneNodeId: Long? = null,
        forumNodeId: Long? = null,
        order: String? = null,
        page: Int = 1,
        pageSize: Int = 20,
        keyword: String? = null,
        itemType: String? = null
    ): CommonResponse<PostListPayload> = client.get("api/posts/search") {
        url {
            zoneNodeId?.let { parameters.append("zoneNodeId", it.toString()) }
            forumNodeId?.let { parameters.append("forumNodeId", it.toString()) }
            order?.let { parameters.append("order", it.toString()) }
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
            keyword?.let { parameters.append("keyword", it.toString()) }
            itemType?.let { parameters.append("itemType", it.toString()) }
        }
    }.bodyAsJsonRef(json)

/**
     * 获取帖子详情 (api/posts/info-v2, GET)
     */
    suspend fun getPostInfo(
        nodeId: Long? = null,
        itemId: Long? = null
    ): CommonResponse<PostDetailPayload> = client.get("api/posts/info-v2") {
        url {
            nodeId?.let { parameters.append("nodeId", it.toString()) }
            itemId?.let { parameters.append("itemId", it.toString()) }
        }
    }.bodyAsJsonRef(json)

/**
     * 获取帖子详情基础版 (api/posts/info, GET)
     * 支持通过 nodeId 或 itemId 查询，可用于反查 postId / nodeId 映射
     */
    suspend fun getPostInfoV1(
        nodeId: Long? = null,
        itemId: Long? = null
    ): CommonResponse<PostDetailPayload> = client.get("api/posts/info") {
        url {
            nodeId?.let { parameters.append("nodeId", it.toString()) }
            itemId?.let { parameters.append("itemId", it.toString()) }
        }
    }.bodyAsJsonRef(json)

/**
     * 获取讨论贴回帖列表 (api/discuss/get, POST Form)
     * @param order ""(最新) / "asc"(最早)
     */
    suspend fun getDiscussList(
        itemNodeId: Long,
        order: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): CommonResponse<DiscussListPayload> = client.post("api/discuss/get") {
        setBody(FormDataContent(Parameters.build {
            append("itemNodeId", itemNodeId.toString())
            order?.let { append("order", it.toString()) }
            append("page", page.toString())
            append("pageSize", pageSize.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取讨论贴楼中楼回复列表 (api/discuss/reply/get, POST Form)
     */
    suspend fun getDiscussReplies(
        discussNodeId: Long,
        page: Int = 1
    ): CommonResponse<DiscussReplyListPayload> = client.post("api/discuss/reply/get") {
        setBody(FormDataContent(Parameters.build {
            append("discussNodeId", discussNodeId.toString())
            append("page", page.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 发表讨论贴主楼回帖 (api/discuss/add, POST Form)
     */
    suspend fun addDiscuss(
        itemNodeId: Long,
        content: String,
        emoticon: String? = null
    ): CommonResponse<JsonObject> = client.post("api/discuss/add") {
        setBody(FormDataContent(Parameters.build {
            append("itemNodeId", itemNodeId.toString())
            append("content", content.toString())
            emoticon?.let { append("emoticon", it.toString()) }
        }))
    }.bodyAsJsonRef(json)

/**
     * 发表讨论贴楼中楼回复 (api/discuss/reply/add, POST Form)
     */
    suspend fun addDiscussReply(
        itemNodeId: Long,
        discussNodeId: Long,
        content: String,
        toUserNodeId: Long? = null,
        toReplyNodeId: Long? = null,
        emoticon: String? = null
    ): CommonResponse<JsonObject> = client.post("api/discuss/reply/add") {
        setBody(FormDataContent(Parameters.build {
            append("itemNodeId", itemNodeId.toString())
            append("discussNodeId", discussNodeId.toString())
            append("content", content.toString())
            toUserNodeId?.let { append("toUserNodeId", it.toString()) }
            toReplyNodeId?.let { append("toReplyNodeId", it.toString()) }
            emoticon?.let { append("emoticon", it.toString()) }
        }))
    }.bodyAsJsonRef(json)

/**
     * 删除讨论回帖或楼中楼 (api/discuss/del, POST Form)
     * @param type "discuss" 或 "discuss_reply"
     */
    suspend fun deleteDiscuss(
        nodeId: Long,
        type: String
    ): CommonResponse<JsonObject> = client.post("api/discuss/del") {
        setBody(FormDataContent(Parameters.build {
            append("nodeId", nodeId.toString())
            append("type", type.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 删除讨论贴自身 (api/posts/del, POST Form)
     */
    suspend fun deletePost(
        nodeId: Long
    ): CommonResponse<JsonObject> = client.post("api/posts/del") {
        setBody(FormDataContent(Parameters.build {
            append("nodeId", nodeId.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取已关注版块列表 (api/forum/focus/list, POST Form)
     */
    suspend fun getFocusForumList(
        forumPage: Int = 1
    ): CommonResponse<ForumListPayload> = client.post("api/forum/focus/list") {
        setBody(FormDataContent(Parameters.build {
            append("forumPage", forumPage.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取未关注版块列表 (api/forum/not/focus/list, POST Form)
     */
    suspend fun getUnfocusForumList(
        forumPage: Int = 1
    ): CommonResponse<ForumListPayload> = client.post("api/forum/not/focus/list") {
        setBody(FormDataContent(Parameters.build {
            append("forumPage", forumPage.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 关注或取关版块 (api/forum/focus, POST Form)
     * @param state 1=关注，0=取关
     */
    suspend fun toggleForumFocus(
        forumNodeId: Long,
        state: Int
    ): CommonResponse<JsonObject> = client.post("api/forum/focus") {
        setBody(FormDataContent(Parameters.build {
            append("forumNodeId", forumNodeId.toString())
            append("state", state.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 保存已关注版块排序 (api/forum/focus/sort/upd, POST Form)
     * @param order 逗号分隔的版块 id 串
     */
    suspend fun updateForumFocusOrder(
        order: String
    ): CommonResponse<JsonObject> = client.post("api/forum/focus/sort/upd") {
        setBody(FormDataContent(Parameters.build {
            append("order", order.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取版块详情 (api/forum/{id}, GET)
     */
    suspend fun getForumDetail(
        forumId: Long
    ): CommonResponse<ForumDetailPayload> = client.get("api/forum/${forumId}") {
    }.bodyAsJsonRef(json)

/**
     * 获取相册频道列表 (api/album/list, POST Form)
     * order: top(推荐) / updTime(最新) / praise(TOP赞) / care(关注)，排行榜人气榜追加 releaseStart/releaseEnd 窗口。
     */
    suspend fun getAlbumList(
        page: Int = 1,
        pageSize: Int = 24,
        order: String? = null,
        zones: String? = null,
        category: Int? = null,
        releaseStart: String? = null,
        releaseEnd: String? = null
    ): CommonResponse<AlbumListPayload> = client.post("api/album/list") {
        setBody(FormDataContent(Parameters.build {
            append("page", page.toString())
            append("pageSize", pageSize.toString())
            order?.let { append("order", it.toString()) }
            zones?.let { append("zones", it.toString()) }
            category?.let { append("category", it.toString()) }
            releaseStart?.let { append("releaseStart", it.toString()) }
            releaseEnd?.let { append("releaseEnd", it.toString()) }
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取点赞榜 (api/item/praise/top, GET)
     */
    suspend fun getPraiseTop(
        type: String = "month",
        page: Int = 1,
        pageSize: Int = 10
    ): CommonResponse<PraiseTopPayload> = client.get("api/item/praise/top") {
        url {
            parameters.append("type", type.toString())
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取用户相册列表 (api/hobby/album, POST Form)
     */
    suspend fun getHobbyAlbum(
        nodeId: Long,
        itemType: String,
        page: Int = 1,
        pageSize: Int = 6,
        order: String = "hits"
    ): CommonResponse<HobbyAlbumPayload> = client.post("api/hobby/album") {
        setBody(FormDataContent(Parameters.build {
            append("id", nodeId.toString())
            append("itemType", itemType.toString())
            append("page", page.toString())
            append("pageSize", pageSize.toString())
            append("order", order.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取评论列表 (api/comment/get, POST Form)
     * type 为 null 时拉取最新评论，"hot_top" 拉取热门精选神评。
     */
    suspend fun getComments(
        nodeId: Long,
        type: String? = null,
        page: Int = 1,
        pageSize: Int = 10
    ): CommonResponse<CommentPayload> = client.post("api/comment/get") {
        setBody(FormDataContent(Parameters.build {
            append("id", nodeId.toString())
            type?.let { append("type", it.toString()) }
            append("page", page.toString())
            append("pageSize", pageSize.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取单条主评论的子评论详情（评论详情页，api/comment/getreply, POST Form）
     * 返回主楼快照 comment 与子评论分页列表 list。
     */
    suspend fun getCommentReplies(
        commentId: Long,
        page: Int = 1,
        pageSize: Int = 10
    ): CommonResponse<CommentReplyPayload> = client.post("api/comment/getreply") {
        setBody(FormDataContent(Parameters.build {
            append("id", commentId.toString())
            append("page", page.toString())
            append("pageSize", pageSize.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 主评论点赞（api/item/praise/add, POST Form）
     * @param relateNodeId 条目节点 id（详情页 actualNodeId）
     */
    suspend fun praiseCommentAdd(
        commentId: Long,
        relateNodeId: Long,
        itemType: String = "comment"
    ): CommonResponse<JsonObject> = client.post("api/item/praise/add") {
        setBody(FormDataContent(Parameters.build {
            append("id", commentId.toString())
            append("relateItemNodeId", relateNodeId.toString())
            append("itemType", itemType.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 取消主评论点赞（api/item/praise/del, POST Form）
     */
    suspend fun praiseCommentDel(
        commentId: Long,
        relateNodeId: Long,
        itemType: String = "comment"
    ): CommonResponse<JsonObject> = client.post("api/item/praise/del") {
        setBody(FormDataContent(Parameters.build {
            append("id", commentId.toString())
            append("relateItemNodeId", relateNodeId.toString())
            append("itemType", itemType.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 查询相册/条目点赞状态 (api/item/likeInfo, POST Form)
     */
    suspend fun getAlbumLikeInfo(
        nodeId: Long,
        itemType: String = "album"
    ): CommonResponse<ItemLikeInfoPayload> = client.post("api/item/likeInfo") {
        setBody(FormDataContent(Parameters.build {
            append("nodeId", nodeId.toString())
            append("itemType", itemType.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 相册点赞 (api/item/praise/add, POST Form)
     */
    suspend fun praiseAlbumAdd(
        id: Long,
        itemType: String = "album"
    ): CommonResponse<JsonObject> = client.post("api/item/praise/add") {
        setBody(FormDataContent(Parameters.build {
            append("id", id.toString())
            append("itemType", itemType.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 取消相册点赞 (api/item/praise/del, POST Form)
     */
    suspend fun praiseAlbumDel(
        id: Long,
        itemType: String = "album"
    ): CommonResponse<JsonObject> = client.post("api/item/praise/del") {
        setBody(FormDataContent(Parameters.build {
            append("id", id.toString())
            append("itemType", itemType.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 发表主楼评论（api/comment/add, POST Form，原版成功后直接刷新列表）。
     * @param nodeId 条目节点 id（详情页 actualNodeId）。
     */
    suspend fun addComment(
        nodeId: Long,
        content: String,
        emoticon: String? = null
    ): CommonResponse<JsonObject> = client.post("api/comment/add") {
        setBody(FormDataContent(Parameters.build {
            append("id", nodeId.toString())
            append("content", content.toString())
            emoticon?.let { append("emoticon", it.toString()) }
        }))
    }.bodyAsJsonRef(json)

/**
     * 发表子评论回复（api/comment/add/reply, POST Form）
     * @param toUserId 被回复人 user.id（直接回复主楼时为空）
     * @param toReplyId 被回复子评论 id（直接回复主楼时为空）
     */
    suspend fun addCommentReply(
        commentId: Long,
        content: String,
        toUserId: Long? = null,
        toReplyId: Long? = null,
        emoticon: String? = null
    ): CommonResponse<CommentReplyAddPayload> = client.post("api/comment/add/reply") {
        setBody(FormDataContent(Parameters.build {
            append("commentId", commentId.toString())
            append("content", content.toString())
            toUserId?.let { append("toUserId", it.toString()) }
            toReplyId?.let { append("toReply", it.toString()) }
            emoticon?.let { append("emoticon", it.toString()) }
        }))
    }.bodyAsJsonRef(json)

    suspend fun deleteComment(
        nodeId: Long,
        type: String
    ): CommonResponse<JsonObject> = client.post("api/comment/del-v2") {
        setBody(FormDataContent(Parameters.build {
            append("nodeId", nodeId.toString())
            append("type", type.toString())
        }))
    }.bodyAsJsonRef(json)

    suspend fun getStickerPacks(): CommonResponse<StickerPackPayload> =
        client.get("api/sticker/pack/list").bodyAsJsonRef(json)

    suspend fun getStickerBuyTime(): CommonResponse<StickerBuyTimePayload> =
        client.get("api/sticker/pack/buyTime").bodyAsJsonRef(json)

/**
     * 获取当前登录用户信息 (api/user/self, POST Form)
     */
    suspend fun getUserSelf(): CommonResponse<UserSelfPayload> =
        client.post("api/user/self").bodyAsJsonRef(json)

/**
     * 获取用户社交统计 (api/user/fellow/count, POST Form)
     */
    suspend fun getFellowCount(
        userNodeId: Long
    ): CommonResponse<FellowCountPayload> = client.post("api/user/fellow/count") {
        setBody(FormDataContent(Parameters.build {
            append("userNodeId", userNodeId.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 查询用户关注关系类型 (api/user/fellow/type, POST Form)
     * @param userNId 目标作者用户 ID
     */
    suspend fun getFellowType(
        userNId: Long
    ): CommonResponse<FellowTypePayload> = client.post("api/user/fellow/type") {
        setBody(FormDataContent(Parameters.build {
            append("userNId", userNId.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 更新用户好友/关注状态 (api/user/friend/upd, POST Form)
     * @param userId 目标用户 ID
     * @param state 0=关注, 5=取消关注
     */
    suspend fun updateFriend(
        userId: Long,
        state: Int
    ): CommonResponse<JsonObject> = client.post("api/user/friend/upd") {
        setBody(FormDataContent(Parameters.build {
            append("userId", userId.toString())
            append("state", state.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取用户社交列表 (关注/粉丝) (api/user/fellow, POST Form)
     * @param userId 目标用户 ID
     * @param type 0=关注列表, 1=粉丝列表
     */
    suspend fun getUserFellow(
        userId: Long,
        type: Int,
        page: Int = 1,
        pageSize: Int = 20
    ): CommonResponse<UserFellowPayload> = client.post("api/user/fellow") {
        setBody(FormDataContent(Parameters.build {
            append("id", userId.toString())
            append("type", type.toString())
            append("page", page.toString())
            append("pageSize", pageSize.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取用户获赞条目列表 (api/item/praise/forUser, POST Form)
     * @param userId 目标用户 ID
     */
    suspend fun getUserPraiseForUser(
        userId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): CommonResponse<UserPraisePayload> = client.post("api/item/praise/forUser") {
        setBody(FormDataContent(Parameters.build {
            append("userNId", userId.toString())
            append("page", page.toString())
            append("pageSize", pageSize.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 更新用户基本资料 (api/user/base/upd, POST Form)
     * @param key 修改字段项: "nickname" / "sign" / "intro" / "sex" / "birth" / "city"
     * @param value 修改后的取值
     */
    suspend fun updateUserBase(
        key: String,
        value: String
    ): CommonResponse<JsonObject> = client.post("api/user/base/upd") {
        setBody(FormDataContent(Parameters.build {
            append("key", key.toString())
            append("val", value.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 查询指定用户的资料详情 (api/user/getinfo, POST Form)
     * 支持以用户主节点 ID (id) 或账号 ID (userId) 查询
     */
    suspend fun getUserInfo(
        id: Long? = null,
        userId: Long? = null
    ): CommonResponse<UserSelfPayload> = client.post("api/user/getinfo") {
        setBody(FormDataContent(Parameters.build {
            id?.let { append("id", it.toString()) }
            userId?.let { append("userId", it.toString()) }
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取用户订单统计 (api/user/orderStatic, POST Form)
     */
    suspend fun getOrderStatic(
        userId: Long
    ): CommonResponse<OrderStaticPayload> = client.post("api/user/orderStatic") {
        setBody(FormDataContent(Parameters.build {
            append("id", userId.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取用户收藏相似度 (api/user/collect/similarity, POST Form)
     * @param userNodeId 目标用户节点 ID
     */
    suspend fun getCollectSimilarity(
        userNodeId: Long
    ): CommonResponse<CollectSimilarityPayload> = client.post("api/user/collect/similarity") {
        setBody(FormDataContent(Parameters.build {
            append("userNodeId", userNodeId.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取用户个人动态列表 (api/user-action/user/list, GET)
     * @param userNodeId 用户节点 ID
     * @param pageSize 每页条数
     * @param lastTime 分页时间戳偏移（首次传 0）
     */
    suspend fun getUserActionList(
        userNodeId: Long,
        pageSize: Int = 20,
        lastTime: Long = 0L
    ): CommonResponse<ActionListData> = client.get("api/user-action/user/list") {
        url {
            parameters.append("userNodeId", userNodeId.toString())
            parameters.append("pageSize", pageSize.toString())
            parameters.append("lastTime", lastTime.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取用户收藏封面展示条目 (api/user/collect/top, POST Form)
     * state: "preorder" (预定) / "buy" (已入) / "care" (想要)
     */
    suspend fun getCollectTop(
        userId: Long,
        state: String
    ): CommonResponse<CollectTopPayload> = client.post("api/user/collect/top") {
        setBody(FormDataContent(Parameters.build {
            append("id", userId.toString())
            append("state", state.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取用户自己发布的相册列表 (api/album/user-v2, GET)
     */
    suspend fun getUserAlbumList(
        userNodeId: Long,
        page: Int = 1,
        pageSize: Int = 6,
        type: String = "upload",
        order: String = "addTime"
    ): CommonResponse<UserCollectAlbumPayload> = client.get("api/album/user-v2") {
        url {
            parameters.append("userNodeId", userNodeId.toString())
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
            parameters.append("type", type.toString())
            parameters.append("order", order.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取用户发布的图片列表 (api/pic/user, GET)
     */
    suspend fun getUserPicList(
        userNodeId: Long,
        page: Int = 1,
        pageSize: Int = 20,
        collectState: String = "upload",
        order: String = "addTime"
    ): CommonResponse<UserCollectPicPayload> = client.get("api/pic/user") {
        url {
            parameters.append("userNodeId", userNodeId.toString())
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
            parameters.append("collectState", collectState.toString())
            parameters.append("order", order.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取每日签到状态 (api/mp/task/checkIn/get, POST Form)
     */
    suspend fun getCheckInStatus(): CommonResponse<CheckInStatusPayload> =
        client.post("api/mp/task/checkIn/get").bodyAsJsonRef(json)

/**
     * 执行每日签到打卡 (api/mp/task/checkIn, POST Form)
     */
    suspend fun checkIn(): CommonResponse<JsonObject> =
        client.post("api/mp/task/checkIn").bodyAsJsonRef(json)

/**
     * 获取用户魔力值/积分 (api/mp/get, POST Form)
     */
    suspend fun getUserMp(): CommonResponse<UserMpPayload> =
        client.post("api/mp/get").bodyAsJsonRef(json)

/**
     * 查询用户全量手办收藏列表 (api/collect/hobby-v2, GET)
     */
    suspend fun getUserCollectHobby(
        params: Map<String, String>
    ): CommonResponse<UserCollectHobbyPayload> = client.get("api/collect/hobby-v2") {
        url {
            parameters.appendAll(toParameters(params))
        }
    }.bodyAsJsonRef(json)

/**
     * 查询用户通用实体收藏列表 (厂商/系列/作品/角色/人物) (api/collect/common-v3, GET)
     */
    suspend fun getUserCollectCommon(
        params: Map<String, String>
    ): CommonResponse<UserCollectCommonPayload> = client.get("api/collect/common-v3") {
        url {
            parameters.appendAll(toParameters(params))
        }
    }.bodyAsJsonRef(json)

/**
     * 查询用户相册收藏列表 (api/collect/album-v2, GET)
     */
    suspend fun getUserCollectAlbum(
        params: Map<String, String>
    ): CommonResponse<UserCollectAlbumPayload> = client.get("api/collect/album-v2") {
        url {
            parameters.appendAll(toParameters(params))
        }
    }.bodyAsJsonRef(json)

/**
     * 查询用户图片收藏列表 (api/collect/pic-v2, GET)
     */
    suspend fun getUserCollectPic(
        params: Map<String, String>
    ): CommonResponse<UserCollectPicPayload> = client.get("api/collect/pic-v2") {
        url {
            parameters.appendAll(toParameters(params))
        }
    }.bodyAsJsonRef(json)

/**
     * 用户账号密码登录 (api/user/loginV2, POST Form)
     */
    suspend fun login(
        account: String,
        password: String,
        loginType: String = "6"
    ): CommonResponse<LoginData> = client.post("api/user/loginV2") {
        setBody(FormDataContent(Parameters.build {
            append("account", account.toString())
            append("password", password.toString())
            append("loginType", loginType.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 全局主搜 (api/search, POST Form)
     * category 遇周边/相册/要素细分类直接覆盖传入，不再使用独立 hobbyCategory 参数
     * 支持 category, itemType, keyword, page, pageSize, order, company, releaseYear, attribute 等
     */
    suspend fun search(
        params: Map<String, String>
    ): CommonResponse<SearchResponsePayload> = client.post("api/search") {
        setBody(FormDataContent(Parameters.build {
            appendAll(toParameters(params))
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取分类热词 (api/search/hotword, POST Form)
     */
    suspend fun getHotwords(
        category: Int
    ): CommonResponse<HotwordPayload> = client.post("api/search/hotword") {
        setBody(FormDataContent(Parameters.build {
            append("category", category.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 输入联想 (api/search/auto/fill, POST Form)
     */
    suspend fun getAutoFill(
        type: Int,
        keywords: String
    ): CommonResponse<AutoFillPayload> = client.post("api/search/auto/fill") {
        setBody(FormDataContent(Parameters.build {
            append("type", type.toString())
            append("keywords", keywords.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 用户搜索 (api/search/user, POST Form)
     */
    suspend fun searchUsers(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 10
    ): CommonResponse<UserSearchPayload> = client.post("api/search/user") {
        setBody(FormDataContent(Parameters.build {
            append("keyword", keyword.toString())
            append("page", page.toString())
            append("pageSize", pageSize.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 要素属性列表 (api/charactar/attribute/list, GET)
     * 50000 本级返回空，进 40100 等细类才有数据
     */
    suspend fun getElementAttributes(
        categoryId: Int
    ): CommonResponse<AttributeListPayload> = client.get("api/charactar/attribute/list") {
        url {
            parameters.append("categoryId", categoryId.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取厂商主体详情 (api/company/v2/get, GET)
     * 注意：必须传入 companyId (即 itemId)
     */
    suspend fun getCompanyDetail(
        companyId: Long
    ): CommonResponse<CompanyDetailPayload> = client.get("api/company/v2/get") {
        url {
            parameters.append("companyId", companyId.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取厂商最新动态列表 (api/user-action/company/list, GET)
     */
    suspend fun getCompanyActions(
        companyNodeId: Long,
        subType: String? = null,
        category: Int = 0,
        lastId: Long = 0L
    ): CommonResponse<CompanyActionPayload> = client.get("api/user-action/company/list") {
        url {
            parameters.append("companyNodeId", companyNodeId.toString())
            subType?.let { parameters.append("subType", it.toString()) }
            parameters.append("category", category.toString())
            parameters.append("lastId", lastId.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取厂商旗下系列列表 (api/company/series, GET)
     */
    suspend fun getCompanySeries(
        companyNodeId: Long,
        page: Int = 1,
        pageSize: Int = 9
    ): CommonResponse<CompanySeriesPayload> = client.get("api/company/series") {
        url {
            parameters.append("companyNodeId", companyNodeId.toString())
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取厂商官方/品牌标签列表 (api/company/tag/display, GET)
     */
    suspend fun getCompanyTags(
        companyId: Long
    ): CommonResponse<CompanyTagPayload> = client.get("api/company/tag/display") {
        url {
            parameters.append("companyId", companyId.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取当前用户的厂商印象/投票状态及所有候选标签 (api/company/tag/myVotes, GET)
     */
    suspend fun getCompanyMyVotes(
        companyId: Long
    ): CommonResponse<CompanyMyVotesData> = client.get("api/company/tag/myVotes") {
        url {
            parameters.append("companyId", companyId.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 提交厂商印象投票 (api/company/tag/vote, POST Form)
     */
    suspend fun submitCompanyTagVote(
        companyId: Long,
        normalTagIds: String,
        scoreTagIds: String
    ): CommonResponse<Unit> = client.post("api/company/tag/vote") {
        setBody(FormDataContent(Parameters.build {
            append("companyId", companyId.toString())
            append("normalTagIds", normalTagIds.toString())
            append("scoreTagIds", scoreTagIds.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 查询条目收藏状态 (api/collect/state, POST Form)
     */
    suspend fun getCollectState(
        id: Long
    ): CommonResponse<CollectStatePayload> = client.post("api/collect/state") {
        setBody(FormDataContent(Parameters.build {
            append("id", id.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 添加条目/相册收藏 (api/collect/item/add, POST Form)
     */
    suspend fun collectItemAdd(
        id: Long
    ): CommonResponse<JsonObject> = client.post("api/collect/item/add") {
        setBody(FormDataContent(Parameters.build {
            append("id", id.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 取消条目/相册收藏 (api/collect/del, POST Form)
     */
    suspend fun collectItemDel(
        id: Long
    ): CommonResponse<JsonObject> = client.post("api/collect/del") {
        setBody(FormDataContent(Parameters.build {
            append("id", id.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 批量取消收藏 (api/collect/batch/cancel, POST Form)
     * idArr 为**外层收藏节点 id** 拼成的 JSON 数组字符串，如 "[1,2,3]"。
     * 原版常量 i.R，仅登录用户本人（isSelf）的收藏中心可用。
     */
    suspend fun cancelCollectBatch(
        idArr: String
    ): CommonResponse<JsonObject> = client.post("api/collect/batch/cancel") {
        setBody(FormDataContent(Parameters.build {
            append("idArr", idArr.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取作品收藏候选标签池 (api/user/glean/textTag/get, GET)
     * ⚠️ 与手办标签池 api/collect/tag **不是同一套**，作品页标签筛选必须用本接口。
     */
    suspend fun getWorksCollectTags(
        page: Int = 1,
        pageSize: Int = 20
    ): CommonResponse<JsonElement> = client.get("api/user/glean/textTag/get") {
        url {
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 查询作品收藏详情 (api/works/glean/get/{worksId}, GET)
     * 返回 JsonElement 是因为不同服务端版本存在「glean 包裹」与「铺平」两种形态，由仓储层统一兼容。
     */
    suspend fun getWorksCollect(
        worksId: Long
    ): CommonResponse<JsonElement> = client.get("api/works/glean/get/${worksId}") {
    }.bodyAsJsonRef(json)

/**
     * 保存作品收藏 (api/works/glean/add, POST Form)
     * state：1 想看 / 2 在看 / 3 看过；tagStr 为英文逗号分隔的标签串。
     */
    suspend fun saveWorksCollect(
        itemType: String = "Works",
        itemId: Long,
        tagStr: String,
        comment: String,
        state: Int
    ): CommonResponse<JsonObject> = client.post("api/works/glean/add") {
        setBody(FormDataContent(Parameters.build {
            append("itemType", itemType.toString())
            append("itemId", itemId.toString())
            append("tagStr", tagStr.toString())
            append("comment", comment.toString())
            append("state", state.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 取消作品收藏 (api/works/glean/del, POST Form)
     */
    suspend fun deleteWorksCollect(
        itemType: String = "Works",
        itemId: Long
    ): CommonResponse<JsonObject> = client.post("api/works/glean/del") {
        setBody(FormDataContent(Parameters.build {
            append("itemType", itemType.toString())
            append("itemId", itemId.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取图片关联条目（相册/手办/文章三类）(api/pic/relate, POST Form)
     */
    suspend fun getPictureRelate(
        picId: Long
    ): CommonResponse<PictureRelatePayload> = client.post("api/pic/relate") {
        setBody(FormDataContent(Parameters.build {
            append("picId", picId.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 提交举报 (api/web-report/add, POST Form)
     *
     * ⚠️ 服务端字段名**不是** `nodeId` / `content`：
     * 原版两个独立调用点（`f3.java:55` 图片举报、`ResaleReportActivity.java:125` 二手举报）
     * 一致提交 `type` / `userReason` / `objId` 三个字段，此处按原版字段名对齐。
     *
     * @param objId 被举报对象 ID（图片页传 picId）
     * @param userReason 用户填写的举报理由
     * @param type 举报对象类型：图片 picture_info / 二手 resale_item
     */
    suspend fun submitReport(
        objId: Long,
        userReason: String,
        type: String
    ): CommonResponse<JsonObject> = client.post("api/web-report/add") {
        setBody(FormDataContent(Parameters.build {
            append("objId", objId.toString())
            append("userReason", userReason.toString())
            append("type", type.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取条目收藏分类统计人数 (api/hobby/collectCount, POST Form)
     */
    suspend fun getCollectCount(
        id: Long
    ): CommonResponse<CollectCountPayload> = client.post("api/hobby/collectCount") {
        setBody(FormDataContent(Parameters.build {
            append("id", id.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取候选推荐标签列表 (api/collect/tag, GET)
     */
    suspend fun getCollectTags(
        page: Int = 1,
        pageSize: Int = 20,
        categoryId: Int = 10000
    ): CommonResponse<CollectTagsPayload> = client.get("api/collect/tag") {
        url {
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
            parameters.append("categoryId", categoryId.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 保存/更新/取消条目收藏信息 (api/collect/hobby/upd, POST Form)
     */
    suspend fun updateCollect(
        nodeId: Long,
        json: String
    ): CommonResponse<JsonObject> = client.post("api/collect/hobby/upd") {
        setBody(FormDataContent(Parameters.build {
            append("nodeId", nodeId.toString())
            append("json", json.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 查询条目屏蔽状态 (api/mask/state-v2, GET)
     */
    suspend fun getMaskState(
        itemNodeId: Long
    ): CommonResponse<JsonElement> = client.get("api/mask/state-v2") {
        url {
            parameters.append("itemNodeId", itemNodeId.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取系列主体详情 (api/item/get-v2, GET)
     */
    suspend fun getSeriesDetail(
        itemId: Long,
        itemType: String = "series"
    ): CommonResponse<SeriesDetailPayload> = client.get("api/item/get-v2") {
        url {
            parameters.append("itemId", itemId.toString())
            parameters.append("itemType", itemType.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取系列关联手办列表 (api/item/relate/hobby, POST Form)
     */
    suspend fun getSeriesHobbys(
        id: Long,
        itemType: String = "series",
        pageSize: Int = 9
    ): CommonResponse<RelatedHobbyPayload> = client.post("api/item/relate/hobby") {
        setBody(FormDataContent(Parameters.build {
            append("id", id.toString())
            append("itemType", itemType.toString())
            append("pageSize", pageSize.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取系列子系列或相关系列列表 (api/series/link/series, GET)
     * isChild: 1=子系列, 2=相关系列
     */
    suspend fun getSeriesLinkSeries(
        seriesId: Long,
        isChild: Int,
        page: Int = 1,
        pageSize: Int = 6
    ): CommonResponse<SeriesLinkPayload> = client.get("api/series/link/series") {
        url {
            parameters.append("seriesId", seriesId.toString())
            parameters.append("isChild", isChild.toString())
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取系列关联相册列表 (api/series/album, GET)
     */
    suspend fun getSeriesAlbums(
        seriesId: Long,
        page: Int = 1,
        pageSize: Int = 6
    ): CommonResponse<SeriesAlbumPayload> = client.get("api/series/album") {
        url {
            parameters.append("seriesId", seriesId.toString())
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取系列在售周边推荐列表 (api/goods/tb/more-v2, GET)
     */
    suspend fun getSeriesGoods(
        nodeId: Long,
        page: Int = 1,
        pageSize: Int = 10
    ): CommonResponse<SeriesGoodsPayload> = client.get("api/goods/tb/more-v2") {
        url {
            parameters.append("nodeId", nodeId.toString())
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取系列论坛与帖子 (api/series/forum, GET)
     */
    suspend fun getSeriesForum(
        seriesId: Long
    ): CommonResponse<SeriesForumPayload> = client.get("api/series/forum") {
        url {
            parameters.append("seriesId", seriesId.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取人物主体详情 (api/person/get, GET)
     */
    suspend fun getPersonDetail(
        personId: Long
    ): CommonResponse<PersonDetailPayload> = client.get("api/person/get") {
        url {
            parameters.append("personId", personId.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取人物参与作品列表 (api/person/works/get, GET)
     */
    suspend fun getPersonWorks(
        personId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): CommonResponse<PersonWorksPayload> = client.get("api/person/works/get") {
        url {
            parameters.append("personId", personId.toString())
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取人物出演角色列表 (api/person/character/get, GET)
     */
    suspend fun getPersonCharacter(
        personId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): CommonResponse<PersonCharacterPayload> = client.get("api/person/character/get") {
        url {
            parameters.append("personId", personId.toString())
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取人物参与周边列表 (api/person/hobby/get, GET)
     */
    suspend fun getPersonHobby(
        personId: Long
    ): CommonResponse<PersonHobbyPayload> = client.get("api/person/hobby/get") {
        url {
            parameters.append("personId", personId.toString())
        }
    }.bodyAsJsonRef(json)

    suspend fun getWorksDetail(
        worksId: Long
    ): CommonResponse<WorksDetailPayload> = client.get("api/works/get") {
        url {
            parameters.append("worksId", worksId.toString())
        }
    }.bodyAsJsonRef(json)

    suspend fun getWorksAlbums(
        worksId: Long,
        page: Int = 1,
        pageSize: Int = 10
    ): CommonResponse<WorksAlbumPayload> = client.get("api/works/album/get") {
        url {
            parameters.append("worksId", worksId.toString())
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
        }
    }.bodyAsJsonRef(json)

    suspend fun getWorksCharacters(
        worksId: Long
    ): CommonResponse<WorksCharacterPayload> = client.get("api/works/character/home/get") {
        url {
            parameters.append("worksId", worksId.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取角色/要素主体详情 (api/charactar/get, GET)
     */
    suspend fun getCharacterDetail(
        characterId: Long
    ): CommonResponse<CharacterDetailPayload> = client.get("api/charactar/get") {
        url {
            parameters.append("charactarId", characterId.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取角色登场作品列表 (api/charactar/works/get, GET)
     */
    suspend fun getCharacterWorks(
        characterId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): CommonResponse<CharacterWorksPayload> = client.get("api/charactar/works/get") {
        url {
            parameters.append("charactarId", characterId.toString())
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取角色相关相册列表 (api/charactar/album/get, GET)
     */
    suspend fun getCharacterAlbums(
        characterId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): CommonResponse<CharacterAlbumPayload> = client.get("api/charactar/album/get") {
        url {
            parameters.append("charactarId", characterId.toString())
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取推荐要素/相关角色列表 (api/charactar/relevant/get, GET)
     */
    suspend fun getCharacterRelevant(
        characterId: Long,
        pageSize: Int = 10
    ): CommonResponse<CharacterRelevantPayload> = client.get("api/charactar/relevant/get") {
        url {
            parameters.append("charactarId", characterId.toString())
            parameters.append("pageSize", pageSize.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取条目关联的分区/属性标签 (api/zone/item, GET)
     */
    suspend fun getItemZones(
        nodeId: Long
    ): CommonResponse<ZoneItemPayload> = client.get("api/zone/item") {
        url {
            parameters.append("nodeId", nodeId.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取用户浏览历史列表 (api/user/read/item/get, POST Form)
     */
    suspend fun getUserHistoryList(
        itemType: String,
        page: Int = 1
    ): CommonResponse<UserHistoryPayload> = client.post("api/user/read/item/get") {
        setBody(FormDataContent(Parameters.build {
            append("itemType", itemType.toString())
            append("page", page.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 上报/添加浏览历史 (api/user/read/item/add, POST Form)
     */
    suspend fun addUserHistory(
        itemNodeId: Long
    ): CommonResponse<JsonElement> = client.post("api/user/read/item/add") {
        setBody(FormDataContent(Parameters.build {
            append("itemNodeId", itemNodeId.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 批量删除用户浏览历史 (api/user/read/item/del, POST Form)
     * @param ids 格式为 JSON 数组字符串，如 "[101,102]"
     */
    suspend fun deleteUserHistory(
        ids: String
    ): CommonResponse<JsonElement> = client.post("api/user/read/item/del") {
        setBody(FormDataContent(Parameters.build {
            append("ids", ids.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取未读通知消息数 (api/unread/push-message/count-v2, GET)
     */
    suspend fun getUnreadPushCount(): CommonResponse<UnreadPushCountPayload> =
        client.get("api/unread/push-message/count-v2").bodyAsJsonRef(json)

/**
     * 获取私信联系人列表 (api/message/contact/get, POST Form)
     */
    suspend fun getMessageContacts(
        page: Int = 1
    ): CommonResponse<MessageContactPayload> = client.post("api/message/contact/get") {
        setBody(FormDataContent(Parameters.build {
            append("page", page.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 更新私信联系人置顶状态 (api/message/contact/top/upd, POST Form)
     * @param state 1: 置顶, 0: 取消置顶
     */
    suspend fun updateContactTop(
        id: Long,
        state: Int
    ): CommonResponse<JsonElement> = client.post("api/message/contact/top/upd") {
        setBody(FormDataContent(Parameters.build {
            append("id", id.toString())
            append("state", state.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 删除私信联系人会话 (api/message/contact/del, POST Form)
     */
    suspend fun deleteContact(
        id: Long,
        userNodeId: Long
    ): CommonResponse<JsonElement> = client.post("api/message/contact/del") {
        setBody(FormDataContent(Parameters.build {
            append("id", id.toString())
            append("userNodeId", userNodeId.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取分类推送通知列表 (api/push/{type}, POST Form)
     * @param type "system", "reply", "praise", "recommend", "follow", "commodity"
     */
    suspend fun getPushMessages(
        type: String,
        page: Int = 1,
        pageSize: Int = 20
    ): CommonResponse<PushMessagePayload> = client.post("api/push/${type}") {
        setBody(FormDataContent(Parameters.build {
            append("page", page.toString())
            append("pageSize", pageSize.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 标记推送通知已读 (api/push/read, POST Form)
     * @param type 消息分类数值 (1:系统, 2:回复, 3:赞, 4:推荐, 5:关注, 6:商品)
     * @param messageId 单条已读时传入消息 ID；为 null 时表示该分类全部已读
     */
    suspend fun markPushRead(
        type: Int,
        messageId: Long? = null
    ): CommonResponse<JsonElement> = client.post("api/push/read") {
        setBody(FormDataContent(Parameters.build {
            append("type", type.toString())
            messageId?.let { append("messageId", it.toString()) }
        }))
    }.bodyAsJsonRef(json)

/**
     * 删除单条推送通知 (api/push/del, POST Form)
     */
    suspend fun deletePushMessage(
        messageId: Long,
        pushType: Int
    ): CommonResponse<JsonElement> = client.post("api/push/del") {
        setBody(FormDataContent(Parameters.build {
            append("messageId", messageId.toString())
            append("pushType", pushType.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 批量删除推送通知 (api/push/batch/del, POST Form)
     * @param isAll 是否全部删除
     * @param ids 选中的通知 ID 数组格式字符串，例如 "[123, 456]"
     * @param read 未读数统计
     */
    suspend fun batchDeletePushMessages(
        pushType: Int,
        isAll: Boolean,
        ids: String? = null,
        read: Int? = null
    ): CommonResponse<JsonElement> = client.post("api/push/batch/del") {
        setBody(FormDataContent(Parameters.build {
            append("pushType", pushType.toString())
            append("isAll", isAll.toString())
            ids?.let { append("ids", it.toString()) }
            read?.let { append("read", it.toString()) }
        }))
    }.bodyAsJsonRef(json)

/**
     * 获取与指定用户的私信聊天记录 (api/message/get-v2, GET)
     */
    suspend fun getChatMessages(
        fromId: Long,
        page: Int = 1,
        pageSize: Int = 20
    ): CommonResponse<ChatMessagePayload> = client.get("api/message/get-v2") {
        url {
            parameters.append("fromId", fromId.toString())
            parameters.append("page", page.toString())
            parameters.append("pageSize", pageSize.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 发送文本私信消息 (api/message/add-v2, POST Form)
     */
    suspend fun sendTextMessage(
        toId: Long,
        content: String,
        emoticon: String? = null
    ): CommonResponse<JsonElement> = client.post("api/message/add-v2") {
        setBody(FormDataContent(Parameters.build {
            append("toId", toId.toString())
            append("content", content.toString())
            emoticon?.let { append("emoticon", it.toString()) }
        }))
    }.bodyAsJsonRef(json)

/**
     * 发送二手/条目卡片私信 (api/message/add/resale-v2, POST Form)
     */
    suspend fun sendResaleMessage(
        toId: Long,
        resaleId: Long,
        content: String? = null
    ): CommonResponse<JsonElement> = client.post("api/message/add/resale-v2") {
        setBody(FormDataContent(Parameters.build {
            append("toId", toId.toString())
            append("resaleId", resaleId.toString())
            content?.let { append("content", it.toString()) }
        }))
    }.bodyAsJsonRef(json)

/**
     * 上传并发送私信图片 (api/message/upload-v2, Multipart POST)
     * Ktor 形态：Retrofit 侧 `(MultipartBody.Part file, RequestBody toId)`
     * 改为平台无关的 `(toId: Long, fileName: String, bytes: ByteArray)`；
     * part 名 / Content-Type / filename 与老调用点 createFormData("file", file.name, image/jpeg) 一致。
     */
    suspend fun uploadChatMessagePicture(
        toId: Long,
        fileName: String,
        bytes: ByteArray
    ): CommonResponse<JsonElement> = client.post("api/message/upload-v2") {
        setBody(MultiPartFormDataContent(formData {
            append("toId", toId.toString())
            append("file", bytes, Headers.build {
                append(HttpHeaders.ContentType, "image/jpeg")
                append(HttpHeaders.ContentDisposition, "form-data; name=\"file\"; filename=\"$fileName\"")
            })
        }))
    }.bodyAsJsonRef(json)

/**
     * 撤回私信消息 (api/message/withdraw, POST Form)
     */
    suspend fun withdrawMessage(
        messageId: Long
    ): CommonResponse<JsonElement> = client.post("api/message/withdraw") {
        setBody(FormDataContent(Parameters.build {
            append("messageId", messageId.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 查询最新私信消息 ID (api/message/latestMsgId/get, GET)
     */
    suspend fun getLatestMsgId(
        userId: Long
    ): CommonResponse<LatestMsgIdPayload> = client.get("api/message/latestMsgId/get") {
        url {
            parameters.append("userId", userId.toString())
        }
    }.bodyAsJsonRef(json)

/**
     * 获取黑名单屏蔽列表 (api/block/list, POST Form)
     */
    suspend fun getBlockList(
        page: Int = 1,
        pageSize: Int = 50
    ): CommonResponse<BlockListPayload> = client.post("api/block/list") {
        setBody(FormDataContent(Parameters.build {
            append("page", page.toString())
            append("pageSize", pageSize.toString())
        }))
    }.bodyAsJsonRef(json)

/**
     * 更新用户黑名单屏蔽状态 (api/block/upd, POST Form)
     * @param blockState 1: 屏蔽, 0: 解除屏蔽
     */
    suspend fun updateUserBlock(
        userId: Long,
        blockState: Int
    ): CommonResponse<JsonElement> = client.post("api/block/upd") {
        setBody(FormDataContent(Parameters.build {
            append("userId", userId.toString())
            append("blockState", blockState.toString())
        }))
    }.bodyAsJsonRef(json)
}
