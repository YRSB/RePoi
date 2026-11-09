# ADR-0008: 动态主题下的色彩风格 / 色彩标准重推导（issue #82）

## 状态
已采纳 (Accepted) - 2026-11-09

## 上下文与问题陈述
issue #82 报告：**开启动态主题时，「色彩风格」与「色彩标准」两项设置完全不生效**；切回静态种子主题才生效。

诊断（完整过程见 PR 描述）用项目依赖里真实的 material-kolor 6.0.0 字节码做差分测量，
把 `PoiShelfTheme` 的决策树逐步镜像后复现：

- 动态主题分支 8 种 `(PaletteStyle × SpecVersion)` 组合 → 最终配色 **只有 1 种输出**；
- 静态种子分支同样 8 种组合 → **8 种输出**（证明测量本身敏感，不是死探针）。

根因在 `Theme.kt` 的取值顺序：

```kotlin
val seedScheme = seedPrimary?.let { seedColorScheme(..., style = effectiveStyle, specVersion = effectiveSpec) }
val dynamicScheme = if (seedScheme == null && dynamicColor) dynamicPoiShelfColorScheme(darkTheme) else null
val baseScheme = seedScheme ?: dynamicScheme ?: (if (darkTheme) DarkColorScheme else LightColorScheme)
```

动态主题时 `themeSeed == Dynamic`，被 `RePoiApp` 映射成 `null` → `seedScheme == null` →
`baseScheme` 直接采用平台 `dynamicLightColorScheme/dynamicDarkColorScheme`。
而这两个 Android API **没有** style/spec 参数，于是 `effectiveStyle` / `effectiveSpec`
在动态路径上从未被读取，是彻底的死代码。

次要问题：设置页「动态主题」色卡 `remember(paletteStyle, specVersion)` 却只把
色值传给 `themeCardColorsFromScheme`，导致**静态色卡跟随设置变化、动态色卡不变**，
放大"设置没反应"的观感。

## 决策结论

### 1. 动态主题 = 「壁纸主色锚点 + 用户风格重新推导」
平台配色只作为**锚点**使用，不整份照抄：

```
动态主题 && 有壁纸配色：
  seed = platformScheme.primary          // 保留 OEM/壁纸色调
  → seedColorScheme(seed, style = paletteStyle, specVersion = specVersion)
```

- 次要色/三色**不**从平台注入：否则切换风格时只有主色变化，用户仍会认为"没生效"；
- 默认组合（`TonalSpot` + `SPEC_2021`）重新推导出的 **primary 与系统 Monet 一致**：
  实测 6 组壁纸种子中 4 组逐值相同，其余 2 组仅差 1 个 RGB 步进（tone 量化舍入，
  例 `#FF006EBE` → `#FF38608F` vs `#FF39608F`），未自定义外观的用户不会察觉主色跳变。

### 2. 决策树抽成纯函数，并加注入缝
- [`resolveSchemeSource`](../../shared/src/commonMain/kotlin/com/nobody/repoi/ui/theme/ThemeSchemeResolver.kt)
  是无 Compose 依赖的纯判定：`Seed` / `Dynamic` / `Fallback` 三态；
- `PoiShelfTheme(..., systemSchemeProvider = { dynamicPoiShelfColorScheme(it) })`
  暴露平台取色注入缝，使这条链路首次可被 `jvmTest` 覆盖。

> 依据：issue #82 诊断阶段明确记录"平台取色是 `expect/actual`，JVM 恒为 `null`，
> 没有正确 seam 就无法写回归测试"。本次一并补齐。

### 3. 设置页色卡与真实主题同源
动态色卡改为 `resolveSchemeSource → seedColorScheme → themeCardColorsFromScheme`，
与根主题共用同一条重推导路径，切换风格/标准时色卡必须同步变化。

### 4. 收敛 `expressiveColor` 遗留维度
`ThemeManager.expressiveColor` / `setExpressiveColor` / `getInitialExpressiveColor` /
`expressiveColorFlow` 与 `paletteStyle` 语义完全重叠（前者只是 `== Expressive` 的投影），
一并删除；`SeedSchemes` 里两个 `expressiveColor` 重载同样移除。
`app_expressive_color` 键降级为**仅迁移来源**：读到即映射为 `PaletteStyle.Expressive`，
`setPaletteStyle` 写入新键后清除，避免二次回填。

## 影响
- **积极影响**：
  - 动态主题下 9 种色彩风格 / 2 种色彩标准真正生效（回归测试断言 ≥2 种输出）；
  - 主题决策树可单测、可注入，issue #82 这类"设置项失效"不再只能靠真机发现；
  - 主题维度与 ADR-0002 声称的 7 维模型一致（不再有第 8 个重复入口）。
- **边界与约束**：
  - 非默认风格/标准下，平台原始的次要色/三色不会保留，一律由所选风格派生——这是让设置生效的必要代价；
  - `systemSchemeProvider` 是测试缝，业务代码禁止传入自定义实现覆盖平台取色。
