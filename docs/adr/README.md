# 架构决策记录目录 (Architecture Decision Records - ADR)

本目录用于持久化保存 RePoi 的重大技术选型、重构方案对比及全局架构决策。

> **智能体守则**：
> 1. 凡在方案调研或架构重构任务中产生的决策提案，统一在此目录下创建或更新版本化的 Markdown 文件（如 `0001-xxx.md`）。
> 2. 严禁将关键架构文档仅留在临时目录或对话上下文中，必须保证工程内自闭环与版本可追溯。

---

## 决策记录索引 (ADR Index)

| 编号 | 决策标题 | 核心关注领域 / 触发分支 | 状态与最后更新日期 |
| :--- | :--- | :--- | :--- |
| [ADR-0001](0001-kmp-migration-architecture.md) | PoiShelf 迁移至 KMP 跨平台架构与技术栈对齐 | **跨平台/架构迁移**：单 shared 模块、Koin、Ktor、Coil 3、Multiplatform DataStore | 已采纳 (Accepted) - 2026-10-30 |
| [ADR-0002](0002-settings-theme-material-kolor.md) | 设置页主题六维取色系统与 material-kolor 演进选型 | **主题/取色系统**：六维主题（ExpressiveColor 维度已删）、色板预热无锁缓存、三色几何色卡、消除非动态深灰底色、状态承载改为单一快照流 | 已采纳并演进更新 (Accepted & Amended) - 2026-11-09 |
| [ADR-0003](0003-settings-structure-migration.md) | 设置页自适应双窗格架构、原生大顶栏与迁移清单 | **设置页/自适应双窗格/拓扑**：宽窄屏断点、双独立顶栏、原生 LargeTopAppBar 常规字重、三大业务分组 | 已采纳并演进更新 (Accepted & Amended) - 2026-11-06 |
| [ADR-0004](0004-m3e-theme-shapes-convergence.md) | Material 3 Expressive 设计系统收敛、终结 ExpressiveTokens 反模式与全面回归官方原生组件 | **主题/设计系统/形状**：收敛至 MaterialTheme.shapes、回归原生 ElevatedCard/Card 零传参、非对称圆角扩展属性、全量彻底移除自建包装 | 已采纳并全量执行 (Accepted & Completed) - 2026-11-07 |
| [ADR-0005](0005-token-auth-import-export.md) | 登录令牌导入导出与 Token 登录原子验权 | **鉴权/会话/跨端**：请求体候选令牌直达验权、登录页双模、设置页导出安全闭环 | 已采纳并全量执行 (Accepted & Completed) - 2026-11-07 |
| [ADR-0006](0006-unified-route-action-resolver.md) | 统一路由与动作解析器 (AppRouteResolver) 架构深化 | **导航/路由/解耦**：无副作用纯解析器、强类型 RouteTarget、终结回调击穿、直连 Navigation 3 | 已采纳 (Accepted) - 2026-11-08 |
| [ADR-0007](0007-media-pipeline-async-image.md) | 多媒体管道 (MediaPipeline) 与自适应图片呈现基元架构深化 | **多媒体/呈现/防抖/手势**：纯计算 MediaPipeline、404 防御闭环、PoiAsyncImage 内置宽高比防抖、跨平台 PoiZoomableImage、渐进桥接 | 已采纳 (Accepted) - 2026-11-08 |
| [ADR-0008](0008-dynamic-theme-style-spec.md) | 动态主题下的色彩风格 / 色彩标准重推导 | **主题/取色系统/可测性**：壁纸主色作锚点重推导、纯决策树 `resolveSchemeSource`、`systemSchemeProvider` 注入缝、收敛 expressiveColor 重复维度 | 已采纳 (Accepted) - 2026-11-09 |
| [ADR-0009](0009-theme-color-motion.md) | 主题切换的颜色弹簧过渡 | **主题/动效**：逐角色 animateColorAsState、首帧与深浅模式切换不补间、走 ColorScheme.copy | 已采纳 (Accepted) - 2026-11-09 |
| [ADR-0010](0010-theme-settings-single-flow.md) | 六维主题配置合并为单一状态流 | **主题/状态管理**：StateFlow<ThemeSettings?>、null 表达未就绪、ensureLoaded 幂等并发安全、删除六个 getInitialXxx | 已采纳 (Accepted) - 2026-11-09 |
| [ADR-0011](0011-collect-architecture-eventbus-decoupling.md) | 收藏模块解耦与状态驱动架构演进 | **收藏/状态驱动/解耦**：彻底剔除 EventBus、单一事实源 StateFlow、查询归集至 CollectRepository、纯 MVI 与 PagedListLoader | 已采纳 (Accepted) - 2026-11-09 |
| [ADR-0012](0012-cold-start-theme-settling.md) | 冷启动色彩就绪与启动补间抑制 | **主题/启动体验/动效**：系统级 Splash 门禁、首次落定 Snap 抑制、48 角色弹簧动画与冷启动解耦、闭环 ADR-0009 与 ADR-0010 | 已采纳 (Accepted) - 2026-11-10 |
| [ADR-0013](0013-card-primitives-convergence.md) | 沉淀核心卡片展示基元与全业务实体卡收敛架构 | **UI/卡片基元/深模块**：PoiPosterCard、PoiListCard、PoiCaptionGridCard、PoiThumbnail，消除样式漂移，全量收敛手办/专区/搜索/收藏/相册/历史卡片 | 已采纳并全量推广 (Accepted & Deployed) - 2026-11-11 |
| [ADR-0014](0014-cover-freshness-persistent-cache.md) | 封面新鲜度持久化缓存与列表前置自愈架构 | **多媒体/持久化/自愈**：DataStore 内存镜像、前置主动重写、永久有效与 1000 条插入序 FIFO 淘汰、404 穿透自愈 | 已采纳 (Accepted) - 2026-11-11 |
| [ADR-0015](0015-action-card-poster-rigid-box-fit.md) | 动态流卡片海报槽位刚性定框与无裁切完全呈现防跳变架构 | **UI/防跳变/多媒体**：9:13 黄金刚性定框、ContentScale.Fit 居中完全呈现、骨架屏 1:1 等高对齐、彻底消除 CLS | 已被 ADR-0016 修正演进 (Amended by ADR-0016) - 2026-11-12 |
| [ADR-0016](0016-action-card-categorized-poster-layout.md) | 动态流卡片海报分类分流（手办刚性定框与相册高度自适应）架构 | **UI/防跳变/多媒体**：手办 9:13 黄金刚性定框、相册自适应高度且不设上限、消除相册白边、右侧内容高度锚定 | 已被 ADR-0021 修订演进 (Amended by ADR-0021) - 2026-11-12 |
| [ADR-0017](0017-search-pager-static-header.md) | 搜索结果页常驻顶栏与 HorizontalPager 分类分页架构 | **搜索/UI架构/多页分页**：静态 Column 平铺顶栏、彻底移除嵌套折叠与 topContentPadding、HorizontalPager 8 分类双向联动、分类独立缓存与按需懒加载、页面级独立下拉刷新 | 已采纳 (Accepted) - 2026-11-12 |
| [ADR-0018](0018-deprecate-skeletons-m3e-loading-indicator.md) | 全面废止骨架屏并收敛至 Material 3 Expressive 统一加载指示器架构 | **UI/加载态/基元收敛**：全面移除骨架屏与 Shimmer 时钟、沉淀通用展示基元 PoiLoadingView、28dp 紧凑分页指示器、保留顶栏导航可用性、修正 ADR-0015/0016 骨架屏条款 | 已采纳 (Accepted) - 2026-11-13 |
| [ADR-0019](0019-search-split-navigation-and-predictive-back.md) | 搜索路由两级解耦与 Material 3 Expressive 预测性返回动效架构 | **搜索/导航拓扑/动效**：解耦 SearchNavKey 与 SearchResultNavKey、回归 Navigation 3 原生单根栈与 MaterialSharedAxis.Z 预测性返回、重搜栈深收敛、拆分 SearchLandingViewModel 与 SearchResultViewModel | 已采纳 (Accepted) - 2026-11-14 |
| [ADR-0020](0020-zone-category-bar-and-forum-entry.md) | 专区分类栏纯化与讨论板顶栏解耦重构 | **社区/导航拓扑/Tab栏纯化**：清除分类栏伪Tab、TabRow挂载尾部常驻专区广场锚点、讨论板提升至TopAppBar动作区、消除与底栏重叠的冗余头像 | 已采纳 (Accepted) - 2026-11-14 |
| [ADR-0021](0021-action-card-entity-poster-square.md) | 动态流卡片非相册海报按实体分流（手办 9:13 与其余 1:1） | **UI/防跳变/多媒体**：手办/周边 9:13 刚性定框、厂商/角色/作品/系列/人物及未知类型 1:1 正方形、纯分类器 ActionPosterShape、相册策略保持 ADR-0016 现状 | 已采纳 (Accepted) - 2026-11-14 |



---

## 模板参考

```markdown
# ADR-000X: [决策标题]

## 状态
[提议 (Proposed) / 已采纳 (Accepted) / 已废止 (Deprecated) / 已替代 (Superseded)] - YYYY-MM-DD

## 上下文与问题陈述
[面临的技术痛点、性能瓶颈或业务诉求]

## 候选方案评估
### 方案 A: [...]
- 优点：...
- 缺点：...

### 方案 B: [...]
- 优点：...
- 缺点：...

## 决策结论
[选定方案及其核心技术考量]

## 影响
- 积极影响：...
- 消极影响 / 迁移成本：...
```
