package com.nobody.repoi.util

import com.nobody.repoi.data.remote.model.ActionItem
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.navigation.CharacterNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam

/**
 * 动态流卡片目标路由解析结果。
 */
sealed interface FeedTargetDestination {
    data class Hobby(val param: DetailNavParam) : FeedTargetDestination
    data class Album(val param: AlbumNavParam) : FeedTargetDestination
    data class Vendor(val param: VendorNavParam) : FeedTargetDestination
    data class Character(val param: CharacterNavParam) : FeedTargetDestination
    data class Series(val param: SeriesNavParam) : FeedTargetDestination
    data class Person(val param: PersonNavParam) : FeedTargetDestination
    data class Works(val param: WorksNavParam) : FeedTargetDestination
    data class User(val userId: Long) : FeedTargetDestination
    data class Invalid(val message: String) : FeedTargetDestination
}

object FeedActionDispatcher {

    private fun FeedTargetDestination.describe(): String = when (this) {
        is FeedTargetDestination.Hobby -> "手办(itemId=${param.itemId},itemType=${param.itemType})"
        is FeedTargetDestination.Album -> "相册(albumId=${param.albumId})"
        is FeedTargetDestination.Vendor -> "厂商(companyId=${param.companyId},nodeId=${param.companyNodeId})"
        is FeedTargetDestination.Character -> "角色(characterId=${param.characterId},nodeId=${param.characterNodeId})"
        is FeedTargetDestination.Series -> "系列(seriesId=${param.seriesId},nodeId=${param.seriesNodeId})"
        is FeedTargetDestination.Person -> "人物(personId=${param.personId},nodeId=${param.personNodeId})"
        is FeedTargetDestination.Works -> "作品(worksId=${param.worksId})"
        is FeedTargetDestination.User -> "用户(userId=$userId)"
        is FeedTargetDestination.Invalid -> "无效($message)"
    }

    /**
     * 根据 [ActionItem] 的关联条目和类型纯函数解析目标路由与参数。
     */
    fun resolveDestination(actionItem: ActionItem): FeedTargetDestination {
        val relate = actionItem.relateItem
        if (relate == null) {
            val userId = actionItem.user?.actualUserId ?: 0L
            return if (userId > 0L) {
                FeedTargetDestination.User(userId)
            } else {
                FeedTargetDestination.Invalid("条目信息暂未就绪")
            }
        }

        val itemType = relate.itemType?.lowercase()?.trim()
        val categoryId = relate.categoryId
        val displayName = relate.nameCN?.ifBlank { null } ?: relate.name
        val cover = relate.cover ?: relate.path

        return when {
            // 1. 相册（原版 album / 分类区间 60000..79999 / actionType=3）
            itemType == "album" || relate.isAlbum || categoryId in 60000..79999 || actionItem.actionType == 3 -> {
                val targetAlbumId = if (relate.id > 0) relate.id else relate.itemId
                if (targetAlbumId > 0) {
                    FeedTargetDestination.Album(
                        AlbumNavParam(
                            albumId = targetAlbumId,
                            initialTitle = displayName,
                            initialCover = cover
                        )
                    )
                } else {
                    FeedTargetDestination.Invalid("条目信息暂未就绪：${displayName ?: "相册详情"}")
                }
            }

            // 2. 厂商 / 品牌（原版 company / vendor / 分类 40000；注意 40100~40500 为角色要素子分类，不可划入厂商）
            itemType == "company" || itemType == "vendor" || categoryId == 40000 -> {
                val targetCompanyId = if (relate.itemId > 0) relate.itemId else relate.id
                val targetCompanyNodeId = if (relate.id > 0) relate.id else relate.itemId
                if (targetCompanyId > 0) {
                    FeedTargetDestination.Vendor(
                        VendorNavParam(
                            companyId = targetCompanyId,
                            companyNodeId = targetCompanyNodeId,
                            initialName = displayName,
                            initialCover = cover
                        )
                    )
                } else {
                    FeedTargetDestination.Invalid("条目信息暂未就绪：${displayName ?: "厂商详情"}")
                }
            }

            // 3. 角色 / 要素（原版 character / charactar / 分类 50000 及其子分类 40100~40500）
            itemType == "character" || itemType == "charactar" || categoryId in 40100..40599 || categoryId in 50000..59999 -> {
                val targetCharacterId = if (relate.itemId > 0) relate.itemId else relate.id
                val targetCharacterNodeId = if (relate.id > 0) relate.id else relate.itemId
                if (targetCharacterId > 0) {
                    FeedTargetDestination.Character(
                        CharacterNavParam(
                            characterId = targetCharacterId,
                            characterNodeId = targetCharacterNodeId,
                            initialName = displayName,
                            initialCover = cover
                        )
                    )
                } else {
                    FeedTargetDestination.Invalid("条目信息暂未就绪：${displayName ?: "角色详情"}")
                }
            }

            // 4. 系列（原版 series / 分类区间 80000..89999）
            itemType == "series" || categoryId in 80000..89999 -> {
                val targetSeriesId = if (relate.itemId > 0) relate.itemId else relate.id
                val targetSeriesNodeId = if (relate.id > 0) relate.id else relate.itemId
                if (targetSeriesId > 0) {
                    FeedTargetDestination.Series(
                        SeriesNavParam(
                            seriesId = targetSeriesId,
                            seriesNodeId = targetSeriesNodeId,
                            initialName = displayName,
                            initialCover = cover
                        )
                    )
                } else {
                    FeedTargetDestination.Invalid("条目信息暂未就绪：${displayName ?: "系列详情"}")
                }
            }

            // 5. 人物 / 创作者 / 声优（原版 person / 分类区间 30000..39999）
            itemType == "person" || categoryId in 30000..39999 -> {
                val targetPersonId = if (relate.itemId > 0) relate.itemId else relate.id
                val targetPersonNodeId = if (relate.id > 0) relate.id else relate.itemId
                if (targetPersonId > 0) {
                    FeedTargetDestination.Person(
                        PersonNavParam(
                            personId = targetPersonId,
                            personNodeId = targetPersonNodeId,
                            initialName = displayName,
                            initialCover = cover
                        )
                    )
                } else {
                    FeedTargetDestination.Invalid("条目信息暂未就绪：${displayName ?: "人物详情"}")
                }
            }

            // 6. 作品（原版 works / 分类区间 20000..29999）
            itemType == "works" || categoryId in 20000..29999 -> {
                val targetWorksId = if (relate.itemId > 0) relate.itemId else relate.id
                if (targetWorksId > 0) {
                    FeedTargetDestination.Works(
                        WorksNavParam(
                            worksId = targetWorksId,
                            initialName = displayName,
                            initialCover = cover
                        )
                    )
                } else {
                    FeedTargetDestination.Invalid("条目信息暂未就绪：${displayName ?: "作品详情"}")
                }
            }

            // 7. 手办 / 周边 / 默认条目（hobby / figure / 分类 10000..19999 / 缺省）
            else -> {
                val targetItemId = if (relate.itemId > 0) relate.itemId else relate.id
                if (targetItemId > 0) {
                    FeedTargetDestination.Hobby(
                        DetailNavParam(
                            itemId = targetItemId,
                            itemType = itemType ?: "hobby",
                            initialName = displayName,
                            initialCover = cover
                        )
                    )
                } else {
                    FeedTargetDestination.Invalid("条目信息暂未就绪：${displayName ?: "手办详情"}")
                }
            }
        }
    }

