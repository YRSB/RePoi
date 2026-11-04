# ADR-0002: 设置页主题 4 维与 material-kolor 6.0.0 选型

## 状态
已采纳 (Accepted) - 2026-11-04

## 上下文与问题陈述
RePoi 设置页需新增自身主题能力（参考 PodAura M3 取色系统），现有 `ThemeManager` 仅深色模式与动态取色双维、手写固定配色。需决定主题维度、种子方案、配色生成依赖与光谱版本。

## 候选方案评估
### 方案 A: 主题 4 维 + material-kolor 6.0.0（采纳）
- 优点：种子色板 + 深色模式 + 动态取色 + AMOLED 四维；6.0.0 与 RePoi 三件套（kotlin 2.4.21 / CMP 1.12.2 / m3 1.12.0-alpha03）逐项一致；Expressive 常开（style Expressive + SPEC_2025）与现有 Expressive 约束一致；MIT 协议。
- 缺点：6.0 默认 SPEC_2025，相对 5.x/2021 变色；旧单体坐标已删，移植 PodAura 代码需换坐标与包名。

### 方案 B: 5 维全抄 PodAura（含 Expressive 开关）
- 优点：与参考实现逐项对齐。
- 缺点：Expressive 开关与「全应用 Expressive」约束冲突，需改规范；多一维状态与测试组合。

### 方案 C: 手写固定配色、无新依赖
- 优点：零新依赖。
- 缺点：种子扩展与深色/AMOLED 组合爆炸，维护成本高。

## 决策结论
采用方案 A：Dynamic + 全抄 PodAura 7 种子（含真寻三色）；默认 Dynamic/Pink 双默认（动态色可用则 Dynamic，否则 Pink）；动态色三段判定移植 PodAura（S 以下 false、T 以上 true、S 区间厂商白名单）；存储沿用 `ThemeManager` 加 `app_theme_seed` 键，不引入 BasePreference 重构。

## 影响
- 积极影响：主题能力一次到位；存储改动收敛在 `ThemeManager` 一处。
- 消极影响 / 迁移成本：全光谱 SPEC_2025 变色需接受；PodAura 5.x 调用点移植时需做 6.0 迁移（坐标、包名、seed 首参）。
