# 架构决策记录目录 (Architecture Decision Records - ADR)

本目录用于持久化保存 RePoi 的重大技术选型、重构方案对比及全局架构决策。

> **智能体守则**：
> 1. 凡在方案调研或架构重构任务中产生的决策提案，统一在此目录下创建或更新版本化的 Markdown 文件（如 `0001-xxx.md`）。
> 2. 严禁将关键架构文档仅留在临时目录或对话上下文中，必须保证工程内自闭环与版本可追溯。

---

## 决策记录索引 (ADR Index)

| 编号 | 决策标题 | 核心关注领域 / 触发分支 | 状态与最后更新日期 |
| :--- | :--- | :--- | :--- |
| [ADR-0001](0001-kmp-migration-architecture.md) | PoiShelf 迁移至 KMP 跨平台架构与技术栈对齐 | **跨平台/架构迁移**：单 shared 模块、Koin、Ktor、Coil 3、Multiplatform DataStore | 已采纳 (Accepted) - 2026-10-30 |
| [ADR-0002](0002-settings-theme-material-kolor.md) | 设置页主题 7 维取色系统与 material-kolor 演进选型 | **主题/取色系统**：7 维主题、色板预热无锁缓存、三色几何色卡、消除非动态深灰底色 | 已采纳并演进更新 (Accepted & Amended) - 2026-11-06 |
| [ADR-0003](0003-settings-structure-migration.md) | 设置页自适应双窗格架构、原生大顶栏与迁移清单 | **设置页/自适应双窗格/拓扑**：宽窄屏断点、双独立顶栏、原生 LargeTopAppBar 常规字重、三大业务分组 | 已采纳并演进更新 (Accepted & Amended) - 2026-11-06 |

---

## 模板参考

```markdown
# ADR-000X: [决策标题]

## 状态
[提议 (Proposed) / 已采纳 (Accepted) / 已废止 (Deprecated) / 已替代 (Superseded)] - YYYY-MM-DD

## 上下文与问题陈述
[面临的技术痛点、性能瓶颈或业务诉求]

## 候选方案评估
### 方案 A: [...]
- 优点：...
- 缺点：...

### 方案 B: [...]
- 优点：...
- 缺点：...

## 决策结论
[选定方案及其核心技术考量]

## 影响
- 积极影响：...
- 消极影响 / 迁移成本：...
```
