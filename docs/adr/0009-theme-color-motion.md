# ADR-0009: 主题切换的颜色弹簧过渡（对齐 KernelSU animateAsState）

## 状态
已采纳 (Accepted) - 2026-11-09

## 上下文与问题陈述

ADR-0008 让「色彩风格 / 色彩标准」在动态主题下真正生效之后，暴露出一个观感问题：
用户在外观设置里改动任意一项，`ColorScheme` 会**整份瞬间换新**，48 个颜色角色同时跳变，
视觉上像屏幕闪了一下。设置页本身还带色卡预览，跳变更明显。

[KernelSU manager](https://github.com/tiann/KernelSU/blob/main/manager/app/src/main/java/me/weishu/kernelsu/ui/theme/ThemeExt.kt)
用 `ColorScheme.animateAsState()` 解决了同一问题：给每个颜色角色套一条
`animateColorAsState(spring())`，新旧配色之间弹簧过渡。

## 候选方案评估

### 方案 A：逐角色 `animateColorAsState`（KernelSU 做法，选定）
- 优点：实现直白、与上游一致、弹簧参数天然带回弹手感；每个角色独立插值，不依赖中间态对象；
  只影响呈现层，取色决策与回归测试读到的目标值完全不变。
- 缺点：过渡期间每帧产出新的 `ColorScheme`，读取 `MaterialTheme.colorScheme` 的组件逐帧重组；
  同时挂着 48 个动画节点。

### 方案 B：`animateColorAsState` 只包少量主色
- 优点：动画节点少，重组面小。
- 缺点：主色在动、`surface`/`onSurface` 不动，会出现「字色与底色错拍」的割裂感，比直接跳变更难看。

### 方案 C：外层 `Crossfade` 包整棵界面
- 优点：零逐角色节点。
- 缺点：`Crossfade` 会把整棵 UI 树复制两份并做透明度混合，等于把整屏重绘两遍；
  且滚动位置、图片解码状态都要额外处理，代价远高于收益。

## 决策结论

采用方案 A，新增 [`ThemeMotion.kt`](../../shared/src/commonMain/kotlin/com/nobody/repoi/ui/theme/ThemeMotion.kt)，
在 `PoiShelfTheme` 内于 `MaterialExpressiveTheme` 之前套一层：

```kotlin
val animatedScheme = colorScheme.animateAsState(darkTheme = darkTheme)
MaterialExpressiveTheme(colorScheme = animatedScheme, ...)
```

三条刻意加上的边界（KernelSU 版本没有，属于本地适配）：

1. **深浅模式切换不过渡**：以 `darkTheme` 作为 `key(...)`，模式一变就重建全部动画节点，
   新节点以目标色初始化，整屏明暗反转立即生效；否则逐角色补间会经过一串灰，既脏又慢。
2. **首帧不过渡**：`animateColorAsState` 首次组合即以目标值初始化，冷启动不会从默认色淡入。
   > **修订（ADR-0012）**：本点仅在「持久化配置已落定」之后成立。冷启动读盘到达的那一次跃迁
   > 曾以第二个组合传入新配色而被误判为用户切换，触发 48 角色弹簧补间；现由首态落定门禁修正。
   顺带解决了另一个坑——若在首帧提前返回，动画节点根本没被创建过，
   启动后第一次改主题反而没有过渡。
3. **`ColorScheme` 的 48 参主构造是 private**（material3 1.12.0-alpha03 实测），
   公开的整份替换入口只有 `copy(...)`，因此走 `copy` 而不是像 KernelSU 那样直接 `ColorScheme(...)`。

## 影响

- **积极影响**：切换风格 / 标准 / 种子色 / AMOLED 时有连续的颜色过渡，不再闪屏；
  与 KernelSU 行为一致，后续升级 material-kolor 或排查观感问题有共同参照。
- **代价与边界**：
  - 过渡期间（约数百毫秒）UI 逐帧重组，低端机可能掉帧。若收到此类反馈，
    应先加「减弱动画」开关（`spring` → `snap`）而不是砍掉颜色角色；
  - 动画只作用于最终配色对象，`resolvePoiColorScheme` 与
    `ThemeDynamicRegressionTest` 的断言不受影响；
  - `ThemeMotionTest` 用 `mainClock` 手动推进时钟，锁死「首帧落定、过渡中、最终收敛、
    深浅模式不补间」四条正确性约束。
