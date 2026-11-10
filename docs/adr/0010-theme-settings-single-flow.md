# ADR-0010: 六维主题配置合并为单一状态流

## 状态
已采纳 (Accepted) - 2026-11-09

## 上下文与问题陈述

ADR-0002 建立的六维主题模型中（原为 7 维，`ExpressiveColor` 已由 ADR-0008 删除），`ThemeManager` 为每个维度各提供一条 Flow：

```kotlin
val themeModeFlow: Flow<ThemeMode>
val dynamicColorFlow: Flow<Boolean>
val themeSeedFlow: Flow<ThemeSeed>
val amoledFlow: Flow<Boolean>
val paletteStyleFlow: Flow<PaletteStyle>
val specVersionFlow: Flow<ColorSpec.SpecVersion>
```

（`ExpressiveColor` 维度已由 ADR-0008 删除，实为 6 条。）

这套结构有两个问题：

1. **冷启动各维度独立回落默认值**。每条 Flow 在 UI 侧都以
   `collectAsStateWithLifecycle(initialValue = getInitialXxx())` 订阅，
   而 `getInitialXxx()` 读的是内存默认值。DataStore 异步读完才发真实值，
   于是首帧用六个默认值渲染，第二帧才跳到用户配置。ADR-0009 引入颜色过渡后，
   这段跳变被渲染成约 400ms 的可见渐变，比没有动画时更显眼。
2. **六个 `getInitialXxx()` 只是「首帧兜底」的补丁**，与 Flow 重复维护同一份状态，
   且必须靠人工约定「什么时候已经落定」，容易再次写出默认值先行渲染的代码。

## 候选方案评估

### 方案 A：保留六条 Flow，首帧前加占位门禁
- 优点：改动局限在 UI 层。
- 缺点：**实测被否决**。占位期间没有真实界面，用户看到的是黑屏/白屏，
  在冷启动读盘偏慢的设备上可感知。项目明确不接受启动黑屏。

### 方案 B：合并为一条 `StateFlow<ThemeSettings?>`（选定）
- 优点：`null` 直接表达「未就绪」，兜底只需一处 `?: defaultThemeSettings()`；
  六维一致性由类型保证（不可能出现「风格是新的、种子还是旧的」这种中间态）；
  `getInitialXxx()` 与六个原子字段全部删除。
- 缺点：任何一项变化都会让订阅方重组（可接受：主题变更本就是低频全局事件）。

### 方案 C：迁移到同步 KV（照抄 KernelSU 的 SharedPreferences）
- 优点：结构上不存在异步窗口，首帧必然正确。
- 缺点：推翻现有 4 个具名 DataStore、Koin 装配与整套测试体系，改动面远大于收益。

## 决策结论

采用方案 B：

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

关键实现约束：

1. **`ensureLoaded()` 幂等且并发安全**：`compareAndSet(null, loaded)` 保证只有第一个
   调用者写入；读盘失败时回落 `defaultThemeSettings()`，避免 `settings` 永远停在 `null`。
2. **各 setter 先 `ensureLoaded()` 再 `update {}`**：否则用户在首帧前改动设置时，
   会以默认值为基底，把其他已持久化字段一起冲掉。
3. **UI 侧兜底只写一处**：`val current = settings ?: defaultThemeSettings()`。
   设置页与根组合共用同一条流。

## 影响

- **积极影响**：
  - `ThemeManager` 从 267 行降到 251 行，公开成员减少约一半（删除 6 条 Flow、
    6 个 `getInitialXxx()`、6 个原子字段）；
  - 六维配置的一致性由类型保证，不再可能读到「部分新、部分旧」的组合；
  - 后续若要接入同步 KV（方案 C），只需替换 `ensureLoaded()` 的实现，UI 无需改动。
- **已知遗留问题（已由 ADR-0012 闭环）**：
  - 冷启动仍会出现一次「默认配色 → 真实配色」的切换，因为首帧直接用默认值渲染、
    不等读盘完成。这是**刻意保留**的取舍：项目不接受启动黑屏/白屏占位。
  - 该次切换会经过 ADR-0009 的颜色过渡，观感比加动画前更明显。
  - 后续可选方向：同步 KV（方案 C）、DataStore 同步预读、或仅对启动那一次关闭补间。
  - **闭环**：已采用第三条方向（仅对启动那一次关闭补间）并叠加 Android 原生启动遮罩，
    详见 ADR-0012；本 ADR 的 `null` 表达未就绪语义保持不变。
