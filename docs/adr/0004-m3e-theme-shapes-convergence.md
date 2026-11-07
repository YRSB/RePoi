# ADR-0004: Material 3 Expressive 设计系统收敛与终结 ExpressiveTokens 反模式

## 状态
已采纳 (Accepted) - 2026-11-07

## 上下文与问题陈述
在前期特性迭代中，为规避局部硬编码 `dp` 圆角并贴近 Material 3 Expressive 设计规范，工程引入了单例对象 `ExpressiveTokens`。然而该实现方式脱离了 Jetpack Compose 的根主题依赖分发机制，带来了严重的架构坏味道与协作摩擦：

1. **脱离 Compose 主题树（Bypassing CompositionLocal）**：
   在 `Theme.kt` 中调用 `MaterialExpressiveTheme` 时，未向其传递 `shapes` 参数。根主题缺少形状真理来源，使得官方 Material 3 组件（如 `Card`、`ElevatedCard`、`Button`、`AlertDialog` 等）在默认缺省状态下完全感知不到项目定制的形状令牌，依然回退至 M3 默认值。
2. **海量样板代码与数据泥团（Data Clumps）**：
   全工程业务界面为了强行“对齐规范”，在数十个页面、上百处组件调用点重复显式传入 `shape = ExpressiveTokens.MediumCardShape` 或 `shape = ExpressiveTokens.CardShape`。
3. **代码评审误报（Review Friction）**：
   `CODING_STANDARDS.md` 机械化要求“界面所有圆角统一定义自 `ExpressiveTokens.*Shape`”，导致开发者正常使用 Compose 标准组件或原生令牌时屡屡被审查机器人告警阻断。

## 候选方案评估

### 方案 A: 维持 `ExpressiveTokens` 单例并补齐更多静态常量
- **优点**：无需对现有调用点做兼容或改动。
- **缺点**：继续违背 Compose 官方设计系统架构指南；所有组件依然无法享受默认形状注入；持续带来参数样板代码；无法配合动态主题或局部作用域切换。

### 方案 B: 依照官方 Compose 规范全面收敛至 `MaterialTheme.shapes`（选定方案）
- **优点**：
  - **真理来源收敛**：通过 `Theme.kt` 的 `MaterialExpressiveTheme(shapes = ExpressiveShapes)` 统一注入，与官方 `ColorScheme`、`Typography` 享有相同的主题分发地位。
  - **组件零传参**：官方组件（`CardDefaults.shape`、`ButtonDefaults.shape` 等）天生绑定 `MaterialTheme.shapes`，业务层写卡片/按钮不再需要显式传参。
  - **符合标准扩展范式**：非对称圆角（如 `bottomSheet`、`sideSheet`、`topBarScrolled`）以 `Shapes` 扩展属性形式挂载，统一收敛在 `MaterialTheme.shapes.*` 入口下。
  - **平滑渐进迁移**：废弃 `ExpressiveTokens`（标记 `@Deprecated` 并配置 `ReplaceWith`），既不破坏旧代码编译，又为后续清理指明路径。
- **缺点**：需要维护过渡期的兼容层。

## 决策结论
采纳方案 B：
1. **注入统一形状**：新增 `shared/.../ui/theme/Shapes.kt`，声明对齐 M3E 梯度的 `ExpressiveShapes`，并在 `PoiShelfTheme` 中正式接入 `MaterialExpressiveTheme(shapes = ExpressiveShapes)`。
2. **扩展非对称与业务形状**：在 `Shapes.kt` 中为 `Shapes` 添加 `largeCard` (24dp)、`bottomSheet` (顶角28dp)、`sideSheet` (侧角28dp)、`topBarScrolled` (底角20dp) 等扩展属性。
3. **废弃 `ExpressiveTokens`**：将 `ExpressiveTokens` 全面标记为 `@Deprecated`，组件内部（如 `ExpressiveElevatedCard`）默认值改为消费 `MaterialTheme.shapes`。
4. **修正评审规范**：同步修订 `CODING_STANDARDS.md`，鼓励标准组件使用原生默认形状，自定义裁剪使用 `MaterialTheme.shapes.*`，彻底解除强制绑定 `ExpressiveTokens` 的教条约束。

## 影响
- **积极影响**：
  - 根除 Code Review 误报，审阅流程恢复顺畅；
  - 新增 UI 代码无需再导入并手动传递 `ExpressiveTokens` 形状；
  - 架构完全对齐 Google 官方 *Custom design systems in Compose* 与 *Material 3 Expressive* 指南。
- **消极影响 / 迁移成本**：
  - 历史代码中的旧引用保持 `@Deprecated` 告警，将在日常业务重构中逐步用 `MaterialTheme.shapes` 替换。
