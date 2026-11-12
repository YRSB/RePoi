# ADR-0015: 动态流卡片海报槽位刚性定框与无裁切完全呈现防跳变架构

## 状态
已采纳 (Accepted) - 2026-11-12

## 上下文与问题陈述
在 RePoi 客户端的多个信息流场景（`HomeFeedScreen` 首页动态情报、`FollowScreen` 关注流、`UserInfoScreen` 个人动态、`VendorActionsScreen` 厂商动态及 `ZoneDetailScreen` 专区动态）中，列表在首次加载、Tab 切换或快速滚动时，用户频繁遭遇卡片高度剧烈跳变（Cumulative Layout Shift, CLS）与瀑布流列重排的视觉痛点。

### 逆向工程事实与根因剖析
1. **原版 Fresco 滞后自适应的固有缺陷**：深入 Hpoi 原版逆向源码（`ActionListAdapter.java:798`）可知，原版海报控件 `WrapContentDraweeView` 采用了 `b(1.0f).e(0.4f)`。它在视图绑定期预设 `1.0f`（1:1 正方形）占位，待图片下载并解码出 `ImageInfo` 后，再于回调中动态执行 `setAspectRatio` 伸缩。这在遇到相册传图（大量横向照片、手机截图）时，必然触发 `requestLayout` 导致卡片被撑大或压扁；原版为了掩盖该问题，甚至在全局强关了列表动画（`list.setItemAnimator(null)`）；
2. **RePoi 早期移植的继承痛点**：RePoi 在早期 `ActionCard.kt` 中采用了 `PoiAsyncImage(adaptiveRatio = true, fallbackRatio = 1.0f)`。首帧在内存未命中 `ImageAspectRatioCache` 时强制以 1:1（`96dp × 96dp`）绘制；当手办或相册图片下载完毕，`aspectRatio` 突变（例如手办标准海报落到约 0.69），海报高度瞬间自 96dp 撑大至 138.7dp。在 Compose 的 `LazyVerticalStaggeredGrid` 下，item 高度突变直接导致瀑布流重新计算跨列跨行分布，产生严重的跳动抽搐；
3. **用户核心业务约束**：
   - 手办维基条目与厂商海报规范基本收敛于 9:13 黄金纵横比；
   - 动态卡片跳变的主因是相册传图、玩家随拍等非标尺寸图片；
   - **严禁粗暴裁剪图片**（必须完整呈现照片细节与构图，允许留白）。

---

## 候选方案评估

### 方案 A：维持 `adaptiveRatio = true` 仅纠正占位比与增强缓存
- **优点**：改动小，只需修改 `fallbackRatio`。
- **缺点**：治标不治本。相册传图包含各种宽高比（16:9、4:3、9:16、9:20 等），任何静态回退比例都无法预知未知相片；且缓存未命中或被淘汰时，依然必然发生高度重排拉扯。

### 方案 B：固定物理尺寸 + `ContentScale.Crop`
- **优点**：卡片尺寸恒定，无留白。
- **缺点**：严重破坏相册照片与生活实拍的构图，横向宽图两侧大量细节被无情切断，不符合用户“完整呈现”的刚性诉求。

### 方案 C (采纳)：对齐原版角色库 9:13 刚性定框 + `ContentScale.Fit` 居中完全呈现 + M3E 极简留白
- **优点**：
  1. **零测量重排 (Zero Relayout)**：借鉴 Hpoi 原版在角色与作品列表（`LibraryCharacterListAdapter.java:76`）锁死 `0.6923077f`（9:13）的精髓，在 Compose Measure 阶段即根据 `Modifier.width(96.dp).aspectRatio(9f / 13f)` 瞬时算出确切高度（约 138.7dp）。图片解码后仅填充像素缓冲区，彻底阻断任何尺寸二次伸缩；
  2. **100% 完整呈现无裁剪**：采用 `ContentScale.Fit` + `Alignment.Center`。9:13 手办海报自然撑满；相册横图（16:9/4:3）上下留白，手机长图左右留白，原汁原味呈现完整构图，零像素裁切；
  3. **M3E 原生中性底色**：留白底色直接复用卡片容器的原生 `surfaceContainerHighest` 底色（对齐原版 `bgPictureGallery`），呈现极简相框质感，零额外 GPU 模糊与内存开销；
  4. **全链路骨架 1 像素级契合**：骨架屏 `ActionCardSkeleton` 与无图纯文本相册 `AlbumTextCover` 全部收敛至 9:13，实现数据加载前后 1 像素级等高无缝置换。

---

## 决策结论

### 1. `ActionCard` 左侧槽位锁死 9:13 刚性定框并关闭 `adaptiveRatio`
在 [ActionCard.kt](shared/src/commonMain/kotlin/com/nobody/repoi/ui/home/components/ActionCard.kt) 中：
- 废弃 `adaptiveRatio = true` 与 `fallbackRatio = 1.0f`；
- 海报外框固定为 `Modifier.width(96.dp).aspectRatio(9f / 13f).clip(MaterialTheme.shapes.small)`；
- 内部配置 `contentScale = ContentScale.Fit`、`alignment = Alignment.Center`；
- 无封面纯文本相册 `AlbumTextCover` 同步采用 `Modifier.width(96.dp).aspectRatio(9f / 13f)`。

### 2. `ActionCardSkeleton` 骨架屏几何尺寸严格同步
在 [ShimmerSkeleton.kt](shared/src/commonMain/kotlin/com/nobody/repoi/ui/home/components/ShimmerSkeleton.kt) 中：
- 将海报占位由 `aspectRatio(1.0f)` 统一变更为 `aspectRatio(9f / 13f)`；
- 确保骨架屏在卡片整体高度（约 162dp）上与真实数据卡片实现绝对 1:1 等高对齐。

### 3. 保留配图锁高与无封面自适应
- 右侧配图矩阵（`displayExtraImages`）继续维持现有的 `heightIn(max = 72.dp)` 与 1:1 正方形缩略图（对齐原版 `HScrollPictureListPart` 垂直隔离机制）；
- 无关联条目的纯文本动态（`shouldShowLeftCover == false`）继续保持通栏紧凑包裹自适应，兼顾信息流排版疏密美感。

---

## 架构收益与影响
1. **彻底终结信息流 CLS**：卡片在首次合成、网络下载、图片解码全生命周期高度恒定，瀑布流列表滚动与加载零抖动、零列换位；
2. **构图完整性保全**：相册传图、玩家随拍与异形长图在完全不裁切的前提下优雅呈现；
3. **消除骨架屏跳变顿挫**：骨架屏到正式内容卡片切换实现纯视觉淡入，物理框架零位移。
