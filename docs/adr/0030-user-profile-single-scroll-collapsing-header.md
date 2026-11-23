# ADR-0030: 用户主页单源层叠折叠与分页视口保高架构

## 状态
已采纳 (Accepted) - 2026-11-22

## 上下文与问题陈述
在既有 PR #225 的实现中，RePoi 用户个人主页（`UserInfoScreen.kt`）采用了「外层 `LazyColumn` + `stickyHeader` + 内层 `HorizontalPager`（内含多个子 `LazyLayout`）」的双层列表嵌套模型：
1. **流畅度与物理动量撕裂**：Compose 架构中外层与内层两个独立的 `ScrollableState` 无法共享原生物理时钟。为了在内层到顶后带动外层展开卡片，代码依赖在 `NestedScrollConnection` 中手写 `dispatchRawDelta` 并通过协程分发 `performFling`。这一机制破坏了系统原生 120Hz 物理衰减与手势追踪，导致大力下滑时常在中途发生冲撞顿挫或突兀刹车停死。
2. **Tab 切换起始位置偏离与跳动**：外层 `outerListState` 是三个 Tab 共享的，而各子列表（相册、动态、二手）维护独立的滚动偏移。当用户在一个 Tab 下滑将头部折叠吸顶后横滑切换到其他 Tab 时，由于子列表缺乏统一的折叠锚点对齐与保高约束，极易引发视口上方突兀出现大片空白或内容被遮挡跳变。
3. **状态栏避让与导航冗余**：手写的吸顶偏移若未精确锁定状态栏边界，易导致 TabRow 与系统时间/电池图标重叠；且在吸顶栏强塞返回按钮导致界面杂乱。

---

## 候选方案评估

### 方案 A：继续在双层 Lazy 嵌套上排查手势接力补丁
- **优点**：不需要对现有列表树拓扑大动干戈；
- **缺点**：双层 Lazy 列表同向嵌套是 Compose 官方明文警示的脆弱模式。由于外层 LazyColumn 在滚动时不断重组与复用 Item，导致视口内边距动态变动，无法从数学和物理层面根治手势竞争与 Fling 衰减时钟断裂；
- **结论**：否。

### 方案 B：静态常驻紧凑卡片 + Pager（效仿 ADR-0017 搜索页）
- **优点**：完全无折叠联动，零手势冲突，极为稳定；
- **缺点**：用户主页包含 Banner、大头像、简介、关注/粉丝、资产统计与基础信息，展开高度约 400~500dp。若做成固定常驻，移动端竖屏垂直视口被严重压缩，无法满足用户对沉浸式大卡片浏览的完整视觉诉求；
- **结论**：否。

### 方案 C（采纳）：单源折叠头部（满屏 Pager + 顶层绝对层叠 Header + 视口保高撑底）
- **优点**：
  1. **零多层争抢，100% 原生物理流畅度**：彻底移除外层 `LazyColumn`，由底层满屏 `HorizontalPager` 承载子列表，Fling 与手势追踪完全由当前活动 Tab 的原生列表接管，彻底消灭下滑顿挫卡死；
  2. **零尺寸重测**：Pager 全程保持 `fillMaxSize()` 不变，Header 浮动层叠在上方，仅通过 `Modifier.offset` 响应单源浮点数位移，列表顶部通过 `contentPadding` 弹性占位，性能极致平滑；
  3. **吸顶锚点锁定与短列表防抖**：切换 Tab 时锁定吸顶状态，配合「视口保高撑底」规则，使任何短列表或空分页均具备稳定高度，彻底根除切 Tab 时的位置突变；
  4. **极简 Edge-to-Edge**：吸顶终点精确停靠在系统状态栏正下方，返回键随 Banner 滚走，利用已落地的 Tab 再次点击回顶与 FAB 回顶形成轻快闭环；
- **结论**：采纳。

---

## 决策结论

### 1. 布局结构重塑：绝对层叠与弹性占位
在 `UserInfoScreen.kt` 的紧凑屏模式中，采用单根 `Box` 层叠拓扑：
```kotlin
Box(modifier = Modifier.fillMaxSize()) {
    // 1. 底层：满屏 HorizontalPager（尺寸永恒不变，零重测）
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)
    ) { page ->
        // 各子列表 contentPadding 顶端留出 Header 展开高度
        UserSubPage(contentPadding = PaddingValues(top = headerExpandedHeight, bottom = 80.dp))
    }

    // 2. 顶层：浮动收折头部（完整 Banner + 资料卡 + 资产卡 + 基础卡 + 吸顶 TabRow）
    CollapsingHeader(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset { IntOffset(x = 0, y = headerOffset) }
    )
}
```

### 2. 物理与手势单一真相源
- 彻底移除 `outerListState` 与所有手写 `dispatchRawDelta`、`performFling` 协程接力逻辑；
- 顶部 Header 挂载背景轻量手势代理，手指无论在资料卡空白处还是下方列表上推，均能丝滑联动收折；
- 点击类组件（头像、关注按钮、复制 UID、编辑）通过指针拦截保持独立精准点击，无手势冲突。

### 3. Tab 切换吸顶锁定与视口保高撑底
- **吸顶对齐锁定**：当 Header 处于完全折叠吸顶状态时，横滑切换到任意 Tab，新页面内容第 1 项严格对齐在吸顶 TabRow 正下方呈现；
- **视口保高撑底 (Viewport Height Filler)**：各 Tab 页面最小高度约束不小于 `屏幕可用高度 - TabRow高度 - 状态栏高度`，确保即便在空数据或单条动态下，TabRow 依然稳固吸顶，不发生页面晃动与头部掉落。

### 4. Edge-to-Edge 与安全区契约
- 展开态：Banner 沉浸穿透状态栏铺满，返回键附带 `statusBarsPadding()`；
- 吸顶终态：最大折叠位移 `maxCollapse = headerContentHeight`，TabRow 精确停靠在系统状态栏正下方，状态栏底色由 TabRow 背景无缝延伸沉浸填充。

### 5. 导航操作极简收敛
- 返回按钮随 Banner 滚出视野，吸顶栏不保留额外返回按钮；
- 用户凭「当前活跃 Tab 再次点击」或「吸顶右下角浮现的 FAB」触发回顶与局部刷新。
