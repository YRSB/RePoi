# 收藏仓储自治化演进、变更代数闭环与跨 Tab 惰性恢复感知 (Autonomous Collect Store, Mutation Generation & Resumed Refresh)

- 状态：已采纳 (Accepted) - 2026-11-18
- 修订/取代：补充并演进 [ADR-0011](0011-collect-architecture-eventbus-decoupling.md)

## 背景与上下文 (Context)

在 [ADR-0011](0011-collect-architecture-eventbus-decoupling.md) 中，RePoi 移除了 `CollectInvalidationBus`，确立了状态驱动架构，并在画廊（`PictureGalleryViewModel`）与收藏中心图片页之间建立了「写操作计数标记 (`markGalleryMutation()`) + 页面恢复感知比对 (`galleryMutationCount`)」的静默刷新链路。

随着后续版本对相册详情、厂商详情、系列详情、角色详情、人物详情等全业务实体页面的演进，该机制暴露出以下深层设计缺陷：
1. **浅用例击穿与职责碎片化**：`ToggleItemCollectUseCase` 仅有 26 行，纯粹将调用转手给 `CollectRepository.collectItemAdd/Del`，既无领域编排，也未闭环写操作副作用。
2. **写侧职责倒置与标记漏调**：仓储将递增计数的职责通过公开方法 `markGalleryMutation()` 泄露给调用方。除 `PictureGalleryViewModel` 显式调用外，相册、厂商、系列、角色、人物等 5 个实体详情 ViewModel 虽均调用用例切换了收藏，但均漏调该标记。
3. **语义与范围狭隘**：原机制字面绑定「画廊 (Gallery)」，`UserCollectViewModel.handleResume()` 硬编码仅刷新图片大类（`UserCollectCategory.PICTURE`）。用户在相册或厂商详情页切换收藏后返回收藏中心，对应列表静默失真。

## 架构决策 (Decision)

1. **加深 `CollectRepository` 并物理删除浅用例**：
   吸收 `ToggleItemCollectUseCase`，在 `CollectRepository` 中沉淀一等公民方法：
   ```kotlin
   suspend fun toggleItemCollect(nodeId: Long, isCollected: Boolean): Result<Boolean>
   ```
   物理删除 `ToggleItemCollectUseCase.kt`，全工程 6 处 ViewModel 及相关单测统一直接依赖 `CollectRepository`。
2. **收拢写侧闭环，内部原子推进变更代数**：
   彻底移除公开方法 `markGalleryMutation()`；将 `galleryMutationCount` 重构泛化为 `collectMutationGeneration: StateFlow<Long>`（收藏变更代数）。仓储内所有写操作（`saveCollect`、`deleteCollect`、`collectItemAdd`、`collectItemDel`、`cancelCollectBatch`、`saveWorksCollect`、`deleteWorksCollect`）在网络请求成功后，由仓储内部统一原子执行：
   ```kotlin
   _collectMutationGeneration.update { it + 1 }
   ```
   调用方物理上无法再遗漏代数推进。
3. **消费端游标持久化与幂等**：
   `UserCollectViewModel` 通过 `ViewPreferencesManager`（DataStore）持久化 `consumedMutationGeneration`，覆盖配置变更与进程重建，不引入 KMP SavedStateHandle 依赖；构造期读回后按当前仓储代数夹逼（仓储代数随进程从 0 重启，旧游标不得阻塞新进程的刷新）。在执行内部批量取消成功并刷新当前大类后，主动同步 `consumedMutationGeneration = collectRepository.collectMutationGeneration.value` 并落盘，杜绝冗余重载。
4. **跨 Tab 惰性刷新机制**：
   `UserCollectViewModel` 维护 `dirtyCategories: MutableSet<UserCollectCategory>`。在 `handleResume()` 检测到代数落后时，仅对当前激活 Tab 立即触发 `loader.refresh()`，其余已有数据的大类记入 `dirtyCategories`；当用户后续点击切入对应 Tab 时，就地平滑执行 `loader.refresh()`（保留现有条目原位刷新，新数据到达后原子替换），未切入的 Tab 零网络开销，且避免骨架屏白屏闪烁。
5. **领域词汇表与实现规范同步**：
   在 `GLOSSARY.md` 与 `docs/collect.md` 中将「画廊写操作计数」全面替换为「收藏变更代数 (CollectMutationGeneration)」。

## 考虑备选 (Considered Options)

- **保留 `ToggleItemCollectUseCase` 并在用例内部自增**：未能解决手办编辑页、作品编辑页、批量取消等其他写侧入口的代数递增，仍导致写侧职责碎片化。
- **仓储内按 `UserCollectCategory` 维护独立代数**：通用条目收藏接口（如 `api/collect/item/add`）并不携带实体分类，强行要求调用端透传分类会污染底层 API 契约；采用全局单调代数 + 消费端 Tab 惰性刷新更轻量且鲁棒。
- **仓储内维护客户端游标注册表 (`consumeStaleness(consumerKey)`)**：破坏了单例仓储的无状态性，且进程销毁重建后内存游标丢失易引发重复刷新或键无法回收引发的内存泄漏。

## 后续影响 (Consequences)

- 修复相册、角色、厂商、系列、人物等二级页收藏切换后返回收藏中心列表不刷新的静默 BUG；
- 消除 1 个冗余浅用例和 1 个向外泄露的脏位标记方法；
- 收藏中心跨 Tab 切换体验更平滑，无骨架屏闪跳。
