package com.nobody.repoi.ui.zone

import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.ItemRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.ResaleRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.ui.board.ZoneBoard
import com.nobody.repoi.ui.board.ZoneBoardContent
import com.nobody.repoi.ui.board.session.ZoneBoardSessionImpl
import com.nobody.repoi.ui.item.list.components.HobbyAdvancedFilterParam
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
import kotlin.test.assertTrue

/**
 * 专区详情页 ViewModel（ZoneDetailViewModel）单测。
 *
 * 验证：
 * 1. 构造初始化与 [ZoneBoardSession] 创建；
 * 2. [ZoneDetailViewModel.setNodeId] 作用域更新与专区信息加载；
 * 3. 板块切换意图派发与相册分类懒同步；
 * 4. 关注状态切换（toggleFocus）；
 * 5. 各板块筛选/排序/搜索意图向 [ZoneBoardSession] 的转发；
 * 6. 滚动记忆的保存与读取委托；
 * 7. 二手交易举报提交；
 * 8. 刷新意图分发。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ZoneDetailViewModelTest {

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

    private class TestEnv(val testScope: TestScope, val mockApi: MockApi) {
        val dispatcher = testScope.coroutineContext[kotlinx.coroutines.CoroutineDispatcher]!!
        val preferences = ViewPreferencesManager(TestDataStore.inMemory())
        val homeRepository = HomeRepository(mockApi.api(dispatcher))
        val albumRepository = AlbumRepository(mockApi.api(dispatcher))
        val zoneRepository = ZoneRepository(mockApi.api(dispatcher), preferences)
        val itemRepository = ItemRepository(mockApi.api(dispatcher))
        val resaleRepository = ResaleRepository(mockApi.api(dispatcher))
        val collectRepository = CollectRepository(mockApi.api(dispatcher), mockApi.json, FakeUserSession("utoken-test"))
        val categoryManager = CategoryManager(mockApi.api(dispatcher))

        val sessionFactory = ZoneBoardSessionImpl.Factory(
            homeRepository = homeRepository,
            albumRepository = albumRepository,
            zoneRepository = zoneRepository,
            itemRepository = itemRepository,
            resaleRepository = resaleRepository,
            viewPreferencesManager = preferences
        )

        fun createViewModel(): ZoneDetailViewModel {
            return ZoneDetailViewModel(
                zoneRepository = zoneRepository,
                collectRepository = collectRepository,
                categoryManager = categoryManager,
                albumRepository = albumRepository,
                sessionFactory = sessionFactory
            )
        }
    }

    private fun setupDefaultRoutes(mockApi: MockApi) {
        mockApi.route(
            "api/zone/info",
            """{"success":true,"code":200,"msg":"ok","data":{"zone":{"nodeId":100,"nameCN":"初音未来","icon":"icon.jpg","cover":"cover.jpg"},"focusState":true,"followCount":888,"tabs":[{"type":"action","title":"动态"},{"type":"album","title":"相册"},{"type":"posts","title":"讨论"},{"type":"hobby","title":"手办"},{"type":"resale_item","title":"集市"}]}}"""
        )
        mockApi.route(
            "api/zone/focus",
            """{"success":true,"code":200,"msg":"ok","data":{"focus":false,"count":887}}"""
        )
        mockApi.route(
            "api/zone/categories",
            """{"success":true,"code":200,"msg":"ok","data":[{"categoryId":1,"name":"手办"},{"categoryId":2,"name":"周边"}]}"""
        )
        mockApi.route(
            "api/report",
            """{"success":true,"code":200,"msg":"ok","data":{}}"""
        )
        mockApi.route(
            "api/user-action/list-v3",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1,"actionDate":"2026-11-18 10:00:00","actionText":"动态1"},{"id":2,"actionDate":"2026-11-18 09:00:00","actionText":"动态2"}]}}"""
        )
        mockApi.route(
            "api/album/list",
            """{"success":true,"code":200,"msg":"ok","data":{"hasMore":true,"items":[{"targetAlbumId":101,"title":"相册1"}]}}"""
        )
        mockApi.route(
            "api/posts/search",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":201,"title":"讨论1","isPinned":false}]}}"""
        )
        mockApi.route(
            "api/hobby/query-v2",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":301,"nameCN":"手办1","cover":"c1.jpg"}]}}"""
        )
        mockApi.route(
            "api/resale/search",
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":401,"title":"二手1","addDate":"2026-11-18 10:00:00"}]}}"""
        )
        mockApi.route(
            "api/zone/items",
            """{"success":true,"code":200,"msg":"ok","data":{"hasMore":true,"list":[{"targetId":501,"name":"厂商1"}]}}"""
        )
    }

    @Test
    fun testViewModelInitializationAndSessionCreation() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val vm = env.createViewModel()

        assertEquals(0L, vm.nodeId)
        assertNotNull(vm.session)
        assertEquals(ZoneBoard.Action, vm.activeSnapshot.value.board)
        assertEquals("action", vm.uiState.value.selectedTab)
        assertFalse(vm.uiState.value.showPublishSheet)
    }

    @Test
    fun testSetNodeIdUpdatesScopeAndLoadsZoneInfo() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val vm = env.createViewModel()
        vm.setNodeId(100L, initialName = "初音未来", initialIcon = "icon.jpg", initialCover = "cover.jpg")
        advanceUntilIdle()

        assertEquals(100L, vm.nodeId)
        val state = vm.uiState.value
        assertEquals("初音未来", state.zoneInfo?.displayName)
        assertTrue(state.focusState)
        assertEquals(888L, state.followCount)
        assertFalse(state.isInfoLoading)
        assertEquals(5, state.availableTabs.size)

        // 验证当前板块为 Action 且已完成加载
        assertEquals(ZoneBoard.Action, vm.activeSnapshot.value.board)
        val actionContent = vm.activeSnapshot.value.content as ZoneBoardContent.Action
        assertEquals(2, actionContent.items.size)
    }

    @Test
    fun testSelectTabDispatchesToSessionAndSyncsCategories() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val vm = env.createViewModel()
        vm.setNodeId(100L)
        advanceUntilIdle()

        // 切换到相册 Tab
        vm.selectTab("album")
        advanceUntilIdle()

        assertEquals("album", vm.uiState.value.selectedTab)
        assertEquals(ZoneBoard.Album, vm.activeSnapshot.value.board)
        val albumContent = vm.activeSnapshot.value.content as ZoneBoardContent.Album
        assertEquals(1, albumContent.items.size)

        // 验证相册单专区分类已同步
        assertEquals(2, vm.uiState.value.albumCategoryOptions.size)
        assertEquals(1, vm.uiState.value.albumCategoryOptions[0].id)
        assertEquals("手办", vm.uiState.value.albumCategoryOptions[0].name)
    }

    @Test
    fun testToggleFocus() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val vm = env.createViewModel()
        vm.setNodeId(100L)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.focusState)
        assertEquals(888L, vm.uiState.value.followCount)

        vm.toggleFocus()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.focusState)
        assertEquals(887L, vm.uiState.value.followCount)
    }

    @Test
    fun testBoardActionsForwarding() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val vm = env.createViewModel()
        vm.setNodeId(100L)
        advanceUntilIdle()

        // 1. Action 板块子分类切换
        vm.selectSubType("create")
        advanceUntilIdle()
        assertEquals("create", vm.activeSnapshot.value.filters.subType)

        // 2. Album 板块排序与分类切换
        vm.selectTab("album")
        advanceUntilIdle()
        vm.selectAlbumOrder("hits")
        advanceUntilIdle()
        assertEquals("hits", vm.activeSnapshot.value.filters.albumOrder)
        vm.selectAlbumCategory(1)
        advanceUntilIdle()
        assertEquals(1, vm.activeSnapshot.value.filters.albumCategory)

        // 3. Posts 板块排序切换
        vm.selectTab("posts")
        advanceUntilIdle()
        vm.selectPostsOrder("add")
        advanceUntilIdle()
        assertEquals("add", vm.activeSnapshot.value.filters.postsOrder)

        // 4. Resale 板块类型、成色与搜索
        vm.selectTab("resale_item")
        advanceUntilIdle()
        vm.setResaleType(2)
        advanceUntilIdle()
        assertEquals(2, vm.activeSnapshot.value.filters.resaleType)
        vm.setResalePurity("95")
        advanceUntilIdle()
        assertEquals("95", vm.activeSnapshot.value.filters.resalePurity)
        vm.updateResaleKeyword("手办")
        assertEquals("手办", vm.activeSnapshot.value.filters.resaleKeyword)
        vm.submitResaleSearch()
        advanceUntilIdle()

        // 5. Hobby 板块排序、分类、高级筛选、搜索与视图切换
        vm.selectTab("hobby")
        advanceUntilIdle()
        vm.setHobbyOrder("release_desc")
        advanceUntilIdle()
        assertEquals("release_desc", vm.activeSnapshot.value.filters.hobby.order)
        vm.setHobbyYear(2026)
        advanceUntilIdle()
        assertEquals(2026, vm.activeSnapshot.value.filters.hobby.releaseYear)
        vm.setHobbyMonth(11)
        advanceUntilIdle()
        assertEquals(11, vm.activeSnapshot.value.filters.hobby.releaseMonth)
        vm.selectHobbyCategory(100, 101)
        advanceUntilIdle()
        assertEquals(100, vm.activeSnapshot.value.filters.hobby.categoryId)
        assertEquals(101, vm.activeSnapshot.value.filters.hobby.subCategoryId)

        vm.applyHobbyAdvancedFilters(sex = 1, specs = "PVC", scale = "1/7", r18 = 0)
        advanceUntilIdle()
        assertEquals(1, vm.activeSnapshot.value.filters.hobby.sex)
        assertEquals("PVC", vm.activeSnapshot.value.filters.hobby.specs)
        assertEquals("1/7", vm.activeSnapshot.value.filters.hobby.scale)

        vm.resetHobbyAdvancedFilters()
        advanceUntilIdle()
        kotlin.test.assertNull(vm.activeSnapshot.value.filters.hobby.sex)

        vm.updateHobbyKeyword("初音")
        assertEquals("初音", vm.activeSnapshot.value.filters.hobby.keyword)
        vm.submitHobbySearch()
        advanceUntilIdle()

        val initialGrid = (vm.activeSnapshot.value.content as ZoneBoardContent.Hobby).isGridMode
        vm.toggleHobbyViewMode()
        advanceUntilIdle()
        val toggledGrid = (vm.activeSnapshot.value.content as ZoneBoardContent.Hobby).isGridMode
        assertEquals(!initialGrid, toggledGrid)

        vm.openHobbyFilterSheet(true)
        assertTrue(vm.activeSnapshot.value.filters.showHobbyFilterSheet)
        vm.openHobbyFilterSheet(false)
        assertFalse(vm.activeSnapshot.value.filters.showHobbyFilterSheet)

        vm.openHobbyCategoryPicker(true)
        assertTrue(vm.activeSnapshot.value.filters.showHobbyCategoryPickerSheet)
        vm.openHobbyCategoryPicker(false)
        assertFalse(vm.activeSnapshot.value.filters.showHobbyCategoryPickerSheet)
    }

    @Test
    fun testScrollPositionDelegation() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val vm = env.createViewModel()
        vm.setNodeId(100L)
        advanceUntilIdle()

        vm.saveTabScrollPosition("action", index = 5, offset = 120)
        val pos = vm.getTabScrollPosition("action")
        assertEquals(5, pos.first)
        assertEquals(120, pos.second)
    }

    @Test
    fun testSubmitResaleReport() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val vm = env.createViewModel()
        var reportedSuccess = false
        var reportedMsg = ""

        vm.submitResaleReport(401L, "涉嫌虚假信息") { success, msg ->
            reportedSuccess = success
            reportedMsg = msg
        }
        advanceUntilIdle()

        assertTrue(reportedSuccess)
        assertEquals("举报成功，感谢您的反馈", reportedMsg)
    }

    @Test
    fun testShowPublishSheet() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val vm = env.createViewModel()
        assertFalse(vm.uiState.value.showPublishSheet)
        vm.showPublishSheet(true)
        assertTrue(vm.uiState.value.showPublishSheet)
        vm.showPublishSheet(false)
        assertFalse(vm.uiState.value.showPublishSheet)
    }

    @Test
    fun testRefreshDelegation() = runTest(testDispatcher) {
        val mockApi = MockApi()
        setupDefaultRoutes(mockApi)
        val env = TestEnv(this, mockApi)

        val vm = env.createViewModel()
        vm.setNodeId(100L)
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        // 验证刷新后数据保持完整
        assertEquals(100L, vm.nodeId)
        val actionContent = vm.activeSnapshot.value.content as ZoneBoardContent.Action
        assertEquals(2, actionContent.items.size)
    }
}
