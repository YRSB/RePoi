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

enum class ThemeSeed(val title: String, val argb: Long?) {
    Dynamic("动态壁纸取色", null),
    Pink("樱粉", 0xFFFF4D8D),
    Coral("珊瑚红", 0xFFFF4D4F),
    Amber("琥珀金", 0xFFFA8C16),
    Lotus("青莲", 0xFF1890FF),
    Mint("薄荷", 0xFF12B76A),
    Grape("紫藤", 0xFF7A5AF8),
    Tricolor("真寻三色", 0xFF2EC4B6)
}

enum class ThemeMode(val title: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式")
}

@OptIn(ExperimentalAtomicApi::class)
class ThemeManager(
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val themeModeRef = AtomicReference(ThemeMode.SYSTEM)
    private val dynamicColorRef = AtomicBoolean(supportsDynamicColor)
    private val themeSeedRef = AtomicReference(defaultThemeSeed())
    private val amoledRef = AtomicBoolean(false)

    var themeMode: ThemeMode
        get() = themeModeRef.load()
        private set(value) { themeModeRef.store(value) }

    var dynamicColor: Boolean
        get() = dynamicColorRef.load()
        private set(value) { dynamicColorRef.store(value) }

    var themeSeed: ThemeSeed
        get() = themeSeedRef.load()
        private set(value) { themeSeedRef.store(value) }

    var amoled: Boolean
        get() = amoledRef.load()
        private set(value) { amoledRef.store(value) }

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

    val themeSeedFlow: Flow<ThemeSeed> = dataStore.data.map { prefs ->
        prefs[THEME_SEED_KEY]?.let { runCatching { ThemeSeed.valueOf(it) }.getOrNull() }
            ?: defaultThemeSeed()
    }

    val amoledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AMOLED_KEY] ?: false
    }

    init {
        runCatching {
            scope.launch {
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
                prefs[THEME_SEED_KEY]?.let {
                    runCatching { ThemeSeed.valueOf(it) }.getOrNull()?.let { seed -> themeSeed = seed }
                }
                prefs[AMOLED_KEY]?.let {
                    amoled = it
                }
            }
        }
    }

    fun getInitialThemeMode(): ThemeMode = themeMode

    fun getInitialDynamicColor(): Boolean = dynamicColor

    fun getInitialThemeSeed(): ThemeSeed = themeSeed

    fun getInitialAmoled(): Boolean = amoled

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

    suspend fun setThemeSeed(seed: ThemeSeed) {
        themeSeed = seed
        dataStore.edit { prefs ->
            prefs[THEME_SEED_KEY] = seed.name
        }
    }

    suspend fun setAmoled(enabled: Boolean) {
        amoled = enabled
        dataStore.edit { prefs ->
            prefs[AMOLED_KEY] = enabled
        }
    }

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("app_theme_mode")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("app_dynamic_color")
        private val THEME_SEED_KEY = stringPreferencesKey("app_theme_seed")
        private val AMOLED_KEY = booleanPreferencesKey("app_amoled")
    }
}

private fun defaultThemeSeed(): ThemeSeed =
    if (supportsDynamicColor) ThemeSeed.Dynamic else ThemeSeed.Pink
