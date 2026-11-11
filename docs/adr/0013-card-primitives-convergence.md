# ADR-0013: 沉淀核心卡片展示基元与全业务实体卡收敛架构

## 状态
已采纳并全量推广 (Accepted & Deployed) - 2026-11-11

## 上下文与问题陈述
在 RePoi 的长期演进中，多达 5 个独立业务模块（手办条目列表、专区详情、跨域综合搜索、个人收藏中心 8 类子页、浏览历史与相册瀑布流）各自实现了数十个实体展示卡片组件。随之演进出以下架构痛点：
1. **尺寸与视觉规范漂移**：列表卡缩略图在 68dp 与 70dp 之间漂移，内边距在 8dp、10dp 与 12dp 之间摇摆，圆角形状各不相同；
2. **多媒体管道绕过与重复造轮子**：多处卡片绕过 `PoiAsyncImage` 与 `MediaPipeline`，直接裸调 `coil3.compose.AsyncImage` 并手写破损图标回落逻辑，丢失了服务端 404 兜底、缓存优化与纵横比自适应能力；
3. **排版对齐缺陷**：部分列表卡使用了 `Alignment.CenterVertically`，导致缺少副标题信息的单行标题条目（如纯角色名、厂商名）在缩略图右侧居中悬空，视觉极不协调；
4. **反模式诱惑**：如果设计一个绑定业务 DTO 的单一万能实体卡，会导致 UI 层与特定数据模型强耦合，不可复用且难以演进；若仅做极度薄弱的容器包裹，又无法内聚防眩光蒙层、黄金比例与遮罩手势。

## 决策结论

### 1. 沉淀高内聚、小接口的卡片展示基元 (`ui/common/card/`)
设计并沉淀三项核心卡片基元与一套缩略图辅助构件，业务层只通过组合插槽进行装配：
- **`PoiPosterCard` (网格海报卡)**：
  - 承载多列网格海报展示；
  - 默认采用 9:13 黄金纵横比，亦支持传 `null` 由内容纵横比自适应驱动；
  - 内置防眩光渐变底部半透明蒙层、右上角徽章插槽、底部微标签插槽及多选半透明遮罩插槽。
- **`PoiListCard` (列表条目卡)**：
  - 承载单列流水平横向图文展示；
  - 统一 10dp M3E 规范内边距与横向 10dp 间距；
  - 默认垂直对齐为 `Alignment.Top`，确保短标题严格顶端对齐，解决居中悬空漂移；
  - 左侧开放 `thumbnail` 插槽，配套 `PoiThumbnail.Square` (标准 68dp) 与 `PoiThumbnail.Poster` (标准 90×130dp)；
  - 原生支持 `onLongClick`、`enabled`（置灰与禁用交互）以及 `PoiListCardSkeleton` 骨架屏基元。
- **`PoiCaptionGridCard` (分体图文网格卡)**：
  - 承载上图下文（如厂商/系列 1:1 正方形卡、相册瀑布流）呈现；
  - 统一采用 `surfaceContainerLow` 容器底色与 1dp 标高；
  - 内置 `overlay` 插槽，使多选勾选与半透明遮罩能以声明式方式叠加在整卡上方。

### 2. 多媒体管道收敛与图片策略解耦
- 全量卡片严禁裸调 Coil `AsyncImage`，一律收敛至 `PoiAsyncImage`，由 `MediaPipeline` 统一裁决 CDN 变体（手办 `/n`、相册 `/sr`、缩略图 `/s` 等）；
- 封面纵横比由 `aspectRatio` 与 `adaptiveRatio` 结合 `ImageAspectRatioCache` 驱动，彻底杜绝网格与瀑布流加载跳动。

### 3. 三批渐进推广与全量落地
- **Batch 1 (基元试点)**：
  - `ui/item/list/components/HobbyCards.kt` (`HobbyGridCard`, `HobbyListCard`, 骨架屏)
- **Batch 2 (检索与专区)**：
  - `ui/search/components/SearchResultCards.kt` (`HobbySearchCard`, `GeneralSearchCard`)
  - `ui/zone/components/ZoneEntityCard.kt` (`ZoneEntityCard`)
- **Batch 3 (收藏、相册与历史)**：
  - `ui/collect/subpage/HobbyCards.kt` (`HobbyCollectGridCard`, `HobbyCollectListCard`)
  - `ui/collect/subpage/CommonCards.kt` (`CommonCollectListCard`, `CommonCollectGridCard`)
  - `ui/collect/components/WorksCollectCards.kt` (`WorksCollectCard`)
  - `ui/collect/components/AlbumCards.kt` (`CollectAlbumCard`)
  - `ui/album/components/AlbumFeedCard.kt` (`AlbumFeedCard`)
  - `ui/user/history/UserHistoryScreen.kt` (`HobbyHistoryGridCard`, `AlbumHistoryWaterfallCard`)

## 架构收益与影响
1. **代码大幅瘦身**：仅 Batch 2 与 Batch 3 的卡片重构便净消除 500 余行重复的布局、圆角与图片破损兜底逻辑；
2. **零样式漂移**：全站横向条目卡缩略图统一为 68dp/90×130dp，内边距统一为 10dp，所有卡片严格遵从 Material 3 Expressive 规范；
3. **修复排版瑕疵**：顶部对齐收敛彻底修复了单标题文本垂直居中浮空的视觉缺陷。
