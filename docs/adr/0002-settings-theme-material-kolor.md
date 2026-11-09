# ADR-0002: 设置页主题六维取色系统与 material-kolor 演进选型

> **演进说明**：本 ADR 最初记录「7 维主题系统」。其中第 7 维 `ExpressiveColor` 已由
> [ADR-0008](0008-dynamic-theme-style-spec.md) 删除（它只是 `paletteStyle == Expressive`
> 的投影，语义完全重叠）；六维的承载方式也由 [ADR-0010](0010-theme-settings-single-flow.md)
> 从「六条独立 Flow」改为「单一 `StateFlow<ThemeSettings?>`」。以下正文已按最终状态改写，
> 涉及历史决策的地方保留原始表述并标注演进结果。

## 状态
已采纳并演进更新 (Accepted & Amended) - 2026-11-09（原决策：2026-11-04；历次修订：2026-11-06、2026-11-09）

## 上下文与问题陈述
RePoi 需对齐 PodAura 取色系统并适配 MaterialKolor 6.0+。在初始 4 维（种子、深浅、动态、AMOLED）落地的实机运行中，暴露出以下关键工程与交互痛点：
1. **主线程掉帧与切换顿挫**：色卡列表（LazyRow）在进入设置页及切换主题时，每次实时触发 HCT 色彩空间三次贝塞尔色相计算，导致 Compose 重绘严重卡顿。
2. **非动态色卡底色暗灰污染**：色卡圆角卡片背景曾误用 `inverseOnSurface`，在 Android 动态取色下为浅白，但在静态种子色（如 Lotus 蓝、Coral 红）下计算为 Tone 60 的深灰暗底。
3. **动静态切换竞态闪烁**：从动态壁纸取色切回静态种子色时，异步 Flow 与 DataStore 写入不同步导致短暂停留在初始珊瑚红色。
4. **维度扩展诉求**：用户需要对色彩风格变体（PaletteStyle）与色彩标准（ColorSpec.SpecVersion）进行个性化微调。

## 决策结论

