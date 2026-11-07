# ADR-0002: 设置页主题 7 维取色系统与 material-kolor 演进选型

## 状态
已采纳并演进更新 (Accepted & Amended) - 2026-11-06（原决策：2026-11-04）

## 上下文与问题陈述
RePoi 需对齐 PodAura 取色系统并适配 MaterialKolor 6.0+。在初始 4 维（种子、深浅、动态、AMOLED）落地的实机运行中，暴露出以下关键工程与交互痛点：
1. **主线程掉帧与切换顿挫**：色卡列表（LazyRow）在进入设置页及切换主题时，每次实时触发 HCT 色彩空间三次贝塞尔色相计算，导致 Compose 重绘严重卡顿。
2. **非动态色卡底色暗灰污染**：色卡圆角卡片背景曾误用 `inverseOnSurface`，在 Android 动态取色下为浅白，但在静态种子色（如 Lotus 蓝、Coral 红）下计算为 Tone 60 的深灰暗底。
3. **动静态切换竞态闪烁**：从动态壁纸取色切回静态种子色时，异步 Flow 与 DataStore 写入不同步导致短暂停留在初始珊瑚红色。
4. **维度扩展诉求**：用户需要对色彩风格变体（PaletteStyle）与色彩标准（ColorSpec.SpecVersion）进行个性化微调。

## 决策结论

### 1. 7 维主题系统模型
将单一配置收敛于 [`ThemeManager`](file:///c:/Users/nobody/AndroidReverse/RePoi/shared/src/commonMain/kotlin/com/nobody/repoi/data/local/ThemeManager.kt)，提供 7 维状态响应流：
- **显示模式 (ThemeMode)**：浅色 (Light) / 深色 (Dark) / 跟随系统 (System)，由 Expressive 胶囊分段按钮驱动。
- **主题配色种子 (ThemeSeed)**：8 项预置（`Dynamic`, `Pink`, `Coral`, `Amber`, `Lotus`, `Mint`, `Grape`, `Tricolor` 真寻三色）。
- **动态取色 (DynamicColor)**：Android 12+ 动态壁纸 Monet 取色，低版本与桌面端平滑降级。
- **纯黑模式 (Amoled)**：深色模式下背景设为绝对纯黑 (`Color.Black`)，保护 OLED 屏幕并省电。
- **色彩风格变体 (PaletteStyle)**：支持 `TonalSpot`, `Neutral`, `Vibrant`, `Expressive`, `Rainbow`, `FruitSalad`, `Monochrome`, `Fidelity`, `Content` 共 9 种枚举。
- **色彩标准规范 (ColorSpec.SpecVersion)**：默认 `SPEC_2025`，提供 `SPEC_2021` 兼容选项。
- **色彩表现力增强 (ExpressiveColor)**：适配 Material 3 Expressive 高饱和高动态色系。

### 2. 色板预热与无锁派生缓存 (Pre-warmed Cache)
- 为避免运行时实时演算色盘阻塞主线程，构建 [`ThemeCardColors.kt`](file:///c:/Users/nobody/AndroidReverse/RePoi/shared/src/commonMain/kotlin/com/nobody/repoi/ui/theme/ThemeCardColors.kt) 记忆化派生缓存池；
- 采用 `AtomicReference` 实现 Copy-On-Write 无锁高并发读取；
- 在 `ThemeManager` 初始化时于后台协程调用 `warmUpThemeCardColors()`，提前完成静态预置种子的派生演算，消除进入设置页与快速切色时的掉帧卡顿。

### 3. 色卡几何与色彩容器层级契约
- **三色圆盘分割**：上部半圆为 `primaryTone36`（占 50%），左下 1/4 圆为 `secondaryTone80`（占 25%），右下 1/4 圆为 `tertiaryTone65`（占 25%）；选中项外围呈现 2dp 主色描边环；
- **容器底色严禁使用前景色**：色卡圆角卡片底色固定使用 `MaterialTheme.colorScheme.surfaceContainerLow`，在所有静态与动态主题下恒定提供清爽通透的浅色容器背景，彻底根除深灰底色 bug。

### 4. 同步快照与状态竞态保护
- `ThemeManager` 采用内存原子快照（`AtomicReference`）搭配 DataStore Preferences 异步持久化；
- 在获取初始配置时提供同步方法（如 `getInitialThemeMode()`, `getInitialThemeSeed()`），消除组件首次装载及动静态切换时的竞态闪烁。

## 影响
- **积极影响**：
  - 外观设置页达到 60/120fps 流畅帧率，色相切换毫秒级响应；
  - 无论是否开启动态取色，全主题色系视觉统一通透，无色彩污染；
  - 多平台（Android / Windows Desktop）遵循同一套主题管理契约。
- **边界与约束**：
  - 桌面端环境不支持系统级壁纸 Monet 动态取色，默认走预置静态种子色兜底；
  - `ThemeManager` 为全局唯一状态源，UI 组件只读订阅 Flow，禁止私自维护本地主题派生状态。
