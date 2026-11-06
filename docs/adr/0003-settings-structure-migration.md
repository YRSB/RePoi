# ADR-0003: 设置页结构、迁移清单与 Navigation 3 原生自适应双窗格架构演进

## 状态
已采纳并演进更新 (Accepted & Amended) - 2026-11-06（原决策：2026-11-04）

## 上下文与问题陈述
RePoi 设置页需承载原版 Hpoi 核心功能迁移、第三方客户端能力增强（图片缓存滑块与清理、R18 过滤）及 RePoi 主题体系。在实现与实机迭代中面临如下挑战：
1. **宽屏布局断裂与顶部空白**：早期宽屏实现右侧缺失顶栏且分隔线截断，形成巨大顶部空白；单栏堆叠无法有效利用平板与桌面端横向空间。
2. **顶栏排版与视觉压迫**：尝试通过人为加粗标题、放大字号及压缩间距来填补留白，破坏了 Material 3 空间呼吸感与滑动过渡自然度。
3. **设置层级归属分散**：原版 13 项入口繁杂，需按功能属性重组为高内聚、易检索的一级分组。
4. **架构摩擦与状态耦合**：早期采用局部状态 (`selectedGroup` / `narrowGroup`) 与手绘 `Row` 分割，宽屏切换分组时需手动侵入性清空右侧顶栏的滚动偏移以规避残留，且无法获得 Nav3 原生场景过渡动效与多 Pane 协同调度。

随着 Navigation 3 与 Material 3 Adaptive 的成熟，决定将设置页整体升级为官方原生自适应双窗格架构 (`rememberListDetailSceneStrategy`)。

## 候选方案评估
### 方案 A: Navigation 3 原生自适应体系 + 模块化双窗格架构（采纳）
- 优点：与原版结构同构，对照清晰；RePoi 能力独立成组；采用官方 `rememberListDetailSceneStrategy` 原生双窗格体系，各 Pane 独立封装 `Scaffold` 与 `LargeTopAppBar`，天然隔离滚动偏置与折叠状态；完美支持原生预测性返回（Predictive Back）。
- 缺点：需将页面拆解为独立模块与局部子路由契约。

### 方案 B: 手绘 Row + narrowGroup 局部状态机（已废弃）
- 优点：改动集中在单个大文件内。
- 缺点：大文件过度膨胀（达 1400+ 行），宽屏切换分组时需手动侵入性清空右侧顶栏的滚动偏移，且无法获得 Nav3 原生场景过渡动效与多 Pane 协同调度。

## 决策结论

### 1. Navigation 3 原生自适应双窗格架构 (Native Nav3 Adaptive List-Detail)
- **全局单根栈隔离**：外部全局根栈始终保持单一 `SettingsNavKey`，设置页内部子页面（`SettingsAppearanceNavKey`, `SettingsContentNavKey`, `SettingsAccountNavKey`）为私有子 Key，定义于 `ui/settings/navigation/SettingsSubNavKeys.kt`，不污染全局导航拓扑；内部子栈多态序列化统一由 `SettingsSubNavSerializersModule` 闭环管理；
- **官方 SceneStrategy 驱动**：使用 Compose Multiplatform 原生 `rememberListDetailSceneStrategy<NavKey>` 结合 `calculatePaneScaffoldDirective(windowAdaptiveInfo).copy(horizontalPartitionSpacerSize = 0.dp)`；
- **元数据角色声明**：
  - `SettingsListNavKey` 声明 `metadata = ListDetailSceneStrategy.listPane(...)`；
  - 各二级详情页声明 `metadata = ListDetailSceneStrategy.detailPane()`；
- **宽窄屏返回栈交互规范**：
  - 宽屏（>= 600dp）：初始返回栈为 `[SettingsListNavKey, SettingsAppearanceNavKey]`，右侧始终呈现默认详情；左侧点击不同分组时执行栈顶替换（Replace Top），避免无谓的历史栈堆叠；
  - 窄屏（< 600dp）：初始返回栈为 `[SettingsListNavKey]`，点击分组后正常压栈，按返回键出栈回到分组列表，在仅有一级列表时按返回退出设置页；
  - 窗口跨端拉伸时：自适应策略根据当前窗口尺寸平滑在单/双栏间切换，保留当前激活的分组。

### 2. 标准 Material 3 原生大顶栏与独立 Pane 封装 (Independent Scaffolds)
- **模块化解耦**：彻底拆分为 `SettingsListPane.kt`, `SettingsAppearancePane.kt`, `SettingsContentPane.kt`, `SettingsAccountPane.kt`，顶层容器 `SettingsScreen.kt` 瘦身为主入口调度器；
- **各 Pane 自包含独立 Scaffold + LargeTopAppBar**：
  - 各 Pane 独立持有自己的 `TopAppBarScrollBehavior` 与 `nestedScrollConnection`，彻底免除跨分组重置滚动偏移的代码；
  - 左侧 `SettingsListPane` 顶栏标题“系统设置”，常驻返回键调用全局 `onBack`；
  - 右侧详情 Pane（外观、内容、账号）接收 `showBackButton: Boolean`，仅在单栏窄屏模式下呈现返回箭头，宽屏双栏下自动隐藏；
  - 维持 Material 3 原生大顶栏常规字重与展开折叠高度，确保全平台视觉一致。

### 3. 一级分组与迁移清单收敛
一级列表整体收敛为三大核心业务组：
1. **外观与主题 (Appearance)**：显示模式切换（跟随系统/浅色/深色）、三色几何色卡圆盘选择、色彩风格与色彩标准下拉框、动态取色开关、AMOLED 纯黑开关。
2. **浏览与内容 (Content)**：R18/NSFW 开关、ExHobby 扩展图库开关、屏蔽列表与黑名单管理入口、收藏与动态隐私可见性配置。
3. **账号与安全 (Account)**：用户头像昵称与账号状态卡片、个人主页入口、账号安全与第三方绑定状态（登录密码/手机/邮箱/微信只读状态）、退出当前账号。
4. **系统与通用（挂载于主列表底部）**：图片缓存上限滑块（100MB - 2GB / 无限制）、清理图片缓存、导出应用日志、关于 RePoi。

### 4. 统一图标与视觉规范
- **线性图标**：全量升级为 **Material Symbols Outlined** 统一风格；
- **组件形态**：全面采用 Material 3 Expressive 卡片式分段流（分组标头 `labelLarge` 主色小字 + 大圆角内凹容器卡片）。

### 5. 不做项边界严格锁定
- 明确放弃项：推送通知、账号注销、原版意见反馈、第三方三协议、原版图片质量切换（因 cover 系变体比例互不兼容且实际请求已验证不可行）、CDN 域名切换、原版版本更新与贡献榜、邀请码与繁体语言隐藏项、API 控制台（仅保留轻量日志导出）。

## 影响
- **积极影响**：
  - 彻底对齐 Navigation 3 原生自适应规范，与全局单根栈及 `RePoiNavDisplay` 无缝契合；
  - 代码大幅精简并高度模块化，`SettingsScreen.kt` 从 1445 行瘦身至 260 行左右，可读性与可维护性极大提高；
  - 自动支持原生预测性返回手势（Predictive Back）与多窗格平滑折叠；
  - 各 Pane 滚动与顶栏行为完全隔离，消除了手动重置 `heightOffset` 的偶发 UI 异常。
- **消极影响 / 迁移成本**：
  - 新增 `panes` 与 `navigation` 子包，需要维护独立的 Pane 组件契约。
