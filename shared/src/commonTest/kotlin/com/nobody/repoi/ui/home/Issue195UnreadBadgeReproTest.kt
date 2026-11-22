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

        assertEquals(2, homeVm.uiState.value.unreadMessageCount)

        val centerVm = MessageCenterViewModel(messageRepository)
        advanceUntilIdle()
        assertEquals(2, centerVm.uiState.value.unreadCounts.system)
        assertEquals(3, centerVm.uiState.value.unreadCounts.postsReply)

        centerVm.markAllRead()
        advanceUntilIdle()
        accountMock.replaceRoute("api/unread/push-message/count-v2", countJson())

        homeVm.refreshUnreadMessages()
        advanceUntilIdle()
        assertEquals(0, homeVm.uiState.value.unreadMessageCount)
    }

    @Test
    fun postsReplyOnly_serviceCount_returnsHome_redDotNeverLights() = runTest(testDispatcher) {

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

    private fun pushJson(items: String): String =
        """{"success":true,"code":200,"msg":"ok","data":{"pushMessage":[$items]}}"""

    @Test
    fun markItemRead_sendsItemTypeNotPageTypeNumber() = runTest(testDispatcher) {

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
