package com.nobody.repoi.data.remote.api

import com.nobody.repoi.data.remote.model.AccountCheckPayload
import com.nobody.repoi.data.remote.model.ActionListData
import com.nobody.repoi.data.remote.model.ActionSubType
import com.nobody.repoi.data.remote.model.AlbumDetailPayload
import com.nobody.repoi.data.remote.model.AlbumListPayload
import com.nobody.repoi.data.remote.model.AttributeListPayload
import com.nobody.repoi.data.remote.model.AutoFillPayload
import com.nobody.repoi.data.remote.model.BlockListPayload
import com.nobody.repoi.data.remote.model.MaskListPayload
import com.nobody.repoi.data.remote.model.UserPrivacyPayload
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
import com.nobody.repoi.data.remote.model.GkdiyAlbumPayload
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
import com.nobody.repoi.data.remote.model.CollectTopPayload
import com.nobody.repoi.data.remote.model.OrderStaticPayload
import com.nobody.repoi.data.remote.model.OrderStaticData
import com.nobody.repoi.data.remote.model.WorksAlbumPayload
import com.nobody.repoi.data.remote.model.WorksCharacterPayload
import com.nobody.repoi.data.remote.model.WorksDetailPayload
import com.nobody.repoi.data.remote.model.ZoneCategoriesPayload
import com.nobody.repoi.data.remote.model.ZoneFocusPayload
import com.nobody.repoi.data.remote.model.ZoneHomeTabsPayload
import com.nobody.repoi.data.remote.model.ZoneInfoPayload
import com.nobody.repoi.data.remote.model.ZoneItemPayload
import com.nobody.repoi.data.remote.model.ZoneItemsPayload
import com.nobody.repoi.data.remote.plugin.decodeRefAware
import com.nobody.repoi.data.remote.model.ApiException
import com.nobody.repoi.data.remote.model.CommentNotFoundException
import com.nobody.repoi.data.remote.plugin.BypassTokenSelfHealingAttributeKey
import com.nobody.repoi.data.remote.plugin.PoiRequestTokenAttributeKey
import io.ktor.client.statement.HttpResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class HpoiApi(
    private val client: HttpClient,
    private val json: Json,
    private val onTokenExpired: suspend (triggeredToken: String) -> Unit = {},
) {

    private suspend inline fun <reified T> HttpResponse.unwrapData(
        json: Json,
        fallbackMessage: String,
    ): T {
        val envelope = decodeRefAware<CommonResponse<T>>(json)
        checkBusinessSuccess(envelope, fallbackMessage)
        return envelope.data ?: throw ApiException(envelope.code, "服务端响应数据为空")
    }

    private suspend inline fun HttpResponse.unwrapUnit(json: Json, fallbackMessage: String) {
        checkBusinessSuccess(decodeRefAware<CommonResponse<JsonElement>>(json), fallbackMessage)
    }

    private suspend inline fun <reified T> HttpResponse.unwrapDataOrNull(
        json: Json,
        fallbackMessage: String,
    ): T? {
        val envelope = decodeRefAware<CommonResponse<T>>(json)
        checkBusinessSuccess(envelope, fallbackMessage)
        return envelope.data
    }

    private suspend fun HttpResponse.checkBusinessSuccess(
        envelope: CommonResponse<*>,
        fallbackMessage: String,
    ) {
        if (envelope.isSuccess) return
        val bypass = call.request.attributes.getOrNull(BypassTokenSelfHealingAttributeKey) == true
        if (envelope.isTokenExpired && !bypass) {
            call.request.attributes.getOrNull(PoiRequestTokenAttributeKey)?.let { onTokenExpired(it) }
        }
        val message = envelope.msg ?: fallbackMessage
        if (envelope.isCommentNotFound) throw CommentNotFoundException(envelope.code, message)
        throw ApiException(envelope.code, message)
    }

    private suspend fun HttpResponse.unwrapLogin(json: Json, fallbackMessage: String): LoginData {
        val envelope = decodeRefAware<CommonResponse<LoginData>>(json)
        if (envelope.isTokenExpired) checkBusinessSuccess(envelope, fallbackMessage)
        val accepted = envelope.isSuccess ||
            (envelope.success && envelope.data?.utoken?.isNotBlank() == true)
        if (!accepted) checkBusinessSuccess(envelope, fallbackMessage)
        return envelope.data ?: throw ApiException(envelope.code, "服务器未返回登录数据")
    }

    suspend fun getActionList(
        categoryId: Int = 0,
        lastTime: Long = 0L,
        pageSize: Int = 20,
        subType: String? = ActionSubType.DEFAULT.apiValue,
        zoneNodeId: Long? = null,
        zoneIds: String? = null,
    ): ActionListData = client.get("api/user-action/list-v3") {
        parameter("categoryId", categoryId)
        parameter("lastTime", lastTime)
        parameter("pageSize", pageSize)
        if (subType != null) parameter("subType", subType)
        if (zoneNodeId != null) parameter("zoneNodeId", zoneNodeId)
        if (zoneIds != null) parameter("zoneIds", zoneIds)
    }.unwrapData(json, "数据加载失败")

    suspend fun getFollowDynamics(
        page: Int? = null,
        lastId: Long = 0L,
        pageSize: Int = 20,
        actionType: Int? = null,
        subType: String? = null,
        type: String = "item",
        isHome: Int? = null,
    ): JsonObject = client.get("api/user-action/collect/care/list-v2") {
        if (page != null) parameter("page", page)
        parameter("lastId", lastId)
        parameter("pageSize", pageSize)
        if (actionType != null) parameter("actionType", actionType)
        if (subType != null) parameter("subType", subType)
        parameter("type", type)
        if (isHome != null) parameter("isHome", isHome)
    }.unwrapData(json, "获取关注动态失败")

    suspend fun queryHobbyList(params: Map<String, String>): HobbyListData =
        client.post("api/hobby/query-v2") {
            setBody(formOf(params))
        }.unwrapData(json, "获取手办列表失败")

    suspend fun getAllCategories(): CategoryTreeData =
        client.post("api/category/web/all") {
            setBody(FormDataContent(Parameters.Empty))
        }.unwrapData(json, "分类加载失败")

    suspend fun getHobbyDetail(itemId: Long, itemType: String): HobbyDetailPayload =
        client.get("api/item/get-v2") {
            parameter("itemId", itemId)
            parameter("itemType", itemType)
        }.unwrapData(json, "获取手办详情失败")

    suspend fun getInfoList(nodeId: Long, itemType: String): InfoListPayload =
        client.post("api/item/infoList") {
            setBody(formOf("id" to nodeId, "itemType" to itemType))
        }.unwrapData(json, "获取属性列表失败")

    suspend fun getTimeline(
        nodeId: Long,
        page: Int = 1,
        pageSize: Int = 20,
    ): HobbyTimelinePayload = client.get("api/user-action/item-info/list") {
        parameter("nodeId", nodeId)
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.unwrapData(json, "获取情报时间轴失败")

    suspend fun getHobbyGroup(id: Long): HobbyGroupPayload =
        client.post("api/hobby/group") {
            setBody(formOf("id" to id))
        }.unwrapData(json, "获取手办分组失败")

    suspend fun getHobbyPushList(page: Int = 1, pageSize: Int = 10): HobbyPushListPayload =
        client.post("api/hobby/push/list/V2") {
            setBody(formOf("page" to page, "pageSize" to pageSize))
        }.unwrapData(json, "获取推荐手办失败")

    suspend fun getPicList(
        itemId: Long,
        itemType: String,
        picSubType: String? = null,
        page: Int = 1,
        pageSize: Int = 20,
        order: String? = null,
        nodeId: Long? = null,
    ): PicListPayload = client.post("api/pic/list/relate-v2") {
        setBody(
            formOf(
                "itemId" to itemId, "itemType" to itemType, "subType" to picSubType,
                "page" to page, "pageSize" to pageSize, "order" to order, "nodeId" to nodeId,
            ),
        )
    }.unwrapData(json, "获取图片列表失败")

    suspend fun getAlbumDetail(albumId: Long, itemId: Long? = null): AlbumDetailPayload =
        client.post("api/album/detail") {
            setBody(formOf("id" to albumId, "itemId" to itemId))
        }.unwrapData(json, "获取相册详情失败")

    suspend fun getRelatedAlbums(
        albumId: Long,
        page: Int = 1,
        pageSize: Int = 8,
    ): RelatedAlbumPayload = client.post("api/album/relate/album") {
        setBody(formOf("nodeId" to albumId, "page" to page, "pageSize" to pageSize))
    }.unwrapData(json, "获取相关相册失败")

    suspend fun getRelatedHobbies(
        albumId: Long,
        itemType: String = "album",
        pageSize: Int = 8,
    ): RelatedHobbyPayload = client.post("api/item/relate/hobby") {
        setBody(formOf("id" to albumId, "itemType" to itemType, "pageSize" to pageSize))
    }.unwrapData(json, "获取相关手办失败")

    suspend fun getTags(albumId: Long): ItemTagsPayload =
        client.post("api/item/tags") {
            setBody(formOf("id" to albumId))
        }.unwrapData(json, "获取相册标签失败")

    suspend fun getZoneHomeTabs(): ZoneHomeTabsPayload =
        client.get("api/zone/home-tabs").unwrapData(json, "获取专区数据失败")

    suspend fun getZoneInfo(nodeId: Long): ZoneInfoPayload =
        client.get("api/zone/info") {
            parameter("nodeId", nodeId)
        }.unwrapData(json, "获取专区详情失败")

    suspend fun getZoneCategories(
        nodeId: Long,
        itemType: String,
    ): ZoneCategoriesPayload =
        client.get("api/zone/categories") {
            parameter("nodeId", nodeId)
            parameter("itemType", itemType)
        }.unwrapData(json, "获取专区分类失败")

    suspend fun getZoneItems(
        itemType: String,
        zones: String? = null,
        category: Int = 0,
        page: Int = 1,
        pageSize: Int = 20,
    ): ZoneItemsPayload = client.get("api/zone/items") {
        if (zones != null) parameter("zones", zones)
        parameter("itemType", itemType)
        parameter("category", category)
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.unwrapData(json, "获取专区条目失败")

    suspend fun searchResale(params: Map<String, String>): ResaleSearchPayload =
        client.post("api/resale/search") {
            setBody(formOf(params))
        }.unwrapData(json, "获取二手交易列表失败")

    suspend fun getResaleDetail(nodeId: Long): ResaleDetailPayload =
        client.get("api/resale/data/get") {
            parameter("nodeId", nodeId)
        }.unwrapData(json, "获取二手交易详情失败")

    suspend fun toggleZoneFocus(nodeId: Long): ZoneFocusPayload =
        client.post("api/zone/focus") {
            setBody(formOf("nodeId" to nodeId))
        }.unwrapData(json, "关注状态切换失败")

    suspend fun updateZoneSettings(
        showAll: Boolean,
        sort: String,
        hiddenZoneIds: String,
        customOrder: String,
    ): Unit = client.post("api/zone/home-tabs/settings") {
        setBody(
            formOf(
                "showAll" to showAll, "sort" to sort,
                "hiddenZoneIds" to hiddenZoneIds, "customOrder" to customOrder,
            ),
        )
    }.unwrapUnit(json, "保存专区设置失败")

    suspend fun reportZoneVisited(nodeId: Long): Unit =
        client.post("api/zone/home-tabs/visited") {
            setBody(formOf("nodeId" to nodeId))
        }.unwrapUnit(json, "专区访问上报失败")

    suspend fun searchPosts(
        zoneNodeId: Long? = null,
        order: String? = null,
        page: Int = 1,
        pageSize: Int = 20,
        keyword: String? = null,
        itemType: String? = null,
    ): PostListPayload = client.get("api/posts/search") {
        if (zoneNodeId != null) parameter("zoneNodeId", zoneNodeId)
        if (order != null) parameter("order", order)
        parameter("page", page)
        parameter("pageSize", pageSize)
        if (keyword != null) parameter("keyword", keyword)
        if (itemType != null) parameter("itemType", itemType)
    }.unwrapData(json, "获取帖子列表失败")

    suspend fun getPostInfo(nodeId: Long? = null, itemId: Long? = null): PostDetailPayload? =
        client.get("api/posts/info-v2") {
            if (nodeId != null) parameter("nodeId", nodeId)
            if (itemId != null) parameter("itemId", itemId)
        }.unwrapDataOrNull<PostDetailPayload>(json, "获取帖子详情失败")

    suspend fun getPostInfoV1(nodeId: Long? = null, itemId: Long? = null): PostDetailPayload? =
        client.get("api/posts/info") {
            if (nodeId != null) parameter("nodeId", nodeId)
            if (itemId != null) parameter("itemId", itemId)
        }.unwrapDataOrNull<PostDetailPayload>(json, "获取帖子详情失败")

    suspend fun getDiscussList(
        itemNodeId: Long,
        order: String? = null,
        page: Int = 1,
        pageSize: Int = 20,
    ): DiscussListPayload? = client.post("api/discuss/get") {
        setBody(formOf("itemNodeId" to itemNodeId, "order" to order, "page" to page, "pageSize" to pageSize))
    }.unwrapDataOrNull<DiscussListPayload>(json, "获取讨论回帖列表失败")

    suspend fun getDiscussReplies(
        discussNodeId: Long,
        page: Int = 1,
    ): DiscussReplyListPayload? = client.post("api/discuss/reply/get") {
        setBody(formOf("discussNodeId" to discussNodeId, "page" to page))
    }.unwrapDataOrNull<DiscussReplyListPayload>(json, "获取回复失败")

    suspend fun addDiscuss(
        itemNodeId: Long,
        content: String,
        emoticon: String? = null,
    ): Unit = client.post("api/discuss/add") {
        setBody(formOf("itemNodeId" to itemNodeId, "content" to content, "emoticon" to emoticon))
    }.unwrapUnit(json, "发表回帖失败")

    suspend fun addDiscussReply(
        itemNodeId: Long,
        discussNodeId: Long,
        content: String,
        toUserNodeId: Long? = null,
        toReplyNodeId: Long? = null,
        emoticon: String? = null,
    ): Unit = client.post("api/discuss/reply/add") {
        setBody(
            formOf(
                "itemNodeId" to itemNodeId, "discussNodeId" to discussNodeId, "content" to content,
                "toUserNodeId" to toUserNodeId, "toReplyNodeId" to toReplyNodeId, "emoticon" to emoticon,
            ),
        )
    }.unwrapUnit(json, "回复发表失败")

    suspend fun deleteDiscuss(nodeId: Long, type: String): Unit =
        client.post("api/discuss/del") {
            setBody(formOf("nodeId" to nodeId, "type" to type))
        }.unwrapUnit(json, "删除评论失败")

    suspend fun deletePost(nodeId: Long): Unit =
        client.post("api/posts/del") {
            setBody(formOf("nodeId" to nodeId))
        }.unwrapUnit(json, "删除帖子失败")

    suspend fun getAlbumList(
        page: Int = 1,
        pageSize: Int = 24,
        order: String? = null,
        zones: String? = null,
        category: Int? = null,
        releaseStart: String? = null,
        releaseEnd: String? = null,
    ): AlbumListPayload = client.post("api/album/list") {
        setBody(
            formOf(
                "page" to page, "pageSize" to pageSize, "order" to order, "zones" to zones,
                "category" to category, "releaseStart" to releaseStart, "releaseEnd" to releaseEnd,
            ),
        )
    }.unwrapData(json, "获取相册列表失败")

    suspend fun getPraiseTop(
        type: String = "month",
        page: Int = 1,
        pageSize: Int = 10,
    ): PraiseTopPayload = client.get("api/item/praise/top") {
        parameter("type", type)
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.unwrapData(json, "获取点赞榜失败")

    suspend fun getHobbyAlbum(
        nodeId: Long,
        itemType: String,
        page: Int = 1,
        pageSize: Int = 6,
        order: String = "hits",
    ): HobbyAlbumPayload = client.post("api/hobby/album") {
        setBody(
            formOf(
                "id" to nodeId, "itemType" to itemType,
                "page" to page, "pageSize" to pageSize, "order" to order,
            ),
        )
    }.unwrapData(json, "获取用户相册失败")

    suspend fun getComments(
        nodeId: Long,
        type: String? = null,
        page: Int = 1,
        pageSize: Int = 10,
    ): CommentPayload = client.post("api/comment/get") {
        setBody(formOf("id" to nodeId, "type" to type, "page" to page, "pageSize" to pageSize))
    }.unwrapData(json, "获取评论失败")

    suspend fun getCommentReplies(
        commentId: Long,
        page: Int = 1,
        pageSize: Int = 10,
    ): CommentReplyPayload? = client.post("api/comment/getreply") {
        setBody(formOf("id" to commentId, "page" to page, "pageSize" to pageSize))
    }.unwrapDataOrNull<CommentReplyPayload>(json, "获取回复失败")

    suspend fun praiseCommentAdd(
        commentId: Long,
        relateNodeId: Long,
        itemType: String = "comment",
    ): Unit = client.post("api/item/praise/add") {
        setBody(formOf("id" to commentId, "relateItemNodeId" to relateNodeId, "itemType" to itemType))
    }.unwrapUnit(json, "点赞操作失败")

    suspend fun praiseCommentDel(
        commentId: Long,
        relateNodeId: Long,
        itemType: String = "comment",
    ): Unit = client.post("api/item/praise/del") {
        setBody(formOf("id" to commentId, "relateItemNodeId" to relateNodeId, "itemType" to itemType))
    }.unwrapUnit(json, "点赞操作失败")

    suspend fun getAlbumLikeInfo(
        nodeId: Long,
        itemType: String = "album",
    ): ItemLikeInfoPayload = client.post("api/item/likeInfo") {
        setBody(formOf("nodeId" to nodeId, "itemType" to itemType))
    }.unwrapData(json, "获取点赞状态失败")

    suspend fun praiseAlbumAdd(id: Long, itemType: String = "album"): Unit =
        client.post("api/item/praise/add") {
            setBody(formOf("id" to id, "itemType" to itemType))
        }.unwrapUnit(json, "相册点赞失败")

    suspend fun praiseAlbumDel(id: Long, itemType: String = "album"): Unit =
        client.post("api/item/praise/del") {
            setBody(formOf("id" to id, "itemType" to itemType))
        }.unwrapUnit(json, "取消相册点赞失败")

    suspend fun addComment(
        nodeId: Long,
        content: String,
        emoticon: String? = null,
    ): Unit = client.post("api/comment/add") {
        setBody(formOf("id" to nodeId, "content" to content, "emoticon" to emoticon))
    }.unwrapUnit(json, "评论发表失败")

    suspend fun addCommentReply(
        commentId: Long,
        content: String,
        toUserId: Long? = null,
        toReplyId: Long? = null,
        emoticon: String? = null,
    ): CommentReplyAddPayload? = client.post("api/comment/add/reply") {
        setBody(
            formOf(
                "commentId" to commentId, "content" to content, "toUserId" to toUserId,
                "toReply" to toReplyId, "emoticon" to emoticon,
            ),
        )
    }.unwrapDataOrNull<CommentReplyAddPayload>(json, "回复发表失败")

    suspend fun deleteComment(nodeId: Long, type: String): Unit =
        client.post("api/comment/del-v2") {
            setBody(formOf("nodeId" to nodeId, "type" to type))
        }.unwrapUnit(json, "删除评论失败")

    suspend fun getStickerPacks(): StickerPackPayload =
        client.get("api/sticker/pack/list").unwrapData(json, "表情包加载失败")

    suspend fun getStickerBuyTime(): StickerBuyTimePayload? =
        client.get("api/sticker/pack/buyTime").unwrapDataOrNull<StickerBuyTimePayload>(json, "获取表情包购买时间失败")

    suspend fun getUserSelf(): UserSelfPayload =
        client.post("api/user/self") {
            setBody(FormDataContent(Parameters.Empty))
        }.unwrapData(json, "获取用户信息失败")

    suspend fun getUserSelfWithToken(candidateToken: String): UserSelfPayload =
        client.post("api/user/self") {
            attributes.put(BypassTokenSelfHealingAttributeKey, true)
            setBody(formOf("utoken" to candidateToken))
        }.unwrapData(json, "Token 登录失败")

    suspend fun getFellowCount(userNodeId: Long): FellowCountPayload? =
        client.post("api/user/fellow/count") {
            setBody(formOf("userNodeId" to userNodeId))
        }.unwrapDataOrNull<FellowCountPayload>(json, "获取关注粉丝数失败")

    suspend fun getFellowType(userNId: Long): FellowTypePayload? =
        client.post("api/user/fellow/type") {
            setBody(formOf("userNId" to userNId))
        }.unwrapDataOrNull<FellowTypePayload>(json, "获取用户关注状态失败")

    suspend fun updateFriend(userId: Long, state: Int): Unit =
        client.post("api/user/friend/upd") {
            setBody(formOf("userId" to userId, "state" to state))
        }.unwrapUnit(json, "更新用户关注状态失败")

    suspend fun getUserFellow(
        userId: Long,
        type: Int,
        page: Int = 1,
        pageSize: Int = 20,
    ): UserFellowPayload? = client.post("api/user/fellow") {
        setBody(formOf("id" to userId, "type" to type, "page" to page, "pageSize" to pageSize))
    }.unwrapDataOrNull<UserFellowPayload>(json, "获取好友列表失败")

    suspend fun getUserPraiseForUser(
        userId: Long,
        page: Int = 1,
        pageSize: Int = 20,
    ): UserPraisePayload? = client.post("api/item/praise/forUser") {
        setBody(formOf("userNId" to userId, "page" to page, "pageSize" to pageSize))
    }.unwrapDataOrNull<UserPraisePayload>(json, "获取获赞列表失败")

    suspend fun updateUserBase(key: String, value: String): Unit =
        client.post("api/user/base/upd") {
            setBody(formOf("key" to key, "val" to value))
        }.unwrapUnit(json, "更新用户资料失败")

    suspend fun getUserInfo(id: Long? = null, userId: Long? = null): UserSelfPayload? =
        client.post("api/user/getinfo") {
            setBody(formOf("id" to id, "userId" to userId))
        }.unwrapDataOrNull<UserSelfPayload>(json, "获取用户信息失败")

    suspend fun getOrderStatic(userId: Long): OrderStaticData =
        client.post("api/user/orderStatic") {
            setBody(formOf("id" to userId))
        }.unwrapDataOrNull<OrderStaticPayload>(json, "获取订单统计失败")?.data ?: OrderStaticData()

    suspend fun getCollectSimilarity(userNodeId: Long): CollectSimilarityPayload? =
        client.post("api/user/collect/similarity") {
            setBody(formOf("userNodeId" to userNodeId))
        }.unwrapDataOrNull<CollectSimilarityPayload>(json, "获取收藏相似度失败")

    suspend fun getUserActionList(
        userNodeId: Long,
        pageSize: Int = 20,
        lastTime: Long = 0L,
    ): ActionListData? = client.get("api/user-action/user/list") {
        parameter("userNodeId", userNodeId)
        parameter("pageSize", pageSize)
        parameter("lastTime", lastTime)
    }.unwrapDataOrNull<ActionListData>(json, "获取用户动态列表失败")

    suspend fun getCollectTop(id: Long, state: String): CollectTopPayload? =
        client.post("api/user/collect/top") {
            setBody(formOf("id" to id, "state" to state))
        }.unwrapDataOrNull<CollectTopPayload>(json, "获取展柜列表失败")

    suspend fun getUserAlbumList(
        userNodeId: Long,
        page: Int = 1,
        pageSize: Int = 6,
        type: String = "upload",
        order: String = "addTime",
    ): UserCollectAlbumPayload? = client.get("api/album/user-v2") {
        parameter("userNodeId", userNodeId)
        parameter("page", page)
        parameter("pageSize", pageSize)
        parameter("type", type)
        parameter("order", order)
    }.unwrapDataOrNull<UserCollectAlbumPayload>(json, "获取用户相册列表失败")

    suspend fun getUserGkdiyAlbumList(
        userId: Long,
        page: Int = 1,
        pageSize: Int = 20,
        order: String = "addTime",
    ): GkdiyAlbumPayload? = client.post("api/album/get/gkdiy/list") {
        setBody(formOf(
            "userNId" to userId,
            "userNodeId" to userId,
            "page" to page,
            "pageSize" to pageSize,
            "order" to order
        ))
    }.unwrapDataOrNull<GkdiyAlbumPayload>(json, "获取用户作品相册失败")

    suspend fun getUserPicList(
        userNodeId: Long,
        page: Int = 1,
        pageSize: Int = 20,
        collectState: String = "upload",
        order: String = "addTime",
    ): UserCollectPicPayload? = client.get("api/pic/user") {
        parameter("userNodeId", userNodeId)
        parameter("page", page)
        parameter("pageSize", pageSize)
        parameter("collectState", collectState)
        parameter("order", order)
    }.unwrapDataOrNull<UserCollectPicPayload>(json, "获取用户发布图片失败")

    suspend fun getCheckInStatus(): CheckInStatusPayload? =
        client.post("api/mp/task/checkIn/get") {
            setBody(FormDataContent(Parameters.Empty))
        }.unwrapDataOrNull<CheckInStatusPayload>(json, "获取签到状态失败")

    suspend fun checkIn(): Unit =
        client.post("api/mp/task/checkIn") {
            setBody(FormDataContent(Parameters.Empty))
        }.unwrapUnit(json, "签到失败")

    suspend fun getUserMp(): UserMpPayload? =
        client.post("api/mp/get") {
            setBody(FormDataContent(Parameters.Empty))
        }.unwrapDataOrNull<UserMpPayload>(json, "获取魔力值失败")

    suspend fun getUserCollectHobby(params: Map<String, String>): UserCollectHobbyPayload? =
        client.get("api/collect/hobby-v2") {
            params.forEach { (k, v) -> parameter(k, v) }
        }.unwrapDataOrNull<UserCollectHobbyPayload>(json, "获取收藏列表失败")

    suspend fun getUserCollectCommon(params: Map<String, String>): UserCollectCommonPayload? =
        client.get("api/collect/common-v3") {
            params.forEach { (k, v) -> parameter(k, v) }
        }.unwrapDataOrNull<UserCollectCommonPayload>(json, "获取通用实体收藏列表失败")

    suspend fun getUserCollectAlbum(params: Map<String, String>): UserCollectAlbumPayload? =
        client.get("api/collect/album-v2") {
            params.forEach { (k, v) -> parameter(k, v) }
        }.unwrapDataOrNull<UserCollectAlbumPayload>(json, "获取相册收藏列表失败")

    suspend fun getUserCollectPic(params: Map<String, String>): UserCollectPicPayload? =
        client.get("api/collect/pic-v2") {
            params.forEach { (k, v) -> parameter(k, v) }
        }.unwrapDataOrNull<UserCollectPicPayload>(json, "获取图片收藏列表失败")

    suspend fun login(
        account: String,
        password: String,
        loginType: String = "6",
    ): LoginData = client.post("api/user/loginV2") {
        setBody(formOf("account" to account, "password" to password, "loginType" to loginType))
    }.unwrapLogin(json, "登录失败")

    suspend fun search(params: Map<String, String>): SearchResponsePayload =
        client.post("api/search") {
            setBody(formOf(params))
        }.unwrapData(json, "搜索失败")

    suspend fun getHotwords(category: Int): HotwordPayload =
        client.post("api/search/hotword") {
            setBody(formOf("category" to category))
        }.unwrapData(json, "热词加载失败")

    suspend fun getAutoFill(type: Int, keywords: String): AutoFillPayload =
        client.post("api/search/auto/fill") {
            setBody(formOf("type" to type, "keywords" to keywords))
        }.unwrapData(json, "联想词加载失败")

    suspend fun searchUsers(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 10,
    ): UserSearchPayload? = client.post("api/search/user") {
        headers[HttpHeaders.Accept] = "*/*"
        setBody(formOf("keyword" to keyword, "page" to page, "pageSize" to pageSize))
    }.unwrapDataOrNull(json, "搜索用户失败")

    suspend fun getElementAttributes(categoryId: Int): AttributeListPayload =
        client.get("api/charactar/attribute/list") {
            parameter("categoryId", categoryId)
        }.unwrapData(json, "属性加载失败")

    suspend fun getCompanyDetail(companyId: Long): CompanyDetailPayload =
        client.get("api/company/v2/get") {
            parameter("companyId", companyId)
        }.unwrapData(json, "获取厂商详情失败")

    suspend fun getCompanyActions(
        companyNodeId: Long,
        subType: String? = null,
        category: Int = 0,
        lastId: Long = 0L,
    ): CompanyActionPayload = client.get("api/user-action/company/list") {
        parameter("companyNodeId", companyNodeId)
        if (subType != null) parameter("subType", subType)
        parameter("category", category)
        parameter("lastId", lastId)
    }.unwrapData(json, "获取厂商动态失败")

    suspend fun getCompanySeries(
        companyNodeId: Long,
        page: Int = 1,
        pageSize: Int = 9,
    ): CompanySeriesPayload = client.get("api/company/series") {
        parameter("companyNodeId", companyNodeId)
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.unwrapData(json, "获取厂商系列失败")

    suspend fun getCompanyTags(companyId: Long): CompanyTagPayload =
        client.get("api/company/tag/display") {
            parameter("companyId", companyId)
        }.unwrapData(json, "获取厂商标签失败")

    suspend fun getCompanyMyVotes(companyId: Long): CompanyMyVotesData =
        client.get("api/company/tag/myVotes") {
            parameter("companyId", companyId)
        }.unwrapData(json, "获取厂商印象数据失败")

    suspend fun submitCompanyTagVote(
        companyId: Long,
        normalTagIds: String,
        scoreTagIds: String,
    ): Unit = client.post("api/company/tag/vote") {
        setBody(formOf("companyId" to companyId, "normalTagIds" to normalTagIds, "scoreTagIds" to scoreTagIds))
    }.unwrapUnit(json, "提交印象投票失败")

    suspend fun getCollectState(id: Long): CollectStatePayload? =
        client.post("api/collect/state") {
            setBody(formOf("id" to id))
        }.unwrapDataOrNull<CollectStatePayload>(json, "获取收藏状态失败")

    suspend fun collectItemAdd(id: Long): Unit =
        client.post("api/collect/item/add") {
            setBody(formOf("id" to id))
        }.unwrapUnit(json, "收藏失败")

    suspend fun collectItemDel(id: Long): Unit =
        client.post("api/collect/del") {
            setBody(formOf("id" to id))
        }.unwrapUnit(json, "取消收藏失败")

    suspend fun cancelCollectBatch(idArr: String): Unit =
        client.post("api/collect/batch/cancel") {
            setBody(formOf("idArr" to idArr))
        }.unwrapUnit(json, "批量取消收藏失败")

    suspend fun getWorksCollectTags(
        page: Int = 1,
        pageSize: Int = 20,
    ): JsonElement? = client.get("api/user/glean/textTag/get") {
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.unwrapDataOrNull<JsonElement>(json, "获取作品收藏标签失败")

    suspend fun getWorksCollect(worksId: Long): JsonElement? =
        client.get("api/works/glean/get/$worksId").unwrapDataOrNull<JsonElement>(json, "获取作品收藏信息失败")

    suspend fun saveWorksCollect(
        itemType: String = "Works",
        itemId: Long,
        tagStr: String,
        comment: String,
        state: Int,
    ): Unit = client.post("api/works/glean/add") {
        setBody(
            formOf(
                "itemType" to itemType, "itemId" to itemId, "tagStr" to tagStr,
                "comment" to comment, "state" to state,
            ),
        )
    }.unwrapUnit(json, "保存作品收藏失败")

    suspend fun deleteWorksCollect(
        itemType: String = "Works",
        itemId: Long,
    ): Unit = client.post("api/works/glean/del") {
        setBody(formOf("itemType" to itemType, "itemId" to itemId))
    }.unwrapUnit(json, "取消作品收藏失败")

    suspend fun getPictureRelate(
        picId: Long? = null,
        path: String? = null,
    ): PictureRelatePayload? {
        val validPicId = picId?.takeIf { it > 0L }
        val validPath = path?.trim()?.ifBlank { null }
        if (validPicId == null && validPath == null) return null
        return client.post("api/pic/relate") {
            val pairs = buildList {
                if (validPicId != null) {
                    add("picId" to validPicId)
                } else {
                    add("path" to validPath!!)
                }
            }
            setBody(formOf(*pairs.toTypedArray()))
        }.unwrapDataOrNull<PictureRelatePayload>(json, "获取图片关联条目失败")
    }

    suspend fun submitReport(
        objId: Long,
        userReason: String,
        type: String,
    ): Unit = client.post("api/web-report/add") {
        setBody(formOf("objId" to objId, "userReason" to userReason, "type" to type))
    }.unwrapUnit(json, "举报提交失败")

    suspend fun getCollectCount(id: Long): CollectCountPayload? =
        client.post("api/hobby/collectCount") {
            setBody(formOf("id" to id))
        }.unwrapDataOrNull<CollectCountPayload>(json, "获取收藏统计失败")

    suspend fun getCollectTags(
        page: Int = 1,
        pageSize: Int = 20,
        categoryId: Int = 10000,
    ): CollectTagsPayload? = client.get("api/collect/tag") {
        parameter("page", page)
        parameter("pageSize", pageSize)
        parameter("categoryId", categoryId)
    }.unwrapDataOrNull<CollectTagsPayload>(json, "获取推荐标签失败")

    suspend fun updateCollect(nodeId: Long, payloadJson: String): Unit =
        client.post("api/collect/hobby/upd") {
            setBody(formOf("nodeId" to nodeId, "json" to payloadJson))
        }.unwrapUnit(json, "保存收藏失败")

    suspend fun getMaskState(itemNodeId: Long): JsonElement =
        client.get("api/mask/state-v2") {
            parameter("itemNodeId", itemNodeId)
        }.unwrapData(json, "获取屏蔽状态失败")

    suspend fun getSeriesDetail(itemId: Long, itemType: String = "series"): SeriesDetailPayload =
        client.get("api/item/get-v2") {
            parameter("itemId", itemId)
            parameter("itemType", itemType)
        }.unwrapData(json, "获取系列详情失败")

    suspend fun getSeriesHobbys(
        id: Long,
        itemType: String = "series",
        pageSize: Int = 9,
    ): RelatedHobbyPayload = client.post("api/item/relate/hobby") {
        setBody(formOf("id" to id, "itemType" to itemType, "pageSize" to pageSize))
    }.unwrapData(json, "获取系列关联手办失败")

    suspend fun getSeriesLinkSeries(
        seriesId: Long,
        isChild: Int,
        page: Int = 1,
        pageSize: Int = 6,
    ): SeriesLinkPayload = client.get("api/series/link/series") {
        parameter("seriesId", seriesId)
        parameter("isChild", isChild)
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.unwrapData(json, "获取系列列表失败")

    suspend fun getSeriesAlbums(
        seriesId: Long,
        page: Int = 1,
        pageSize: Int = 6,
    ): SeriesAlbumPayload = client.get("api/series/album") {
        parameter("seriesId", seriesId)
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.unwrapData(json, "获取系列相册失败")

    suspend fun getSeriesGoods(
        nodeId: Long,
        page: Int = 1,
        pageSize: Int = 10,
    ): SeriesGoodsPayload = client.get("api/goods/tb/more-v2") {
        parameter("nodeId", nodeId)
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.unwrapData(json, "获取系列商品失败")

    suspend fun getSeriesForum(seriesId: Long): SeriesForumPayload? =
        client.get("api/series/forum") {
            parameter("seriesId", seriesId)
        }.unwrapDataOrNull(json, "获取系列论坛失败")

    suspend fun getPersonDetail(personId: Long): PersonDetailPayload =
        client.get("api/person/get") {
            parameter("personId", personId)
        }.unwrapData(json, "获取人物详情失败")

    suspend fun getPersonWorks(
        personId: Long,
        page: Int = 1,
        pageSize: Int = 20,
    ): PersonWorksPayload = client.get("api/person/works/get") {
        parameter("personId", personId)
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.unwrapData(json, "获取人物作品失败")

    suspend fun getPersonCharacter(
        personId: Long,
        page: Int = 1,
        pageSize: Int = 20,
    ): PersonCharacterPayload = client.get("api/person/character/get") {
        parameter("personId", personId)
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.unwrapData(json, "获取人物角色失败")

    suspend fun getPersonHobby(personId: Long): PersonHobbyPayload =
        client.get("api/person/hobby/get") {
            parameter("personId", personId)
        }.unwrapData(json, "获取人物周边失败")

    suspend fun getWorksDetail(worksId: Long): WorksDetailPayload =
        client.get("api/works/get") {
            parameter("worksId", worksId)
        }.unwrapData(json, "获取作品详情失败")

    suspend fun getWorksAlbums(
        worksId: Long,
        page: Int = 1,
        pageSize: Int = 10,
    ): WorksAlbumPayload = client.get("api/works/album/get") {
        parameter("worksId", worksId)
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.unwrapData(json, "获取作品相册失败")

    suspend fun getWorksCharacters(worksId: Long): WorksCharacterPayload =
        client.get("api/works/character/home/get") {
            parameter("worksId", worksId)
        }.unwrapData(json, "获取作品角色失败")

    suspend fun getCharacterDetail(characterId: Long): CharacterDetailPayload =
        client.get("api/charactar/get") {
            parameter("charactarId", characterId)
        }.unwrapData(json, "获取角色详情失败")

    suspend fun getCharacterWorks(
        characterId: Long,
        page: Int = 1,
        pageSize: Int = 20,
    ): CharacterWorksPayload = client.get("api/charactar/works/get") {
        parameter("charactarId", characterId)
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.unwrapData(json, "获取角色登场作品失败")

    suspend fun getCharacterAlbums(
        characterId: Long,
        page: Int = 1,
        pageSize: Int = 20,
    ): CharacterAlbumPayload = client.get("api/charactar/album/get") {
        parameter("charactarId", characterId)
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.unwrapData(json, "获取角色相关相册失败")

    suspend fun getCharacterRelevant(
        characterId: Long,
        pageSize: Int = 10,
    ): CharacterRelevantPayload = client.get("api/charactar/relevant/get") {
        parameter("charactarId", characterId)
        parameter("pageSize", pageSize)
    }.unwrapData(json, "获取推荐要素失败")

    suspend fun getItemZones(nodeId: Long): ZoneItemPayload =
        client.get("api/zone/item") {
            parameter("nodeId", nodeId)
        }.unwrapData(json, "获取属性标签失败")

    suspend fun getUserHistoryList(itemType: String, page: Int = 1): UserHistoryPayload? =
        client.post("api/user/read/item/get") {
            setBody(formOf("itemType" to itemType, "page" to page))
        }.unwrapDataOrNull<UserHistoryPayload>(json, "获取浏览历史失败")

    suspend fun addUserHistory(itemNodeId: Long): Unit =
        client.post("api/user/read/item/add") {
            setBody(formOf("itemNodeId" to itemNodeId))
        }.unwrapUnit(json, "添加浏览历史失败")

    suspend fun deleteUserHistory(ids: String): Unit =
        client.post("api/user/read/item/del") {
            setBody(formOf("ids" to ids))
        }.unwrapUnit(json, "删除浏览历史失败")

    suspend fun getUnreadPushCount(): UnreadPushCountPayload? =
        client.get("api/unread/push-message/count-v2").unwrapDataOrNull<UnreadPushCountPayload>(json, "获取未读消息数失败")

    suspend fun getMessageContacts(page: Int = 1): MessageContactPayload? =
        client.post("api/message/contact/get") {
            setBody(formOf("page" to page))
        }.unwrapDataOrNull<MessageContactPayload>(json, "获取私信列表失败")

    suspend fun updateContactTop(id: Long, state: Int): Unit =
        client.post("api/message/contact/top/upd") {
            setBody(formOf("id" to id, "state" to state))
        }.unwrapUnit(json, "更新置顶状态失败")

    suspend fun deleteContact(id: Long, userNodeId: Long): Unit =
        client.post("api/message/contact/del") {
            setBody(formOf("id" to id, "userNodeId" to userNodeId))
        }.unwrapUnit(json, "删除会话失败")

    suspend fun getPushMessages(
        type: String,
        page: Int = 1,
        pageSize: Int = 20,
    ): PushMessagePayload? = client.post("api/push/$type") {
        setBody(formOf("page" to page, "pageSize" to pageSize))
    }.unwrapDataOrNull<PushMessagePayload>(json, "获取通知列表失败")

    suspend fun markPushRead(type: Int, messageId: Long? = null, page: Int? = null, pageSize: Int? = null): Unit =
        client.post("api/push/read") {

            setBody(formOf("type" to type, "messageId" to messageId, "page" to page, "pageSize" to pageSize))
        }.unwrapUnit(json, "标记已读失败")

    suspend fun deletePushMessage(messageId: Long, pushType: Int): Unit =
        client.post("api/push/del") {
            setBody(formOf("messageId" to messageId, "pushType" to pushType))
        }.unwrapUnit(json, "删除通知失败")

    suspend fun batchDeletePushMessages(
        pushType: Int,
        isAll: Boolean,
        ids: String? = null,
        read: Int? = null,
    ): Unit = client.post("api/push/batch/del") {
        setBody(formOf("pushType" to pushType, "isAll" to isAll, "ids" to ids, "read" to read))
    }.unwrapUnit(json, "批量删除通知失败")

    suspend fun getChatMessages(
        fromId: Long,
        page: Int = 1,
        pageSize: Int = 20,
    ): ChatMessagePayload? = client.get("api/message/get-v2") {
        parameter("fromId", fromId)
        parameter("page", page)
        parameter("pageSize", pageSize)
    }.unwrapDataOrNull<ChatMessagePayload>(json, "获取聊天记录失败")

    suspend fun sendTextMessage(
        toId: Long,
        content: String,
        emoticon: String? = null,
    ): Unit = client.post("api/message/add-v2") {
        setBody(formOf("toId" to toId, "content" to content, "emoticon" to emoticon))
    }.unwrapUnit(json, "发送消息失败")

    suspend fun sendResaleMessage(
        toId: Long,
        resaleId: Long,
        content: String? = null,
    ): Unit = client.post("api/message/add/resale-v2") {
        setBody(formOf("toId" to toId, "resaleId" to resaleId, "content" to content))
    }.unwrapUnit(json, "发送二手卡片失败")

    suspend fun uploadChatMessagePicture(
        toId: Long,
        fileName: String,
        fileBytes: ByteArray,
        mimeType: String = "image/jpeg",
    ): Unit = client.post("api/message/upload-v2") {
        setBody(
            MultiPartFormDataContent(
                formData {
                    append("toId", toId.toString())
                    append(
                        "file",
                        fileBytes,
                        Headers.build {
                            append(HttpHeaders.ContentType, mimeType)
                            append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                        },
                    )
                },
            ),
        )
    }.unwrapUnit(json, "发送图片失败")

    suspend fun withdrawMessage(messageId: Long): Unit =
        client.post("api/message/withdraw") {
            setBody(formOf("messageId" to messageId))
        }.unwrapUnit(json, "撤回消息失败")

    suspend fun getLatestMsgId(userId: Long): LatestMsgIdPayload? =
        client.get("api/message/latestMsgId/get") {
            parameter("userId", userId)
        }.unwrapDataOrNull<LatestMsgIdPayload>(json, "获取最新消息状态失败")

    suspend fun getBlockList(page: Int = 1, pageSize: Int = 50): BlockListPayload? =
        client.post("api/block/list") {
            setBody(formOf("page" to page, "pageSize" to pageSize))
        }.unwrapDataOrNull<BlockListPayload>(json, "获取黑名单失败")

    suspend fun addMask(itemNodeId: Long): Unit =
        client.post("api/mask/add") {
            setBody(formOf("itemNodeId" to itemNodeId))
        }.unwrapUnit(json, "屏蔽失败")

    suspend fun deleteMask(itemNodeId: Long): Unit =
        client.post("api/mask/del") {
            setBody(formOf("itemNodeId" to itemNodeId))
        }.unwrapUnit(json, "解除屏蔽失败")

    suspend fun getMaskList(page: Int = 1, pageSize: Int = 20, itemType: String): MaskListPayload? =
        client.post("api/mask/list") {
            setBody(formOf("page" to page, "pageSize" to pageSize, "itemType" to itemType))
        }.unwrapDataOrNull<MaskListPayload>(json, "获取屏蔽列表失败")

    suspend fun getMysqlSettings(): JsonElement =
        client.get("api/setting/mysql/get").unwrapData<JsonElement>(json, "获取隐私设置失败")

    suspend fun updateMysqlSetting(key: String, value: Int): Unit =
        client.post("api/setting/mysql/upd") {
            setBody(formOf("key" to key, "val" to value))
        }.unwrapUnit(json, "更新隐私设置失败")

    suspend fun getRiakSettings(): JsonElement =
        client.post("api/setting/riak/get") {
            setBody(formOf("type" to "privacy"))
        }.unwrapData<JsonElement>(json, "获取隐私设置失败")

    suspend fun updateRiakSetting(key: String, value: Int): Unit =
        client.post("api/setting/riak/upd") {
            setBody(formOf("key" to key, "val" to value))
        }.unwrapUnit(json, "更新隐私设置失败")

    suspend fun checkAccount(): AccountCheckPayload? =
        client.post("api/user/check/account") {
            setBody(FormDataContent(Parameters.Empty))
        }.unwrapDataOrNull<AccountCheckPayload>(json, "获取账号状态失败")

    suspend fun getUserPrivacy(): UserPrivacyPayload? =
        client.post("api/user/privacy") {
            setBody(formOf("type" to "all"))
        }.unwrapDataOrNull(json, "获取账号信息失败")

    suspend fun updateUserBlock(userId: Long, blockState: Int): Unit =
        client.post("api/block/upd") {
            setBody(formOf("userId" to userId, "blockState" to blockState))
        }.unwrapUnit(json, "更新黑名单状态失败")
}

private fun formOf(vararg pairs: Pair<String, Any?>): FormDataContent =
    FormDataContent(
        Parameters.build {
            for ((key, value) in pairs) {
                if (value != null) append(key, value.toString())
            }
        },
    )

private fun formOf(params: Map<String, String>): FormDataContent =
    FormDataContent(
        Parameters.build {
            params.forEach { (k, v) -> append(k, v) }
        },
    )
