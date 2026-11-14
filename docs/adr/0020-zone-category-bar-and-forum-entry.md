# ADR-0020: 专区分类栏纯化与讨论板顶栏解耦重构

## 状态
已采纳 (Accepted) - 2026-11-14

## 上下文与问题陈述
在动态首页（`HomeFeedScreen`）的历史实现中，`PrimaryScrollableTabRow` 末尾硬编码追加了两个未选中状态的伪 Tab 项：
1. `Tab(selected = false, onClick = onNavigateToForumHome, text = { Text("讨论板") })`；
2. `Tab(selected = false, onClick = onNavigateToZoneBrowse, icon = { Icon(Icons.Filled.Add, "专区广场") })`。

这种实现存在严重的反模式（反用例风味）与体验缺陷：
1. **指示器语义破坏与假选项坏味道**：Tab 控件本质用于同一层级视图的流式切换或分类过滤，硬编码 `selected = false` 触发路由压栈全屏跳转破坏了 Tab 的心理模型与指示器布局引擎；
2. **分类管理可达性劣质化**：用户关注较多专区时，`+` 号（专区广场）被推至极右侧视口外，用户必须横向滑动多次才能触达；
3. **顶栏空间利用冗余**：`TopAppBar` 原有右侧操作区放置了用户头像，但其点击回调仅为切换至底栏的「我的」Tab（`selectTab(AppDestinations.PROFILE)`），与屏幕底部的「我的」导航项 100% 功能重复。

## 架构决策与选型

### 1. 纯化分类栏（Zone Category Bar）并挂载常驻尾部操作钮
- `PrimaryScrollableTabRow` 内部仅渲染纯粹的分类数据项（`uiState.categories`），移除所有伪 Tab；
- 使用 `Row` 容器包裹，在右侧固定放置 `IconButton(Icons.Filled.Add, "专区广场")` 作为尾部常驻操作锚点（Trailing Action），底部搭配贯穿式 `HorizontalDivider`；
- 保证用户在关注任意数量专区时均能一键进入专区广场管理分类，且 Tab 指示器随时平滑兼容后续手势分页。

### 2. 讨论板（Forum Home）提拔至顶栏动作区
- 移除顶栏与底栏完全重叠的冗余个人头像按钮；
- 在 `TopAppBar` 动作区引入 `Icons.Outlined.Forum` 讨论板专属图标，排列为 `[搜索框] [讨论板] [消息中心]`；
- 将讨论板作为全局社区独立入口直观暴露，彻底终结用分类栏承载全屏社区路由的坏味道。

## 影响与后果
- **正面**：动态页顶栏与分类栏交互心智完全对齐主流设计规范；TabRow 布局与指标计算回归纯净；
- **兼容性**：`HomeFeedScreen` 保留 `onNavigateToProfile` 可选入参签名，不破坏调用端与既有测试。
