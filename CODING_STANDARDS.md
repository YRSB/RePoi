# RePoi 编码规范与实现标准

本项目为 Kotlin Multiplatform 跨平台应用，包名统一为 `com.nobody.repoi`。本文档定义各层级具体开发与实现契约。

---

## 架构与数据流 (Architecture & DI)

### 1. KMP 模块分层职责
- **`:shared:commonMain`**：核心代码集，承载领域模型、Ktor API、仓储、ViewModel、Compose UI 页面及组件。
- **`:shared:androidMain` / `:shared:jvmMain`**：平台能力垫片（`com.nobody.repoi.platform` 下的 `expect/actual` 实现，如 `AppDirs`, `BackPress`, `LogShare`, `Sharing`, `ThemeCaps`）与平台专属 Koin 扩展。
- **`:androidApp` 与 `:desktopApp`**：极薄宿主外壳，仅负责平台生命周期初始化与挂载跨平台根组件 `RePoiApp()`。

### 2. Koin 依赖注入与单向数据流 (UDF)
- **仓储注入**：仓储统一为具体类，在 `RepositoryModule` 中通过 `singleOf(::XxxRepository)` 单例声明。
- **ViewModel 注入**：在 `ViewModelModule` 中通过 `viewModelOf(::XxxViewModel)` 声明，UI 层使用 `koinViewModel()` 获取实例。
- **数据流转**：界面层通过 `StateFlow` 订阅单一不可变状态，用户意图通过事件向上单向流动。
- **挂起契约**：数据层挂起函数操作统一返回 `Result<T>`；网络与数据异常统一映射并抛出 `ApiException(code, message)`。

### 3. 统一列表分页加载 (`PagedListLoader`)
- 分页加载统一采用 `com.nobody.repoi.util.paging.PagedListLoader`，封装触底拉取、防重触发、游标递增与状态机切换。
- ViewModel 暴露 `StateFlow<PagedListUiState<T>>`，禁止在 ViewModel 中手工维护孤立的 `isFetching` 标志位。

### 4. 验证原则：CI 裁决权威 (CI Authority)
- **严禁本地编译**：为消除开发环境差异并提升开发敏捷度，本项目**严禁在本地执行任何 Gradle 编译、打包或单元测试任务**。
- **全量走 CI 验证**：代码变更通过 Git 提交并推送至分支/PR，全量交由 GitHub Actions CI 流水线权威执行与裁决。
- **状态追踪**：统一使用 `gh pr checks` 或 `gh run list` 监控远端 CI 矩阵（Android APK、JVM 单测、Android-host 单测及桌面端打包）执行状态。

---

## 路由契约与拓扑速查 (Navigation 3)

### 1. 路由规则与拓扑
- **路由基类**：所有路由统一继承 `AppNavKey : NavKey`（声明于 `ui/navigation/NavRoutes.kt`），由 `ui/navigation/AppNavDisplay.kt` 承载映射。
- **全局单根栈**：应用只有一条根栈 `[MainNavKey, 二级页...]`（`RePoiNavigator.rootBackStack`）。`MainNavKey` 承载主界面（`ui/navigation/MainScreen.kt`：自适应导航套件 + 5 个 Tab 首屏），二级页平铺压栈并整屏覆盖主界面；返回完全交由 Nav3 原生出栈，栈底 `MainNavKey` 永不出栈，主界面按返回直接退出 Activity。
- **Tab 切换**：仅变更 `RePoiNavigationState.currentDestination`，不触碰根栈，也不修改任何 Tab 栈内容。
- **实例隔离**：
  - 顶层 Tab 使用无参 `data object`。
  - 详情与搜索等可重复入栈路由必须包含唯一实例键 `val id: String`，确保返回栈实例身份独立与 `contentKey` 稳定隔离。
