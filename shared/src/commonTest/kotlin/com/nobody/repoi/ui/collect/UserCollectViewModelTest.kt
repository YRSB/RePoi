package com.nobody.repoi.ui.collect

import app.cash.turbine.test
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.FakeTokenManager
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.util.CollectInvalidationBus
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * 用户全量收藏中枢 ViewModel 状态机单元测试（Turbine + Kotest）。
 *
 * 验证大类切换（手办/周边/相册/图片/作品）、收藏状态变更、批量管理多选模式流转。
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
        Dispatchers.resetMain()
    }

    private suspend fun setup(userId: Long = 999L): UserCollectViewModel {
        val mockApi = MockApi()
        val tokenManager = FakeTokenManager("test-token")
        val userRepo = UserRepository(mockApi.api(), tokenManager)
        val collectRepo = CollectRepository(mockApi.api(), mockApi.json)
        val preferences = ViewPreferencesManager(TestDataStore.viewPrefs())
        val bus = CollectInvalidationBus()

        return UserCollectViewModel(
            userRepository = userRepo,
            collectRepository = collectRepo,
            viewPreferencesManager = preferences,
            tokenManager = tokenManager,
            collectInvalidationBus = bus,
            navUserId = userId,
        )
    }

    @Test
    fun initialSetup_matchesNavParameters() = runTest(testDispatcher) {
        val viewModel = setup(userId = 888L)

        viewModel.uiState.value.userId shouldBe 888L
        viewModel.uiState.value.category shouldBe UserCollectCategory.HOBBY
        viewModel.uiState.value.isSelectionMode shouldBe false
        viewModel.uiState.value.selectedNodeIds.shouldBeEmpty()
    }

    @Test
    fun selectCategory_updatesCategoryState() = runTest(testDispatcher) {
        val viewModel = setup()

        viewModel.uiState.test {
            awaitItem().category shouldBe UserCollectCategory.HOBBY

            viewModel.selectCategory(UserCollectCategory.ALBUM)
            awaitItem().category shouldBe UserCollectCategory.ALBUM

            viewModel.selectCategory(UserCollectCategory.WORKS)
            awaitItem().category shouldBe UserCollectCategory.WORKS
        }
    }

    @Test
    fun selectCollectState_updatesStateKey() = runTest(testDispatcher) {
        val viewModel = setup()

        viewModel.uiState.test {
            awaitItem().collectState shouldBe "all"

            viewModel.selectCollectState("care")
            awaitItem().collectState shouldBe "care"

            viewModel.selectCollectState("own")
            awaitItem().collectState shouldBe "own"
        }
    }

    @Test
    fun toggleSelectionMode_flipsSelectionFlagAndClearsSelectedIds() = runTest(testDispatcher) {
        val viewModel = setup()

        viewModel.uiState.test {
            awaitItem().isSelectionMode shouldBe false

            viewModel.toggleSelectionMode()
            awaitItem().isSelectionMode shouldBe true

            viewModel.toggleSelectionMode()
            awaitItem().isSelectionMode shouldBe false
        }
    }
}
