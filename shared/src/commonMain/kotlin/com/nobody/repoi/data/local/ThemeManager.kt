package com.nobody.repoi.data.local

import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nobody.repoi.platform.isDynamicSeedAvailable
import com.nobody.repoi.platform.supportsDynamicColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.nobody.repoi.ui.theme.warmUpThemeCardColors
import com.nobody.repoi.util.AppLogger
import kotlin.concurrent.atomics.ExperimentalAtomicApi

enum class ThemeSeed(val title: String, val argb: Long?) {
    Dynamic("动态主题", null),
    Pink("粉色", 0xFFFF7AA3),
    Coral("红色", 0xFFB90037),
    Amber("黄色", 0xFFFABE03),
    Lotus("蓝色", 0xFF006EBE),
    Mint("绿色", 0xFF3F975B),
    Grape("紫色", 0xFF7E6195),
    Tricolor("真寻三色", 0xFFEAD4CE)
}

fun ThemeSeed.toColors(): Triple<Color?, Color?, Color?> = when (this) {
    ThemeSeed.Dynamic -> Triple(null, null, null)
    ThemeSeed.Lotus -> Triple(Color(0xFF006EBE), null, null)
    ThemeSeed.Coral -> Triple(Color(0xFFB90037), null, null)
    ThemeSeed.Amber -> Triple(Color(0xFFFABE03), null, null)
    ThemeSeed.Pink -> Triple(Color(0xFFFF7AA3), null, null)
    ThemeSeed.Mint -> Triple(Color(0xFF3F975B), null, null)
    ThemeSeed.Grape -> Triple(Color(0xFF7E6195), null, null)
    ThemeSeed.Tricolor -> Triple(Color(0xFFEAD4CE), Color(0xFF7D859D), Color(0xFFEC9CA8))
}

enum class ThemeMode(val title: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色模式"),
    DARK("深色模式")
}

/**
 * 主题配置快照：六维设置的完整取值。
 *
 * 为什么要打成一个整体而不是六条独立流：六条流各自带 `initialValue` 时，
 * 冷启动会先拿六个内存默认值渲染一帧，DataStore 读完再逐个跳到真实值。
 * 配合 `ColorScheme.animateAsState`，这段跳变会被渲染成一段可见渐变。
 * 合成一条流后，**「未就绪」由 `null` 表达**，首帧门禁成为类型系统的自然结果，
 * 不再需要额外的 `getInitialXxx()` 与启动快照。
 */
data class ThemeSettings(
    val themeMode: ThemeMode,
    val dynamicColor: Boolean,
    val themeSeed: ThemeSeed,
    val amoled: Boolean,
    val paletteStyle: PaletteStyle,
    val specVersion: ColorSpec.SpecVersion,
)

/** 无持久化配置时的平台默认值（动态取色能力与可用种子随平台不同）。 */
fun defaultThemeSettings(): ThemeSettings = ThemeSettings(
    themeMode = ThemeMode.SYSTEM,
    dynamicColor = supportsDynamicColor,
    themeSeed = defaultThemeSeed(),
    amoled = false,
    paletteStyle = PaletteStyle.TonalSpot,
    specVersion = ColorSpec.SpecVersion.SPEC_2025,
)

