package com.nobody.repoi.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nobody.repoi.platform.supportsDynamicColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

enum class ThemeMode(val title: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式")
}

/**
 * 主题管理器（KMP 移植）。
 *
 * - 构造器改取 `DataStore<Preferences>`；`SharedPreferences` 同步镜相对 DataStore 单一真相源
 *   + atomics 内存镜像替代（桌面无 SP；common 无 `@Volatile`）。
 * - **已删除**旧版 `checkLegacyDynamicColorPreference` 字节扫描（ThemeManager.kt:92-118）：
 *   桌面路径不存在、Android 侧为脆弱字节嗅探；改用 `supportsDynamicColor` expect。
 */
@OptIn(ExperimentalAtomicApi::class)
class ThemeManager(
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val themeModeRef = AtomicReference(ThemeMode.SYSTEM)
    private val dynamicColorRef = AtomicBoolean(supportsDynamicColor)

    var themeMode: ThemeMode
        get() = themeModeRef.load()
        private set(value) { themeModeRef.store(value) }

    var dynamicColor: Boolean
        get() = dynamicColorRef.load()
        private set(value) { dynamicColorRef.store(value) }

    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        when (prefs[THEME_MODE_KEY]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val dynamicColorFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DYNAMIC_COLOR_KEY] ?: supportsDynamicColor
    }

    init {
        runCatching {
            scope.launch {
                // 冷启动预热：从 DataStore 一次性恢复内存镜像。
                // 不使用持续 collect，避免与本地写入产生异步时序竞态。
                val prefs = dataStore.data.first()
                prefs[THEME_MODE_KEY]?.let {
                    themeMode = when (it) {
                        ThemeMode.LIGHT.name -> ThemeMode.LIGHT
                        ThemeMode.DARK.name -> ThemeMode.DARK
                        else -> ThemeMode.SYSTEM
                    }
                }
                prefs[DYNAMIC_COLOR_KEY]?.let {
                    dynamicColor = it
                }
            }
        }
    }

    fun getInitialThemeMode(): ThemeMode = themeMode

    fun getInitialDynamicColor(): Boolean = dynamicColor

    suspend fun setThemeMode(mode: ThemeMode) {
        themeMode = mode
        dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dynamicColor = enabled
        dataStore.edit { prefs ->
            prefs[DYNAMIC_COLOR_KEY] = enabled
        }
    }

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("app_theme_mode")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("app_dynamic_color")
    }
}
