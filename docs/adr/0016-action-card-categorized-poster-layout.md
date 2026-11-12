# ADR-0016: 动态流卡片海报分类分流（手办刚性定框与相册高度自适应）架构

## 状态
已采纳 (Accepted) - 2026-11-12

## 上下文与问题陈述
在 [ADR-0015](0015-action-card-poster-rigid-box-fit.md) 中，RePoi 针对全量动态卡片（`ActionCard`）的左侧海报槽位统一锁死为 9:13 黄金刚性定框（`Modifier.width(96.dp).aspectRatio(9f / 13f)`），并采用 `ContentScale.Fit`，成功解决了手办条目解码引发的瀑布流列重排与高度跳变（CLS）。

然而，一刀切锁死 9:13 定框在面对相册传图类动态（`isAlbum || actionType in (3, 103, 104)`）时产生了严重的视觉次生问题：玩家实拍照片绝大多数为横屏照片（16:9 或 4:3）。在 9:13 纵向细长容器内，`ContentScale.Fit` 导致照片上下留出大面积的浅灰中性底色（`surfaceContainerHighest`），在浅色主题下表现为极其突兀的“白边”，严重影响美观。

---

## 根因与排版几何测算

### 1. 右侧内容的“高度锚定效应”
`ActionCard` 整体为水平双栏结构（`Row(modifier = Modifier.height(IntrinsicSize.Min))`），卡片整体高度由左右两列的较大者决定：
- **右侧内容的物理高度是天然刚性的**：玩家头部信息（~36dp）+ 附图缩略图矩阵（72dp）+ 沉底手办统计（~24dp）= **天然需要至少约 134dp 的垂直高度**；
- 当相册传图按真实比例自适应时（宽 96dp）：
  - 16:9 横图高度为 **54dp**；
  - 4:3 横图高度为 **72dp**；
  - 1:1 正方形高度为 **96dp**。
- **数学事实**：因为 $54\text{dp} < 72\text{dp} < 96\text{dp} \le 134\text{dp}$，所以**对于绝大多数常见的相册横图与方图，卡片整体外框高度由右侧内容牢牢锁死在 ~134dp**。左侧图片在 54dp~96dp 内自适应，仅在卡片左下方留下干净的原生底色留白，**绝大多数横图与方图不会撑大或缩小卡片整体外框**。

### 2. PR #119 跳变的本质诱因
PR #119 之前跳变的核心原因在于：未携带附图的常规手办维基卡片（右侧仅 ~80dp 高）在首帧被 1:1（96dp）占位，解码后突变到 9:13（138.7dp），从而撑大卡片外框导致全流抽搐。只要对手办条目严格维持 9:13 刚性定框，信息流中 80%+ 的卡片便享有绝对稳定的布局。

---

## 候选方案评估

### 方案 A：全量保持 9:13 刚性定框（ADR-0015 现状）
- 优点：全流零跳动，实现最简单；
- 缺点：相册横图在 9:13 竖框内上下露出大面积浅灰底色（浅色主题下如白边），构图虽完整但视觉突兀；
- 结论：否。

### 方案 B：全量恢复高度自适应
- 优点：无白边、零裁切；
- 缺点：80%+ 的手办条目解码后从占位比例突变到 9:13，瀑布流全流重排，正是 ADR-0015 要解决的问题；
- 结论：否。

### 方案 C（采纳）：按业务分类分流
- 优点：手办/官方条目维持刚性定框，保留 ADR-0015 的全部防跳变收益；相册传图按真实比例伸缩，消除白边；
- 缺点：相册首帧仍可能有一次高度微调，用 4:3 折中回退与 140dp 封顶把影响压到最小；
- 结论：采纳。

---

## 决策结论

### 1. 采纳「分类分流方案 (Categorized Poster Strategy)」
在 [ActionCard.kt](../../shared/src/commonMain/kotlin/com/nobody/repoi/ui/home/components/ActionCard.kt) 中：
- 识别相册传图类动作：
  ```kotlin
  val isAlbumAction = relateItem?.isAlbum == true ||
      actionItem.actionType == 3 || actionItem.actionType == 103 || actionItem.actionType == 104
  ```
- **常规手办 / 官方条目 (`!isAlbumAction`)**：
  - 维持 ADR-0015 规范：`Modifier.width(96.dp).aspectRatio(9f / 13f).clip(MaterialTheme.shapes.small)`；
  - `adaptiveRatio = false`，`contentScale = ContentScale.Fit`，`alignment = Alignment.Center`；
  - 骨架屏 `ActionCardSkeleton` 保持 9:13，确保首屏加载与手办卡片 100% 绝对等高。
- **相册传图类卡片 (`isAlbumAction`)**：
  - 左侧海报槽位恢复高度自适应：`Modifier.width(96.dp).clip(MaterialTheme.shapes.small)`，并由基元 `PoiAsyncImage(maxAdaptiveHeight = 140.dp)` 封顶；
  - `adaptiveRatio = true`，`fallbackRatio = 4f / 3f`，纵横比缓存键沿用基元默认的「封面 URL + 缓存桶」；
  - `contentScale = ContentScale.Fit`，`alignment = Alignment.TopCenter`；
  - 允许图片下方自然保留卡片底色留白，彻底消灭图片容器内部的“白边”，实现 100% 构图零裁切。

### 2. 细长竖图 140dp 封顶线保护
相册左侧槽位通过 `PoiAsyncImage(maxAdaptiveHeight = 140.dp)`（对齐手办 9:13 的 138.7dp 封顶线）施加高度上限——`Modifier.aspectRatio` 在宽度固定时会忽略外层 `heightIn`，故上限由基元在尺寸推导阶段夹取，当遇到 9:16 或 9:20 手机超长截屏时，槽位在 140dp 处安全封顶，阻止超长竖图无休止向下撑大卡片破坏信息流节奏。

### 3. 4:3 黄金折中回退比例
未命中 `ImageAspectRatioCache` 时，相册卡片首帧回退比例指定为 `4f / 3f`（约 72dp 高），居于 16:9（54dp）与 1:1（96dp）之间，处于右侧 134dp 物理高度保护区内，将相册首帧落差最小化至 $\pm 18$dp 以内。

---

## 影响
1. **消除相册图片容器白边**：常见横图/方图在 96dp 宽槽位内完全贴合，零容器内白边、零构图裁切（超长竖图封顶后按 `Fit` 留边），无需引入重型模糊背景或额外 GPU 开销；
2. **全流 80%+ 条目零跳动**：常规手办维基、官方情报及周边条目严格锁定 9:13 黄金定框，绝大多数卡片全生命周期尺寸绝对恒定；
3. **右侧高度锚定消除大部分相册跳动**：绝大多数横图与方图高度均小于右侧内容（134dp），相册卡片外框高度依然平稳，兼得美观与流畅。

**消极影响 / 迁移成本**：相册槽位高度不再恒定，缓存未命中时首帧 4:3（72dp）与真实比例之间有 ≤18dp 的一次性微调；
`PoiAsyncImage` 新增 `maxAdaptiveHeight` 参数，需同步维护其 KDoc 与 `docs/media.md` 的约束前提说明。
