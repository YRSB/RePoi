# ADR-0030: 个人中心 Navigation 3 自适应双窗格与双端横幅 2:1 比例重构

## 状态
已采纳 (Accepted) - 2026-11-26

## 上下文与问题陈述
RePoi 的「我的」Tab（`ProfileScreen`）此前采用单/双列混排布局：在横屏且宽度达到 `>= 600dp` 时将 `FigureAssetHubSection` 与 `ProfileQuickActionBar` 拆为左右两列，而点击「我的收藏」「我的发布」「浏览历史」一律全屏压入全局 `rootBackStack`。在平板与桌面端大屏上，左侧信息密度过疏、右侧无法同屏浏览与切换个人资产列表；同时 `ExpressiveProfileHeader`（`180.dp`）与 `UserProfileBanner`（`200.dp`）的固定像素高度在不同窗宽下纵横比不一致，横幅画面裁切失衡。

## 候选方案评估
### 方案 A: 仅宽屏手写左右 `Row` 分栏 + 窄屏继续全局压栈
- 优点：无需引入内嵌子栈，窄屏交互与旧版完全一致；
- 缺点：需要维护宽窄屏两套状态与导航分支，窗口缩放时无法无缝保留当前预览条目，且违背 `ADR-0003` 已验证的 Navigation 3 原生自适应范式。

### 方案 B: Tab 内嵌局部子栈 + `rememberListDetailSceneStrategy`（采纳）
- 优点：复用 `ADR-0003` 原生 `ListDetailSceneStrategy` 机制，宽窄屏共用一套声明式路由与状态恢复模型；支持窗口跨断点平滑折叠/展开、原生预测性返回以及单窗格返回栈导航；
- 缺点：需要为复用的详情页增加顶栏返回键显隐开关，并在登出/切号时清理局部子栈。

## 决策结论

### 1. 双端 Banner 统一 2:1 纵横比与高度上限
- `ProfileScreen`（`ExpressiveProfileHeader`）与 `UserInfoScreen`（`UserProfileBanner`）的横幅容器均调整为 `fillMaxWidth().heightIn(max = 240.dp).aspectRatio(2f)`（受限在 2:1 比例内，同时在单栏宽幅场景下防止纵向过度膨胀）。
- 双窗格左侧 `listPane`（约 360–412dp 宽）与手机窄屏下高度自然落在 ~180–206dp 区间，贴合原版视觉重心且不再硬编码高度。

### 2. `ListDetail` 窗格边界与全局整页路由划分
- **迁入 `detailPane` 的页面**：
  - **我的收藏 (`UserCollectScreen`)**：包含 `FigureAssetHubSection` 点击「已预定 / 已入」与 `ProfileQuickActionBar` 点击「我的收藏」两条入口；
  - **我的发布 (`UserCreationScreen`)**；
  - **浏览历史 (`UserHistoryScreen`)**。
- **保持全局 `rootBackStack` 整页跳转的页面**：
  - **设置 (`SettingsNavKey`)**、**信箱 / 消息中心 (`MessageCenterNavKey`)**、**用户个人主页 (`UserInfoNavKey`)**、**社交关系 (`FriendListNavKey`)**、**登录 (`LoginNavKey`)**，以及详情页内部二次下钻的目标（如手办详情、相册详情等）。
- **保持原有局部交互形式**：
  - **资金明细 (`FinanceDetailBottomSheet`)** 保留窄屏 BottomSheet / 宽屏居中 Dialog 形式；
  - **我的二手** 保持当前「即将上线」提示占位。

### 3. Tab 内局部子栈、断点与左侧单列收敛
- `ProfileScreen` 内部维护私有 `ProfileSubNavKey` 返回栈与 `rememberListDetailSceneStrategy<NavKey>`，外部 `MainScreen` 的 `ProfileNavKey` 保持不变。
- **双窗格断点对齐 `>= 840dp`（`WIDTH_DP_EXPANDED_LOWER_BOUND`）**：由于 `MainScreen` 在 `>= 600dp` 已挂载左侧 `NavigationRail`，以 `840dp` 作为双窗格展开阈值与 `calculatePaneScaffoldDirective` 默认分栏规则一致，避免 600–839dp 下左右窗格过窄。
- 左侧 `listPane` 移除原手写 `isDualPane` 两列 `Row`，统一收敛为单列纵向流；窄屏推入 `detailPane` 时保留 `MainScreen` 底栏并通过 `rememberBottomBarContentPadding` 适配底部安全距离。

### 4. 宽屏默认占位、高亮联动与复用兼容
- **初始默认占位 (`detailPlaceholder`)**：宽屏初始不自动加载重列表，右侧展示柔和引导占位：
  - **已登录态**：标题「收藏、发布与浏览历史」+ 副标题「点击左侧对应入口，即可在这里展开查看」；
  - **未登录态**：标题「登录后查看你的收藏与发布」+ 副标题「可同步预定清单、已入收藏、个人发布与浏览历史」+「去登录」按钮。
- **左侧选中态与参数联动**：宽屏双窗格下，`ProfileQuickActionBar` 对应条目展示选中高亮（从「已预定 / 已入」进入同样高亮「我的收藏」）；已在收藏详情态时切换「已预定 / 已入 / 我的收藏」，通过栈顶参数更新驱动 `UserCollectViewModel.initParam` 即时切换。
- **复用页面顶栏返回键控制**：`UserCollectScreen`、`UserCreationScreen`、`UserHistoryScreen` 新增可选参数 `showBackButton: Boolean = true`。全局整页路由（含 `UserInfoScreen` 跳转）与个人中心窄屏模式保持 `true`；宽屏双窗格传入 `false` 隐藏左上角返回箭头（`UserHistoryScreen` 管理态仍保留右侧「完成」操作）。
- **账号切换与登出重置**：监听 `ProfileViewModel` 的登录态与用户 ID 变化，一旦退出登录或切换账号，自动将局部子栈重置回个人中心根页。

## 影响
- **积极影响**：
  - 平板与桌面端「我的」Tab 获得完整的双窗格资产浏览体验，同时保留设置、信箱与个人主页的独立全屏层级；
  - `ProfileScreen` 与 `UserInfoScreen` 横幅统一 2:1 比例，自适应不同窗口宽度；
  - 与 `ADR-0003` 的 Navigation 3 局部子栈模式保持架构一致。
- **消极影响 / 迁移成本**：
  - 需为三个复用资产页面新增 `showBackButton` 参数并在窄屏 Tab 内适配底栏留白。
