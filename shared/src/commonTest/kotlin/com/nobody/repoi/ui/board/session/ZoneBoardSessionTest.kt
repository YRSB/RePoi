package com.nobody.repoi.ui.board.session

import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.ZoneQueryScope
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.ItemRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.ResaleRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.item.list.components.HobbyAdvancedFilterParam
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ZoneBoardSessionTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        testDispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    private inner class TestEnv(val testScope: TestScope, val mockApi: MockApi) {
        val preferences = ViewPreferencesManager(TestDataStore.inMemory())
        val homeRepository = HomeRepository(mockApi.api(testDispatcher))
        val albumRepository = AlbumRepository(mockApi.api(testDispatcher))
        val zoneRepository = ZoneRepository(mockApi.api(testDispatcher), preferences)
        val itemRepository = ItemRepository(mockApi.api(testDispatcher))
        val resaleRepository = ResaleRepository(mockApi.api(testDispatcher))

        val factory = ZoneBoardSessionImpl.Factory(
            homeRepository = homeRepository,
            albumRepository = albumRepository,
            zoneRepository = zoneRepository,
            itemRepository = itemRepository,
            resaleRepository = resaleRepository,
            viewPreferencesManager = preferences
        )

        fun createSession(
            initialScope: ZoneQueryScope = ZoneQueryScope.All,
            initialBoard: ZoneBoard = ZoneBoard.Action
        ): ZoneBoardSession {
            return factory.create(
                coroutineScope = testScope,
                initialScope = initialScope,
                initialBoard = initialBoard
            )
        }
    }

    private fun setupDefaultRoutes(mockApi: MockApi) {
        mockApi.route(
            "api/user-action/list-v3",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1,"actionDate":"2026-11-18 10:00:00","actionText":"动态1"},{"id":2,"actionDate":"2026-11-18 09:00:00","actionText":"动态2"}]}}"""
        )
        mockApi.route(
            "api/album/list",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":101,"nameCN":"相册1"},{"id":102,"nameCN":"相册2"}]}}"""
        )
        mockApi.route(
            "api/posts/search",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":201,"title":"讨论1","isPinned":false},{"id":202,"title":"讨论2","isPinned":false}]}}"""
        )
        mockApi.route(
            "api/hobby/query-v2",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":301,"nameCN":"手办1","cover":"c1.jpg"},{"id":302,"nameCN":"手办2","cover":"c2.jpg"}]}}"""
        )
        mockApi.route(
            "api/resale/search",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":401,"title":"二手1","addDate":"2026-11-18 10:00:00"},{"id":402,"title":"二手2","addDate":"2026-11-18 09:00:00"}]}}"""
        )
        mockApi.route(
            "api/zone/items",
            """{"success":true,"code":200,"msg":"ok","data":{"hasMore":true,"list":[{"targetId":501,"name":"实体1"},{"targetId":502,"name":"实体2"}]}}"""
        )
    }

    @Test
    fun testInitialLazyLoadingAndBoardSwitching() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val session = env.createSession(initialBoard = ZoneBoard.Action)

        assertEquals(ZoneBoard.Action, session.activeSnapshot.value.board)
        advanceUntilIdle()

        val actionSnapshot = session.activeSnapshot.value
        assertEquals(ZoneBoard.Action, actionSnapshot.board)
        val actionContent = actionSnapshot.content as ZoneBoardContent.Action
        assertEquals(2, actionContent.items.size)
        assertFalse(actionSnapshot.pageView.isLoading)

        session.dispatch(ZoneBoardIntent.SelectBoard(ZoneBoard.Album))
        assertEquals(ZoneBoard.Album, session.activeSnapshot.value.board)
        advanceUntilIdle()

        val albumSnapshot = session.activeSnapshot.value
        assertEquals(ZoneBoard.Album, albumSnapshot.board)
        val albumContent = albumSnapshot.content as ZoneBoardContent.Album
        assertEquals(2, albumContent.items.size)
        assertEquals(101L, albumContent.items[0].targetAlbumId)

        session.dispatch(ZoneBoardIntent.SelectBoard(ZoneBoard.Action))
        assertEquals(ZoneBoard.Action, session.activeSnapshot.value.board)
        val actionRestored = session.activeSnapshot.value.content as ZoneBoardContent.Action
        assertEquals(2, actionRestored.items.size)

        val postsSnapshot = session.snapshotFor(ZoneBoard.Posts)
        assertEquals(ZoneBoard.Posts, postsSnapshot.board)
        advanceUntilIdle()
        val postsLoaded = session.snapshotFor(ZoneBoard.Posts)
        val postsContent = postsLoaded.content as ZoneBoardContent.Posts
        assertEquals(2, postsContent.items.size)
    }

    @Test
    fun testFilterChangeResetsPagingAndScrollMemory() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val session = env.createSession(initialBoard = ZoneBoard.Action)
        advanceUntilIdle()

        session.dispatch(ZoneBoardIntent.SaveScrollPosition(ZoneBoard.Action, index = 5, offset = 120))
        session.dispatch(ZoneBoardIntent.SaveFilterCollapseFraction(ZoneBoard.Action, 0.75f))
        assertEquals(5, session.activeSnapshot.value.scrollPosition.index)
        assertEquals(120, session.activeSnapshot.value.scrollPosition.offset)
        assertEquals(0.75f, session.activeSnapshot.value.scrollPosition.filterCollapseFraction)

        mockApi.replaceRoute(
            "api/user-action/list-v3",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":10,"actionDate":"2026-11-18 11:00:00","actionText":"新子类动态"}]}}"""
        )
        session.dispatch(ZoneBoardIntent.SelectSubType("create"))
        advanceUntilIdle()

        val updatedAction = session.activeSnapshot.value
        assertEquals("create", updatedAction.filters.subType)
        val updatedContent = updatedAction.content as ZoneBoardContent.Action
        assertEquals(1, updatedContent.items.size)
        assertEquals(10L, updatedContent.items[0].id)

        assertEquals(0, updatedAction.scrollPosition.index)
        assertEquals(0, updatedAction.scrollPosition.offset)
        assertEquals(0f, updatedAction.scrollPosition.filterCollapseFraction)

        session.dispatch(ZoneBoardIntent.SelectBoard(ZoneBoard.Album))
        advanceUntilIdle()
        session.dispatch(ZoneBoardIntent.SaveScrollPosition(ZoneBoard.Album, index = 8, offset = 50))
        assertEquals(8, session.activeSnapshot.value.scrollPosition.index)

        session.dispatch(ZoneBoardIntent.SelectAlbumOrder("hits"))
        advanceUntilIdle()
        assertEquals("hits", session.activeSnapshot.value.filters.albumOrder)
        assertEquals(0, session.activeSnapshot.value.scrollPosition.index)

        session.dispatch(ZoneBoardIntent.SelectBoard(ZoneBoard.Posts))
        advanceUntilIdle()
        session.dispatch(ZoneBoardIntent.SaveScrollPosition(ZoneBoard.Posts, index = 3, offset = 20))
        session.dispatch(ZoneBoardIntent.SelectPostsOrder("add"))
        advanceUntilIdle()
        assertEquals("add", session.activeSnapshot.value.filters.postsOrder)
        assertEquals(0, session.activeSnapshot.value.scrollPosition.index)

        session.dispatch(ZoneBoardIntent.SelectBoard(ZoneBoard.Resale))
        advanceUntilIdle()
        session.dispatch(ZoneBoardIntent.SaveScrollPosition(ZoneBoard.Resale, index = 4, offset = 10))
        session.dispatch(ZoneBoardIntent.SelectResaleType(2))
        advanceUntilIdle()
        assertEquals(2, session.activeSnapshot.value.filters.resaleType)
        assertEquals(0, session.activeSnapshot.value.scrollPosition.index)

        session.dispatch(ZoneBoardIntent.SelectBoard(ZoneBoard.Hobby))
        advanceUntilIdle()
        session.dispatch(ZoneBoardIntent.SaveScrollPosition(ZoneBoard.Hobby, index = 7, offset = 30))
        session.dispatch(ZoneBoardIntent.ApplyHobbyFilter(HobbyAdvancedFilterParam(sex = 1, scale = "1/7")))
        advanceUntilIdle()
        assertEquals(1, session.activeSnapshot.value.filters.hobby.sex)
        assertEquals("1/7", session.activeSnapshot.value.filters.hobby.scale)
        assertEquals(0, session.activeSnapshot.value.scrollPosition.index)

        session.dispatch(ZoneBoardIntent.ResetHobbyFilter)
        advanceUntilIdle()
        assertNull(session.activeSnapshot.value.filters.hobby.sex)
    }

    @Test
    fun testLoadMoreAndDeduplication() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val session = env.createSession(initialBoard = ZoneBoard.Action)
        advanceUntilIdle()

        val initialContent = session.activeSnapshot.value.content as ZoneBoardContent.Action
        assertEquals(2, initialContent.items.size)

        mockApi.replaceRoute(
            "api/user-action/list-v3",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":2,"actionDate":"2026-11-18 09:00:00","actionText":"动态2"},{"id":3,"actionDate":"2026-11-18 08:00:00","actionText":"动态3"}]}}"""
        )

        session.dispatch(ZoneBoardIntent.LoadMore())
        advanceUntilIdle()

        val appendedContent = session.activeSnapshot.value.content as ZoneBoardContent.Action

        assertEquals(3, appendedContent.items.size)
        assertEquals(listOf(1L, 2L, 3L), appendedContent.items.map { it.id })

        mockApi.replaceRoute(
            "api/user-action/list-v3",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[]}}"""
        )
        session.dispatch(ZoneBoardIntent.LoadMore())
        advanceUntilIdle()

        assertTrue(session.activeSnapshot.value.pageView.isEndReached)
    }

    @Test
    fun testScopeUpdateCancelsAndResets() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val session = env.createSession(initialScope = ZoneQueryScope.All, initialBoard = ZoneBoard.Action)
        advanceUntilIdle()

        session.dispatch(ZoneBoardIntent.SelectBoard(ZoneBoard.Album))
        advanceUntilIdle()
        session.dispatch(ZoneBoardIntent.SaveScrollPosition(ZoneBoard.Album, 5, 20))
        session.dispatch(ZoneBoardIntent.SaveScrollPosition(ZoneBoard.Action, 2, 10))

        session.updateScope(ZoneQueryScope.Single(100L))
        advanceUntilIdle()

        val currentSnapshot = session.activeSnapshot.value
        assertEquals(ZoneBoard.Album, currentSnapshot.board)

        assertEquals(0, currentSnapshot.scrollPosition.index)
        assertEquals(0, session.snapshotFor(ZoneBoard.Action).scrollPosition.index)

        session.updateScope(ZoneQueryScope.Follow(emptyList()))
        advanceUntilIdle()

        val followEmptySnapshot = session.activeSnapshot.value
        assertTrue(followEmptySnapshot.pageView.isEndReached)
        val albumEmptyContent = followEmptySnapshot.content as ZoneBoardContent.Album
        assertTrue(albumEmptyContent.items.isEmpty())
    }

    @Test
    fun testErrorIsolationAndRetry() = runTest(testDispatcher) {
        val mockApi = MockApi()

        mockApi.route(
            "api/user-action/list-v3",
            """{"success":false,"code":500,"msg":"内部错误"}""",
            status = HttpStatusCode.InternalServerError
        )
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val session = env.createSession(initialBoard = ZoneBoard.Action)
        advanceUntilIdle()

        val errorSnapshot = session.activeSnapshot.value
        assertNotNull(errorSnapshot.pageView.errorMessage)
        val actionContent = errorSnapshot.content as ZoneBoardContent.Action
        assertTrue(actionContent.items.isEmpty())

        session.dispatch(ZoneBoardIntent.SelectBoard(ZoneBoard.Album))
        advanceUntilIdle()
        val albumSnapshot = session.activeSnapshot.value
        assertNull(albumSnapshot.pageView.errorMessage)
        val albumContent = albumSnapshot.content as ZoneBoardContent.Album
        assertEquals(2, albumContent.items.size)

        mockApi.replaceRoute(
            "api/user-action/list-v3",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1,"actionDate":"2026-11-18 10:00:00","actionText":"动态1"}]}}"""
        )
        session.dispatch(ZoneBoardIntent.RetryInitial(ZoneBoard.Action))
        advanceUntilIdle()

        val recoveredAction = session.snapshotFor(ZoneBoard.Action)
        assertNull(recoveredAction.pageView.errorMessage)
        val recoveredContent = recoveredAction.content as ZoneBoardContent.Action
        assertEquals(1, recoveredContent.items.size)
    }

    @Test
    fun testScrollPositionMemory() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val session = env.createSession(initialBoard = ZoneBoard.Hobby)
        advanceUntilIdle()

        session.dispatch(ZoneBoardIntent.SaveScrollPosition(ZoneBoard.Hobby, index = 12, offset = 340))
        session.dispatch(ZoneBoardIntent.SaveFilterCollapseFraction(ZoneBoard.Hobby, 0.6f))
        session.dispatch(ZoneBoardIntent.SaveFilterRowScroll(ZoneBoard.Hobby, 88))

        val snapshot = session.activeSnapshot.value
        assertEquals(12, snapshot.scrollPosition.index)
        assertEquals(340, snapshot.scrollPosition.offset)
        assertEquals(0.6f, snapshot.scrollPosition.filterCollapseFraction)
        assertEquals(88, snapshot.scrollPosition.filterRowScrollX)

        session.dispatch(ZoneBoardIntent.ResetScrollMemory(ZoneBoard.Hobby))
        val resetSnapshot = session.activeSnapshot.value
        assertEquals(0, resetSnapshot.scrollPosition.index)
        assertEquals(0, resetSnapshot.scrollPosition.offset)
    }

    @Test
    fun testHobbyViewModeToggle() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val session = env.createSession(initialBoard = ZoneBoard.Hobby)
        advanceUntilIdle()

        val initialContent = session.activeSnapshot.value.content as ZoneBoardContent.Hobby
        assertTrue(initialContent.isGridMode)

        session.dispatch(ZoneBoardIntent.ToggleHobbyViewMode)
        advanceUntilIdle()

        val toggledContent = session.activeSnapshot.value.content as ZoneBoardContent.Hobby
        assertFalse(toggledContent.isGridMode)
    }

    @Test
    fun testResaleLoadMoreDeduplicationReachesEnd() = runTest(testDispatcher) {
        val mockApi = MockApi()
        val firstPageItems = (1..24).joinToString(",") { id ->
            """{"id":$id,"title":"二手$id","addDate":"2026-11-18 10:00:00"}"""
        }
        mockApi.route(
            "api/resale/search",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[$firstPageItems]}}"""
        )
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val session = env.createSession(initialBoard = ZoneBoard.Resale)
        advanceUntilIdle()

        val initialSnapshot = session.activeSnapshot.value
        assertEquals(ZoneBoard.Resale, initialSnapshot.board)
        val initialContent = initialSnapshot.content as ZoneBoardContent.Resale
        assertEquals(24, initialContent.items.size)
        assertFalse(initialSnapshot.pageView.isEndReached)

        session.dispatch(ZoneBoardIntent.LoadMore(ZoneBoard.Resale))
        advanceUntilIdle()

        val secondSnapshot = session.activeSnapshot.value
        val secondContent = secondSnapshot.content as ZoneBoardContent.Resale
        assertEquals(24, secondContent.items.size)

        assertTrue(secondSnapshot.pageView.isEndReached)
    }

    @Test
    fun testPostsLoadMoreDeduplicatesAgainstPinnedPosts() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)

        val pinnedPost = """{"id":999,"title":"置顶讨论","topTime":1000}"""
        val normalPosts = (1..19).joinToString(",") { id ->
            """{"id":$id,"title":"讨论$id","isPinned":false}"""
        }
        mockApi.replaceRoute(
            "api/posts/search",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[$pinnedPost,$normalPosts]}}"""
        )
        val env = TestEnv(this, mockApi)

        val session = env.createSession(
            initialScope = ZoneQueryScope.Single(100L),
            initialBoard = ZoneBoard.Posts
        )
        advanceUntilIdle()

        val initialSnapshot = session.activeSnapshot.value
        assertEquals(ZoneBoard.Posts, initialSnapshot.board)
        val initialContent = initialSnapshot.content as ZoneBoardContent.Posts
        assertEquals(1, initialContent.pinnedPosts.size)
        assertEquals(999L, initialContent.pinnedPosts[0].id)
        assertEquals(19, initialContent.items.size)
        assertFalse(initialSnapshot.pageView.isEndReached)

        val duplicatePage = """{"id":999,"title":"置顶讨论","isPinned":false},""" +
            (1..19).joinToString(",") { id ->
                """{"id":$id,"title":"讨论$id","isPinned":false}"""
            }
        mockApi.replaceRoute(
            "api/posts/search",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[$duplicatePage]}}"""
        )

        session.dispatch(ZoneBoardIntent.LoadMore(ZoneBoard.Posts))
        advanceUntilIdle()

        val secondSnapshot = session.activeSnapshot.value
        val secondContent = secondSnapshot.content as ZoneBoardContent.Posts

        assertEquals(19, secondContent.items.size)

        assertTrue(secondSnapshot.pageView.isEndReached)
    }
}
