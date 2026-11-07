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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.nobody.repoi.ui.theme.warmUpThemeCardColors
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
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

@OptIn(ExperimentalAtomicApi::class)
class ThemeManager(
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val themeModeRef = AtomicReference(ThemeMode.SYSTEM)
    private val dynamicColorRef = AtomicBoolean(supportsDynamicColor)
    private val themeSeedRef = AtomicReference(defaultThemeSeed())
    private val amoledRef = AtomicBoolean(false)
    private val paletteStyleRef = AtomicReference<PaletteStyle>(PaletteStyle.TonalSpot)
    private val specVersionRef = AtomicReference<ColorSpec.SpecVersion>(ColorSpec.SpecVersion.SPEC_2025)

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

    var paletteStyle: PaletteStyle
        get() = paletteStyleRef.load()
        private set(value) { paletteStyleRef.store(value) }

    var specVersion: ColorSpec.SpecVersion
        get() = specVersionRef.load()
        private set(value) { specVersionRef.store(value) }

    var expressiveColor: Boolean
        get() = paletteStyle == PaletteStyle.Expressive
        private set(value) {
            paletteStyle = if (value) PaletteStyle.Expressive else PaletteStyle.TonalSpot
        }

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
        prefs[THEME_SEED_KEY]?.let { runCatching { ThemeSeed.valueOf(it) }.getOrNull() }?.orAvailable()
            ?: defaultThemeSeed()
    }

    val amoledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AMOLED_KEY] ?: false
    }

    val paletteStyleFlow: Flow<PaletteStyle> = dataStore.data.map { prefs ->
        parsePaletteStyle(prefs[PALETTE_STYLE_KEY])
            ?: if (prefs[EXPRESSIVE_COLOR_KEY] == true) PaletteStyle.Expressive else PaletteStyle.TonalSpot
    }

    val specVersionFlow: Flow<ColorSpec.SpecVersion> = dataStore.data.map { prefs ->
        parseSpecVersion(prefs[SPEC_VERSION_KEY])
            ?: ColorSpec.SpecVersion.SPEC_2025
    }

    val expressiveColorFlow: Flow<Boolean> = paletteStyleFlow.map { it == PaletteStyle.Expressive }

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
                    runCatching { ThemeSeed.valueOf(it) }.getOrNull()?.orAvailable()?.let { seed -> themeSeed = seed }
                }
                prefs[AMOLED_KEY]?.let {
                    amoled = it
                }
                val savedStyle = parsePaletteStyle(prefs[PALETTE_STYLE_KEY])
                if (savedStyle != null) {
                    paletteStyle = savedStyle
                } else if (prefs[EXPRESSIVE_COLOR_KEY] == true) {
                    paletteStyle = PaletteStyle.Expressive
                }
                val savedSpecVersion = parseSpecVersion(prefs[SPEC_VERSION_KEY])
                if (savedSpecVersion != null) {
                    specVersion = savedSpecVersion
                }
            }
        }
        scope.launch(Dispatchers.Default) {
            warmUpThemeCardColors()
        }
    }

    fun getInitialThemeMode(): ThemeMode = themeMode

    fun getInitialDynamicColor(): Boolean = dynamicColor

    fun getInitialThemeSeed(): ThemeSeed = themeSeed

    fun getInitialAmoled(): Boolean = amoled

    fun getInitialPaletteStyle(): PaletteStyle = paletteStyle

    fun getInitialSpecVersion(): ColorSpec.SpecVersion = specVersion

    fun getInitialExpressiveColor(): Boolean = expressiveColor

    suspend fun setThemeMode(mode: ThemeMode) {
        themeMode = mode
        dataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {

        val targetSeed = if (enabled) {
            ThemeSeed.Dynamic
        } else if (themeSeed == ThemeSeed.Dynamic) {
            ThemeSeed.Lotus
        } else {
            themeSeed
        }
        dynamicColor = enabled
        themeSeed = targetSeed
        dataStore.edit { prefs ->
            prefs[DYNAMIC_COLOR_KEY] = enabled
            prefs[THEME_SEED_KEY] = targetSeed.name
        }
    }

    suspend fun setThemeSeed(seed: ThemeSeed) {
        val targetDynamic = (seed == ThemeSeed.Dynamic)
        val targetSeed = seed.orAvailable()
        themeSeed = targetSeed
        dynamicColor = targetDynamic
        dataStore.edit { prefs ->
            prefs[THEME_SEED_KEY] = targetSeed.name
            prefs[DYNAMIC_COLOR_KEY] = targetDynamic
        }
    }

    suspend fun selectTheme(seed: ThemeSeed) = setThemeSeed(seed)

    suspend fun setAmoled(enabled: Boolean) {
        amoled = enabled
        dataStore.edit { prefs ->
            prefs[AMOLED_KEY] = enabled
        }
    }

    suspend fun setPaletteStyle(style: PaletteStyle) {
        paletteStyle = style
        dataStore.edit { prefs ->
            prefs[PALETTE_STYLE_KEY] = style.name
            prefs[EXPRESSIVE_COLOR_KEY] = (style == PaletteStyle.Expressive)
        }
    }

    suspend fun setSpecVersion(version: ColorSpec.SpecVersion) {
        specVersion = version
        dataStore.edit { prefs ->
            prefs[SPEC_VERSION_KEY] = version.name
        }
    }

    suspend fun setExpressiveColor(enabled: Boolean) {
        setPaletteStyle(if (enabled) PaletteStyle.Expressive else PaletteStyle.TonalSpot)
    }

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("app_theme_mode")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("app_dynamic_color")
        private val THEME_SEED_KEY = stringPreferencesKey("app_theme_seed")
        private val AMOLED_KEY = booleanPreferencesKey("app_amoled")
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
