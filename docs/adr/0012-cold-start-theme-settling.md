# ADR-0012: 冷启动色彩就绪与启动补间抑制（issue #103）

## 状态
已采纳 (Accepted) - 2026-11-10

## 上下文与问题陈述
issue #103 报告：当用户开启「动态主题」并选择非默认风格（如 `Expressive`）后，杀掉应用重新冷启动时，RePoi 会先从默认的动态配色（`TonalSpot`）闪变到所选的 `Expressive` 配色。

此现象由两个 ADR 的边界叠加导致：
1. **ADR-0010（单一状态流）**：`ThemeManager.settings` 初始为 `null`，异步读盘期间 `RePoiApp` 拿 `defaultThemeSettings()` 渲染第 1 帧以避免黑白屏占位；
2. **ADR-0009（色彩弹簧过渡）**：`ThemeMotion.animateAsState` 假设首帧组合即为目标配置。当 DataStore 读取完毕在第 2 帧推下持久化配置时，动画层误认为用户主动切换了主题，触发了长达约 400ms 的 48 角色弹簧补间，将配置加载放大为肉眼可见的色彩流光闪变。

## 决策结论

采用「系统启动遮罩（Android Splash Gate）+ 冷启动首次就绪补间抑制（Cold-Start Snap）」双保险联动方案：

### 1. Android 端系统启动门禁 (Splash Screen Gate)
引入官方 `androidx.core:core-splashscreen`（API 24+ 向后兼容），构建 `Theme.RePoi.Starting` 启动主题：
- 应用图标作为启动动画元素，遮罩底色跟随系统夜间模式（`values-night`），不跟随持久化主题模式——后者正是读盘才能得知的配置；
- 在 `MainActivity.onCreate` 于 `super.onCreate` 之前执行 `installSplashScreen()`，
  并在 `lifecycleScope` 中 `awaitSettings()`，以 `setKeepOnScreenCondition` 持有
  「主题是否就绪」的单一布尔（就绪判定收敛在 `ThemeManager.awaitSettings()`，
  宿主不自行为状态流判空；生命周期协程随 Activity 销毁自动取消）；
- 将 DataStore 异步读盘耗时（通常 5~15ms）隐蔽在原生系统启动画面之下，启动遮罩淡出后，首帧展现在屏幕上的即为真实配色，彻底消除前置默认色。
- 落地的资源与清单：新增 `androidApp/src/main/res/values/themes.xml`（`Theme.RePoi.Starting` 与 `Theme.RePoi`）、
  `values/colors.xml` 与 `values-night/colors.xml`（`splash_background`），
  `AndroidManifest.xml` 的 Application 主题改指 `Theme.RePoi`、Activity 主题指 `Theme.RePoi.Starting`；
  `Theme.RePoi` 的 `android:windowBackground` 与遮罩同值，避免「遮罩淡出 → Compose 首帧」之间再露一层异色。

### 2. Compose 动画层首态落定门禁 (Initial Settle Gate)
在 `ThemeMotion.kt` 内接入门禁、把状态机独立到 `ColdStartSettleGate.kt`（`internal`，不暴露到公共 API）：
- 门禁记忆在 `key(darkTheme)` 之外：首个组合拿到的配色记为「启动基准」（动画节点本就以该配色初始化）；
- 基准之后**就绪窗口内**的第一次配色变化即持久化主题配置到达，判定为首态落定，强制使用 `snap()` 动画规格瞬间落定；
  - 就绪窗口取 2 秒：真实读盘在数十毫秒内完成，而冷启动后路由到设置页再改配色至少需要数秒，
    故窗口既能覆盖读盘，也不会把正常交互误判为落定；
  - 窗口内一次配色变化都没有（用户从未自定义外观），窗口过期同样锁定，避免把首次真实切换吞成落定；
  - 深浅模式翻转只重建启动基准、不消耗落定机会，否则读盘期间的一次系统夜间切换会把真实配置错判为交互切换；
  - 注意 `ColorScheme` 是 `@Immutable class` 而没有结构化 `equals`，实例相等即引用相等，
    故比较的是「锚点角色主辅色签名」而不是实例本身，否则参数未变的重组会提前解除门禁；
- 落定后永久锁定，用户后续在外观设置页的点击切换恢复完整的 `spring()` 弹簧流光过渡；
- 保持 `PoiShelfTheme` 与根组件调用接口整洁无侵入。

该判定不依赖外部标志位与平台能力，因此桌面端（没有原生启动遮罩）同样天然免疫冷启动补间；
Activity 重建会重新武装门禁与新窗口，但此时首个组合拿到的已是持久化配置，
仅当用户在一两秒内就改配色时才可能少一次过渡，不会重现流光。

### 3. 自动化防退化测试
在 `ThemeMotionTest` 中新增场景用例，断言：
- 模拟冷启动配置到达时色彩直接瞬切（Snap，无中间插值帧）；
- 模拟用户交互时色彩连续弹性过渡（Spring，存在中间插值帧）；
- 模拟深浅模式切换在首态落定后依旧单帧瞬切，不被门禁破坏。

## 影响
- **积极影响**：
  - 根除冷启动主题闪变，用户选定的风格（如 Expressive）在启动首屏直出；
  - 闭环 ADR-0009 与 ADR-0010 遗留的启动补间取舍；
  - 启动遮罩完全遵循 Material 3 与 Android 12+ 标准规范。
- **代价与约束**：
  - Android 端增加 `androidx.core:core-splashscreen:1.2.0` 单端依赖（Maven Central / Google Maven 最新稳定版，Apache-2.0）；
  - 极弱 IO 性能的古旧设备上 Splash 会额外展示约 10~20ms，但符合用户对系统加载的视觉预期；
  - 门禁只能观察「配色是否变了」而不能观察「配置是否读完了」（公共 API 零侵入的代价），
    故以 2 秒就绪窗口兜底：窗口内未发生配色变化即视为「启动配置与默认值一致」并锁定，
     Activity 重建后的窗口同理：重建后两秒内用户就改配色时那次切换少一次过渡，且无视觉错误（仅动效缺失）；
  - 新增 `ThemeManager.awaitSettings()` 与 `internal` 的 `ColdStartSettleGate` 属于内部接缝，
    不新增任何主题/导航组件的公开参数，仍满足规格的「公共 API 零侵入」。