### 1. 六维主题系统模型
将单一配置收敛于 [`ThemeManager`](file:///c:/Users/nobody/AndroidReverse/RePoi/shared/src/commonMain/kotlin/com/nobody/repoi/data/local/ThemeManager.kt)，由一份 `ThemeSettings` 快照承载六个正交维度：

- **显示模式 (ThemeMode)**：浅色 (Light) / 深色 (Dark) / 跟随系统 (System)，由 Expressive 胶囊分段按钮驱动。
- **主题配色种子 (ThemeSeed)**：8 项预置（`Dynamic`, `Pink`, `Coral`, `Amber`, `Lotus`, `Mint`, `Grape`, `Tricolor` 真寻三色）。
- **动态取色 (DynamicColor)**：Android 12+ 动态壁纸 Monet 取色，低版本与桌面端平滑降级。
- **纯黑模式 (Amoled)**：深色模式下背景设为绝对纯黑 (`Color.Black`)，保护 OLED 屏幕并省电。
- **色彩风格变体 (PaletteStyle)**：支持 `TonalSpot`, `Neutral`, `Vibrant`, `Expressive`, `Rainbow`, `FruitSalad`, `Monochrome`, `Fidelity`, `Content` 共 9 种枚举。
- **色彩标准规范 (ColorSpec.SpecVersion)**：默认 `SPEC_2025`，提供 `SPEC_2021` 兼容选项。

> **演进（ADR-0008）**：原第 7 维「色彩表现力增强 (ExpressiveColor)」已删除。它本质是
> `paletteStyle == PaletteStyle.Expressive` 的布尔投影，与第 5 维语义完全重叠。
> 遗留的 `app_expressive_color` 键降级为**一次性迁移来源**：读到即映射为
> `PaletteStyle.Expressive`，写入新键后清除。
>
> **演进（ADR-0008）**：动态主题不再整份照抄平台配色，改为「壁纸主色作锚点 + 用户所选
> 风格重新推导」，否则 `PaletteStyle` 与 `SpecVersion` 在动态主题下会成为死设置。
> 同时按风格收敛 `SpecVersion`（只有 TonalSpot / Neutral / Vibrant / Expressive 实现
> SPEC_2025，其余会静默回退）。

### 2. 色板预热与无锁派生缓存 (Pre-warmed Cache)
- 为避免运行时实时演算色盘阻塞主线程，构建 [`ThemeCardColors.kt`](file:///c:/Users/nobody/AndroidReverse/RePoi/shared/src/commonMain/kotlin/com/nobody/repoi/ui/theme/ThemeCardColors.kt) 记忆化派生缓存池；
- 采用 `AtomicReference` 实现 Copy-On-Write 无锁高并发读取；
- 在 `ThemeManager` 初始化时于后台协程调用 `warmUpThemeCardColors()`，提前完成静态预置种子的派生演算，消除进入设置页与快速切色时的掉帧卡顿。

### 3. 色卡几何与色彩容器层级契约
- **三色圆盘分割**：上部半圆为 `primaryTone36`（占 50%），左下 1/4 圆为 `secondaryTone80`（占 25%），右下 1/4 圆为 `tertiaryTone65`（占 25%）；选中项外围呈现 2dp 主色描边环；
- **容器底色严禁使用前景色**：色卡圆角卡片底色固定使用 `MaterialTheme.colorScheme.surfaceContainerLow`，在所有静态与动态主题下恒定提供清爽通透的浅色容器背景，彻底根除深灰色底色 bug。
- **动态色卡与根主题同源**（ADR-0008）：动态色卡走 `resolveSchemeSource → seedColorScheme → themeCardColorsFromScheme`，与根主题共用同一条重推导路径，切换风格/标准时色卡必须同步变化。

### 4. 状态承载：单一快照流（ADR-0010 修订）
> **原方案已废止**：本节最初为「六条独立 Flow + 内存原子快照（`AtomicReference`）+
> 同步方法 `getInitialThemeMode()` / `getInitialThemeSeed()`」。

现状：

```kotlin
data class ThemeSettings(
    val themeMode: ThemeMode,
    val dynamicColor: Boolean,
    val themeSeed: ThemeSeed,
    val amoled: Boolean,
    val paletteStyle: PaletteStyle,
    val specVersion: ColorSpec.SpecVersion,
)

class ThemeManager(dataStore: DataStore<Preferences>) {
    private val state = MutableStateFlow<ThemeSettings?>(null)
    val settings: StateFlow<ThemeSettings?> = state.asStateFlow()
}
```

- **六维一致性由类型保证**：不再可能读到「风格是新的、种子还是旧的」这种中间态；
- **`null` 表示首次读盘未完成**，UI 侧兜底只写一处 `?: defaultThemeSettings()`；
- **`ensureLoaded()` 幂等且并发安全**（`compareAndSet(null, …)`），读盘失败回落平台默认值；
  各 setter 先 `ensureLoaded()` 再 `update {}`，避免以默认值为基底冲掉已持久化字段；
- 六个 `getInitialXxx()` 与六个原子字段已全部删除。

**关于竞态闪烁的诚实结论**：本次修订**没有**消除冷启动那一次「默认配色 → 真实配色」
的切换。曾尝试「首帧前挂起读盘 + 黑白占位」的方案，因项目不接受启动黑屏/白屏而否决。
闪变作为已知遗留问题保留，候选解法见 ADR-0010「已知遗留问题」一节。

### 5. 主题切换的颜色过渡（ADR-0009）
配色对象变化时，48 个颜色角色逐条 `animateColorAsState(spring())` 过渡；深浅模式切换
以 `key(darkTheme)` 重建动画节点，立即生效不补间。

## 影响
- **积极影响**：
  - 外观设置页达到 60/120fps 流畅帧率，色相切换毫秒级响应；
  - 无论是否开启动态取色，全主题色系视觉统一通透，无色彩污染；
  - 多平台（Android / Windows Desktop）遵循同一套主题管理契约；
  - 六维配置的一致性由单一快照类型保证，`ThemeManager` 公开成员较原方案减少约一半。
- **边界与约束**：
  - 桌面端环境不支持系统级壁纸 Monet 动态取色，默认走预置静态种子色兜底；
  - `ThemeManager` 为全局唯一状态源，UI 组件只读订阅 `settings`，禁止私自维护本地主题派生状态；
  - 冷启动仍有一次「默认配色 → 真实配色」的切换，且会经过颜色过渡（观感比加动画前更明显）。
