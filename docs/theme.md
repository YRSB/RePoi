# 主题子系统 (Theme)

入口一句话：`ThemeManager` 持有唯一的 `StateFlow<ThemeSettings?>`；`RePoiApp` 消费它并喂给 `PoiShelfTheme`；`PoiShelfTheme` 解出配色后交给 `ThemeMotion` 做过渡。冷启动多两层保护：Android 原生启动遮罩 + Compose 首态落定门禁。

```text
ThemeManager (data/local)
  settings: StateFlow<ThemeSettings?>        ← null = 首次读盘未完成
      │
      ├─ awaitSettings()                     ← 宿主启动门禁等这个（ADR-0012）
      └─ setThemeMode / setDynamicColor / setThemeSeed / setAmoled / setPaletteStyle / setSpecVersion

RePoiApp (ui/navigation)
  settings ?: defaultThemeSettings()
      │  darkTheme / dynamicColor / themeSeed / amoled / paletteStyle / specVersion
      ▼
PoiShelfTheme (ui/theme/Theme.kt)
  resolvePoiColorScheme(...)                 ← 决策核心（issue #82 回归）
      ▼
ColorScheme.animateAsState(darkTheme)        ← ThemeMotion.kt
  ColdStartSettleGate.shouldSnap(...)        ← 冷启动瞬间落定，之后 spring
      ▼
MaterialExpressiveTheme(colorScheme = ...)
```

---

## 1. 六维配置与单一流

`ThemeSettings` 是六维**强类型快照**（主题模式、动态取色、种子色、AMOLED、色彩风格、色彩标准）。`ThemeManager.settings` 是唯一状态源，`null` 表示首次读盘尚未完成——不要在任何地方再引入「默认值先行」的第二条流（ADR-0010）。

- **平台能力差异**：`supportsDynamicColor` / `isDynamicSeedAvailable` 是 `expect val`（`platform/ThemeCaps.kt`），Android 为真、JVM 为假；`defaultThemeSettings()` 与 `ThemeSeed.orAvailable()` 都以它们为门。
- **设置写入**：各 setter 先 `ensureLoaded()` 再乐观更新内存并落盘，避免以默认值为基底冲掉其他已持久化字段。
- **宿主要「就绪」信号**：用 `awaitSettings()`，不要在宿主里自行为 `settings.value` 判空。

## 2. 配色决策 (resolvePoiColorScheme)

`ThemeSchemeResolver.kt` 是**无平台依赖**的决策树，`commonTest` 能直接锁死：

| 来源 | 触发条件 | 结果 |
|---|---|---|
| `Seed` | 有静态种子（含真寻三色） | 用所选的 style / spec 重新派生 |
| `Dynamic` | 开启动态主题且平台给出壁纸主色 | **只取壁纸主色作锚点**，style / spec 仍生效 |
| `Fallback` | 无种子也无壁纸色 | 内置珊瑚红兜底（调用方按深浅与 AMOLED 预处理） |

- 动态分支**绝不整份照抄**平台配色：照抄会让「色彩风格 / 色彩标准」变成死设置（issue #82）。
- `effectiveFor(style)` 把 SPEC_2025 收敛到该风格真正支持的版本：material-kolor 6.0.0 只有 `TonalSpot` / `Neutral` / `Vibrant` / `Expressive` 实现 2025，其余会**静默回退**到 2021。

## 3. 色彩弹簧过渡 (ThemeMotion)

`ThemeMotion.kt` 给 48 个颜色角色各挂一条 `animateColorAsState`，交互切换时弹簧过渡（ADR-0009）。三条边界：

1. **冷启动色彩就绪不补间**：`ColdStartSettleGate` 把首个组合的配色记为「启动基准」，就绪窗口（2s）内的第一次配色变化判为持久化配置到达，强制 `snap()`；落定后锁定，恢复 `spring()`。
2. **深浅模式切换不补间**：以 `darkTheme` 作为 `key`，模式一变就重建全部动画节点，新节点以目标色初始化。
3. **首帧不淡入**：`animateColorAsState` 首次组合即以目标值初始化。

> [!IMPORTANT]
> `ColorScheme` 是 `@Immutable class`，**没有结构化 `equals`**——实例相等即引用相等。
> 任何「两份配色是否相同」的判定都必须比较角色值（参见 `ColdStartSettleGate.settlingSignature()`），
> 否则参数未变的重组会被误判成配置变更。

就绪窗口存在的理由：门禁只能看见「配色变了」，看不见「配置读完了」（公共 API 零侵入的代价）。窗口内没有变化 = 用户从未自定义外观，窗口过期即锁定，避免把首次真实切换吞成落定。完整取舍见 [ADR-0012](../docs/adr/0012-cold-start-theme-settling.md)。

## 4. Android 启动遮罩

- 依赖：`androidx.core:core-splashscreen`（仅 `:androidApp`，不进 `:shared`）。
- 资源：`androidApp/src/main/res/values/themes.xml`（`Theme.RePoi.Starting` / `Theme.RePoi`）、`values*/colors.xml`（`splash_background`，跟随系统夜间模式而非持久化主题模式）。
- `MainActivity`：`installSplashScreen()` 必须早于 `super.onCreate`；保持条件是 `lifecycleScope` 里 `awaitSettings()` 驱动的单一布尔。

## 5. 测试地图

| 测试 | 位置 | 锁死什么 |
|---|---|---|
| `ThemeManagerTest` | `commonTest` | 读盘落定、六维写入、遗留键迁移、种子可用性 |
| `ThemeSchemeResolverTest` | `commonTest` | 三条配色来源与 spec 收敛 |
| `ThemeDynamicRegressionTest` | `jvmTest` | 动态主题下 style / spec 真的生效（issue #82） |
| `ThemeMotionTest` | `jvmTest` | 首帧落定、冷启动 Snap、交互 Spring、深浅瞬切 |

Compose UI 测试（`runComposeUiTest`）必须放 `jvmTest` 并把 `:shared:jvmTest` 跑起来；纯逻辑放 `commonTest`。详见 [docs/testing.md](testing.md)。

## 6. 相关词汇

「主题配置 (ThemeSettings)」「冷启动色彩就绪 (Cold-Start Theme Settling)」「色彩弹簧过渡 (Theme Color Motion)」——定义见 [GLOSSARY.md](../GLOSSARY.md)，避免使用「主题闪变 / 颜色跳动」等模糊说法。
