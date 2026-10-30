package com.nobody.repoi.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.album.AlbumRankListNavParam
import com.nobody.repoi.ui.collect.WorksCollectEditNavParam
import com.nobody.repoi.ui.comment.CommentDetailNavParam
import com.nobody.repoi.ui.comment.HobbyCommentsNavParam
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.item.list.HobbyListNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.search.SearchNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * 验证 [RePoiSerializersModule] 多态序列化完整性。
 *
 * 覆盖全部 44 个 [AppNavKey] 实现，确保 Navigation 3 在 SavedStateConfiguration
 * 状态持久化、Android 进程恢复及桌面端跨会话存储时多态反序列化安全无遗漏。
 */
class RePoiSerializersModuleTest {

    private val json = Json {
        serializersModule = RePoiSerializersModule
        ignoreUnknownKeys = true
    }

    private val polymorphicNavKeySerializer = PolymorphicSerializer(NavKey::class)

    @Test
    fun testAllAppNavKeysPolymorphicSerialization() {
        val testRoutes: List<NavKey> = listOf(
            // 1. 一级底栏 Tab 路由 (5)
            HomeNavKey,
            ItemsNavKey,
            AlbumNavKey,
            FollowNavKey,
            ProfileNavKey,

            // 2. 专区相关路由 (3)
            ZoneBrowseNavKey,
            ZoneDetailNavKey(nodeId = 1001L, initialName = "手办专区", id = "fixed-id-zone-detail"),
            ZoneSettingsNavKey,

            // 3. 讨论板与帖子路由 (3)
            ForumHomeNavKey,
            PlateSubscribeNavKey,
            PostDetailNavKey(postId = 2001L, initialTitle = "讨论帖", nodeId = 1001L, id = "fixed-id-post-detail"),

            // 4. 二手交易与市场路由 (2)
            ResaleDetailNavKey(resaleId = 3001L, id = "fixed-id-resale-detail"),
            ResaleMarketNavKey(hobbyId = 4001L, title = "二手集市", id = "fixed-id-resale-market"),

            // 5. 核心消费与用户资产路由 (7)
            HobbyListNavKey(param = HobbyListNavParam(title = "手办列表"), id = "fixed-id-hobby-list"),
            CollectEditNavKey(param = CollectEditNavParam(itemId = 5001L), id = "fixed-id-collect-edit"),
            UserCollectNavKey(param = UserCollectNavParam(userId = 6001L), id = "fixed-id-user-collect"),
            UserCreationNavKey(param = UserCreationNavParam(userId = 6001L), id = "fixed-id-user-creation"),
            UserHistoryNavKey(id = "fixed-id-user-history"),
            HobbyDetailNavKey(param = DetailNavParam(itemId = 7001L), id = "fixed-id-hobby-detail"),
            FriendListNavKey(param = FriendListNavParam(userId = 6001L), id = "fixed-id-friend-list"),

            // 6. 用户与评论互动路由 (4)
            UserInfoNavKey(param = UserInfoNavParam(userId = 6001L), id = "fixed-id-user-info"),
            CommentDetailNavKey(param = CommentDetailNavParam(commentId = 8001L, relateNodeId = 7001L), id = "fixed-id-comment-detail"),
            HobbyCommentsNavKey(param = HobbyCommentsNavParam(nodeId = 7001L), id = "fixed-id-hobby-comments"),
            SearchNavKey(param = SearchNavParam(initialKeyword = "初音未来"), id = "fixed-id-search"),

            // 7. 相册与厂商关联路由 (4)
            AlbumDetailNavKey(param = AlbumNavParam(albumId = 9001L), id = "fixed-id-album-detail"),
            AlbumRankListNavKey(param = AlbumRankListNavParam(title = "相册排行榜"), id = "fixed-id-album-rank"),
            VendorDetailNavKey(param = VendorNavParam(companyId = 1101L), id = "fixed-id-vendor-detail"),
            VendorActionsNavKey(param = VendorActionsNavParam(companyNodeId = 1101L), id = "fixed-id-vendor-actions"),

            // 8. 人物、角色与作品关联路由 (7)
            PersonDetailNavKey(param = PersonNavParam(personId = 1201L), id = "fixed-id-person-detail"),
            CharacterNavKey(param = CharacterNavParam(characterId = 1301L), id = "fixed-id-character"),
            CharacterAlbumListNavKey(param = CharacterAlbumListNavParam(characterId = 1301L), id = "fixed-id-char-album"),
            HobbyAlbumListNavKey(param = HobbyAlbumListNavParam(nodeId = 7001L), id = "fixed-id-hobby-album"),
            CharacterWorksListNavKey(param = CharacterWorksListNavParam(characterId = 1301L), id = "fixed-id-char-works"),
            WorksCollectEditNavKey(param = WorksCollectEditNavParam(worksId = 1401L), id = "fixed-id-works-collect"),
            SeriesDetailNavKey(param = SeriesNavParam(seriesId = 1501L), id = "fixed-id-series-detail"),

            // 9. 作品、图片与扩展图库路由 (3)
            WorksDetailNavKey(param = WorksNavParam(worksId = 1401L), id = "fixed-id-works-detail"),
            PictureListNavKey(param = PictureListNavParam(itemId = 7001L), id = "fixed-id-picture-list"),
            ExHobbyListNavKey(itemId = 7001L, title = "ExHobby", id = "fixed-id-exhobby-list"),

            // 10. 设置与鉴权路由 (2)
            LoginNavKey,
            SettingsNavKey,

            // 11. 消息中心与通知路由 (4)
            MessageCenterNavKey(id = "fixed-id-message-center"),
            MessageNoticeNavKey(param = MessageNoticeNavParam(type = "reply"), id = "fixed-id-message-notice"),
            MessageChatNavKey(param = MessageChatNavParam(targetUserId = 6001L), id = "fixed-id-message-chat"),
            MessageBlockNavKey(id = "fixed-id-message-block")
        )

        assertEquals(44, testRoutes.size, "测试必须全量覆盖全部 44 个 AppNavKey")

        for (originalKey in testRoutes) {
            val encoded = json.encodeToString(polymorphicNavKeySerializer, originalKey)
            val decoded = json.decodeFromString(polymorphicNavKeySerializer, encoded)

            assertEquals(
                originalKey,
                decoded,
                "多态反序列化后与原实例不相等: ${originalKey::class.simpleName}, json: $encoded"
            )
        }
    }

    @Test
    fun testTypeIntegrityAfterDeserialization() {
        // 单例路由类型完整性
        val homeEncoded = json.encodeToString(polymorphicNavKeySerializer, HomeNavKey)
        val homeDecoded = json.decodeFromString(polymorphicNavKeySerializer, homeEncoded)
        assertIs<HomeNavKey>(homeDecoded)

        // 复杂带参路由类型完整性
        val detailKey = HobbyDetailNavKey(param = DetailNavParam(itemId = 59570L), id = "test-id-123")
        val detailEncoded = json.encodeToString(polymorphicNavKeySerializer, detailKey)
        val detailDecoded = json.decodeFromString(polymorphicNavKeySerializer, detailEncoded)
        assertIs<HobbyDetailNavKey>(detailDecoded)
        assertEquals(59570L, detailDecoded.param.itemId)
        assertEquals("test-id-123", detailDecoded.id)
    }
}