- **装饰器作用域**：Tab 首屏的 `rememberSaveableStateHolderNavEntryDecorator` 与 `rememberViewModelStoreNavEntryDecorator` 由 `RePoiNavigationState.toDecoratedEntries` 在 `RePoiNavHost` 根层恒常驻组合，二级页覆盖期间 Tab 的滚动位置与 ViewModel 不丢失；根栈二级页装饰器由 `RePoiNavDisplay` 默认挂载。
- **转场动画**：
  - Tab 首屏切换：共享轴淡入淡出（`MaterialSharedAxis.Fade`）。
  - 根栈二级页入栈/出栈：共享轴 Z 轴推入推出（`MaterialSharedAxis.Z`）。

### 2. 领域组件与路由拓扑速查

| 领域分类 | 业务特性 / 用户入口 | 对应 NavKey (`NavRoutes.kt`) | 主 Screen (`ui/`) | 状态 ViewModel | 对应仓储 (`data/repository/`) |
|---|---|---|---|---|---|
| **顶层 Tab** | 动态流 (首页) | `HomeNavKey` | `ui/home/HomeFeedScreen` | `HomeFeedViewModel` | `HomeFeedRepository` |
| **顶层 Tab** | 条目精选与分类 | `ItemsNavKey` | `ui/item/home/ItemHomeScreen` | `ItemHomeViewModel` | `HobbyRepository` |
| **顶层 Tab** | 相册广场 | `AlbumNavKey` | `ui/album/AlbumScreen` | `AlbumViewModel` | `AlbumRepository` |
| **顶层 Tab** | 关注动态 | `FollowNavKey` | `ui/follow/FollowScreen` | `FollowViewModel` | `FollowRepository` |
| **顶层 Tab** | 个人主页 | `ProfileNavKey` | `ui/profile/ProfileScreen` | `ProfileViewModel` | `UserRepository` |
| **详情链** | 手办详情 | `HobbyDetailNavKey` | `ui/detail/HobbyDetailScreen` | `HobbyDetailViewModel` | `HobbyDetailRepository` |
| **详情链** | 手办关联相册 | `HobbyAlbumListNavKey` | `ui/detail/album/HobbyAlbumListScreen` | `HobbyAlbumListViewModel` | `HobbyDetailRepository` |
| **详情链** | 角色/要素详情 | `CharacterNavKey` | `ui/character/CharacterDetailScreen` | `CharacterDetailViewModel` | `CharacterDetailRepository` |
| **详情链** | 角色相关相册 | `CharacterAlbumListNavKey` | `ui/character/CharacterAlbumListScreen` | `CharacterDetailViewModel` | `CharacterDetailRepository` |
| **详情链** | 角色登场作品 | `CharacterWorksListNavKey` | `ui/character/CharacterWorksListScreen` | `CharacterDetailViewModel` | `CharacterDetailRepository` |
| **详情链** | 作品详情 | `WorksDetailNavKey` | `ui/works/WorksDetailScreen` | `WorksDetailViewModel` | `WorksDetailRepository` |
| **详情链** | 厂商详情 | `VendorDetailNavKey` | `ui/vendor/VendorDetailScreen` | `VendorDetailViewModel` | `VendorDetailRepository` |
| **详情链** | 厂商动态与发布 | `VendorActionsNavKey` | `ui/vendor/VendorActionsScreen` | `VendorDetailViewModel` | `VendorDetailRepository` |
| **详情链** | 人物详情 (声优/原型师) | `PersonDetailNavKey` | `ui/person/PersonDetailScreen` | `PersonDetailViewModel` | `PersonDetailRepository` |
| **详情链** | 系列详情 | `SeriesDetailNavKey` | `ui/series/SeriesDetailScreen` | `SeriesDetailViewModel` | `SeriesDetailRepository` |
| **详情链** | 相册详情 (双窗格) | `AlbumDetailNavKey` | `ui/album/AlbumDetailScreen` | `AlbumDetailViewModel` | `AlbumDetailRepository` |
| **详情链** | 帖子详情 (双窗格) | `PostDetailNavKey` | `ui/post/PostDetailScreen` | `PostDetailViewModel` | `PostRepository` |
| **详情链** | 相册榜单 | `AlbumRankListNavKey` | `ui/album/AlbumRankListScreen` | `AlbumRankListViewModel` | `AlbumRepository` |
| **专区与社区** | 专区广场浏览 | `ZoneBrowseNavKey` | `ui/zone/ZoneBrowseScreen` | `ZoneBrowseViewModel` | `ZoneRepository` |
| **专区与社区** | 专区详情与分类流 | `ZoneDetailNavKey` | `ui/zone/ZoneDetailScreen` | `ZoneDetailViewModel` | `ZoneRepository` |
| **专区与社区** | 专区关注与偏好管理 | `ZoneSettingsNavKey` | `ui/zone/ZoneSettingsScreen` | `ZoneSettingsViewModel` | `ZoneRepository` |
| **专区与社区** | 讨论板主页 | `ForumHomeNavKey` | `ui/forum/ForumHomeScreen` | `ForumHomeViewModel` | `ForumRepository` |
| **专区与社区** | 版块订阅管理 | `PlateSubscribeNavKey` | `ui/forum/PlateSubscribeScreen` | `PlateSubscribeViewModel` | `ForumRepository` |
| **二手市场** | 同款二手市场流 | `ResaleMarketNavKey` | `ui/resale/ResaleMarketScreen` | `ResaleMarketViewModel` | `ResaleRepository` |
| **二手市场** | 二手交易详情 | `ResaleDetailNavKey` | `ui/resale/ResaleDetailScreen` | `ResaleDetailViewModel` | `ResaleRepository` |
| **社交与用户** | 好友与社交列表 (关注/粉丝/获赞) | `FriendListNavKey` | `ui/friend/FriendListScreen` | `FriendListViewModel` | `UserRepository` |
| **社交与用户** | 用户信息档案与资料编辑 | `UserInfoNavKey` | `ui/user/UserInfoScreen` | `UserInfoViewModel` | `UserRepository` |
| **收藏中心** | 我的收藏 | `UserCollectNavKey` | `ui/collect/UserCollectScreen` | `UserCollectViewModel` | `CollectRepository` |
| **收藏中心** | 手办收藏编辑 | `CollectEditNavKey` | `ui/collect/CollectEditScreen` | `CollectEditViewModel` | `CollectRepository` |
| **收藏中心** | 作品收藏编辑 | `WorksCollectEditNavKey` | `ui/collect/WorksCollectEditScreen` | `WorksCollectEditViewModel` | `WorksRepository` |
| **用户资产** | 我的发布 (相册/图片) | `UserCreationNavKey` | `ui/user/creation/UserCreationScreen` | `UserCreationViewModel` | `UserRepository` |
| **用户资产** | 浏览历史 | `UserHistoryNavKey` | `ui/user/history/UserHistoryScreen` | `UserHistoryViewModel` | `UserRepository` |
| **消息中心** | 消息主中心 (未读徽标/会话) | `MessageCenterNavKey` | `ui/message/center/MessageCenterScreen` | `MessageCenterViewModel` | `MessageRepository` |
| **消息中心** | 分类消息通知 (系统/回复/点赞等) | `MessageNoticeNavKey` | `ui/message/notice/MessageNoticeScreen` | `MessageNoticeViewModel` | `MessageRepository` |
| **消息中心** | 1v1 私信聊天 | `MessageChatNavKey` | `ui/message/chat/MessageChatScreen` | `MessageChatViewModel` | `MessageRepository` |
| **消息中心** | 黑名单管理 | `MessageBlockNavKey` | `ui/message/block/MessageBlockScreen` | `MessageBlockViewModel` | `MessageRepository` |
| **评论互动** | 全屏评论列表 | `HobbyCommentsNavKey` | `ui/comment/HobbyCommentsScreen` | `HobbyCommentsViewModel` | `CommentRepository` |
| **评论互动** | 评论详情与楼中楼 | `CommentDetailNavKey` | `ui/comment/CommentDetailScreen` | `CommentDetailViewModel` | `CommentRepository` |
| **搜索与通用** | 全局搜索中心 | `SearchNavKey` | `ui/search/SearchScreen` | `SearchViewModel` | `SearchRepository` |
| **搜索与通用** | 手办条件检索列表 | `HobbyListNavKey` | `ui/item/list/HobbyListScreen` | `HobbyListViewModel` | `HobbyRepository` |
| **搜索与通用** | 图片列表查看 | `PictureListNavKey` | `ui/picture/PictureListScreen` | `PictureListViewModel` | `PictureRepository` |
| **扩展与第三方** | ExHobby 扩展图库条目 | `ExHobbyListNavKey` | `ui/exhobby/ExHobbyListScreen` | `ExHobbyViewModel` | `ExHobbyRepository` |
| **系统设置** | 设置中心 | `SettingsNavKey` | `ui/settings/SettingsScreen` | `SettingsViewModel` | `UserRepository` |
| **系统设置** | 登录鉴权 | `LoginNavKey` | `ui/login/LoginScreen` | `LoginViewModel` | `UserRepository` |

