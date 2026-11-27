package com.nobody.repoi.ui.collect

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.data.repository.MockApi
import com.nobody.repoi.data.session.FakeUserSession
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.di.repositoryModule
import com.nobody.repoi.di.viewModelModule
import com.nobody.repoi.ui.common.LocalImageViewer
import com.nobody.repoi.ui.common.NoOpImageViewerController
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import kotlinx.coroutines.CompletableDeferred
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
class UserCollectScreenPagerTest {

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

    private fun hobbyListJson(count: Int): String {
        val items = (1..count).joinToString(",") { id ->
            """{"id":$id,"itemId":${1000 + id},"nameCN":"手办$id"}"""
        }
        return """{"success":true,"code":200,"msg":"ok","data":{"list":[$items]}}"""
    }

    private fun albumListJson(count: Int): String {
        val items = (1..count).joinToString(",") { id ->
            """{"id":$id,"name":"相册$id","title":"相册$id"}"""
        }
        return """{"success":true,"code":200,"msg":"ok","data":{"list":[$items]}}"""
    }

    private fun picListJson(count: Int): String {
        val items = (1..count).joinToString(",") { id ->
            """{"id":$id,"path":"2026/09/${id}.jpg"}"""
        }
        return """{"success":true,"code":200,"msg":"ok","data":{"list":[$items]}}"""
    }

    private fun commonListJson(count: Int, prefix: String): String {
        val items = (1..count).joinToString(",") { id ->
            """{"id":$id,"itemId":${2000 + id},"nameCN":"$prefix$id"}"""
        }
        return """{"success":true,"code":200,"msg":"ok","data":{"list":[$items]}}"""
    }

    @Test
    fun swipeAcrossPages_switchesTabsLoadsLazilyAndPreservesScrollState() = runComposeUiTest {
        var hobbyFirstPageRequests = 0
        var hobbyRequests = 0
        var albumRequests = 0
        var picRequests = 0
        val mock = MockApi().apply {
            routeWhen(
                predicate = { path, _, _ -> path.contains("api/collect/hobby-v2") },
                body = { _, _, query ->
                    hobbyRequests++
                    if (query["page"] == "1") {
                        hobbyFirstPageRequests++
                        hobbyListJson(30)
                    } else {
                        hobbyListJson(0)
                    }
                }
            )
            routeWhen(
                predicate = { path, _, _ -> path.contains("api/collect/album-v2") },
                body = { _, _, _ ->
                    albumRequests++
                    albumListJson(4)
                }
            )
            routeWhen(
                predicate = { path, _, _ -> path.contains("api/collect/pic-v2") },
                body = { _, _, _ ->
                    picRequests++
                    picListJson(2)
                }
            )
        }
        val vm = buildKoin(mock).koin.get<UserCollectViewModel>()

        setContent {
            CompositionLocalProvider(LocalImageViewer provides NoOpImageViewerController) {
                UserCollectScreen(
                    param = UserCollectNavParam(userId = 42L),
                    onNavigateBack = {},
                    onNavigateToHobby = {},
                    viewModel = vm
                )
            }
        }

        waitForIdle()
        onNodeWithContentDescription("手办1").assertIsDisplayed()
        assertEquals(1, hobbyRequests)
        assertEquals(0, albumRequests)
        assertEquals(0, picRequests)

        repeat(3) {
            onRoot().performTouchInput { swipeUp() }
            waitForIdle()
        }
        onNodeWithContentDescription("手办1").assertDoesNotExist()
        val visibleAfterScroll = (1..30).first { id ->
            runCatching { onNodeWithContentDescription("手办$id").assertIsDisplayed() }.isSuccess
        }
        val hobbyRequestsBeforeSwipeAway = hobbyRequests

        onRoot().performTouchInput { swipeLeft() }
        waitForIdle()
        assertEquals(UserCollectCategory.ALBUM, vm.uiState.value.selectedCategory)
        onNodeWithText("相册").assertIsSelected()
        onNodeWithText("相册1").assertIsDisplayed()
        assertEquals(1, albumRequests)

        onRoot().performTouchInput { swipeLeft() }
        waitForIdle()
        assertEquals(UserCollectCategory.PICTURE, vm.uiState.value.selectedCategory)
        onNodeWithText("图片").assertIsSelected()
        assertEquals(1, picRequests)

        onRoot().performTouchInput { swipeRight() }
        waitForIdle()
        assertEquals(UserCollectCategory.ALBUM, vm.uiState.value.selectedCategory)

        onRoot().performTouchInput { swipeRight() }
        waitForIdle()
        assertEquals(UserCollectCategory.HOBBY, vm.uiState.value.selectedCategory)
        onNodeWithText("周边").assertIsSelected()
        onNodeWithContentDescription("手办$visibleAfterScroll").assertIsDisplayed()
        assertEquals(1, hobbyFirstPageRequests)
        assertEquals(hobbyRequestsBeforeSwipeAway, hobbyRequests)
        assertEquals(1, albumRequests)
        assertEquals(1, picRequests)
    }

