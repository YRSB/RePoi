package com.nobody.repoi.ui.collect

import app.cash.turbine.test
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.CollectConstants
import com.nobody.repoi.data.remote.model.CollectData
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.remote.model.UserProfile
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.FollowRepository
import com.nobody.repoi.data.repository.UserFinanceRepository
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.data.session.SessionState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * 用户全量收藏中枢 ViewModel 状态机单测（Turbine + Kotest）。
 *
 * 验证大类切换（手办/周边/相册/图片/作品）、收藏状态变更、批量管理多选模式流转，
 * 以及统一收藏事件 [CollectEvent] 触发的内存过滤与精准刷新自愈（ADR-0011/T3）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserCollectViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        testDispatcher.cancelChildren()
        Dispatchers.resetMain()
    }

    private data class Fixture(
        val mock: MockApi,
        val session: FakeUserSession,
        val collectRepo: CollectRepository,
        val viewModel: UserCollectViewModel,
    )

    private fun fixture(userId: Long = 999L): Fixture {
        val mock = MockApi()
        val session = FakeUserSession(
            SessionState.Authenticated(
                userId = userId,
                token = "test-token",
                profile = UserProfile(id = userId),
            )
        )
        val followRepo = FollowRepository(mock.api(testDispatcher), mock.json, session)
        val financeRepo = UserFinanceRepository(mock.api(testDispatcher))
        val userRepo = UserRepository(mock.api(testDispatcher), session, followRepo, financeRepo)
        val collectRepo = CollectRepository(mock.api(testDispatcher), mock.json)
        val preferences = ViewPreferencesManager(TestDataStore.viewPrefs())

        val viewModel = UserCollectViewModel(
            userRepository = userRepo,
            collectRepository = collectRepo,
            viewPreferencesManager = preferences,
            userSession = session,
            navUserId = userId,
        )
        return Fixture(mock, session, collectRepo, viewModel)
    }

    @Test
    fun initialSetup_matchesNavParameters() = runTest(testDispatcher) {
        // 首屏 hobby-v2 先回空列表，避免初始化请求悬空
        val (mock, _, _, viewModel) = fixture(userId = 888L)
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        testScheduler.advanceUntilIdle()

        viewModel.userId shouldBe 888L
        viewModel.uiState.value.selectedCategory shouldBe UserCollectCategory.HOBBY
        viewModel.uiState.value.hobbySelectionMode shouldBe false
        viewModel.uiState.value.hobbySelectedIds.shouldBeEmpty()
    }

    @Test
    fun switchCategory_updatesCategoryState() = runTest(testDispatcher) {
        val (mock, _, _, viewModel) = fixture()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        testScheduler.advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem().selectedCategory shouldBe UserCollectCategory.HOBBY

            viewModel.switchCategory(UserCollectCategory.ALBUM)
            awaitItem().selectedCategory shouldBe UserCollectCategory.ALBUM

            viewModel.switchCategory(UserCollectCategory.WORKS)
            awaitItem().selectedCategory shouldBe UserCollectCategory.WORKS
        }
    }

    @Test
    fun switchCollectState_updatesStateKey() = runTest(testDispatcher) {
        val (mock, _, _, viewModel) = fixture()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        testScheduler.advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem().collectState shouldBe CollectStateOption.DEFAULT

            viewModel.switchCollectState(CollectStateOption.CARE)
            awaitItem().collectState shouldBe CollectStateOption.CARE

            viewModel.switchCollectState(CollectStateOption.BUY)
            awaitItem().collectState shouldBe CollectStateOption.BUY
        }
    }

    @Test
    fun toggleSelectionMode_flipsSelectionFlagAndClearsSelectedIds() = runTest(testDispatcher) {
        val (mock, _, _, viewModel) = fixture()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        testScheduler.advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem().hobbySelectionMode shouldBe false

            viewModel.toggleSelectionMode()
            awaitItem().hobbySelectionMode shouldBe true

            viewModel.toggleSelectionMode()
            awaitItem().hobbySelectionMode shouldBe false
        }
    }

    // ── T3：CollectEvent 自愈（取消类内存过滤零网络，新增/修改类精准刷新）──

    @Test
    fun hobbyCancelEvent_removesItemFromMemoryWithoutNetwork() = runTest(testDispatcher) {
        val (mock, _, collectRepo, viewModel) = fixture()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1001,"itemId":2001,"itemNodeId":7001,"nameCN":"手办A"},{"id":1002,"itemId":2002,"itemNodeId":7002,"nameCN":"手办B"}]}}"""
        )
        testScheduler.advanceUntilIdle()
        viewModel.uiState.value.hobbyData.items.map { it.id }.shouldContainExactlyInAnyOrder(listOf(1001L, 1002L))
        val requestsBefore = mock.requestCount

        // 手办取消：写侧传的是手办节点 id（外层收藏记录 id 与节点 id 不同），须走实体口径命中
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectRepo.deleteCollect(7001L, CollectData(id = 7001L, state = CollectConstants.STATE_BUY))
        testScheduler.advanceUntilIdle()

        viewModel.uiState.value.hobbyData.items.map { it.id } shouldBe listOf(1002L)
        // 内存过滤零网络：除首屏与取消写操作外无新增列表请求
        mock.requestCount shouldBe requestsBefore + 1
    }

    @Test
    fun hobbyAddEvent_onActiveCategory_refreshesImmediately() = runTest(testDispatcher) {
        val (mock, _, collectRepo, viewModel) = fixture()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1001,"itemId":2001,"nameCN":"手办A"}]}}"""
        )
        testScheduler.advanceUntilIdle()
        val requestsBefore = mock.requestCount

        // 新增/修改：手办是当前活跃分类，事件到达后应立即精准刷新
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1001,"itemId":2001,"nameCN":"手办A"},{"id":1003,"itemId":2003,"nameCN":"新手办"}]}}"""
        )
        val data = CollectData(id = 1003L, state = CollectConstants.STATE_BUY)
        collectRepo.saveCollect(1003L, data)
        testScheduler.advanceUntilIdle()

        viewModel.uiState.value.hobbyData.items.map { it.id }.shouldContainExactlyInAnyOrder(listOf(1001L, 1003L))
        // 写操作 1 次 + 刷新拉取 1 次
        mock.requestCount shouldBe requestsBefore + 2
    }

    @Test
    fun hobbyAddEvent_onInactiveCategory_marksStaleWithoutNetwork() = runTest(testDispatcher) {
        val (mock, _, collectRepo, viewModel) = fixture()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1001,"itemId":2001,"nameCN":"手办A"}]}}"""
        )
        testScheduler.advanceUntilIdle()
        // 切到图片页签：手办离开活跃态（但已加载过）
        viewModel.switchCategory(UserCollectCategory.PICTURE)
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        testScheduler.advanceUntilIdle()
        val requestsBefore = mock.requestCount

        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectRepo.saveCollect(1003L, CollectData(id = 1003L, state = CollectConstants.STATE_BUY))
        testScheduler.advanceUntilIdle()

        // 非活跃分类不得发刷新请求，只登记脏标记
        mock.requestCount shouldBe requestsBefore + 1
        viewModel.uiState.value.staleCategories.shouldContain(UserCollectCategory.HOBBY)

        // 切回手办时补一次刷新，列表带出新增项
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1001,"itemId":2001,"nameCN":"手办A"},{"id":1003,"itemId":2003,"nameCN":"新手办"}]}}"""
        )
        viewModel.switchCategory(UserCollectCategory.HOBBY)
        testScheduler.advanceUntilIdle()

        viewModel.uiState.value.hobbyData.items.map { it.id }.shouldContainExactlyInAnyOrder(listOf(1001L, 1003L))
        viewModel.uiState.value.staleCategories shouldBe emptySet()
    }

    @Test
    fun pictureCancelEvent_removesItemFromMemoryWithoutNetwork() = runTest(testDispatcher) {
        val (mock, _, collectRepo, viewModel) = fixture()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        testScheduler.advanceUntilIdle()
        viewModel.switchCategory(UserCollectCategory.PICTURE)
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":9001,"categoryId":80001,"itemId":4235086,"itemType":"picture","collect":2,"path":"2026/09/a.jpg"},{"id":9002,"categoryId":80001,"itemId":4235087,"itemType":"picture","collect":2,"path":"2026/09/b.jpg"}]}}"""
        )
        testScheduler.advanceUntilIdle()
        viewModel.uiState.value.picData.items.map { it.id }.shouldContainExactlyInAnyOrder(listOf(9001L, 9002L))
        val requestsBefore = mock.requestCount

        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectRepo.collectItemDel(9001L, UserCollectCategory.PICTURE)
        testScheduler.advanceUntilIdle()

        viewModel.uiState.value.picData.items.map { it.id } shouldBe listOf(9002L)
        mock.requestCount shouldBe requestsBefore + 1
    }

    @Test
    fun pictureAddEvent_refreshesPictureCategory() = runTest(testDispatcher) {
        val (mock, _, collectRepo, viewModel) = fixture()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        testScheduler.advanceUntilIdle()
        viewModel.switchCategory(UserCollectCategory.PICTURE)
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":9001,"categoryId":80001,"itemId":4235086,"itemType":"picture","collect":2,"path":"2026/09/a.jpg"}]}}"""
        )
        testScheduler.advanceUntilIdle()
        val requestsBefore = mock.requestCount

        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":9001,"categoryId":80001,"itemId":4235086,"itemType":"picture","collect":2,"path":"2026/09/a.jpg"},{"id":9003,"categoryId":80001,"itemId":4235088,"itemType":"picture","collect":2,"path":"2026/09/c.jpg"}]}}"""
        )
        collectRepo.collectItemAdd(9003L, UserCollectCategory.PICTURE)
        testScheduler.advanceUntilIdle()

        viewModel.uiState.value.picData.items.map { it.id }.shouldContainExactlyInAnyOrder(listOf(9001L, 9003L))
        mock.requestCount shouldBe requestsBefore + 2
    }

    @Test
    fun worksCancelEvent_removesItemFromMemoryWithoutNetwork() = runTest(testDispatcher) {
        val (mock, _, collectRepo, viewModel) = fixture()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        testScheduler.advanceUntilIdle()
        viewModel.switchCategory(UserCollectCategory.WORKS)
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":20001,"itemId":30001,"nameCN":"作品A","state":2},{"id":20002,"itemId":30002,"nameCN":"作品B","state":2}]}}"""
        )
        testScheduler.advanceUntilIdle()
        viewModel.uiState.value.worksData.items.map { it.id }.shouldContainExactlyInAnyOrder(listOf(20001L, 20002L))
        val requestsBefore = mock.requestCount
        // 取消写侧传的是实体条目 id（外层 id=20001 与之不同，回归 B1 口径错配）
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectRepo.deleteWorksCollect(30001L)
        testScheduler.advanceUntilIdle()

        viewModel.uiState.value.worksData.items.map { it.id } shouldBe listOf(20002L)
        viewModel.uiState.value.worksData.items.map { it.actualItemId } shouldBe listOf(30002L)
        mock.requestCount shouldBe requestsBefore + 1
    }

    @Test
    fun characterCancelEvent_matchesEntityIdWhenOuterIdDiffers() = runTest(testDispatcher) {
        val (mock, _, collectRepo, viewModel) = fixture()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        testScheduler.advanceUntilIdle()
        viewModel.switchCategory(UserCollectCategory.CHARACTER)
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":50001,"itemId":60001,"nameCN":"角色A"},{"id":50002,"itemId":60002,"nameCN":"角色B"}]}}"""
        )
        testScheduler.advanceUntilIdle()
        viewModel.uiState.value.characterData.items.map { it.id }.shouldContainExactlyInAnyOrder(listOf(50001L, 50002L))
        val requestsBefore = mock.requestCount

        // 通用分类同样兼容实体 id：外层 50001 与实体 60001 不一致仍能过滤（建议 2）
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectRepo.collectItemDel(60001L, UserCollectCategory.CHARACTER)
        testScheduler.advanceUntilIdle()

        viewModel.uiState.value.characterData.items.map { it.id } shouldBe listOf(50002L)
        viewModel.uiState.value.characterData.items.map { it.actualItemId } shouldBe listOf(60002L)
        mock.requestCount shouldBe requestsBefore + 1
    }

    @Test
    fun batchCancelEvent_removesAlbumItemsFromMemoryWithoutNetwork() = runTest(testDispatcher) {
        val (mock, _, collectRepo, viewModel) = fixture()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        testScheduler.advanceUntilIdle()
        viewModel.switchCategory(UserCollectCategory.ALBUM)
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":7001,"itemId":8001,"nameCN":"相册A"},{"id":7002,"itemId":8002,"nameCN":"相册B"},{"id":7003,"itemId":8003,"nameCN":"相册C"}]}}"""
        )
        testScheduler.advanceUntilIdle()
        viewModel.uiState.value.albumData.items.map { it.id }.shouldContainExactlyInAnyOrder(listOf(7001L, 7002L, 7003L))
        val requestsBefore = mock.requestCount

        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectRepo.cancelCollectBatch(listOf(7001L, 7003L), UserCollectCategory.ALBUM)
        testScheduler.advanceUntilIdle()

        viewModel.uiState.value.albumData.items.map { it.id } shouldBe listOf(7002L)
        mock.requestCount shouldBe requestsBefore + 1
    }

    @Test
    fun albumCancelEvent_matchesActualAlbumIdOnSingleCancel() = runTest(testDispatcher) {
        val (mock, _, collectRepo, viewModel) = fixture()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        testScheduler.advanceUntilIdle()
        viewModel.switchCategory(UserCollectCategory.ALBUM)
        // 双层结构：外层收藏记录 id 与真实相册 id 不同（相册详情写侧传 actualAlbumId）
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":7001,"itemId":8001,"item":{"id":8001}},{"id":7002,"itemId":8002,"item":{"id":8002}}]}}"""
        )
        testScheduler.advanceUntilIdle()
        viewModel.uiState.value.albumData.items.map { it.id }.shouldContainExactlyInAnyOrder(listOf(7001L, 7002L))
        val requestsBefore = mock.requestCount

        // 相册详情页取消收藏传的是 actualAlbumId(8001)，外层收藏 id(7001) 不同
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectRepo.collectItemDel(8001L, UserCollectCategory.ALBUM)
        testScheduler.advanceUntilIdle()

        viewModel.uiState.value.albumData.items.map { it.id } shouldBe listOf(7002L)
        mock.requestCount shouldBe requestsBefore + 1
    }

    @Test
    fun switchCategory_loadedEmptyPage_doesNotRequestAgain() = runTest(testDispatcher) {
        val (mock, _, _, viewModel) = fixture()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        testScheduler.advanceUntilIdle()
        viewModel.switchCategory(UserCollectCategory.PICTURE)
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        testScheduler.advanceUntilIdle()
        val requestsBefore = mock.requestCount

        // 已加载过的空列表不应因「items 为空」而反复请求
        viewModel.switchCategory(UserCollectCategory.HOBBY)
        viewModel.switchCategory(UserCollectCategory.PICTURE)
        testScheduler.advanceUntilIdle()

        mock.requestCount shouldBe requestsBefore
    }

    @Test
    fun batchCancelEvent_ignoresEntityIdCollision() = runTest(testDispatcher) {
        val (mock, _, collectRepo, viewModel) = fixture()
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{"list":[]}}""")
        testScheduler.advanceUntilIdle()
        viewModel.switchCategory(UserCollectCategory.CHARACTER)
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":50001,"itemId":60001,"nameCN":"角色A"},{"id":50002,"itemId":60002,"nameCN":"角色B"}]}}"""
        )
        testScheduler.advanceUntilIdle()
        val requestsBefore = mock.requestCount

        // 批量取消是外层 id 语义：60002 只与条目 B 的实体 id 相同（外层均不命中），不得误删 B
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectRepo.cancelCollectBatch(listOf(60002L), UserCollectCategory.CHARACTER)
        testScheduler.advanceUntilIdle()
        viewModel.uiState.value.characterData.items.map { it.id }.shouldContainExactlyInAnyOrder(listOf(50001L, 50002L))
        mock.requestCount shouldBe requestsBefore + 1
    }

    @Test
    fun unrelatedCancelEvent_leavesCurrentListUntouched() = runTest(testDispatcher) {
        val (mock, _, collectRepo, viewModel) = fixture()
        mock.enqueue(
            """{"success":true,"code":200,"msg":"ok","data":{"list":[{"id":1001,"itemId":2001,"nameCN":"手办A"}]}}"""
        )
        testScheduler.advanceUntilIdle()
        val requestsBefore = mock.requestCount

        // 图片分类的取消事件不得污染手办列表，且不产生网络请求（写操作除外）
        mock.enqueue("""{"success":true,"code":200,"msg":"ok","data":{}}""")
        collectRepo.collectItemDel(9001L, UserCollectCategory.PICTURE)
        testScheduler.advanceUntilIdle()

        viewModel.uiState.value.hobbyData.items.map { it.id } shouldBe listOf(1001L)
        mock.requestCount shouldBe requestsBefore + 1
    }
}
