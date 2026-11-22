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
import com.nobody.repoi.ui.search.SearchResultNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RePoiSerializersModuleTest {

    private val json = Json {
        serializersModule = RePoiSerializersModule
        ignoreUnknownKeys = true
    }

    private val polymorphicNavKeySerializer = PolymorphicSerializer(NavKey::class)

    @Test
    fun testAllAppNavKeysPolymorphicSerialization() {
        val testRoutes: List<NavKey> = listOf(

            MainNavKey,

            HomeNavKey,
            ItemsNavKey,
            AlbumNavKey,
            FollowNavKey,
            ProfileNavKey,

            ZoneBrowseNavKey,
            ZoneDetailNavKey(nodeId = 1001L, initialName = "手办专区", id = "fixed-id-zone-detail"),
            ZoneSettingsNavKey,

            PostDetailNavKey(postId = 2001L, initialTitle = "讨论帖", nodeId = 1001L, id = "fixed-id-post-detail"),

            ResaleDetailNavKey(resaleId = 3001L, id = "fixed-id-resale-detail"),
            ResaleMarketNavKey(hobbyId = 4001L, title = "二手集市", id = "fixed-id-resale-market"),

            HobbyListNavKey(param = HobbyListNavParam(title = "手办列表"), id = "fixed-id-hobby-list"),
            CollectEditNavKey(param = CollectEditNavParam(itemId = 5001L), id = "fixed-id-collect-edit"),
            UserCollectNavKey(param = UserCollectNavParam(userId = 6001L), id = "fixed-id-user-collect"),
            UserCreationNavKey(param = UserCreationNavParam(userId = 6001L), id = "fixed-id-user-creation"),
            UserHistoryNavKey(id = "fixed-id-user-history"),
            HobbyDetailNavKey(param = DetailNavParam(itemId = 7001L), id = "fixed-id-hobby-detail"),
            FriendListNavKey(param = FriendListNavParam(userId = 6001L), id = "fixed-id-friend-list"),

            UserInfoNavKey(param = UserInfoNavParam(userId = 6001L), id = "fixed-id-user-info"),
            CommentDetailNavKey(param = CommentDetailNavParam(commentId = 8001L, relateNodeId = 7001L), id = "fixed-id-comment-detail"),
            HobbyCommentsNavKey(param = HobbyCommentsNavParam(nodeId = 7001L), id = "fixed-id-hobby-comments"),
            SearchNavKey(param = SearchNavParam(initialKeyword = "初音未来"), id = "fixed-id-search"),
            SearchResultNavKey(
                param = SearchResultNavParam(
                    keyword = "初音未来",
                    category = 10000,
                    companyNodeId = 1101L,
                    companyName = "Good Smile Company"
                ),
                id = "fixed-id-search-result"
            ),

            AlbumDetailNavKey(param = AlbumNavParam(albumId = 9001L), id = "fixed-id-album-detail"),
            AlbumRankListNavKey(param = AlbumRankListNavParam(title = "相册排行榜"), id = "fixed-id-album-rank"),
            VendorDetailNavKey(param = VendorNavParam(companyId = 1101L), id = "fixed-id-vendor-detail"),
            VendorActionsNavKey(param = VendorActionsNavParam(companyNodeId = 1101L), id = "fixed-id-vendor-actions"),

            PersonDetailNavKey(param = PersonNavParam(personId = 1201L), id = "fixed-id-person-detail"),
            CharacterNavKey(param = CharacterNavParam(characterId = 1301L), id = "fixed-id-character"),
            CharacterAlbumListNavKey(param = CharacterAlbumListNavParam(characterId = 1301L), id = "fixed-id-char-album"),
            HobbyAlbumListNavKey(param = HobbyAlbumListNavParam(nodeId = 7001L), id = "fixed-id-hobby-album"),
            CharacterWorksListNavKey(param = CharacterWorksListNavParam(characterId = 1301L), id = "fixed-id-char-works"),
            WorksCollectEditNavKey(param = WorksCollectEditNavParam(worksId = 1401L), id = "fixed-id-works-collect"),
            SeriesDetailNavKey(param = SeriesNavParam(seriesId = 1501L), id = "fixed-id-series-detail"),

            WorksDetailNavKey(param = WorksNavParam(worksId = 1401L), id = "fixed-id-works-detail"),
            PictureListNavKey(param = PictureListNavParam(itemId = 7001L), id = "fixed-id-picture-list"),
            ImageViewerNavKey(
                param = ImageViewerNavParam(
                    images = listOf("https://example.com/img1.jpg", "https://example.com/img2.jpg"),
                    initialIndex = 1
                ),
                id = "fixed-id-image-viewer"
            ),
            PictureGalleryNavKey(
                param = PictureGalleryNavParam(
                    userId = 6001L,
                    picId = 7001L,
                    initialIndex = 2,
                    order = "praiseCount"
                ),
                id = "fixed-id-picture-gallery"
            ),
            ExHobbyListNavKey(itemId = 7001L, title = "ExHobby", id = "fixed-id-exhobby-list"),

            LoginNavKey,
            SettingsNavKey,
            SettingsShieldListNavKey(id = "fixed-id-settings-shield-list"),

            MessageCenterNavKey(id = "fixed-id-message-center"),
            MessageNoticeNavKey(param = MessageNoticeNavParam(type = "reply"), id = "fixed-id-message-notice"),
            MessageChatNavKey(param = MessageChatNavParam(targetUserId = 6001L), id = "fixed-id-message-chat"),
            MessageBlockNavKey(id = "fixed-id-message-block")
        )

        assertEquals(47, testRoutes.size, "测试必须全量覆盖全部 47 个 AppNavKey")

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
    fun testSearchResultNavKeySerializationAndTypeIntegrity() {
        val originalKey = SearchResultNavKey(
            param = SearchResultNavParam(
                keyword = "初音未来",
                category = 10000,
                companyNodeId = 1101L,
                companyName = "Good Smile Company"
            ),
            id = "test-search-result-id"
        )
        val encoded = json.encodeToString(polymorphicNavKeySerializer, originalKey)
        val decoded = json.decodeFromString(polymorphicNavKeySerializer, encoded)

        assertIs<SearchResultNavKey>(decoded)
        assertEquals("初音未来", decoded.param.keyword)
        assertEquals(10000, decoded.param.category)
        assertEquals(1101L, decoded.param.companyNodeId)
        assertEquals("Good Smile Company", decoded.param.companyName)
        assertEquals("test-search-result-id", decoded.id)
        assertEquals(originalKey, decoded)
    }

    @Test
    fun testTypeIntegrityAfterDeserialization() {

        val homeEncoded = json.encodeToString(polymorphicNavKeySerializer, HomeNavKey)
        val homeDecoded = json.decodeFromString(polymorphicNavKeySerializer, homeEncoded)
        assertIs<HomeNavKey>(homeDecoded)

        val detailKey = HobbyDetailNavKey(param = DetailNavParam(itemId = 59570L), id = "test-id-123")
        val detailEncoded = json.encodeToString(polymorphicNavKeySerializer, detailKey)
        val detailDecoded = json.decodeFromString(polymorphicNavKeySerializer, detailEncoded)
        assertIs<HobbyDetailNavKey>(detailDecoded)
        assertEquals(59570L, detailDecoded.param.itemId)
        assertEquals("test-id-123", detailDecoded.id)
    }
}
