package com.nobody.repoi.ui.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.di.repositoryModule
import com.nobody.repoi.di.viewModelModule
import com.nobody.repoi.ui.collect.UserCollectScreen
import com.nobody.repoi.ui.collect.UserCollectViewModel
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.NoOpImageViewerController
import com.nobody.repoi.ui.friend.FriendListScreen
import com.nobody.repoi.ui.friend.FriendListViewModel
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import com.nobody.repoi.ui.navigation.UserCreationNavParam
import com.nobody.repoi.ui.user.creation.UserCreationScreen
import com.nobody.repoi.ui.user.creation.UserCreationViewModel
import com.nobody.repoi.ui.user.history.UserHistoryScreen
import com.nobody.repoi.ui.user.history.UserHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.koin.core.KoinApplication
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalCoroutinesApi::class)
class ProfileDetailScreensTopBarTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val koinApps = mutableListOf<KoinApplication>()

    @BeforeTest
    fun setUpMain() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDownMain() {
        koinApps.forEach { it.close() }
        koinApps.clear()
        testDispatcher.cancelChildren()
        Dispatchers.resetMain()
    }

    private fun buildKoin(mock: MockApi): KoinApplication {
        val session = FakeUserSession("utoken-test", 42L)
        val viewPrefs: DataStore<Preferences> = TestDataStore.inMemory()
        val app = koinApplication {
            modules(
                repositoryModule,
                viewModelModule,
                module {
                    single<Json> { mock.json }
                    single<HpoiApi> { mock.api(testDispatcher) }
                    single<UserSession> { session }
                    single { ViewPreferencesManager(get(named("view_prefs"))) }
                    single(named("view_prefs")) { viewPrefs }
                    single(named("cover_freshness_prefs")) { TestDataStore.inMemory() }
                }
            )
        }
        koinApps += app
        return app
    }

    @Test
    fun userCollectScreen_togglesTopBarBackButton() = runComposeUiTest {
        val mock = MockApi()
        mock.route("api/user/collect", """{"success":true,"code":200,"data":{"list":[],"more":false}}""")
        val vm = buildKoin(mock).koin.get<UserCollectViewModel>()
        var showBack by mutableStateOf(true)
        var backClicks = 0

        setContent {
            UserCollectScreen(
                param = UserCollectNavParam(userId = 42L),
                onNavigateBack = { backClicks++ },
                onNavigateToHobby = {},
                showBackButton = showBack,
                viewModel = vm
            )
        }

        waitForIdle()
        onNodeWithContentDescription("返回").assertIsDisplayed().performClick()
        assertEquals(1, backClicks)

        showBack = false
        waitForIdle()
        onNodeWithContentDescription("返回").assertDoesNotExist()
    }

    @Test
    fun userCreationScreen_togglesTopBarBackButton() = runComposeUiTest {
        val mock = MockApi()
        mock.route("api/album/user-v2", """{"success":true,"code":200,"data":{"list":[],"more":false}}""")
        val vm = buildKoin(mock).koin.get<UserCreationViewModel>()
        var showBack by mutableStateOf(true)
        var backClicks = 0

        setContent {
            CompositionLocalProvider(LocalImageViewer provides NoOpImageViewerController) {
                UserCreationScreen(
                    param = UserCreationNavParam(userId = 42L),
                    onNavigateBack = { backClicks++ },
                    onNavigateToAlbum = {},
                    showBackButton = showBack,
                    viewModel = vm
                )
            }
        }

        waitForIdle()
        onNodeWithContentDescription("返回").assertIsDisplayed().performClick()
        assertEquals(1, backClicks)

        showBack = false
        waitForIdle()
        onNodeWithContentDescription("返回").assertDoesNotExist()
    }

    @Test
    fun userHistoryScreen_togglesBackButtonAndKeepsDoneActionInManageMode() = runComposeUiTest {
        val mock = MockApi()
        mock.route(
            "api/user/read/item/get",
            """{"success":true,"code":200,"data":{"readList":[{"id":123,"nodeId":456,"itemType":"hobby","title":"手办A"}]}}"""
        )
        val vm = buildKoin(mock).koin.get<UserHistoryViewModel>()
        var showBack by mutableStateOf(true)
        var backClicks = 0

        setContent {
            UserHistoryScreen(
                onBackClick = { backClicks++ },
                onNavigateToHobbyDetail = {},
                onNavigateToAlbumDetail = {},
                showBackButton = showBack,
                viewModel = vm
            )
        }

        waitForIdle()
        onNodeWithContentDescription("返回").assertIsDisplayed().performClick()
        assertEquals(1, backClicks)

        showBack = false
        waitForIdle()
        onNodeWithContentDescription("返回").assertDoesNotExist()

        onNodeWithText("管理").assertIsDisplayed().performClick()
        waitForIdle()
        onNodeWithText("完成").assertIsDisplayed().performClick()
        waitForIdle()
        onNodeWithText("管理").assertIsDisplayed()

        showBack = true
        waitForIdle()
        onNodeWithText("管理").performClick()
        waitForIdle()
        onNodeWithText("已选择 0 项").assertIsDisplayed()
        onNodeWithContentDescription("返回").performClick()
        waitForIdle()
        assertEquals(1, backClicks)
        onNodeWithText("管理").assertIsDisplayed()
        onNodeWithContentDescription("返回").performClick()
        assertEquals(2, backClicks)
    }

    @Test
    fun friendListScreen_togglesTopBarBackButtonAndSwitchesInitialTab() = runComposeUiTest {
        val mock = MockApi()
        mock.route("api/user/fellow", """{"success":true,"code":200,"data":{"list":[]}}""")
        mock.route("api/item/praise/forUser", """{"success":true,"code":200,"data":{"list":[]}}""")
        val vm = buildKoin(mock).koin.get<FriendListViewModel>()
        var showBack by mutableStateOf(true)
        var initialType by mutableStateOf(0)
        var backClicks = 0

        setContent {
            FriendListScreen(
                userId = 42L,
                initialType = initialType,
                onNavigateBack = { backClicks++ },
                showBackButton = showBack,
                viewModel = vm
            )
        }

        waitForIdle()
        onNodeWithText("暂无关注的用户").assertIsDisplayed()
        onNodeWithContentDescription("返回").assertIsDisplayed().performClick()
        assertEquals(1, backClicks)

        showBack = false
        initialType = 3
        waitForIdle()
        onNodeWithContentDescription("返回").assertDoesNotExist()
        onNodeWithText("暂无获赞条目").assertIsDisplayed()
    }
}
