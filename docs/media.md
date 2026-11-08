# 多媒体管道与图片呈现 (Media)

> 任何涉及**图片显示、CDN 路径、缩略图变体、全屏看图**的改动，先读本文件。
> 架构决策与取舍理由见 [ADR-0007](adr/0007-media-pipeline-async-image.md)；
> 逆向踩坑原始事实见 [API_QUIRKS.md](API_QUIRKS.md) 第 7 节。

---

## 三个入口，别再自己拼 URL

| 场景 | 用什么 |
| --- | --- |
| Compose 里显示一张图 | `PoiAsyncImage(...)`（`ui/common/PoiAsyncImage.kt`） |
| 只需要一个 URL 字符串（查看器、分享、下载） | `MediaPipeline.Default.resolveUrl(path, category, purpose)` |
| 全屏可缩放大图 | `PoiZoomableImage(...)`（`ui/common/PoiZoomableImage.kt`） |

`CdnUrlUtils` 的全部方法已 `@Deprecated`，仅作兼容桥接保留（单行委托管道）。
新代码一律不调用它；把旧调用迁移成上述三者之一。

---

## 语义化调用形状

```kotlin
PoiAsyncImage(
    path = item.cover,                       // 原始相对路径或绝对 URL，可空
    category = MediaCategory.ALBUM,          // 这是「什么东西的图」
    purpose = ImagePurpose.ADAPTIVE_GRID,    // 这是「怎么展示」
    contentDescription = item.displayName,
    modifier = Modifier.fillMaxWidth(),
    adaptiveRatio = true,                    // 高度按真实比例撑开（瀑布流）
    fallbackRatio = 1.0f                     // 首帧占位比例，防跳变
)
```

- `path` 为空、空白或 `none.jpg` 时**不发网络请求**，直接显示官方吉祥物缺省图。
- 加载失败自动显示柔底色 + 破损图标；调用方**不需要**再写 `loadFailed` 状态。

### `adaptiveRatio = true` 的约束前提

组件内部用 `Modifier.aspectRatio` 撑开尺寸，**必须至少留一轴自由伸缩**：

| 调用方约束 | 结果 |
| --- | --- |
| `Modifier.fillMaxWidth()`（瀑布流/网格） | ✅ 宽度定、高度按比例算 |
| `Modifier.width(96.dp)`（海报） | ✅ 宽度定、高度按比例算 |
| `Modifier.height(150.dp)`（定高反推宽度） | ✅ 高度定、宽度按比例算 |
| `Modifier.fillMaxSize()` + 父容器宽高都固定 | ❌ 比例失效、图片铺满容器 |

最后一行是踩过的坑：`fillMaxSize()` 在尺寸确定的父容器里会锁死两轴，`aspectRatio` 静默失效。

---

## `MediaCategory` × `ImagePurpose` 真实产出

| 分类 | THUMBNAIL | ADAPTIVE_GRID | CARD_STANDARD | FULLSCREEN_RAW |
| --- | --- | --- | --- | --- |
| `HOBBY` | `cover/s/` | `cover/sr/` | `cover/n/` | `cover/raw/` |
| `ALBUM` | `cover/sr/` | `cover/sr/` | `cover/sr/` | `cover/raw/` |
| `AVATAR` | `head/s/` | `head/s/` | `head/n/` | `head/raw/` |
| `POST` | `posts/n/` | `posts/n/` | `posts/n/` | `posts/n/` |
| `BANNER` | `bnr/n/` | `bnr/n/` | `bnr/n/` | `bnr/n/` |
| `RESALE` | `resale/n/` | `resale/n/` | `resale/n/` | `resale/n/` |
| `ZONE` | `zone/` 直连或 `cover/s/` | 同左 | `zone/` 直连或 `cover/n/` | 同 CARD_STANDARD |
| `EX_HOBBY` | `res.e39x.com/pic/s/` | `…/pic/n/` | `…/pic/n/` | `…/pic/n/` |

用户相片（`pic/` 家族）没有对应分类（枚举已冻结），用接口默认实现：

```kotlin
MediaPipeline.Default.resolvePictureUrl(path, ImagePurpose.CARD_STANDARD)  // pic/n/
MediaPipeline.Default.resolvePictureUrl(path, ImagePurpose.FULLSCREEN_RAW) // pic/raw/
```

---

## 三条 404 红线（服务端没部署这些目录）

1. **相册没有 `cover/n/`** → `ALBUM` 的三档缩略图全部落到 `cover/sr/`。
   误用 `cover/n/` 会让相册封面白屏。
2. **讨论帖没有 `posts/raw/`** → `POST` 恒为 `posts/n/`，**绝对 URL 也会被改写**，
   外部传入 `…/posts/raw/x.jpg` 不会漏网。
3. **用户横幅没有 `bnr/raw/`** → `BANNER` 恒为 `bnr/n/`，同样改写绝对 URL。

前两条由 `MediaPipelineTest` 的 `…neverEmitsCoverNormalDirectory` /
`…downgradeRawSegmentOfAbsoluteUrl` 用例守住。

---

## 全屏看图

```kotlin
PoiZoomableImage(
    imageUrl = url,
    isActive = pagerState.currentPage == page,  // 切页时复位缩放
    maxDecodeHeightPx = windowHeightPx,         // 防巨图 OOM
    onZoomChanged = { zoomed -> pagerScrollEnabled = !zoomed },
    onSingleTap = { /* 放大态由基元自行复位，不会冒泡到这里 */ },
    onLongPress = { showActionSheet = true },
    onDetectLongImage = { viewModel.markLongImage() }
)
```

- 捏合 `1.0x..4.0x`（超上界阻尼 + 松手回弹）、双击在 `1.0x ↔ 2.5x` 切换、放大态单指平移。
- **桌面端**：滚轮缩放（以指针为锚点）+ 按住左键拖拽平移，经 `expect/actual` 提供。
- **长图（高宽比 > 3）**：自动切 `ContentScale.FillWidth`、默认从顶部开始阅读、
  未放大也允许纵向拖拽、双击在**顶部 ↔ 底部**之间跳转（不是放大）。

---

## 加一张新图时的检查单

1. 问自己「这是什么东西的图」→ 选 `MediaCategory`。
2. 问自己「在界面上怎么展示」→ 选 `ImagePurpose`。
3. 从上面的矩阵确认产出的目录是服务端真的部署了的。
4. 瀑布流/网格用 `adaptiveRatio = true` + `fillMaxWidth()`；定高反推宽度用 `height(...)`，**不要**叠 `fillMaxSize()`。
5. 只有需要 URL 字符串的下游（查看器、分享、下载）才直接调 `MediaPipeline.Default.resolveUrl`。
6. 写一个纯 JVM 用例覆盖新分类/新用途的组合，不要只靠人工看图。
