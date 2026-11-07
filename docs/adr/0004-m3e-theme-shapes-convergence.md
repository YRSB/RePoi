# ADR-0004: Material 3 Expressive 设计系统收敛、终结 ExpressiveTokens 反模式与全面回归官方原生组件

## 状态
已采纳并全量执行 (Accepted & Completed) - 2026-11-07

## 上下文与问题陈述
在前期特性迭代中，为规避局部硬编码 `dp` 圆角并贴近 Material 3 Expressive 设计规范，工程引入了单例对象 `ExpressiveTokens` 以及包装控件 `ExpressiveElevatedCard` / `ExpressiveCard`。然而经官方知识库与字节码审计发现，这套自制实现脱离了 Jetpack Compose 的根主题依赖分发机制，带来了严重的架构坏味道与协作摩擦：

1. **脱离 Compose 主题树（Bypassing CompositionLocal）**：
   在 `Theme.kt` 中调用 `MaterialExpressiveTheme` 时，未显式传参或自定义时，官方 M3E 内部本身就自带一套完整的 `ShapeDefaults`（4dp、8dp、12dp、16dp、20dp、28dp、32dp），且官方组件（`ElevatedCard`、`Card`、`Button`、`AlertDialog` 等）天生默认自动消费这些圆角与容器颜色。
2. **中间人二次抽象（Middle Man Code Smell）**：
   `ExpressiveElevatedCard` 内部仅是对官方 `ElevatedCard` 的简单委托，传参的 `surfaceContainerLow` 容器色与 `1.dp` 标高实际上本来就是官方 `CardDefaults.elevatedCardColors()` 和 `elevatedCardElevation()` 的原生默认值。此类包装控件徒增间接层与调用样板代码。
3. **海量样板代码与代码评审误报（Review Friction）**：
   全工程业务界面在数十个页面、上百处组件调用点重复显式传入 `shape = ExpressiveTokens.MediumCardShape`。同时 `CODING_STANDARDS.md` 机械化要求“界面所有圆角统一定义自 `ExpressiveTokens.*Shape`”，导致开发者正常使用 Compose 标准组件或原生令牌时屡屡被审查机器人告警阻断。

## 决策结论
坚决不保留冗余兼容层，实施全量干净迁移：
1. **注入统一形状**：在 `shared/.../ui/theme/Shapes.kt` 中声明对齐 M3E 梯度的 `ExpressiveShapes`，并在 `PoiShelfTheme` 中正式接入 `MaterialExpressiveTheme(shapes = ExpressiveShapes)`。
2. **扩展非对称与业务形状**：在 `Shapes.kt` 中为 `Shapes` 添加 `largeCard` (24dp)、`bottomSheet` (顶角28dp)、`sideSheet` (侧角28dp)、`topBarScrolled` (底角20dp) 等扩展属性。
3. **全面回归官方原生组件**：
   - 全工程所有 `ExpressiveElevatedCard` 一律替换为官方原生的 `androidx.compose.material3.ElevatedCard`；
   - 全工程所有 `ExpressiveCard` 一律替换为官方原生的 `androidx.compose.material3.Card`；
   - 官方卡片零参数调用，直接享受官方原生的容器色阶与形状。
4. **彻底删除 `ExpressiveTokens.kt`**：物理删除该文件，全量迁移所有调用点至 `MaterialTheme.shapes.*` 或 `CircleShape`。
5. **修正评审规范**：同步修订 `CODING_STANDARDS.md`，鼓励标准组件使用原生默认形状，自定义裁剪使用 `MaterialTheme.shapes.*`，彻底解除强制绑定 `ExpressiveTokens` 的教条约束。

## 影响
- **积极影响**：
  - 根除 Code Review 误报，审阅流程恢复顺畅；
  - 彻底消除了 600+ 处无意义的中间人包装与参数样板代码；
  - 架构完全回归 Google 官方 *Custom design systems in Compose* 与 *Material 3 Expressive* 标准体系，零自定义包袱。
- **迁移结果**：
  - 全工程 80+ 业务界面文件已全量原子化迁移完毕，零残留。
