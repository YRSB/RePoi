# ADR-0021: 动态流卡片非相册海报按实体分流（手办 9:13 与其余 1:1）

## 状态
已采纳 (Accepted) - 2026-11-14

## 上下文与问题陈述
[ADR-0016](0016-action-card-categorized-poster-layout.md) 把非相册动态的海报槽位统一锁死为 9:13 竖版定框，成功保住了手办条目的零跳动与零裁切。但实际浏览动态流发现：**厂商、角色、作品、系列等实体的官方封面大量是 1:1 正方形**。方图放进 96dp 宽、138.7dp 高的 9:13 竖框后，`ContentScale.Fit` 会在图片上下留出 `surfaceContainerHighest` 底色，浅色主题下就是明显的白边；这些实体动态在关注流、专区流与个人动态里占比并不低。

相册传图已经由 ADR-0016 单独分流为高度自适应，本次不再改动。

---

## 候选方案评估

### 方案 A：全量改回 1:1
- 优点：实体方图零白边，且与 Hpoi 原版海报的 1:1 首帧占位一致；
- 缺点：手办条目海报本是 9:13 竖图，1:1 会重新引入上下白边，并放弃 ADR-0015/0016 为手办争取到的竖版构图；
- 结论：否。

### 方案 B：维持现状（非相册全量 9:13）
- 优点：手办零跳动、实现最简单；
- 缺点：实体方图的白边问题持续存在，正是本 ADR 要解决的痛点；
- 结论：否。

### 方案 C：显式实体才改 1:1，未知类型维持 9:13
- 优点：对历史数据最保守；
- 缺点：`itemType` 缺失或大小写变体（`Figure` / `charactar`）时仍会掉回竖框；未知条目里也包含 `picture` 等横图动态，白边照旧；
- 结论：否。

### 方案 D（采纳）：手办 9:13，其余非相册一律 1:1
- 优点：只有**能明确识别为手办/周边**的条目才用竖框，方图与未知类型都拿正方形槽位；两类槽位都是刚性定框，解码前后均不重测；
- 缺点：未标注 `itemType` 的手办动态会被当成 1:1（原版同样以 1:1 占位，可接受）；
- 结论：采纳。

---

## 决策结论

### 1. 新增纯分类器 `ActionPosterShape`
在 [ActionPosterPolicy.kt](../../shared/src/commonMain/kotlin/com/nobody/repoi/ui/home/components/ActionPosterPolicy.kt) 中定义：

```kotlin
internal enum class ActionPosterShape { GOLDEN, SQUARE }

internal fun ActionItem.resolvePosterShape(): ActionPosterShape {
    val relate = relateItem ?: return ActionPosterShape.GOLDEN
    val itemType = relate.itemType?.trim()?.lowercase()
    return if (itemType in setOf("hobby", "figure", "item") ||
        relate.categoryId in 10000..19999
    ) ActionPosterShape.GOLDEN else ActionPosterShape.SQUARE
}
```

- **手办/周边**（`hobby` / `figure` / `item`，或分类 10000..19999）→ `GOLDEN`；
- **其余非相册条目**（厂商/角色/作品/系列/人物、`picture`/`post`、未知类型）→ `SQUARE`；
- 相册仍在 [ActionCard.kt](../../shared/src/commonMain/kotlin/com/nobody/repoi/ui/home/components/ActionCard.kt) 中先拦截，`resolvePosterShape` 不参与相册决策。

### 2. 三档槽位几何

| 类别 | 宽度 | 比例 | 高度 | 对齐 / 缩放 |
|---|---|---|---|---|
| 相册传图（含 `actionType` 3/103/104） | 96dp | 不锁比例，按照片真实比例 | 首帧 4:3 = 72dp，之后无上限 | `TopCenter` / `Fit` |
| 手办/周边 | 96dp | 9:13 | 固定 138.7dp | `Center` / `Fit` |
| 其余实体与未知 | 96dp | 1:1 | 固定 96dp | `Center` / `Fit` |

实现上 `ActionCard` 先算出非相册的 `posterAspectRatio`（1:1 或 9:13），相册为 `null` 时交给 `PoiAsyncImage` 的 `adaptiveRatio` 路径；`ContentScale.Fit` 与 `enableFullscreenPreview = false` 保持不变。

### 3. 与旧策略的兼容
- 相册相关行为完全保持 ADR-0016 现状（含相册无封面的 9:13 文本卡）；
- 手办卡片尺寸不变，仍是 96dp × 138.7dp；
- 实体卡片高度从 138.7dp 收缩到 96dp，右侧内容（头部 + 附图矩阵 + 底部统计，约 134dp）通常会成为整卡高度的锚点，空出的区域是卡片底色而非图片容器内的白边。

---

## 影响
1. **实体方图零留边**：厂商/角色/作品/系列/人物的 1:1 封面在 96dp 正方形槽位内完整贴合；
2. **进度条式紧凑**：实体卡片变矮，动态流中相邻卡片的高度节奏更紧凑；
3. **零新增跳动**：三档比例都在组合期确定（相册首帧仍有 4:3 到真实比例的既有一次性微调），非相册不存在解码后重测；
4. **测试**：`ActionPosterPolicyTest` 锁分类映射，`ActionCardPosterLayoutTest` 锁 9:13 / 1:1 / 4:3 三档真实尺寸。
