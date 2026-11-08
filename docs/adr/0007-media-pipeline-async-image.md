# ADR-0007: 多媒体管道 (MediaPipeline) 与自适应图片呈现基元架构深化

## 状态
已采纳 (Accepted) - 2026-11-08

## 上下文与问题陈述
在 RePoi 多媒体处理与图像呈现链路中，存在多头割裂、浅度工具透传与状态样板代码冗余等架构摩擦：
1. **CDN 变体拼接与 404 暗坑分散**：`CdnUrlUtils` 暴露了 25 个静态拼接函数，散落于 162 处调用点。调用端必须自行熟记各类逆向避坑规则（例如相册封面 CDN 无 `/n/` 必须走 `/sr/` 否则 404；讨论帖与横幅背景无 `/raw/` 原图；官方缺省吉祥物 `none.jpg` 判定；专区路径前缀与多重回退等），极易遗漏引发线上异常；
2. **列表与瀑布流宽高比防抖样板泛滥**：在 `AlbumFeedCard`、`HobbyCards`、`ZoneEntityCard` 等 10+ 处页面组件中，调用方各自手写 `ratioKey` 计算、`mutableFloatStateOf` 状态、`rememberListImageRequest` 缓存键构建、`onSuccess` 监听以及 `ImageAspectRatioCache` 的读写回写代码，逻辑冗余超数百行；
3. **看图手势组件双轨重复且桌面端缺失滚轮**：`PictureGalleryScreen`（沉浸式路由页）与 `ImageViewerDialog`（全局浮层弹窗）各自复制了捏合缩放（1~4x）、双击放缩与平移拖拽手势；且双端仅针对触控事件实现，Windows Desktop 桌面端缺少鼠标滚轮缩放与鼠标左键平移，交互体验割裂；
4. **动态流媒体解析浅耦合**：`FeedMediaResolver` 直接硬编码调用 `CdnUrlUtils` 的静态方法，缺乏统一的管道依赖抽象。

## 候选方案评估

### 方案 A：仅在既有 `CdnUrlUtils` 补齐方法并在各卡片局部提取 Composable 宏
- 做法：保留静态类 `CdnUrlUtils`，新增部分静态 helper 方法，各卡片继续手动持有状态。
- 优点：对现有代码几乎无修改成本。
- 缺点：治标不治本；未消除“静态浅工具”与“162 处知识泄露”本质，宽高比防抖样板依旧四处复制，桌面端滚轮手势依然割裂，纯 JVM 单元测试难以针对注入的 Pipeline 做灵活 Mock。

### 方案 B：纯 Kotlin 深服务 `MediaPipeline` 与自洽呈现基元 `PoiAsyncImage` / `PoiZoomableImage`（采纳）
- 做法：
  1. **纯 Kotlin 计算深服务**：定义 `com.nobody.repoi.data.media.MediaPipeline`，完全解耦 Compose 运行时。集中封装 CDN 变体映射、404 暗坑防御、官方缺省图判定与纵横比缓存读写，在 `imageModule` 中通过 Koin 注入，同时提供 `MediaPipeline.Default` 伴生实例以支持非 Compose 环境与桥接；
  2. **语义化分类与用途契约**：引入 `MediaCategory` 与 `ImagePurpose` 强类型枚举，由 `MediaPipeline.resolveUrl(path, category, purpose)` 一站式规避所有 CDN 陷阱；
  3. **自洽呈现基元 `PoiAsyncImage`**：在 `ui/common/` 提供统一组件，同时支持业务路径（Path+Category+Purpose）与通用 URL/Model；内部全自动闭环 `ImageAspectRatioCache` 首帧防抖与异步回写，统一截断在 `[0.4f, 2.5f]`，并内置手办 17:24 与相册 1.0f 语义缺省；
  4. **跨平台手势基元 `PoiZoomableImage`**：抽离手势基元，移动端支持双指捏合与双击放大，桌面端原生适配 `PointerEventType.Scroll` 鼠标滚轮缩放与左键拖拽平移，供 `ImageViewerDialog` 与 `PictureGalleryScreen` 统一复用；
  5. **渐进式平滑迁移**：`CdnUrlUtils` 162 处旧方法就地保留并标为 `@Deprecated`，单行转发给 `MediaPipeline.Default`，首批核心卡片优先重构，确保 CI 持续全绿。
