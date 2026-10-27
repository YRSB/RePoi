package com.nobody.repoi.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.savedstate.compose.serialization.SavedStateConfiguration
import com.nobody.repoi.ui.detail.DetailNavParam
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavEntryTestViewModel : ViewModel() {
    var isCleared: Boolean = false
        private set

    override fun onCleared() {
        super.onCleared()
        isCleared = true
    }
}

@OptIn(ExperimentalTestApi::class)
class NavEntryViewModelStoreTest {

    private val testConfiguration = SavedStateConfiguration {
        serializersModule = SerializersModule {
            polymorphic(NavKey::class) {
                subclass(HomeNavKey::class, HomeNavKey.serializer())
                subclass(ProfileNavKey::class, ProfileNavKey.serializer())
                subclass(HobbyDetailNavKey::class, HobbyDetailNavKey.serializer())
            }
        }
    }

    @Test
    fun navEntryViewModelStore_whenEntryPopped_triggersOnCleared() = runComposeUiTest {
        lateinit var backStackRef: NavBackStack<NavKey>
        var capturedVm: NavEntryTestViewModel? = null

        val detailKey = HobbyDetailNavKey(param = DetailNavParam(itemId = 1001L))

        setContent {
            val backStack = rememberNavBackStack(
                testConfiguration,
                HomeNavKey,
                detailKey
            )
            backStackRef = backStack

            val decorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>()
            )

            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = decorators,
                entryProvider = { key ->
                    when (key) {
                        is HomeNavKey -> NavEntry(key) {
                            // 首页
                        }
                        is HobbyDetailNavKey -> NavEntry(key) {
                            val vm = viewModel<NavEntryTestViewModel>()
                            capturedVm = vm
                        }
                        else -> error("unexpected key $key")
                    }
                }
            )
        }

        waitForIdle()
        val vm = capturedVm
        assertTrue(vm != null, "ViewModel 应当已成功创建")
        assertFalse(vm!!.isCleared, "ViewModel 在条目处于返回栈内时不应被清理")

        // 触发详情页退栈：弹出顶层的 detailKey
        runOnIdle {
            backStackRef.removeLastOrNull()
        }
        waitForIdle()

        // 验证条目退栈离开组合后，ViewModelStore 被自动回收，触发 onCleared
        assertTrue(vm.isCleared, "条目出栈离开组合后，Scoped ViewModel 的 onCleared 应当被调用")
    }

    @Test
    fun navEntryViewModelStore_distinctInstances_haveIsolatedViewModelStores() = runComposeUiTest {
        lateinit var backStackRef: NavBackStack<NavKey>
        val vmMap = mutableMapOf<String, NavEntryTestViewModel>()

        val firstKey = HobbyDetailNavKey(param = DetailNavParam(itemId = 2001L))
        val secondKey = HobbyDetailNavKey(param = DetailNavParam(itemId = 2001L))

        // 两者业务参数相同，但基于 UUID 的实例 id 独立
        assertTrue(firstKey.id != secondKey.id)

        setContent {
            val backStack = rememberNavBackStack(testConfiguration, firstKey)
            backStackRef = backStack

            val decorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>()
            )

            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = decorators,
                entryProvider = { key ->
                    when (key) {
                        is HobbyDetailNavKey -> NavEntry(key) {
                            val vm = viewModel<NavEntryTestViewModel>()
                            vmMap[key.id] = vm
                        }
                        else -> error("unexpected key $key")
                    }
                }
            )
        }

        waitForIdle()
        val firstVm = vmMap[firstKey.id]
        assertTrue(firstVm != null, "第一个实例的 ViewModel 应当存在")

        // 入栈第二个同参数详情实例
        runOnIdle {
            backStackRef.add(secondKey)
        }
        waitForIdle()

        val secondVm = vmMap[secondKey.id]
        assertTrue(secondVm != null, "第二个实例的 ViewModel 应当存在")
        assertTrue(firstVm !== secondVm, "不同 NavEntry 实例必须拥有隔离的 ViewModel 实例")

        // 退栈第二个实例（回到第一个实例）
        runOnIdle {
            backStackRef.removeLastOrNull()
        }
        waitForIdle()

        assertTrue(secondVm!!.isCleared, "顶层条目出栈后其 ViewModel 应当被清理")
        assertFalse(firstVm!!.isCleared, "栈底保留条目的 ViewModel 不应被误清理")
    }

    @Test
    fun navEntryViewModelStore_whenTabSwitchedAndReturned_preservesTabViewModel() = runComposeUiTest {
        lateinit var backStackRef: NavBackStack<NavKey>
        var homeVm: NavEntryTestViewModel? = null
        var profileVm: NavEntryTestViewModel? = null
        var homeVmSecondVisit: NavEntryTestViewModel? = null

        setContent {
            val backStack = rememberNavBackStack(testConfiguration, HomeNavKey)
            backStackRef = backStack

            val decorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                rememberViewModelStoreNavEntryDecorator<NavKey>()
            )

            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = decorators,
                entryProvider = { key ->
                    when (key) {
                        is HomeNavKey -> NavEntry(key) {
                            val vm = viewModel<NavEntryTestViewModel>()
                            if (homeVm == null) {
                                homeVm = vm
                            } else {
                                homeVmSecondVisit = vm
                            }
                        }
                        is ProfileNavKey -> NavEntry(key) {
                            val vm = viewModel<NavEntryTestViewModel>()
                            profileVm = vm
                        }
                        else -> error("unexpected key $key")
                    }
                }
            )
        }

        waitForIdle()
        assertTrue(homeVm != null, "初始 Home ViewModel 应当存在")
        assertFalse(homeVm!!.isCleared, "初始 Home ViewModel 不应被清理")

        // 切换到 Profile 页（保留 HomeNavKey，将 ProfileNavKey 添加到返回栈末尾）
        runOnIdle {
            backStackRef.add(ProfileNavKey)
        }
        waitForIdle()

        assertTrue(profileVm != null, "Profile ViewModel 应当已创建")
        assertFalse(homeVm!!.isCleared, "切换到 Profile 时，处于栈底的 Home ViewModel 不应被清理")

        // 切换回 Home 页（将 HomeNavKey 移动到返回栈末尾）
        runOnIdle {
            backStackRef.remove(HomeNavKey)
            backStackRef.add(HomeNavKey)
        }
        waitForIdle()

        assertFalse(homeVm!!.isCleared, "返回 Home 后，Home ViewModel 依旧未被清理")
        assertTrue(homeVm === homeVmSecondVisit, "返回 Home 后，必须复用原有的 ViewModel 实例")
        assertFalse(profileVm!!.isCleared, "底部的 Profile ViewModel 也不应被清理")
    }
}