@OptIn(ExperimentalAtomicApi::class)
class ThemeManager(
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 唯一的主题状态流。
     *
     * `null` 表示**首次读盘尚未完成**，UI 必须停在启动占位而不是拿默认值先画一帧；
     * 一旦读出（成功或回落默认值）就只可能是非空。
     */
    private val state = MutableStateFlow<ThemeSettings?>(null)

    val settings: StateFlow<ThemeSettings?> = state.asStateFlow()

    init {
        scope.launch { ensureLoaded() }
        scope.launch(Dispatchers.Default) {
            warmUpThemeCardColors()
        }
    }

    /**
     * 确保首次读盘已落定；失败时回落默认值，避免 `settings` 永远停在 `null`。
     *
     * 幂等且并发安全：`compareAndSet(null, ...)` 保证只有第一个调用者写入，
     * 其余调用者直接返回（此时 `state` 已非空）。
     */
    private suspend fun ensureLoaded() {
        if (state.value != null) return
        val loaded = runCatching { readSettings(dataStore.data.first()) }
            .getOrElse { error ->
                AppLogger.w("ThemeManager", "读取主题配置失败，沿用平台默认值：${error.message}")
                defaultThemeSettings()
            }
        state.compareAndSet(null, loaded)
    }

    private fun readSettings(prefs: Preferences): ThemeSettings {
        val mode = when (prefs[THEME_MODE_KEY]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
        val seed = prefs[THEME_SEED_KEY]
            ?.let { runCatching { ThemeSeed.valueOf(it) }.getOrNull() }
            ?.orAvailable()
            ?: defaultThemeSeed()
        val style = parsePaletteStyle(prefs[PALETTE_STYLE_KEY])
            ?: if (prefs[EXPRESSIVE_COLOR_KEY] == true) PaletteStyle.Expressive else PaletteStyle.TonalSpot
        return ThemeSettings(
            themeMode = mode,
            dynamicColor = prefs[DYNAMIC_COLOR_KEY] ?: supportsDynamicColor,
            themeSeed = seed,
            amoled = prefs[AMOLED_KEY] ?: false,
            paletteStyle = style,
            specVersion = parseSpecVersion(prefs[SPEC_VERSION_KEY]) ?: ColorSpec.SpecVersion.SPEC_2025,
        )
    }

    /**
     * 乐观更新内存状态并返回新值：UI 立即生效，DataStore 只负责持久化。
     *
     * 先等首次读盘落定再改，否则改动会以默认值为基底，把其他已持久化字段一起冲掉。
     */
    private suspend fun update(transform: (ThemeSettings) -> ThemeSettings): ThemeSettings {
        ensureLoaded()
        val next = transform(state.value ?: defaultThemeSettings())
        state.value = next
        return next
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        update { it.copy(themeMode = mode) }
        dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        val next = update { current ->
            val targetSeed = when {
                enabled -> ThemeSeed.Dynamic
                current.themeSeed == ThemeSeed.Dynamic -> ThemeSeed.Lotus
                else -> current.themeSeed
            }
            current.copy(dynamicColor = enabled, themeSeed = targetSeed)
        }
        dataStore.edit { prefs ->
            prefs[DYNAMIC_COLOR_KEY] = enabled
            prefs[THEME_SEED_KEY] = next.themeSeed.name
        }
    }

    suspend fun setThemeSeed(seed: ThemeSeed) {
        val targetSeed = seed.orAvailable()
        val targetDynamic = (seed == ThemeSeed.Dynamic && supportsDynamicColor)
        update { it.copy(themeSeed = targetSeed, dynamicColor = targetDynamic) }
        dataStore.edit { prefs ->
            prefs[THEME_SEED_KEY] = targetSeed.name
            prefs[DYNAMIC_COLOR_KEY] = targetDynamic
        }
    }

    suspend fun selectTheme(seed: ThemeSeed) = setThemeSeed(seed)

    suspend fun setAmoled(enabled: Boolean) {
        update { it.copy(amoled = enabled) }
        dataStore.edit { prefs ->
            prefs[AMOLED_KEY] = enabled
        }
    }

    suspend fun setPaletteStyle(style: PaletteStyle) {
        update { it.copy(paletteStyle = style) }
        dataStore.edit { prefs ->
            prefs[PALETTE_STYLE_KEY] = style.name
            // 遗留键仅作一次性迁移来源，写入新键后清除
            prefs.remove(EXPRESSIVE_COLOR_KEY)
        }
    }

    suspend fun setSpecVersion(version: ColorSpec.SpecVersion) {
        update { it.copy(specVersion = version) }
        dataStore.edit { prefs ->
            prefs[SPEC_VERSION_KEY] = version.name
        }
    }

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("app_theme_mode")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("app_dynamic_color")
        private val THEME_SEED_KEY = stringPreferencesKey("app_theme_seed")
        private val AMOLED_KEY = booleanPreferencesKey("app_amoled")
        /** 遗留键：仅供一次性迁移到 [PALETTE_STYLE_KEY]，写入新键后即清除 */
        private val EXPRESSIVE_COLOR_KEY = booleanPreferencesKey("app_expressive_color")
        private val PALETTE_STYLE_KEY = stringPreferencesKey("app_palette_style")
        private val SPEC_VERSION_KEY = stringPreferencesKey("app_spec_version")
    }
}

private fun defaultThemeSeed(): ThemeSeed =
    if (isDynamicSeedAvailable) ThemeSeed.Dynamic else ThemeSeed.Pink

fun ThemeSeed.orAvailable(): ThemeSeed =
    if (this == ThemeSeed.Dynamic && !isDynamicSeedAvailable) ThemeSeed.Pink else this

val AllPaletteStyles: List<PaletteStyle> = listOf(
    PaletteStyle.TonalSpot,
    PaletteStyle.Neutral,
    PaletteStyle.Vibrant,
    PaletteStyle.Expressive,
    PaletteStyle.Rainbow,
    PaletteStyle.FruitSalad,
    PaletteStyle.Monochrome,
    PaletteStyle.Fidelity,
    PaletteStyle.Content
)

val AllSpecVersions: List<ColorSpec.SpecVersion> = listOf(
    ColorSpec.SpecVersion.SPEC_2025,
    ColorSpec.SpecVersion.SPEC_2021
)

fun parsePaletteStyle(name: String?): PaletteStyle? =
    AllPaletteStyles.firstOrNull { it.name == name }

fun parseSpecVersion(name: String?): ColorSpec.SpecVersion? =
    AllSpecVersions.firstOrNull { it.name == name }