    /**
     * 执行分发并在 UI 层执行导航跳转与容错提示。
     * 分发结果同步记入应用日志便于排查跳转问题。
     */
    fun dispatch(
        actionItem: ActionItem,
        onMessage: (String) -> Unit,
        onNavigateToDetail: ((DetailNavParam) -> Unit)? = null,
        onNavigateToAlbum: ((AlbumNavParam) -> Unit)? = null,
        onNavigateToVendor: ((VendorNavParam) -> Unit)? = null,
        onNavigateToCharacter: ((CharacterNavParam) -> Unit)? = null,
        onNavigateToPerson: ((PersonNavParam) -> Unit)? = null,
        onNavigateToWorks: ((WorksNavParam) -> Unit)? = null,
        onNavigateToSeries: ((SeriesNavParam) -> Unit)? = null,
        onNavigateToUser: ((Long) -> Unit)? = null
    ) {
        val dest = resolveDestination(actionItem)
        val relate = actionItem.relateItem
        AppLogger.i(
            "导航",
            "分发 actionId=${actionItem.id} actionType=${actionItem.actionType} " +
                "itemType=${relate?.itemType} categoryId=${relate?.categoryId} " +
                "id=${relate?.id} itemId=${relate?.itemId} => ${dest.describe()}"
        )
        when (dest) {
            is FeedTargetDestination.Hobby -> {
                if (onNavigateToDetail != null) {
                    onNavigateToDetail(dest.param)
                } else {
                    onMessage("${dest.param.initialName ?: "手办"}详情页暂未开放")
                }
            }
            is FeedTargetDestination.Album -> {
                if (onNavigateToAlbum != null) {
                    onNavigateToAlbum(dest.param)
                } else {
                    onMessage("${dest.param.initialTitle ?: "相册"}详情页暂未开放")
                }
            }
            is FeedTargetDestination.Vendor -> {
                if (onNavigateToVendor != null) {
                    onNavigateToVendor(dest.param)
                } else {
                    onMessage("${dest.param.initialName ?: "厂商"}详情页暂未开放")
                }
            }
            is FeedTargetDestination.Character -> {
                if (onNavigateToCharacter != null) {
                    onNavigateToCharacter(dest.param)
                } else {
                    onMessage("${dest.param.initialName ?: "角色"}详情页暂未开放")
                }
            }
            is FeedTargetDestination.Series -> {
                if (onNavigateToSeries != null) {
                    onNavigateToSeries(dest.param)
                } else {
                    onMessage("${dest.param.initialName ?: "系列"}详情页暂未开放")
                }
            }
            is FeedTargetDestination.Person -> {
                if (onNavigateToPerson != null) {
                    onNavigateToPerson(dest.param)
                } else {
                    onMessage("${dest.param.initialName ?: "人物"}详情页暂未开放")
                }
            }
            is FeedTargetDestination.Works -> {
                if (onNavigateToWorks != null) {
                    onNavigateToWorks(dest.param)
                } else {
                    onMessage("${dest.param.initialName ?: "作品"}详情页暂未开放")
                }
            }
            is FeedTargetDestination.User -> {
                if (onNavigateToUser != null) {
                    onNavigateToUser(dest.userId)
                } else {
                    onMessage("用户主页暂未开放")
                }
            }
            is FeedTargetDestination.Invalid -> {
                onMessage(dest.message)
            }
        }
    }
}
