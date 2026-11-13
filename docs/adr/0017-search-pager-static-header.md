# ADR-0017: 搜索结果页常驻顶栏与 HorizontalPager 分类分页架构

## 状态
已采纳 (Accepted) - 2026-11-12

## 上下文与问题陈述
在既有实现中，RePoi 全局搜索结果页（`SearchScreen.kt`）采用了手写的 `ExitUntilCollapsedScrollBehavior` 协同滚动体系：
1. **嵌套折叠的交互脆弱性**：通过在 `SearchResultsBody` 上挂载 `nestedScrollConnection`，在上滑时消费垂直手势并驱动 `SearchTopBar` 的 `IntOffset` 进行收折，同时保留 `PrimaryScrollableTabRow` 吸顶。为了防止列表被顶栏遮盖，页面采用动态高度估算与 `topContentPadding` 强行将列表内容内边距向下推移。这一手写机制在不同屏幕尺寸、双栏/单栏以及交替切换瀑布流与网格时，频繁引起手势消费竞争、触顶判定不同步与列表跳变。
2. **缺乏原生翻页体验与状态驻留**：下方的 8 类搜索分类结果（周边、相册、厂商、角色/要素、作品、系列、人物、用户）在 `SearchResultsBody` 内部通过单容器 `when (uiState.selectedTab)` 进行全量组件替换。由于缺乏标准分页容器，既无法支持用户通过横向手势在不同分类间流畅滑动，又导致每次切换 Tab 时必须清空全局 `items` 并重新发起全量网络请求，无法在 Tab 间保留已加载数据与列表滚动位置。

---

## 候选方案评估

### 方案 A：保留折叠嵌套滚动，仅在外层包裹 HorizontalPager
- **优点**：能够保留搜索框上滑收折以节省屏幕垂直空间的特性；
- **缺点**：`HorizontalPager`（水平手势）与外层嵌套折叠（垂直手势消费）及内部多列网格产生严重的手势冲突；各子页面必须继续共享动态变动的 `topContentPadding`，滑动到不同页时列表顶部易发生塌陷与跳跃；
- **结论**：否。

### 方案 B（采纳）：静态常驻 Column 顶栏 + HorizontalPager 8 分类独立缓存与懒加载
- **优点**：
  1. **零手势竞态与极致稳定**：顶栏纯静态平铺在屏幕顶部（附带 `statusBarsPadding()`），完全消除 `ExitUntilCollapsedScrollBehavior`、`nestedScrollConnection`、`IntOffset` 偏移与 `topContentPadding` 估高补丁，列表滚动与下拉刷新具备确定性的边界约束；
  2. **标准 M3 双向平滑翻页**：官方 `HorizontalPager` 搭配 `PrimaryScrollableTabRow`，提供符合现代移动交互习惯的左右滑动手势翻页与指示器渐变动效；
  3. **分类独立数据缓存与首屏秒开**：各 Tab 维护独立状态，切出再切回秒级恢复已加载内容与浏览进度，免除重复请求；
  4. **按需懒加载节省资源**：维持 `beyondViewportPageCount = 0`，未浏览的分类绝不提前触发网络请求与渲染计算；
- **缺点**：搜索顶栏常驻顶部（占高约 104dp），无法在长列表滚动时腾出额外的垂直浏览空间（但在现代大屏与自适应断点下此妥协完全在可接受范围内）；
- **结论**：采纳。

---

## 决策结论

### 1. 布局结构重塑：静态平铺 Column
在 [SearchScreen.kt](../../shared/src/commonMain/kotlin/com/nobody/repoi/ui/search/SearchScreen.kt) 的结果态中：
- 采用单一主纵向容器：
  ```kotlin
  Column(
      modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .statusBarsPadding()
  ) {
      // 1. 常驻搜索操作栏
      SearchTopBar(...)
      // 2. 常驻分类滚动页签
      PrimaryScrollableTabRow(...)
      // 3. 分类分页器
      HorizontalPager(
          state = pagerState,
          modifier = Modifier.weight(1f),
          userScrollEnabled = true,
          beyondViewportPageCount = 0
      ) { pageIndex ->
          val tab = SearchTab.entries[pageIndex]
          SearchTabContent(...)
      }
  }
  ```
- 彻底剔除 `rememberExitUntilCollapsedScrollBehavior`、`nestedScrollConnection`、`IntOffset` 位移逻辑以及用于垫高的动态 `topContentPadding` 计算。

### 2. 双向联动契约
- 用户点击 `PrimaryScrollableTabRow` 的 Tab 时，启动协程调用 `pagerState.animateScrollToPage(index)` 平滑滚入目标页；
- 用户通过左右滑动手势滚动 `HorizontalPager` 时，`PrimaryScrollableTabRow` 的选中指示器随滑动进度实时插值；当 `pagerState.settledPage` 变化时，触发 `SearchViewModel.onSelectTab(tab)` 同步逻辑。

### 3. ViewModel 多分类状态独立化与懒加载
在 [SearchViewModel.kt](../../shared/src/commonMain/kotlin/com/nobody/repoi/ui/search/SearchViewModel.kt) 中：
- 引入分类搜索状态模型 `TabSearchState`：
  ```kotlin
  data class TabSearchState(
      val items: List<SearchResultItem> = emptyList(),
      val userItems: List<SearchUserItem> = emptyList(),
      val page: Int = 1,
      val hasMore: Boolean = false,
      val isLoading: Boolean = false,
      val isRefreshing: Boolean = false,
      val isLoadingMore: Boolean = false,
      val errorMessage: String? = null,
      val isLoaded: Boolean = false
  )
  ```
- `SearchUiState` 中维护 `tabStates: Map<SearchTab, TabSearchState>`。
- **新搜索会话重置**：用户提交新搜索词时，自增 `searchSessionId` 并原子重置所有 Tab 状态，仅立即发起当前前台 Tab 的第 1 页拉取。
- **按需懒加载**：Pager 呈现某 Tab 时，若其 `isLoaded == false`，则触发该 Tab 的第 1 页网络请求。切回已加载过的 Tab 时直接复用内存数据。

### 4. 独立作用域的下拉刷新 (PullToRefresh)
- 每个 Tab 页面内部各自包裹独立的 `PullToRefreshBox`，刷新时仅针对当前 Tab 重新拉取第 1 页；
- 刷新指示器吸附于列表自身顶端（即 TabRow 紧邻下方），消灭全局偏置与跨页手势干扰。

### 5. 分类筛选状态独立持久化
- 「周边」、「相册」与「角色/要素」的筛选条件分别独立保存在 `SearchUiState` 中；
- 顶栏右上角的筛选按钮仅在当前 `SearchTab` 具备筛选能力时显示，其激活状态红点与筛选弹窗完全对齐当前活动 Tab。