- 优点：
  - 高内聚深模块：将 CDN 25 个静态规则与避坑逻辑完整闭包在 Pipeline 内部，100% 纯 JVM 单元测试覆盖；
  - 极高杠杆率：彻底消灭 10+ 处卡片中重复的 15 行宽高比与 Coil 请求样板；
  - 跨平台体验一致：桌面端获得原生的鼠标滚轮缩放能力；
  - 平滑无中断演进：无编译打断，零回归风险。
- 缺点：
  - 新增核心基元组件需要编写完备的单测与设计文档。

## 决策结论
采用方案 B。

1. **核心文件拓扑**：
   - 管道接口与契约：`shared/src/commonMain/kotlin/com/nobody/repoi/data/media/MediaPipeline.kt`
   - 默认实现：`shared/src/commonMain/kotlin/com/nobody/repoi/data/media/DefaultMediaPipeline.kt`
   - 依赖注入注册：`shared/src/commonMain/kotlin/com/nobody/repoi/di/ImageModule.kt`
   - 呈现基元组件：`shared/src/commonMain/kotlin/com/nobody/repoi/ui/common/PoiAsyncImage.kt`
   - 缩放手势基元：`shared/src/commonMain/kotlin/com/nobody/repoi/ui/common/PoiZoomableImage.kt`
   - 管道单元测试：`shared/src/commonTest/kotlin/com/nobody/repoi/data/media/MediaPipelineTest.kt`

2. **核心契约定义**：
   ```kotlin
   enum class MediaCategory {
       HOBBY, ALBUM, AVATAR, POST, BANNER, RESALE, ZONE, EX_HOBBY
   }

   enum class ImagePurpose {
       THUMBNAIL,      // 方形小缩略图 (cover/s/, head/s/, pic/s/, ex/s/)
       ADAPTIVE_GRID,  // 瀑布流缩略图 (cover/sr/ 保留纵横比)
       CARD_STANDARD,  // 标准卡片与详情页展示 (cover/n/, posts/n/, bnr/n/, resale/n/)
       FULLSCREEN_RAW  // 全屏高清原图 (cover/raw/, pic/raw/，避坑拦截 posts/bnr 原图 404)
   }

   interface MediaPipeline {
       companion object {
           val Default: MediaPipeline get() = DefaultMediaPipeline
       }
       fun resolveUrl(path: String?, category: MediaCategory, purpose: ImagePurpose): String
       fun resolveRawUrl(path: String?, category: MediaCategory): String
       fun isPlaceholder(path: String?): Boolean
       fun getDefaultCoverUrl(purpose: ImagePurpose): String
       fun getAspectRatio(key: String): Float?
       fun recordAspectRatio(key: String, ratio: Float)
   }
   ```

3. **防抖与呈现基元契约**：
   ```kotlin
   @Composable
   fun PoiAsyncImage(
       path: String?,
       category: MediaCategory,
       purpose: ImagePurpose,
       contentDescription: String?,
       modifier: Modifier = Modifier,
       contentScale: ContentScale = ContentScale.Crop,
       adaptiveRatio: Boolean = false,
       fallbackRatio: Float? = null,
       enableFullscreenPreview: Boolean = false,
       onClick: (() -> Unit)? = null
   )
   ```

4. **迁移与兼容**：
   - `CdnUrlUtils` 保留所有函数，标注 `@Deprecated("请使用 MediaPipeline.Default 或 PoiAsyncImage", ReplaceWith(...))`，函数体单行委托给 `MediaPipeline.Default`；
   - `FeedMediaResolver` 保留动态流卡片展示信息拼装模型，底层调用切换为委托 `MediaPipeline`。

## 影响
- **积极影响**：
  - 彻底终结 CDN 静态拼接知识的散落泄露，相册/讨论帖/横幅等 404 避坑规则收敛在 `MediaPipeline` 中一处生效；
  - 消除卡片组件中上百行重复的宽高比缓存读写与 Coil 请求样板，提升 Compose 重组性能并根治列表跳变抖动；
  - 跨平台大图浏览体验对齐，Desktop 端支持鼠标滚轮连续缩放；
  - 100% 纯 JVM 单元测试覆盖各类异常边界 URL 解析与纵横比截断。
- **消极影响 / 迁移成本**：
  - 需维护过渡期 `CdnUrlUtils` 的兼容桥接层，后续需分批逐步迁移 162 处既有调用点。
