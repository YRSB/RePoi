# ADR-0019: 搜索路由两级解耦与 Material 3 Expressive 预测性返回动效架构

## 状态
已采纳 (Accepted) - 2026-11-14

## 上下文与问题陈述
在既有实现中，RePoi 全局搜索（`SearchScreen.kt`）将「搜索输入落地态（历史记录、热词榜、联想补全）」与「8 分类搜索结果浏览态（HorizontalPager）」耦合在单一 Composable 内，通过内部布尔状态 `isSearchInputActive` 进行瞬时布局切换。这带来了以下严重的架构缺陷与交互体验问题：
1. **预测性返回动效失效与底层劫持**：`SearchScreen.kt` 内部无条件注册了 `PoiShelfBackHandler`。无论处于落地页还是结果页浏览态，该 Handler 都在 Activity 层面抢先拦截系统返回手势，绕过了 Navigation 3 原生 `NavDisplay` 的预测性手势调度器（Predictive Back Dispatcher）。用户在边缘侧滑返回时无法看到底层页面的缩放预览，手势结束后界面直接生硬跳变。
2. **动效规范降级与空间断层**：`InteractionEntries.kt` 对 `SearchNavKey` 注册了特异化的 `fadeIn(200) togetherWith fadeOut(150)` 与纯淡出淡入的 `PredictivePopTransitionKey`，完全脱离了 RePoi 全局标准的 `MaterialSharedAxis.Z` 缩放穿透（0.8x ~ 1.1x + 错峰 FadeThrough）规范。
3. **单页面模式瞬时切换**：落地页与结果页之间通过条件分支瞬时置换，缺乏手势层级的撤销能力。用户在结果页点击搜索栏想要微调搜索词时，一旦放弃编辑无法平滑手势滑回原有浏览现场。
4. **ViewModel 职责过载与内存冗余**：单一 `SearchViewModel` 聚合了落地页与结果页的全部逻辑，代码超 700 行。用户浏览结果页时仍常驻落地页的热词与历史缓存，且 NavEntry 无法独立管理两者的生命周期。

---

## 候选方案评估

### 方案 A：维持单路由，在页面内部手写 PredictiveBackHandler 手势插值
- **优点**：无需修改外部路由拓扑与 NavKey 契约。
- **缺点**：需引入 `NavigationBackHandler` 或 `PredictiveBackHandler` 在单个 Composable 内部自绘两个子视图的位移、缩放与渐变进度；极易与外层 `NavDisplay` 的手势分配产生竞态与手势击穿；跨平台（Android / Desktop）维护代价极高；无法解决单 ViewModel 臃肿问题。
- **结论**：否。

### 方案 B（采纳）：解耦为独立两级路由 + Navigation 3 单根栈原生预测性返回
- **优点**：
  1. **零手势竞态与原生预测性返回**：拆分为 `SearchNavKey`（搜索落地页）与 `SearchResultNavKey`（搜索结果页），彻底移除私有 `BackHandler`，完全回归 Navigation 3 原生单根栈与 `RePoiNavDisplay` 默认的 `MaterialSharedAxis.Z` 预测性返回缩放动效；
  2. **高内聚的生命周期与双 ViewModel**：拆分为 `SearchLandingViewModel` 与 `SearchResultViewModel`，分别绑定各自的 `NavEntry`，状态隔离且内存自然回收；
  3. **可撤销的重搜交互体验**：在结果页点击搜索栏时，向单根栈压入带有当前词的 `SearchNavKey`。用户若取消编辑，侧滑手势即可触发 M3E 缩放预览无缝滑回当前结果页（保留 Tab 与滚动现场）；提交新词时执行栈收敛，确保返回链路清晰。
- **缺点**：需重构搜索路由拓扑、参数模型与外部调用入口。
- **结论**：采纳。

---

## 决策结论