---

## 网络契约与 DTO 体系 (Network & DTO)

- **统一响应封包**：由 `CommonResponse<T>` 封装。
  - 成功判定：`code == 200 || code == 0 || code == -1`。
  - 认证失效：`code == 4001`（触发全局会话失效与重登流程）。
- **DTO 三层命名规范**：
  - 外层报文：`[Feature]Payload`
  - 数据主体：`[Feature]Data`
  - 列表项条目：`[Feature]Item`
- **特异性与避坑规范**：
  - 新增/修改接口与 DTO 前，**强制查阅 [docs/API_QUIRKS.md](docs/API_QUIRKS.md)**（防范 `common-v3` 的 `state` 多态、图片 `path` 缺失绝对 URL、`itemId` 业务号 vs `nodeId` 节点号混淆等生产陷阱）。

---

## 自适应与 Material 3 Expressive 设计规范

### 1. 详情页排版
- **单流平铺规范**：手办、厂商、人物、系列、作品等详情页统一使用单一 `LazyColumn` 滚动流平铺所有数据卡片。
- **列表渲染性能**：所有 Lazy 列表项必须显式配置稳定的 `key` 与 `contentType`，避免频繁触发不必要的重组。

### 2. 自适应双窗格
- **断点分配**：通过 `currentWindowAdaptiveInfoV2().windowSizeClass` 断点判定：
  - 展开态（宽屏）：划分为左侧主信息流（`weight(mainPaneWeight)`）与右侧评论支持栏（`SupportingCommentPane`）。
  - 折叠态（紧凑屏）：主信息流占据全屏。

