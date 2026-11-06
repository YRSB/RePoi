# ADR-0003: 设置页自适应双窗格架构、原生大顶栏与迁移清单

## 状态
已采纳并演进更新 (Accepted & Amended) - 2026-11-06（原决策：2026-11-04）

## 上下文与问题陈述
RePoi 设置页需承载原版 Hpoi 核心功能迁移、第三方客户端能力增强（图片缓存滑块与清理、R18 过滤）及 RePoi 主题体系。在实现与实机迭代中面临如下挑战：
1. **宽屏布局断裂与顶部空白**：早期宽屏实现右侧缺失顶栏且分隔线截断，形成巨大顶部空白；单栏堆叠无法有效利用平板与桌面端横向空间。
2. **顶栏排版与视觉压迫**：尝试通过人为加粗标题、放大字号及压缩间距来填补留白，反而破坏了 Material 3 空间呼吸感与滑动过渡自然度。
3. **设置层级归属分散**：原版 13 项入口繁杂，需按功能属性重组为高内聚、易检索的一级分组。

## 决策结论

### 1. 响应式自适应双窗格拓扑 (Responsive Dual-Pane Architecture)
- **断点探测**：采用 Compose Multiplatform 原生 `currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)`（Medium 600dp+ 断点判定）；
- **宽屏架构（双独立 Scaffold + 双 LargeTopAppBar）**：
  - **左栏（权重 0.38f）**：主承载一级分组导航（偏好设置项与系统通用项：图片缓存滑块、清理缓存、关于 RePoi、导出日志、退出登录）；顶部配备常驻返回键；
  - **右栏（权重 0.62f）**：主承载选中组的二级设置详情（外观与主题、浏览与内容、账号与安全），配置对应子组大标题顶栏；
  - **贯穿分隔线**：左右栏之间由全高 `VerticalDivider` 物理分割，消灭顶部断裂；
  - **独立滚动连接**：左右两栏各自绑定独立的 `TopAppBarScrollBehavior` 与 `nestedScrollConnection`，互不干扰滚动偏置。
- **窄屏架构（单栏堆栈 + 物理返回）**：
  - 单栏模式下通过 `narrowGroup` 状态驱动主列表与详情页切换；
  - 与 [`PoiShelfBackHandler`](file:///c:/Users/nobody/AndroidReverse/RePoi/shared/src/commonMain/kotlin/com/nobody/repoi/ui/common/BackHandler.kt) 联动，支持手机端侧滑返回与物理 Back 键出栈。

### 2. 标准 Material 3 大顶栏排版契约 (LargeTopAppBar Contract)
严格遵从 Material 3 原生大顶栏设计语言，拒绝破坏性人为定制：
- **常规字重规范**：移除 `FontWeight.Bold` 加粗，采用优雅舒展的常规字重（`FontWeight.Normal`）；
- **动态平滑过渡**：展开态自动采用 `headlineMedium` (28sp)，列表向上滚动折叠时平滑缩放过渡至单行 `titleLarge` (22sp)；
- **原生展开间距**：保留原生 152dp 大顶栏展开高度，维持充足的上下留白与现代呼吸感，不硬编码压缩。

### 3. 一级分组精炼结构
设置页整体收敛为三大核心业务组：
1. **外观与主题 (Appearance)**：显示模式切换、三色色卡圆盘选择、色彩风格与色彩标准下拉框、动态主题开关、AMOLED 纯黑开关。
2. **浏览与内容 (Content)**：R18/NSFW 开关、屏蔽列表与黑名单管理、图片缓存上限滑块设置（200MB - 2GB / 无限制）及双重清理（内存缓存 + 磁盘缓存）。
3. **账号与安全 (Account)**：用户头像昵称与账号状态卡片、个人主页入口、手账同步、只读安全绑定信息、退出登录。

### 4. 统一图标与视觉规范
- **线性图标**：全量升级为 **Material Symbols Outlined** 统一风格；
- **组件形态**：全面采用 Material 3 Expressive 卡片式分段流（分组标头 `labelLarge` 主色小字 + 大圆角内凹容器卡片）。

### 5. 不做项边界严格锁定
- 明确放弃项：推送通知、账号注销、原版意见反馈、第三方三协议、原版图片质量切换（因 cover 系变体比例互不兼容且实际请求已验证不可行）、CDN 域名切换、原版版本更新与贡献榜、邀请码与繁体语言隐藏项、API 控制台（仅保留轻量日志导出）。

## 影响
- **积极影响**：
  - 宽屏（桌面端、平板、折叠屏展开态）与窄屏手机端体验统一，布局严谨无断层；
  - 顶栏滑动动效自然流畅，视觉体验完全对齐原生 Android / Material 3 标准；
  - 剔除无价值原版冗余功能，应用核心设置聚焦且高内聚。
- **边界与维护成本**：
  - 宽屏双顶栏需在切换一级菜单时重置右侧顶栏的 `heightOffset` 与 `contentOffset`，避免状态残留。
