# ADR-0018: 全面废止骨架屏并收敛至 Material 3 Expressive 统一加载指示器架构

## 状态
已采纳 (Accepted) - 2026-11-13

## 上下文与问题陈述
在过去的架构演进中（尤其是 ADR-0013、ADR-0015 与 ADR-0016），RePoi 为多个核心业务页面（首页动态流、手办列表、专区、详情页等）引入了骨架屏（`ActionCardSkeleton`、`HobbyGridCardSkeleton` 等），并花费大量精力通过 9:13 刚性定框以追求真实卡片与骨架屏置换时的“零跳变”。

然而，在持续的迭代与多端（Android 与 Windows Desktop）实测中，骨架屏体系暴露出诸多难以克服的本质缺陷：
1. **高昂且脆弱的双倍维护负担**：应用内每新增或微调一种卡片（图文卡、海报卡、评论流、专区卡等），都必须并行维护一套高度同构但细节容易脱节的 `*Skeleton` 伪布局。近 30 个业务界面散落着数百行占位布局模板，维护成本极高；
2. **无法真正消除的内容失配与视觉跳变**：骨架屏的核心假设是“能够精准预演真实内容几何尺寸”。但在实际业务中，卡片高度强依赖文字行数（1 行 vs 3 行）、标签折行数、评论数量以及相册图片的真实比例。服务端未返回前无从预知这些动态信息，骨架屏不仅无法消除跳变，反而造成“先看假布局扫光、再被真实排版骤然推移”的双重视觉干扰；
3. **无限动画时钟的无谓资源开销**：`ShimmerHost` 在页面加载期间维持高频无限补间动画（`rememberInfiniteTransition`），在弱网或桌面窗口最小化时持续进行绘制与 GPU 扫动；
4. **加载体验风格分裂**：在收藏模块（`CollectStates.kt`）中，此前已率先决策放弃骨架屏并改用 M3 Expressive 原生 `LoadingIndicator`，取得了清爽、稳定且对齐原版 Hpoi 的效果。而其他页面依然充斥着骨架屏与散落的旧版 `CircularProgressIndicator`（尺寸 24dp/28dp 混杂），视觉语言严重割裂。

---

## 候选方案评估

### 方案 A：继续保留并修复各页面骨架屏
- **优点**：视觉感知上提前告知用户卡片的大致网格结构；
- **缺点**：维护负担不可逆地持续膨胀；无法适配多变内容高度；无法平复跨平台与自适应断点下的测量漂移；
- **结论**：否决。

### 方案 B（采纳）：全面废止骨架屏，收敛至 Material 3 Expressive 统一加载指示器
- **优点**：
  1. **极简、诚实与专注的现代体验**：遵循 Material 3 Expressive 规范，在内容未到达时诚实呈现高辨识度、带动态形变容器的极简 `LoadingIndicator`，杜绝假布局跳动对用户视线的欺骗；
  2. **大幅瘦身与零扫光开销**：彻底删除 `ShimmerSkeleton.kt` 及各页面专属 `*Skeleton`，下线所有后台微光无限过渡，净减近千行脆弱布局代码；
  3. **全局加载基元收敛**：在 `ui/common/loading/` 沉淀单一职责展示基元 `PoiLoadingView`，同时统领首屏加载、局部区块加载与底部 28dp 紧凑触底加载；
  4. **导航脚手架恒定可用**：加载态严格隔离在 `Scaffold` 内容区内，保证顶栏标题栏与返回手势随时可用，杜绝加载过程阻塞用户退出；
- **缺点**：首屏初次加载期间无法提前呈现网格轮廓（但完全符合现代主流应用设计规范，且与原版客户端体验一致）；
- **结论**：采纳。

---

## 决策结论

### 1. 废除微光基础设施与全量业务骨架屏
- 物理删除 `shared/src/commonMain/kotlin/com/nobody/repoi/ui/home/components/ShimmerSkeleton.kt`，彻底废止 `ShimmerHost`、`Modifier.shimmerBackground()` 与共享微光时钟；
- 彻底清理移除所有业务页面中的骨架屏实现，包括但不限于：`ActionCardSkeleton`、`HobbyGridCardSkeleton`、`HobbyListCardSkeleton`、`Zone*Skeleton`、`PersonDetailSkeleton`、`CharacterDetailSkeleton`、`SeriesDetailSkeleton`、`VendorDetailSkeleton`、`AlbumDetailSkeleton`、`PostDetailSkeleton`、`ProfileSkeletonLoading` 等。

### 2. 沉淀全局通用加载基元 `PoiLoadingView`
在 `shared/src/commonMain/kotlin/com/nobody/repoi/ui/common/loading/PoiLoadingView.kt` 中沉淀标准基元：
```kotlin
@Composable
fun PoiLoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator()
    }
}
```
- 默认撑满父容器居中展示原生 `LoadingIndicator()`；
- 支持调用方传入外部 Modifier（如局部子区块可传 `Modifier.fillMaxWidth().padding(32.dp)`）；
- 彻底下线并合并既有 `ui/collect/components/CollectStates.kt` 中的 `LoadingCollectView`。

### 3. 触底分页加载指示器全面收敛
- 列表上拉触底追加下一页（Paging / Load-more）的底部指示器，一律收敛至 M3E 原生 `LoadingIndicator`，尺寸统一约束为 `28.dp`；
- 彻底消灭散落在 `HobbyListStatusComponents` 等多处的旧版单线圈 `CircularProgressIndicator`，保持全应用动态加载视觉语言完全一致。

### 4. 页面脚手架与导航可用性保障
- 实体详情页及二级页面在初始加载中，必须完整保留外层 `Scaffold` 与 `TopAppBar`；
- 仅将主体 `content` 区域切换为 `PoiLoadingView`，确保用户在弱网或等待状态下随时可以点击返回图标或滑动返回。

### 5. 评论流模型与测试平移重构
- 将 `CommentFeedEntries.kt` 状态机中的 `CommentFeedEntry.Skeleton` 领域语义重命名为 `CommentFeedEntry.Loading`；
- UI 侧挂载局部 `PoiLoadingView`，单测 `CommentFeedEntriesTest.kt` 同步平移断言，保持测试回路全绿。

### 6. 修正既有 ADR 条款
- 本决策正式修正并废止 **ADR-0015** 与 **ADR-0016** 中关于“全链路骨架 1 像素级契合”、“骨架屏 1:1 等高对齐”、“消除骨架屏跳变顿挫”的相关条款；
- 动态流海报卡片继续保持 9:13 定框与相册自适应高度的排版策略，但不再参与任何骨架屏置换。

---

## 架构收益与影响
1. **代码大幅精简**：删除约 30 个文件中沉淀的冗余骨架屏组件，消灭近千行无业务价值的占位模板代码；
2. **性能与功耗优化**：消除后台微光扫动时钟对 CPU/GPU 的持续占用；
3. **视觉风格现代统一**：全应用统一接入 Material 3 Expressive 原生 `LoadingIndicator`，动效与容器层次分明，杜绝跳色与样式割裂；
4. **交互安全闭环**：顶栏与返回导航恒定在线，杜绝白屏卡死与手势吞噬。