### 3. 悬浮操作栏与系统栏边距
- **悬浮组件**：底部悬浮胶囊统一使用 `ExpressiveFloatingToolbar` 与 `ExpressiveToolbarButton`。
- **宽度与边距约束**：悬浮顶栏与底栏必须限制最大宽度为 `fillMaxWidth(if (isWidePane) mainPaneWeight else 1f)`，并显式附加 `statusBarsPadding()` 与 `navigationBarsPadding()`。

### 4. 网格与卡片
- **多列网格**：多列自适应列表统一采用 `AdaptiveChunkedGrid`。
- **卡片宽高比**：手办卡片比例采用黄金比例 `aspectRatio(9f / 13f)`。

### 5. 主题令牌与外观
- **设计系统**：全应用统一采用 `MaterialExpressiveTheme`。
- **形状令牌**：界面所有圆角统一定义自 `ExpressiveTokens.*Shape` 设计系统令牌。
- **暗黑主题感知**：通过 `LocalIsDarkTheme.current` 感知当前暗黑模式状态。

### 6. CDN 图片变体工具 (`CdnUrlUtils`)
根据使用场景选择对应的 CDN 规格获取函数：
- `getCoverUrl(path)` (`/n`)：列表封面与详情主图（标准压缩）。
- `getCoverSrUrl(path)` (`/sr`)：瀑布流卡片与相册缩略图（自适应长宽比）。
- `getRawCoverUrl(path)` (`/raw`)：高清原图（仅限全屏大图查看器 `ImageViewerDialog`）。
- `getCoverSmallUrl(path)` / `getPicSmallUrl(path)` (`/s`)：用户头像与微缩预览（1:1 方形）。
