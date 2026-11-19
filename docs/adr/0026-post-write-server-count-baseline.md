# 写后按服务端计数重置详情页基线与乐观增量归零 (Post-Write Server Count Baseline & Optimistic Delta Reset)

- 状态：已采纳 (Accepted) - 2026-11-19
- 修订/取代：补充 [ADR-0025](0025-autonomous-collect-store-and-mutation-generation.md) 的写侧闭环

## 背景与上下文 (Context)

详情页收藏人数显示采用「基线 + 乐观增量」双轨：`collectCountBase` 来自首屏详情接口，
`collectCountDelta` 是本页 toggle 后的 ±1。此前写成功后仅靠乐观增量维持人数，存在两类失真：

1. 服务端真实人数在用户浏览期间持续变化（他人也在收藏/取消），乐观增量从不与服务端对账，
   基线永久漂移；
2. 手办/作品编辑页保存或删除会改变收藏状态与档位，但详情页基线无从感知，
   返回后人数与档位静默失真。

## 架构决策 (Decision)

1. **写成功后以服务端计数为唯一真相源重置基线**：任意写路径成功后调用
   `getCollectCount`（`api/hobby/collectCount`）取五分类总人数 `total`，
   重置 `collectCountBase` 并将 `collectCountDelta` 归零；请求失败什么都不做，
   保留乐观 delta（网络异常不是计数变化）。
2. **编辑页写侧经快照池广播**：`CollectRepository` 新增服务端计数快照池
   `observeCollectCountSnapshot(nodeId)` / `updateCollectCountSnapshot(nodeId, count)`，
   与条目状态池同构（CAS 不可变快照，同 `nodeId` 恒返回同一实例）。
   手办/作品编辑页（`CollectEditViewModel` / `WorksCollectEditViewModel`）保存或删除成功后
   调私有 `refreshCountSnapshot` 写池；Hobby/Works 详情页在 `init` 订阅快照流，
   收到即重置基线归零 delta。
3. **快照携带全局单调 revision**：人数未变时 `StateFlow` 仍会发射新值（值相等不发射），
   消费端才能可靠把 delta 归零。
4. **详情页自身 toggle 就地重置**：相册/画廊/厂商/系列/角色/人物六个直接 toggle 的页面
   写成功后就地 `getCollectCount` 重置自身 uiState，不经快照池——当前视图自己就是唯一消费者。

## 考虑备选 (Considered Options)

- **纯乐观增量（维持现状）**：实现零成本，但基线永不与服务端对账，人数漂移不可收敛。
- **详情页每次 OnResume 重拉计数**：为一个小徽章多付一次网络请求，且仍需处理在途竞态。
- **把计数重置并入变更代数推进**：代数是「列表内容变了」的信号，计数对齐是独立副作用；
  混入会让未发生计数变化的写操作也白发计数请求。

## 后续影响 (Consequences)

- 详情页写后人数与服务端对齐；编辑页改档位后返回详情页不再失真。
- `updateCollectCountSnapshot` 是「写侧必须记得调用」型手动接缝，是 ADR-0025
  「写侧自动推进、调用方无法遗漏」哲学的唯一例外：新增会改变收藏人数的写路径时，
  必须在成功分支刷新计数，契约登记于 [docs/collect.md §1.4](../collect.md)。
- `CollectCountData.total`(Int) 与 `CollectCountSnapshot.total`(Long) 暂存双轨，
  统一计数类型留待后续收敛。
