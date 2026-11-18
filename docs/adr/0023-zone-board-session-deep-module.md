# ADR-0023: 专区子板块会话控制器 (ZoneBoardSession) 深模块化与散装适配器清理

## 状态
已采纳 (Accepted) - 2026-11-18

## 上下文与问题陈述

在 ADR-0022 中，RePoi 完成了首页三级信息架构的演进，并抽离了专区子板块共享渲染面板 `ZoneBoardPane`。然而在后续实现中，各板块的分页加载、首屏懒加载、筛选重置不变性与滚动记忆等核心逻辑依然分散平铺在 `HomeFeedViewModel` 与 `ZoneDetailViewModel` 中：

1. **宿主 ViewModel 极度臃肿与散装派发**：`HomeFeedViewModel` 需管理 10 类子板块的列表数据、页码游标、加载中/错误态等几十个平铺字段（如 `albumItems`, `posts`, `resale`, `hobbyBoard`, `entityBoards` 等），职责严重过载；
2. **万能适配器维护成本高**：为了向共享面板传递交互动作，引入了拥有 17 个参数的回调适配器 `ZoneBoardActions`，宿主与 UI 之间形成了冗长的样板转发代码（如方法引用或闭包转发）；
3. **过渡投影层腐化**：为了兼容旧单测和散装字段，引入了 `HomeBoardProjection.kt`（285 行），在 ViewModel 与快照之间充当中间人层，制造了维护心智负担；
4. **行为一致性缺乏保障**：首页与专区详情页虽然共享了 UI 渲染，但板块调度、取消重试、空关注短路等生命周期规则未能在单点闭环，容易因实现细节不一致而发生行为漂移。

依据深模块设计哲学（Deep Module：简单的接口隐藏巨大的内部复杂性），亟需将专区子板块的状态机、生命周期与分页调度彻底封装为独立控制器。

## 决策结论

1. **确立深模块 `ZoneBoardSession` 为子板块唯一控制契约**：
   - 外部仅暴露极简接口：
     ```kotlin
     interface ZoneBoardSession {
         val activeSnapshot: StateFlow<ZoneBoardSnapshot>
         fun snapshotFor(board: ZoneBoard): ZoneBoardSnapshot
         fun dispatch(intent: ZoneBoardIntent)
         fun updateScope(scope: ZoneQueryScope)
     }
     ```
   - 内部封装 6 类子板块调度器（`ActionBoardPager`, `AlbumBoardPager`, `PostsBoardPager`, `HobbyBoardPager`, `ResaleBoardPager`, `EntityBoardPager`），收敛全部 10 类子板块；
   - 统合核心生命周期规则与不变性：
     - **首屏懒加载 (Lazy Load on Board Select)**：未激活板块不预发网络请求；首次选中触发加载；切走再切回复用快照；
     - **筛选重置不变性 (Filter Change Invariant)**：排序、分类、成色等筛选变更时原子取消在途请求，页码归 1，清空数据重置滚动记忆；
     - **作用域原子重置 (Scope Update Invariant)**：切换专区作用域时，原子取消所有后台协程，清空全板块缓存与滚动记忆，并安全触发新作用域首屏；
     - **错误隔离与独立重试 (Fault Isolation)**：各板块错误互不污染，重试仅针对当前受影响板块。
2. **物理删除 `ZoneBoardActions`**：
   - 将原属于导航维度的卡片点击/全屏看图回调窄化提取为独立的 `ZoneBoardItemActions`；
   - 删除 17 参数适配器 `ZoneBoardActions`；
   - `ZoneBoardPane`、`ZoneBoardFilterBar`、`ZoneBoardHobbySheets` 全面接入单向意图分发 `onIntent: (ZoneBoardIntent) -> Unit`，由宿主直接传递 `viewModel.session::dispatch`。
3. **物理删除 `HomeBoardProjection.kt`**：
   - 删除全部散装派发与冗余转换方法；
   - 必要的业务扩展属性正规收敛：`HomeFeedUiState.zoneScope` 收敛至 `HomeScope.kt`；`HomeFeedUiState.boardTitle` 收敛至 `HomeFeedUiState`；`itemCount` 收敛至 `ZoneBoardContent` 与 `ZoneBoardSnapshot`；`boardFilterToken` 收敛至 `ZoneBoardFilters.kt`。
4. **清理宿主 UIState 中的平铺字段**：
   - `HomeFeedUiState` 与 `ZoneDetailUiState` 仅保留专区级元数据、一级分类/Tab 列表、未读消息等页面级属性；
   - 界面渲染严格基于 `activeSnapshot` 及其强类型 `ZoneBoardContent`。
5. **测试体系重构与解耦**：
   - 核心调度规则由针对深模块的专用单测 `ZoneBoardSessionTest` 全面覆盖；
   - 宿主 ViewModel 单测聚焦于专区切换器、顶层导航、HeaderStack 联动及 Session 意图转发的核心职责，存量断言收敛至单测专用适配层 `HomeBoardTestHelpers.kt`。

## 影响

- **积极影响**：
  - **深模块优势显著**：`HomeFeedViewModel` 与 `ZoneDetailViewModel` 彻底减负，代码精简逾数百行，不再感知 10 类子板块的翻页游标与错误处理；
  - **完全消除中间人适配器**：移除 17 参数 `ZoneBoardActions` 与 `HomeBoardProjection.kt`，UI 组件直接分发单向 `ZoneBoardIntent`，心智模型高度统一；
  - **高内聚单测防护**：子板块复杂的网络行为与不变性由 `ZoneBoardSessionTest` 独立捍卫，大幅提升测试运行效率与诊断确定性。
- **消极影响 / 迁移成本**：
  - 各调用点均从 `actions.onXxx()` 改造为 `onIntent(ZoneBoardIntent.Xxx)`；
  - 存量平铺字段断言在生产代码中彻底废弃，需要单测适配层确保回归覆盖。
- **相关文档与 PR**：
  - 设计提案：Issue #165, #166, #167, #168, #169；
  - 架构指南：[docs/home-hierarchy.md](../home-hierarchy.md)；
  - 术语：[GLOSSARY.md](../../GLOSSARY.md)。
