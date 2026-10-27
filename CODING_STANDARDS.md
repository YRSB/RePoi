# RePoi 编码规范与实现标准

本项目为 Kotlin Multiplatform 单 shared 模块架构（`com.nobody.repoi`，Android + Desktop/JVM）。
本文档定义各层级具体开发与实现契约，由 PoiShelf 版规范平移（Hilt→Koin，其余等价保留）。

---

## 架构与数据流

- **单向数据流 (UDF)**：界面层通过状态流订阅状态，事件向上单向流动。
- **仓储层具体单例实现 (Concrete Repository)**：
  - 仓储统一使用具体类实现并在 Koin `dataModule` 中以 `single {}` 声明，保持依赖层级扁平浅显。
  - 挂起函数操作统一返回 `Result<T>`。
  - 数据层与网络异常统一映射并抛出 `ApiException(code, message)`。
- **ViewModel 注入**：由 Koin `viewModel {}` 声明（同包 `<Feature>Module.kt`），直接注入仓储具体类，向 UI 层暴露单一不可变 UI 状态流；UI 侧用 `koinViewModel()` 获取，导航参数用 `parametersOf(navParam)` 传递。
- **仓储层深度判定**：删除测试（删掉该方法后，接口使用复杂度是否会在调用点重新出现）的结论为**保留仓储层**：本仓仓储挂起方法多为单端点转发（`CommonResponse` 解包 + `ApiException` 映射），删除后该复杂度会在界面层各调用点重现。此处「保留」指仓储类本身，与上一条「依赖层级扁平浅显」（仅指注入层级）不冲突。
- **统一列表分页加载 (`PagedListLoader`)**：
  - 分页加载统一使用 `com.nobody.repoi.util.paging.PagedListLoader`，封装触底加载、防重触发、分页游标自增与空态状态机。
  - 严禁在 ViewModel 内手工维护孤立的 `isFetching` 标志位或手写未解耦的分页游标。
- **平台能力收敛 (`PlatformActions`)**：
  - 禁止在 commonMain 直接调用平台 API（Toast/Context/Intent/剪贴板/文件选择等），一律经 `PlatformActions` 接口（Koin 注入）。
  - 禁止在 UI 层使用 `LocalContext.current` 直触平台能力（Compose 自带 `UriHandler` 等除外）。

---

## 导航与多实例隔离

- **路由契约**：所有路由统一继承 `AppNavKey : NavKey`（声明于 `ui/navigation/NavRoutes.kt`），由 `ui/navigation/AppNavDisplay.kt` 承载映射；`AppNavKey` 实现须为 `@Serializable`。
- **参数化路由栈唯一性**：
  - 顶层 Tab 导航使用无参 `data object`。
  - 可重复入栈的详情与搜索路由必须包含唯一实例键 `val id: String = java.util.UUID.randomUUID().toString()`，确保返回栈实例身份独立与 `contentKey` 稳定隔离。
- **条目作用域与 ViewModelStore 自动回收 (NavEntry Scope)**：
  - 全局导航分发器通过 `rememberViewModelStoreNavEntryDecorator` 与 `rememberSaveableStateHolderNavEntryDecorator` 装饰每一个 `NavEntry`，使各条目拥有独立的 `ViewModelStoreOwner`。
  - 各 Screen 界面签名**一律禁止暴露 `instanceKey` 参数**；获取 ViewModel 时统一采用无参纯净默认值 `viewModel: XxxViewModel = koinViewModel()`。条目在退栈离开组合时由装饰器自动调用 `clearKey` 回收内存，杜绝 ViewModelStore 泄漏与 OOM 隐患。
  - 各 Screen **禁止直接引用 `ImageViewerDialog`**（由 `PoiShelfApp` 顶层统一承载全局大图弹窗）。
- **一级导航状态保留 (Top-Level State Preservation)**：
  - 顶层 5 个一级目的地（动态、条目、相册、关注、我的）在切换 Tab 时通过返回栈保活策略避免被作为出栈销毁，保留各 Tab 的列表滚动位置与已加载状态；再次点击当前 Tab 时退栈回到该 Tab 根视图。
  - 临时二级/详情页面出栈时由装饰器自动回收 ViewModel 与 UI 状态，兼顾多栈浏览连续性与内存防泄漏。
- **Navigation 3 转场与返回动画规范 (Nav3 Transitions)**：
  - 一级目的地间切换：`EnterTransition.None togetherWith ExitTransition.None`，瞬时无感知切换。
  - 二级与详情页推栈/退栈：在 `AppNavDisplay.kt` 统一配置水平平移与淡入淡出（`NAV_TRANSITION_DURATION_MS = 350`, `NAV_FADE_DURATION_MS = 250`）。
  - 预测性返回：由 `predictivePopTransitionSpec` 接管并指定 `targetContentZIndex = -1f`，严禁依赖 Nav3 默认 700ms 缩放淡出（会触发白屏与黑边闪烁）。

### 领域组件与路由拓扑速查

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

> 注：`ui/navigation/AppNavDisplay.kt` 承载上述所有路由在 `NavDisplay` 中的页面映射，`ui/PoiShelfApp.kt` 负责顶层自适应脚手架与全局大图弹窗 (`ImageViewerDialog`)。

---

## 网络契约与 DTO 体系

- **统一响应封包**：网络响应由 `CommonResponse<T>` 封装。
  - 成功判定：`success == true && code ∈ {200, 0, -1}`（见 `isSuccess`）。
  - 认证失效：`code == 4001`（触发全局会话失效与重登流程）。
- **API 调用层**：`data/remote/api/HpoiApi`（Ktor client 类，136 方法）+ `ExHobbyApi`（1 方法）；每方法 `client.post/get + FormDataContent/Parameters` + `.bodyAsJsonRef<CommonResponse<X>>()`；公共参数/头由 `CommonParamsPlugin` 统一注入，调用点不得手拼。
- **DTO 三层命名规范**：
  - 外层报文：`[Feature]Payload`
  - 数据主体：`[Feature]Data`
  - 列表项条目：`[Feature]Item`
- **字段序列化与特异性避坑**：
  - DTO 字段命名默认与 API JSON 键名一致；特殊字符通过 `@SerialName("json_key")` 显式映射。
  - **重要逆向陷阱**：涉及 `common-v3` 中 `state` 字段的 `Int` / `String` 多态、`pic-v2` 仅返回相对 `path` 等边界异常，必须严格遵循 PoiShelf `reverse-artifacts/API_QUIRKS.md` 的解析规范。

---

## 自适应与设计系统

### 1. 详情页排版
- **单流平铺规范**：手办、厂商、人物、系列、作品等详情页统一使用单一 `LazyColumn` 滚动流平铺所有数据卡片，保持信息高密度呈现与连贯滚动体验。

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
- **形状令牌**：界面所有圆角统一定义自 `ExpressiveTokens.*Shape` 设计系统令牌。
- **暗黑主题感知**：通过 `LocalIsDarkTheme.current` 感知当前暗黑模式状态。

### 6. CDN 图片变体工具 (`CdnUrlUtils`)
根据使用场景选择对应的 CDN 规格获取函数：
- `getCoverUrl(path)` (`/n`)：列表封面与详情主图（标准压缩）。
- `getCoverSrUrl(path)` (`/sr`)：瀑布流卡片与相册缩略图（自适应长宽比）。
- `getRawCoverUrl(path)` (`/raw`)：高清原图（仅限全屏大图查看器 `ImageViewerDialog`）。
- `getCoverSmallUrl(path)` / `getPicSmallUrl(path)` (`/s`)：用户头像与微缩预览（1:1 方形）。
