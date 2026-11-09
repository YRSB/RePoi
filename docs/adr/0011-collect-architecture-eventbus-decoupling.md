# 收藏模块解耦与状态驱动架构演进 (Collect Architecture & EventBus Decoupling)

## 背景与上下文 (Context)
历史版本中，画廊页（`PictureGalleryRouteScreen`）与手办详情页等跨组件通信依赖临时事件总线（`CollectInvalidationBus`）与零散广播（`CollectRepository.collectUpdates`）。此外，8 大类用户收藏列表查询分散在 `UserRepository`，而写侧操作（保存、取消、批量管理）又位于 `CollectRepository`，导致收藏业务内聚性缺失；`UserCollectViewModel` 高达 1300 余行，手工维护分页游标与状态机，偏离了项目标准的单向数据流（UDF）与分页加载规范。

## 架构决策 (Decision)
1. **统一领域术语**：严格遵循领域词汇表，全模块统一采用 `Collect` 命名，废止未收录的 `Favorite` 别名。
2. **彻底剔除事件总线**：物理移除 `CollectInvalidationBus`，拒绝以伪领域事件包装总线。全面转向 Android 官方推荐的**状态驱动架构 (State-Driven Architecture)**，由 `CollectRepository` 作为单一真相源（SSOT）维护并暴露条目状态流（`observeCollectState(nodeId): StateFlow<CollectData?>`）。
3. **数据层职责收拢**：将分散在 `UserRepository` 的 8 类用户收藏列表查询完整归集至 `CollectRepository`。
4. **状态感知与静默对齐**：跨页面多条目联动（画廊与列表）采用「标记脏位 + 页面恢复感知」策略，由列表加载器自动维持服务端排序与最终一致性；编辑页通过显式导航出栈，下游被动响应状态流。
5. **UI 层 MVI 规范化与瘦身**：`UserCollectViewModel` 收敛为纯 MVI 单向数据流，仅暴露不可变 `StateFlow<UserCollectUiState>`，由密封类 `UserCollectIntent` 驱动，并采用 `PagedListLoader` 统一接管 8 个子页的分页状态机。

## 考虑备选 (Considered Options)
- **保留 `SharedFlow<CollectEvent>` 领域事件广播**：看似强类型，实则是事件总线的变体，仍然违背状态驱动原则，导致调用端需手工维护增删逻辑，易出现与服务端排序不一致。
- **引入全量 Room / SQLDelight KMP 数据库**：手办资产强依赖实时服务端状态，全量离线数据库同步和冲突仲裁维护成本极高，不如仓储层内存状态池 + DataStore 排序偏好轻量高效。
- **Navigation 3 强校验结果回调协议**：对于多图画廊跨多条目批量变动，导航栈深层多值传递侵入性强且难以维系深链场景，因此由仓储状态流与脏位感知承担更为稳固。

## 后续影响 (Consequences)
- 消除内存泄漏隐患与隐式组件依赖，数据流向清晰可溯。
- `UserCollectViewModel` 样板代码大幅缩减，单元测试可纯粹针对 `Intent -> State` 进行确定性验证。
