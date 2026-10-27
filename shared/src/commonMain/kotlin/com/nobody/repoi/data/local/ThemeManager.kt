package com.nobody.repoi.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class ThemeMode(val title: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式")
}

/**
 * 主题偏好管理器。
 *
 * KMP 版变更（相对老项目）：
 * - DataStore 由 Koin 注入；
 * - 砍掉 SharedPreferences 同步缓存层（新包名=全新安装，无历史数据可迁）：
 *   同步读改为内存字段（初值=默认值），DataStore 为唯一持久化。
 */
class ThemeManager(
    private val dataStore: DataStore<Preferences>
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    var themeMode: ThemeMode = ThemeMode.SYSTEM
        private set

    @Volatile
    var dynamicColor: Boolean = defaultDynamicColor()
        private set

    val themeModeFlow: Flow<ThemeMode> = dataStore.data.map { prefs ->
        when (prefs[THEME_MODE_KEY]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val dynamicColorFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[DYNAMIC_COLOR_KEY] ?: defaultDynamicColor()
    }

    init {
        // 初始加载：只读一次历史数据回填内存（持续 collect 可能用旧快照覆盖内存新值）。
        scope.launch {
            val prefs = dataStore.data.first()
            val mode = when (prefs[THEME_MODE_KEY]) {
                ThemeMode.LIGHT.name -> ThemeMode.LIGHT
                ThemeMode.DARK.name -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            val dyn = prefs[DYNAMIC_COLOR_KEY] ?: defaultDynamicColor()
            themeMode = mode
            dynamicColor = dyn
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

/**
 * 动态取色默认值：Android 12+ 为 true；桌面端无动态取色，固定 false。
 */
expect fun defaultDynamicColor(): Boolean