### 1. 路由拓扑解耦：两级强类型 NavKey
在 [NavRoutes.kt](../../shared/src/commonMain/kotlin/com/nobody/repoi/ui/navigation/NavRoutes.kt) 中确立：
- **`SearchNavKey(param: SearchNavParam)`**：承载「搜索落地页」，呈现搜索框、搜索历史、大家在找热词榜及实时输入联想；
- **`SearchResultNavKey(param: SearchResultNavParam)`**：承载「搜索结果页」，呈现常驻搜索顶栏、8 分类 HorizontalPager 与独立筛选弹窗。

### 2. 回退流语义与栈收敛契约 (Stack Convergence)
- **常规搜索流**：主页点击搜索进入落地页（`[Home, SearchNavKey]`）；输入并提交后压入结果页（`[Home, SearchNavKey, SearchResultNavKey]`）。在结果页侧滑返回时，预测性缩放预览底层落地页并退回；在落地页侧滑返回时，预览底层主页并退回。
- **外部直达流**：条目详情等外部入口直接携带搜索词/分类跳转时，直接调用 `navigateToSearchResult` 压入 `SearchResultNavKey`（`[Detail, SearchResultNavKey]`）。返回时直接预览并退回来源详情页，不插入多余的中间落地页。
- **重搜栈深收敛**：用户在结果页点击顶栏搜索框时，压入 `SearchNavKey(initialKeyword = currentKeyword)`。若用户放弃修改，侧滑手势实时预览并滑回结果页；若用户提交新词，则弹出旧结果页与临时落地页并推入新结果页，单根栈始终维持单一结果页，防止返回栈无限膨胀。

### 3. 转场动效收敛：全面回归 MaterialSharedAxis.Z
彻底剔除 `InteractionEntries.kt` 中对搜索路由硬编码的 `fadeIn / fadeOut`，恢复缺省配置：
- `transitionSpec`: `MaterialSharedAxis.Z.TransitionSpec`
- `popTransitionSpec`: `MaterialSharedAxis.Z.PopTransitionSpec`
- `predictivePopTransitionSpec`: `MaterialSharedAxis.Z.PopTransitionSpec`
彻底清除 `SearchScreen` 内部私有的 `PoiShelfBackHandler`，释放底层 `OnBackPressedDispatcher` 调度权。

### 4. 架构解耦：单一职责的双 ViewModel
- **`SearchLandingViewModel`**：注入 `SearchRepository`，管理搜索历史（`SearchHistoryManager`）、大家在找热词、联想建议以及当前输入关键词草稿。
- **`SearchResultViewModel`**：接收 `SearchResultNavParam`，注入 `SearchRepository` 与 `CategoryManager`，管理 8 分类 `TabSearchState`、数据流懒加载、独立筛选快照与分页游标。

### 5. 焦点与软键盘调度契约
- 落地页进入时，搜索输入框通过 `FocusRequester` 自动获取焦点并呼起系统软键盘；
- 用户触发手势返回或确认提交搜索时，主动向系统软键盘派发 `hide()` 指令，杜绝软键盘悬浮与闪烁。

---

## 影响

### 积极影响
1. **极致的原生预测性返回体验**：在 Android 14+ 及桌面端，搜索落地页与结果页在侧滑或返回时均展现丝滑的 M3E Z 轴立体缩放与错峰渐变预览，彻底告别生硬跳变与无响应。
2. **高内聚低耦合**：彻底消灭 700+ 行的单体 ViewModel 与复杂的内部模式切换布尔标志位，两级页面各自独立可测。
3. **消除手势冲突隐患**：移除了私有 `BackHandler`，杜绝任何潜在的返回手势拦截与争夺。

### 迁移成本
1. 需要迁移 `DetailEntries.kt` 与 `TopLevelEntries.kt` 等处原有的调用点（根据是否带词分流至 `navigateToSearch` 与 `navigateToSearchResult`）；
2. 需要将现有的搜索测试迁移并拆分为落地页与结果页独立单测。
