package com.nobody.repoi.ui.settings.panes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.nobody.repoi.data.local.AllPaletteStyles
import com.nobody.repoi.data.local.AllSpecVersions
import com.nobody.repoi.data.local.ThemeMode
import com.nobody.repoi.data.local.ThemeSeed
import com.nobody.repoi.data.local.orAvailable
import com.nobody.repoi.platform.dynamicPoiShelfColorScheme
import com.nobody.repoi.platform.supportsDynamicColor
import com.nobody.repoi.ui.settings.components.ButtonGroupOption
import com.nobody.repoi.ui.settings.components.SettingButtonGroup
import com.nobody.repoi.ui.settings.components.SettingDropdownItem
import com.nobody.repoi.ui.settings.components.SettingGroupTitle
import com.nobody.repoi.ui.settings.components.SettingItemGroup
import com.nobody.repoi.ui.settings.components.SettingSwitchItem
import com.nobody.repoi.ui.theme.ThemeCardColors
import com.nobody.repoi.ui.theme.getOrCreateThemeCardColors
import com.nobody.repoi.ui.theme.PoiSchemeSource
import com.nobody.repoi.ui.theme.resolveSchemeSource
import com.nobody.repoi.ui.theme.seedColorScheme
import com.nobody.repoi.ui.theme.themeCardColorsFromScheme

/**
 * 外观与主题详情 Pane：承载显示模式、色卡圆盘、色彩算法与规范、AMOLED 纯黑等配置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppearancePane(
    showBackButton: Boolean,
    onBack: () -> Unit,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    themeSeed: ThemeSeed,
    amoled: Boolean,
    paletteStyle: PaletteStyle,
    onPaletteStyleChange: (PaletteStyle) -> Unit,
    specVersion: ColorSpec.SpecVersion,
    onSpecVersionChange: (ColorSpec.SpecVersion) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onThemeSeedChange: (ThemeSeed) -> Unit,
    onAmoledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val seeds = remember {
        if (supportsDynamicColor) {
            listOf(
                ThemeSeed.Dynamic,
                ThemeSeed.Lotus,
                ThemeSeed.Coral,
                ThemeSeed.Amber,
                ThemeSeed.Pink,
                ThemeSeed.Mint,
                ThemeSeed.Grape,
                ThemeSeed.Tricolor
            )
        } else {
            listOf(
                ThemeSeed.Lotus,
                ThemeSeed.Coral,
                ThemeSeed.Amber,
                ThemeSeed.Pink,
                ThemeSeed.Mint,
                ThemeSeed.Grape,
                ThemeSeed.Tricolor
            )
        }
    }
    // 动态色卡必须与真实主题走同一条取色路径，否则切换色彩风格/标准时色卡不跟随（issue #82）
    // 预览刻意固定浅色：该圆盘表示「动态主题会长什么样」，与当前深浅模式无关
    val previewSource = resolveSchemeSource(
        dynamicColor = true,
        seedPrimary = null,
        seedSecondary = null,
        seedTertiary = null,
        wallpaperPrimary = dynamicPoiShelfColorScheme(darkTheme = false)?.primary
    )
    // seedColorScheme 是 @Composable：必须在组合内先算出配色，再按值记忆化卡片色
    val previewScheme = when (previewSource) {
        is PoiSchemeSource.Dynamic -> seedColorScheme(
            seedColor = previewSource.anchor,
            darkTheme = false,
            amoled = false,
            style = paletteStyle,
            specVersion = specVersion
        )

        else -> null
    }
    val dynamicCardColors = remember(previewScheme, paletteStyle, specVersion) {
        if (previewScheme != null) {
            themeCardColorsFromScheme(previewScheme)
        } else {
            getOrCreateThemeCardColors(ThemeSeed.Lotus, paletteStyle, specVersion)
        }
    }

    val staticCardColors: Map<ThemeSeed, ThemeCardColors> = remember(paletteStyle, specVersion) {
        seeds.filter { it != ThemeSeed.Dynamic }.associateWith { seed ->
            getOrCreateThemeCardColors(seed, paletteStyle, specVersion)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "外观与主题",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "theme-mode", contentType = "mode") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingGroupTitle(text = "显示模式")
                    val themeOptions = listOf(
                        ButtonGroupOption(ThemeMode.LIGHT, "浅色", Icons.Outlined.LightMode),
                        ButtonGroupOption(ThemeMode.DARK, "深色", Icons.Outlined.DarkMode),
                        ButtonGroupOption(ThemeMode.SYSTEM, "跟随系统", Icons.Outlined.BrightnessAuto)
                    )
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        SettingButtonGroup(
                            options = themeOptions,
                            selected = themeMode,
                            onSelect = onThemeModeChange
                        )
                    }
                }
            }

            item(key = "seeds-flow", contentType = "seeds") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingGroupTitle(text = "主题配色")
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        items(seeds, key = { it.name }, contentType = { "seed" }) { seed ->
                            val isSelected = if (dynamicColor) {
                                seed == ThemeSeed.Dynamic
                            } else {
                                seed == themeSeed.orAvailable()
                            }
                            val cardColors = if (seed == ThemeSeed.Dynamic) {
                                dynamicCardColors
                            } else {
                                staticCardColors[seed] ?: dynamicCardColors
                            }
                            PodAuraThemeCard(
                                seed = seed,
                                colors = cardColors,
                                isSelected = isSelected,
                                onClick = { onThemeSeedChange(seed) }
                            )
                        }
                    }
                }
            }

            item(key = "color-spec-group", contentType = "segment") {
                SettingItemGroup(title = "色彩算法与规范") {
                    SettingDropdownItem(
                        icon = Icons.Outlined.Style,
                        title = "色彩风格",
                        selectedValue = paletteStyle,
                        values = AllPaletteStyles,
                        valueLabel = { it.name },
                        onValueChange = onPaletteStyleChange
                    )
                    SettingDropdownItem(
                        icon = Icons.Outlined.Tune,
                        title = "色彩标准",
                        selectedValue = specVersion,
                        values = AllSpecVersions,
                        valueLabel = { it.name },
                        onValueChange = onSpecVersionChange
                    )
                }
            }

            item(key = "switches", contentType = "segment") {
                SettingItemGroup(title = "显示效果") {
                    if (supportsDynamicColor) {
                        SettingSwitchItem(
                            icon = Icons.Outlined.Wallpaper,
                            title = "动态主题",
                            subtitle = "将壁纸颜色应用于主题",
                            checked = dynamicColor,
                            onCheckedChange = onDynamicColorChange
                        )
                    }
                    SettingSwitchItem(
                        icon = Icons.Outlined.DarkMode,
                        title = "Amoled 深色",
                        subtitle = "深色模式下使用纯黑背景省电护眼",
                        checked = amoled,
                        onCheckedChange = onAmoledChange
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PodAuraThemeCard(
    seed: ThemeSeed,
    colors: ThemeCardColors,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(text = seed.title)
            }
        },
        state = rememberTooltipState()
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onClick)
        ) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(colors.primaryTone36)
                ) {
                    Box(
                        modifier = Modifier
                            .size(25.dp)
                            .align(Alignment.BottomStart)
                            .background(colors.secondaryTone80)
                    )
                    Box(
                        modifier = Modifier
                            .size(25.dp)
                            .align(Alignment.BottomEnd)
                            .background(colors.tertiaryTone65)
                    )
                }
            }

            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 2.dp,
                            color = colors.primaryTone50,
                            shape = MaterialTheme.shapes.large
                        )
                )
            }
        }
    }
}