    @Test
    fun clickTab_switchesDirectlyWithoutLoadingIntermediateTabs() = runComposeUiTest {
        var albumRequests = 0
        var picRequests = 0
        var worksRequests = 0
        val mock = MockApi().apply {
            route("api/collect/hobby-v2", hobbyListJson(3))
            routeWhen(
                predicate = { path, _, _ -> path.contains("api/collect/album-v2") },
                body = { _, _, _ ->
                    albumRequests++
                    albumListJson(1)
                }
            )
            routeWhen(
                predicate = { path, _, _ -> path.contains("api/collect/pic-v2") },
                body = { _, _, _ ->
                    picRequests++
                    picListJson(1)
                }
            )
            routeWhen(
                predicate = { path, _, query -> path.contains("api/collect/common-v3") && query["type"] == "works" },
                body = { _, _, _ ->
                    worksRequests++
                    commonListJson(2, "作品")
                }
            )
        }
        val vm = buildKoin(mock).koin.get<UserCollectViewModel>()

        setContent {
            CompositionLocalProvider(LocalImageViewer provides NoOpImageViewerController) {
                UserCollectScreen(
                    param = UserCollectNavParam(userId = 42L),
                    onNavigateBack = {},
                    onNavigateToHobby = {},
                    viewModel = vm
                )
            }
        }

        waitForIdle()
        onNodeWithContentDescription("手办1").assertIsDisplayed()

        onNodeWithText("作品").performScrollTo().performClick()
        waitForIdle()

        assertEquals(UserCollectCategory.WORKS, vm.uiState.value.selectedCategory)
        onNodeWithText("作品1").assertIsDisplayed()
        assertEquals(1, worksRequests)
        assertEquals(0, albumRequests)
        assertEquals(0, picRequests)
    }

    @Test
    fun swipeToUnloadedAlbum_doesNotFlashEmptyViewWhileRequestInFlight() = runComposeUiTest {
        val albumGate = CompletableDeferred<Unit>()
        val mock = MockApi().apply {
            route("api/collect/hobby-v2", hobbyListJson(2))
            routeWhenSuspend(
                predicate = { path, _, _ -> path.contains("api/collect/album-v2") },
                body = { _, _, _ ->
                    albumGate.await()
                    albumListJson(2)
                }
            )
        }
        val vm = buildKoin(mock).koin.get<UserCollectViewModel>()

        setContent {
            CompositionLocalProvider(LocalImageViewer provides NoOpImageViewerController) {
                UserCollectScreen(
                    param = UserCollectNavParam(userId = 42L),
                    onNavigateBack = {},
                    onNavigateToHobby = {},
                    viewModel = vm
                )
            }
        }

        waitForIdle()
        onNodeWithContentDescription("手办1").assertIsDisplayed()

        mainClock.autoAdvance = false
        onRoot().performTouchInput { swipeLeft() }
        mainClock.advanceTimeBy(600)
        waitForIdle()

        onNodeWithText("暂无相册~").assertDoesNotExist()

        mainClock.autoAdvance = true
        albumGate.complete(Unit)
        waitForIdle()

        onNodeWithText("相册1").assertIsDisplayed()
    }
}
