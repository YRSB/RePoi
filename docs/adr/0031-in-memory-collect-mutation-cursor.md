# ADR-0031: 收藏已消费变更代数游标收归纯内存（简化 ADR-0025）

## 状态
已采纳 (Accepted) - 2026-11-27
修订：[ADR-0025](0025-autonomous-collect-store-and-mutation-generation.md) 决策 3

## 上下文与问题陈述
在 [ADR-0025](0025-autonomous-collect-store-and-mutation-generation.md) 中，为防止配置变更或进程重建后重复刷新，`UserCollectViewModel` 引入了 DataStore 持久化游标（`ConsumedCollectMutationGeneration`）、异步等待对象 `consumedGenerationReady: Deferred<Long>`、按仓储代数夹逼逻辑以及 `handleResume()` 里的 `resumeAwaitingCursor` 重入锁。

实际实现核查表明：`UserCollectViewModel` 在构造或配置变更重建时，`applyInitParam()` 必然执行 `resetAllLoaders()` 与首屏加载，并直接以 `collectRepository.collectMutationGeneration.value` 覆盖内存游标。DataStore 持久化游标既未真正阻止重建后的重复请求，反而带来了异步竞态与多余的 IO 复杂度。

## 决策结论
1. **移除 DataStore 游标持久化与异步等待链**：
   - 删除 `ViewPreferencesManager` 中的 `get/setConsumedCollectMutationGeneration` 与对应 Preferences Key；
   - 删除 `UserCollectViewModel` 中的 `consumedGenerationReady` 异步 `Deferred` 与 `resumeAwaitingCursor` 重入锁；
   - `consumedMutationGeneration` 仅作为纯内存 `Long` 游标维护，构造期及 `applyInitParam` / 批量取消成功时同步对齐 `collectRepository.collectMutationGeneration.value`。
2. **保留纯内存变更代数与惰性恢复刷新**：
   - 继续保留 `CollectRepository.collectMutationGeneration` 与 `UserCollectViewModel` 的 `dirtyCategories` 机制，确保详情页未修改返回及切后台回前台零请求、不丢失分页与滚动位置，同时在发生收藏写操作后按需静默刷新受影响 Tab。

## 影响
- **积极影响**：`handleResume()` 从异步重入状态机简化为纯同步整数比对，消除不必要的 DataStore 读写与测试异步等待复杂度。
- **消极影响 / 迁移成本**：移除两条专测 DataStore 持久化游标的单元测试，改为验证 VM 重建后通过 `applyInitParam` 消费当前代数且不触发二次 `OnResume` 刷新。
