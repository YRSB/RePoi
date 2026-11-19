package com.nobody.repoi.ui.home

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.PushCountCategory
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.ItemRepository
import com.nobody.repoi.data.repository.MessageRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.ResaleRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.ui.board.session.ZoneBoardSessionImpl
import com.nobody.repoi.ui.message.notice.MessageNoticeViewModel
import com.nobody.repoi.ui.message.center.MessageCenterViewModel
import com.nobody.repoi.ui.navigation.MessageNoticeNavParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * issue #195 回归测试：首页右上角消息红点在用户查看完消息后必须能去掉。
 *
 * 根因（两层，均已修复）：
 * 1. `PushCountCategory.totalCount` 曾把 `posts_reply`（讨论板域未读）计入消息红点，
 *    而消息中心六类入口均无该类可见/可清路径，红点永远无法去除；
 *    官方 app `MainActivity.refreshUnreadCounts` 将其归入 forumCount，不计消息红点。
 * 2. 首页 `unreadMessageCount` 只在手动下拉刷新时重拉，从消息中心返回不刷新，
 *    陈旧快照使已清零的红点继续残留；现由界面层在 RESUMED 时调用
 *    [HomeFeedViewModel.refreshUnreadMessages] 恢复刷新。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Issue195UnreadBadgeReproTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    /** 服务端 count-v2 响应体。 */
    private fun countJson(
        system: Int = 0,
        reply: Int = 0,
        praise: Int = 0,
        recommend: Int = 0,
        follownews: Int = 0,
        follow: Int = 0,
        commodity: Int = 0,
        postsReply: Int = 0,
    ): String =
        """{"success":true,"code":200,"msg":"ok","data":{"map":{"push":{""" +
            """"system":$system,"reply":$reply,"praise":$praise,"recommend":$recommend,""" +
            """"follownews":$follownews,"follow":$follow,"commodity":$commodity,"posts_reply":$postsReply}}}}"""

    /** 模型口径：posts_reply 属讨论板域未读，不得计入消息红点。 */
    @Test
    fun postsReplyOnly_neverLightsMessageBadge() = runTest(testDispatcher) {
        assertEquals(0, PushCountCategory(postsReply = 5).totalCount)
        assertEquals(5, PushCountCategory(system = 5).totalCount)
        assertEquals(3, PushCountCategory(reply = 3, postsReply = 99).totalCount)
    }

    @Test
    fun userViewsCenterAndMarksAllRead_returnsHome_redDotClears() = runTest(testDispatcher) {
        val accountMock = MockApi()
        accountMock.route("api/unread/push-message/count-v2", countJson(system = 2, postsReply = 3))
        accountMock.route(
            "api/message/contact/get",
            """{"success":true,"code":200,"msg":"ok","data":{"contact":[]}}"""
        )
        accountMock.route("api/push/read", """{"success":true,"code":200,"msg":"ok"}""")

        val zoneMock = MockApi()
        zoneMock.route(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[]}}"""
        )
        val feedMock = MockApi()
        feedMock.route(
            "api/user-action/list-v3",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""
        )
        val albumMock = MockApi()
        albumMock.route(
            "api/album/list",
            """{"success":true,"code":200,"msg":"ok","data":{"page":1,"list":[]}}"""
        )
        albumMock.route(
            "api/zone/categories",
            """{"success":true,"code":200,"msg":"ok","data":{"visible":true,"categories":[]}}"""
        )
        val resaleMock = MockApi()
        resaleMock.route(
            "api/resale/search",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""
        )
        val hobbyMock = MockApi()
        hobbyMock.route(
            "api/hobby/query-v2",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""
        )

        val preferences = ViewPreferencesManager(TestDataStore.inMemory())
        preferences.setFeedZoneTabKey("all")
        preferences.setFeedCategory(0)
        preferences.setFeedSubType("recommend")
        val zoneRepository = ZoneRepository(
            api = zoneMock.api(testDispatcher),
            viewPreferencesManager = preferences,
            visitReportScope = CoroutineScope(testDispatcher + SupervisorJob())
        )
        val homeRepository = HomeRepository(feedMock.api(testDispatcher))
        val albumRepository = AlbumRepository(albumMock.api(testDispatcher))
        val messageRepository = MessageRepository(accountMock.api(testDispatcher))

        val homeVm = HomeFeedViewModel(
            sessionFactory = ZoneBoardSessionImpl.Factory(
                homeRepository = homeRepository,
                albumRepository = albumRepository,
                zoneRepository = zoneRepository,
                itemRepository = ItemRepository(hobbyMock.api(testDispatcher)),
                resaleRepository = ResaleRepository(resaleMock.api(testDispatcher)),
                viewPreferencesManager = preferences
            ),
            zoneRepository = zoneRepository,
            viewPreferencesManager = preferences,
            userSession = FakeUserSession("utoken-test"),
            messageRepository = messageRepository,
            albumRepository = albumRepository
        )
        advanceUntilIdle()

        // ① 可见未读 system=2 点亮红点；posts_reply=3 不参与消息红点口径
        assertEquals(2, homeVm.uiState.value.unreadMessageCount)

        // ② 用户进入消息中心：system 分类 badge 可见，存在可清路径
        val centerVm = MessageCenterViewModel(messageRepository)
        advanceUntilIdle()
        assertEquals(2, centerVm.uiState.value.unreadCounts.system)
        assertEquals(3, centerVm.uiState.value.unreadCounts.postsReply)

        // ③ 用户执行一键全部已读，服务端确认后清零 count-v2
        centerVm.markAllRead()
        advanceUntilIdle()
        accountMock.replaceRoute("api/unread/push-message/count-v2", countJson())

        // ④ 返回首页触发恢复感知重拉（HomeFeedScreen 在 RESUMED 时调用）→ 红点归零
        homeVm.refreshUnreadMessages()
        advanceUntilIdle()
        assertEquals(0, homeVm.uiState.value.unreadMessageCount)
    }

    @Test
    fun postsReplyOnly_serviceCount_returnsHome_redDotNeverLights() = runTest(testDispatcher) {
        // 服务端仅有 posts_reply 未读（用户在讨论板被回复）：首页红点从头就不该亮
        val accountMock = MockApi()
        accountMock.route("api/unread/push-message/count-v2", countJson(postsReply = 3))

        val zoneMock = MockApi()
        zoneMock.route(
            "api/zone/home-tabs",
            """{"success":true,"code":200,"msg":"ok","data":{"settings":{"showAll":true},"focusZones":[]}}"""
        )
        val feedMock = MockApi()
        feedMock.route(
            "api/user-action/list-v3",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""
        )
        val albumMock = MockApi()
        albumMock.route(
            "api/album/list",
            """{"success":true,"code":200,"msg":"ok","data":{"page":1,"list":[]}}"""
        )
        albumMock.route(
            "api/zone/categories",
            """{"success":true,"code":200,"msg":"ok","data":{"visible":true,"categories":[]}}"""
        )
        val resaleMock = MockApi()
        resaleMock.route(
            "api/resale/search",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""
        )
        val hobbyMock = MockApi()
        hobbyMock.route(
            "api/hobby/query-v2",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""
        )

        val preferences = ViewPreferencesManager(TestDataStore.inMemory())
        preferences.setFeedZoneTabKey("all")
        preferences.setFeedCategory(0)
        preferences.setFeedSubType("recommend")
        val zoneRepository = ZoneRepository(
            api = zoneMock.api(testDispatcher),
            viewPreferencesManager = preferences,
            visitReportScope = CoroutineScope(testDispatcher + SupervisorJob())
        )
        val homeVm = HomeFeedViewModel(
            sessionFactory = ZoneBoardSessionImpl.Factory(
                homeRepository = HomeRepository(feedMock.api(testDispatcher)),
                albumRepository = AlbumRepository(albumMock.api(testDispatcher)),
                zoneRepository = zoneRepository,
                itemRepository = ItemRepository(hobbyMock.api(testDispatcher)),
                resaleRepository = ResaleRepository(resaleMock.api(testDispatcher)),
                viewPreferencesManager = preferences
            ),
            zoneRepository = zoneRepository,
            viewPreferencesManager = preferences,
            userSession = FakeUserSession("utoken-test"),
            messageRepository = MessageRepository(accountMock.api(testDispatcher)),
            albumRepository = AlbumRepository(albumMock.api(testDispatcher))
        )
        advanceUntilIdle()

        assertEquals(0, homeVm.uiState.value.unreadMessageCount)
    }

    // ── 用户追加报告：普通评论被赞/被回复的红点也去不掉（官方 app 逆向对齐） ──────────────

    private fun pushJson(items: String): String =
        """{"success":true,"code":200,"msg":"ok","data":{"pushMessage":[$items]}}"""

    @Test
    fun markItemRead_sendsItemTypeNotPageTypeNumber() = runTest(testDispatcher) {
        // 官方 app 各通知 Adapter 单条已读传「条目自带 type」而非消息中心入口 typeNumber；
        // 两类数值错位时服务端置位失败，点开通知红点也不消失（issue #195 追加报告）
        val mock = MockApi()
        mock.route(
            "api/push/reply",
            pushJson("""{"id":11,"type":9,"state":0,"content":"有人回复了你"}""")
        )
        mock.route("api/push/read", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        val viewModel = MessageNoticeViewModel(MessageRepository(mock.api(testDispatcher)))
        viewModel.loadInitial(MessageNoticeNavParam(type = "reply", typeNumber = 2, title = "回复通知"))
        advanceUntilIdle()

        viewModel.markItemRead(viewModel.uiState.value.messages[0])
        advanceUntilIdle()

        val readForm = mock.lastRequest().formEntries()
        assertEquals("9", readForm["type"])
        assertEquals("11", readForm["messageId"])
        assertEquals(false, viewModel.uiState.value.messages[0].isUnread)
    }

    @Test
    fun markItemRead_failureKeepsUnreadAndShowsError() = runTest(testDispatcher) {
        // 置位失败时不得本地假置已读：fire-and-forget 掩盖服务端失败曾使红点残留
        val mock = MockApi()
        mock.route(
            "api/push/reply",
            pushJson("""{"id":11,"type":2,"state":0,"content":"有人回复了你"}""")
        )
        mock.route("api/push/read", mock.failure(500, "标记已读失败"))

        val viewModel = MessageNoticeViewModel(MessageRepository(mock.api(testDispatcher)))
        viewModel.loadInitial(MessageNoticeNavParam(type = "reply", typeNumber = 2, title = "回复通知"))
        advanceUntilIdle()

        viewModel.markItemRead(viewModel.uiState.value.messages[0])
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.messages[0].isUnread)
        assertEquals("标记已读失败", viewModel.uiState.value.actionMessage)
    }

    @Test
    fun systemNoticeLoad_autoClearsCurrentPageAsOfficial() = runTest(testDispatcher) {
        // 官方 MessageNoticeActivity：系统通知(typeNumber=1)列表加载成功即清当前页未读
        val mock = MockApi()
        mock.route(
            "api/push/system",
            pushJson("""{"id":1,"type":1,"state":0,"content":"系统通知A"},{"id":2,"type":1,"state":0,"content":"系统通知B"}""")
        )
        mock.route("api/push/read", """{"success":true,"code":200,"msg":"ok","data":{}}""")

        val viewModel = MessageNoticeViewModel(MessageRepository(mock.api(testDispatcher)))
        viewModel.loadInitial(MessageNoticeNavParam(type = "system", typeNumber = 1, title = "系统通知"))
        advanceUntilIdle()

        val readForm = mock.lastRequest().formEntries()
        assertEquals("1", readForm["type"])
        assertEquals("1", readForm["page"])
        assertEquals("20", readForm["pageSize"])
        assertEquals(false, viewModel.uiState.value.messages[0].isUnread)
        assertEquals(false, viewModel.uiState.value.messages[1].isUnread)
    }

    @Test
    fun nonSystemNoticeLoad_doesNotAutoSendPageRead() = runTest(testDispatcher) {
        // 仅系统通知启用浏览即清；赞/回复等分类浏览不发 read（官方同款约束）
        val mock = MockApi()
        mock.route(
            "api/push/reply",
            pushJson("""{"id":11,"type":2,"state":0,"content":"有人回复了你"}""")
        )

        val viewModel = MessageNoticeViewModel(MessageRepository(mock.api(testDispatcher)))
        viewModel.loadInitial(MessageNoticeNavParam(type = "reply", typeNumber = 2, title = "回复通知"))
        advanceUntilIdle()

        assertEquals(1, mock.requestCount)
        assertEquals(true, viewModel.uiState.value.messages[0].isUnread)
    }
}
