package com.nobody.repoi

import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Wave 0 spike 冒烟测试（jvmTest）：
 * 1. DataStore `PreferenceDataStoreFactory.createWithPath` 文件模式读写；
 * 2. Nav3 backstack 进栈/出栈 + 双装饰器；
 * 3. `runComposeUiTest` + Koin 最小图渲染冒烟。
 */
@OptIn(ExperimentalTestApi::class)
class Wave0SmokeTest {

    @AfterTest
    fun tearDown() {
        runCatching { stopKoin() }
    }

    @Test
    fun dataStore_createWithPath_readWrite() = runTest {
        val dir = createTempDirectory(prefix = "repoi-datastore-test")
        try {
            val store = PreferenceDataStoreFactory.createWithPath(
                produceFile = {
                    dir.toString().toPath().resolve("smoke.preferences_pb")
                }
            )
            val key = stringPreferencesKey("smoke_key")
            store.edit { it[key] = "smoke_value" }
            val prefs = store.data.first()
            assertEquals("smoke_value", prefs[key])
        } finally {
            runCatching { FileSystem.SYSTEM.deleteRecursively(dir.toString().toPath()) }
        }
    }

    @Test
    fun nav3_backStack_pushPop() = runComposeUiTest {
        setContent {
            // 冒烟测试不验证跨进程恢复语义，直接 remember 一个 NavBackStack。
            // 生产代码（Wave 5+）用 rememberNavBackStack(configuration, ...) 并注册多态。
            val backStack: NavBackStack<NavKey> = remember { NavBackStack(SmokeHome) }
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = { key ->
                    when (key) {
                        is SmokeHome -> NavEntry(key) { Text("smoke-home") }
                        is SmokeDetail -> NavEntry(key) { Text("smoke-detail:${key.id}") }
                        else -> error("unexpected key $key")
                    }
                }
            )
        }

        onNodeWithText("smoke-home").assertExists()
    }

    @Test
    fun koin_minimalGraph_composeSmoke() = runComposeUiTest {
        startKoin {
            modules(
                module {
                    single<String> { "koin-smoke" }
                }
            )
        }

        setContent {
            val value: String = org.koin.compose.koinInject()
            Text(value)
        }

        onNodeWithText("koin-smoke").assertExists()
    }
}

@Serializable
data object SmokeHome : NavKey

@Serializable
data class SmokeDetail(val id: String) : NavKey
