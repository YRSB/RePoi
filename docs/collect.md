# 收藏域实现规范 (Collect)

收藏是 RePoi 最容易改坏的领域：**8 个列表 × 6 档收藏状态 × 3 种排序 × 写侧跨页同步**。
本文只记录代码里读不出来、但改错就会静默失效的约定。设计动因见
[ADR-0011](adr/0011-collect-architecture-eventbus-decoupling.md) 与
[ADR-0025](adr/0025-autonomous-collect-store-and-mutation-generation.md)。

---

## 1. 单一数据源：`CollectRepository`

`data/repository/CollectRepository.kt` 是收藏业务的**唯一权威数据源与自治存储**。
`UserRepository` 不含任何收藏职责；若你在那里找收藏列表，说明你在读旧代码。

### 1.1 条目状态池与写侧代数

```kotlin
fun observeCollectState(nodeId: Long): StateFlow<CollectData?>   // 非挂起，同 nodeId 必返回同一实例
suspend fun refreshCollectState(nodeId: Long): Result<CollectData?>  // 网络拉取 + 成功才写池
val collectMutationGeneration: StateFlow<Long>                   // 收藏写操作单调代数（写成功自动+1）
suspend fun toggleItemCollect(nodeId: Long, isCollected: Boolean): Result<Boolean> // 统一切换条目收藏
```

**并发模型是 CAS 不可变快照，不是 Mutex**：

```kotlin
private val stateFlows = MutableStateFlow<Map<Long, MutableStateFlow<CollectData?>>>(emptyMap())
```

- commonMain 没有 `ConcurrentHashMap`；`Mutex` 做不到**读无锁**，而观察是热路径。
- 创建新条目的 `getOrPut` 必须放在**同一个** `flows.update { }` 闭包内，否则并发首次观察会造出两个实例，
  破坏「同 nodeId 同实例」这条被单测锁定的契约。

**跨进程恢复**：`commonMain` 没有 androidx `SavedStateHandle`；恢复槽由 NavKey 字段 + 内存 `savedState` 承担，
真正跨进程的游标落在 `ViewPreferencesManager.get/setConsumedCollectMutationGeneration`（DataStore）：
`UserCollectViewModel` 构造期读回并按仓储当前代数夹逼，恢复时不会阻塞新进程的刷新。详见 §4。

### 1.2 写操作与状态池

| 方法 | 成功后池值 |
|---|---|
| `saveCollect(nodeId, collectData)` | `collectData` |
| `deleteCollect(nodeId, currentData)` | `null` |
| `collectItemAdd(nodeId)` | `(旧值 ?: CollectData()).copy(state = "care")` |
| `collectItemDel(nodeId)` | `null` |

> `collectItemAdd` 的 `care` 只是「已收藏」**占位值**（`CollectData.isCollected` 按 `state` 判定），
> 不代表该条目处于手办的心愿档。`CollectRepositoryStateTest` 锁定了这个约定。
>
> **网络失败绝不写池**：失败时保留旧值，避免一次抖动把爱心刷成未收藏。

### 1.3 六类列表查询

4 个收藏列表 + 2 个创作列表全部经由 `listWithSessionEnvelope`，token 快照与 4001 自愈只有一份实现。
新增列表查询请复用该助手，不要重写 `isTokenExpired` 分支。

### 1.4 写后计数基线：服务端快照池与乐观增量

详情页收藏人数 = `collectCountBase + collectCountDelta`（乐观增量，本页 toggle 后 ±1）。
写成功后必须以服务端 `getCollectCount`（`api/hobby/collectCount`，五分类总人数 `total`）
重置 base 并归零 delta：他人也在收藏/取消，纯乐观会让基线永久漂移。

```kotlin
fun observeCollectCountSnapshot(nodeId: Long): StateFlow<CollectCountSnapshot?>  // null = 尚无写后快照
fun updateCollectCountSnapshot(nodeId: Long, count: Long)                        // 仅写侧成功后调用
suspend fun getCollectCount(nodeId: Long): Result<CollectCountData>
```

**这是「写侧必须记得调用」型手动接缝**（§4 变更代数是自动推进的，此处不是）：

- 手办/作品**编辑页**（`CollectEditViewModel` / `WorksCollectEditViewModel`）保存或删除成功后
  必须调私有 `refreshCountSnapshot(nodeId)` → `updateCollectCountSnapshot` 写池；
- Hobby/Works 详情页在 `init` 订阅 `observeCollectCountSnapshot`，收到快照即重置基线归零 delta；
- 六个直接 toggle 的页面（相册/画廊/厂商/系列/角色/人物）写成功后就地 `getCollectCount`
  重置自身 uiState，不经快照池——当前视图自己就是唯一消费者。

漏调症状：详情页计数**静默失真**——编辑页改了收藏状态，返回详情页人数与档位不对齐、delta 不归零。
新增任何改变收藏人数的写路径时，必须在成功分支刷新计数（就地重置或走快照池）。

失败语义：`getCollectCount` 失败时**什么都不做**，保留旧快照与乐观 delta，网络异常不是计数变化。
快照带全局单调 `revision`：人数未变时 StateFlow 仍会发射，delta 才能可靠归零
（`StateFlow` 值相等不发射）。快照池并发模型与条目状态池同构（CAS 不可变快照，同 `nodeId` 恒返回同一实例）。
设计动因见 [ADR-0026](adr/0026-post-write-server-count-baseline.md)。

---

## 2. 分页：8 类各持一个 `PagedListLoader`

`ui/collect/UserCollectViewModel.kt` 里是 `Map<UserCollectCategory, PagedListLoader<*>>`，
**不能共用一个实例**：`KeyedListLoader` 内部只有一个 `activeJob`，共用会让 A 类在途请求取消 B 类。

**去重键不统一**（跨页可能重复，键必须与语义对齐）：

| 大类 | keySelector |
|---|---|
| 手办 / 通用四页 / 作品 | `item.id to item.actualItemId` |
| 相册 / 图片 | `item.id` |

`fetcher` 闭包每次重读 `_uiState.value`，因为筛选条件（收藏状态、排序、升降序、细分类、标签、
作品三态）都是运行时可变的。**条件变化必须先 `resetLoader()` 再 `loadFirstPage()`**，
否则旧页码与 `hasMore` 会污染新条件。

`SubPageData` 是从 `PagedListState` 投影出来的对外形状；`PagedListState` 没有 `isContentHidden`，
该字段由「错误文案含『隐藏』」旁路派生。

---

## 3. 意图入口：`UserCollectIntent`

`UserCollectViewModel` 对外只有 `onIntent(intent)`、`initParam(param)`、`uiState`、`userId`。
**不要新增 public 方法**：新增交互就是新增一条 intent + 一个私有 `applyXxx` 处理器。

```kotlin
viewModel.onIntent(UserCollectIntent.SwitchCollectState("buy"))
viewModel.onIntent(UserCollectIntent.ToggleSelect(item.id))
viewModel.onIntent(UserCollectIntent.ConfirmBatchCancel)
```

私有处理器一律 `apply` 前缀。**`private fun applyX` 与 `fun x` 同名会编译失败**——
Kotlin 可见性不参与签名，这是本项目踩过的坑。

`SwitchSortDirection(sortType: Int)` 携带目标值（而非无字段翻转），以便幂等重放。

---

## 4. 跨页同步：收藏变更代数 + 恢复感知（无事件总线）

画廊或二级详情页（相册/厂商/系列/角色/人物/手办/作品编辑）的写操作会改动自身数据，但**不会**自动让收藏中心各子列表与服务端排序对齐。
ADR-0025 确立的链路：

```text
任意收藏写操作（toggleItemCollect / saveCollect / deleteCollect / cancelCollectBatch / saveWorksCollect 等）成功
  → CollectRepository 内部自动原子执行 _collectMutationGeneration.update { it + 1 }
返回收藏页（Nav3 出栈）→ LifecycleEventEffect(ON_RESUME)
  → viewModel.onIntent(OnResume)
  → 比对 collectMutationGeneration 与已消费游标 consumedMutationGeneration
  → 变大 →
      1. 当前激活 Tab 立即静默 refresh()
      2. 其余已有数据的 Tab 加入 dirtyCategories
      3. 用户后续切入 dirty Tab 时，平滑 refresh()（保留现有列表原位更新，无骨架屏白屏）
```

三条硬约束：

- **不要用共享流 / Channel 做这件事**。ADR-0011 明确拒绝事件总线；代数是**状态**，可重读、不丢、无订阅者也能观察。
- **写侧严禁保留外部手动 mark 方法**。所有写操作成功后必须在仓储内部自动推进代数，调用端无法绕过。
- `consumedMutationGeneration` 必须落 DataStore（`ViewPreferencesManager.get/setConsumedCollectMutationGeneration`），否则配置变更后 VM 重建会重复刷新；恢复时按仓储当前代数夹逼（进程重建后仓储代数从 0 重启）。批量取消成功后主动同步该游标并落盘以保证幂等。
- `UserCollectScreen` 每次进入组合都派发 `OnResume`，不得用普通 `remember` 计数跳过首次组合：二级页覆盖使页面退出组合后计数重置，返回时的事件会被门槛吞掉；幂等由代数游标保证。

---

## 5. 排序记忆（三层，别改错）

| 层 | 位置 | 作用域 |
|---|---|---|
| savedState 内存 | `stateSet("order"/"sortType")` | VM 存活期 |
| 实例 map | `hobbyOrderMemory` / `hobbySortTypeMemory` | VM 存活期，按收藏状态分键 |
| DataStore | `viewPreferencesManager.setCollectHobbyOrder(state, order, sortType)` | 跨进程，**仅手办页** |

优先级：**显式深链 > 持久化记忆 > 类默认值**。
`persistHobbySortToPrefs()` 必须在 `_uiState.update` 之后调用（它读 `uiState.value`）。

`orderTime`（入手时间）只在「已入(buy)」档合法，其余档必须回落 `actionDate`
（`HobbyOrderOption.effective`）。

---

## 6. 滚动位置：条件是键

`subpage/SubPageScrollStates.kt` 的键编码是**不变量**，`CollectFeedQueryKeyTest` 守护它：

- 任一影响内容的维度变化 → 键变化 → 重建 grid state → 自动回顶；
- 条件不变（从二级页返回）→ 同键 → `SaveableStateRegistry` 恢复位置。

手办键含 `collectState / order / sortType / subCategoryId / selectedTag`；
相册键含 `albumType / order`（`albumType` 虽无页内切换入口，仍由深链写入）。
**给列表加筛选维度时必须同时加进键**，否则切换后回顶失效或串位。

---

## 7. 相册语料档位

`UserCollectUiState.albumType` 只有两个合法值：`fav`（`api/collect/album-v2`）与 `upload`
（`api/album/user-v2`）。**页内不提供切换入口**——「我创建的」在「我的」页有独立入口；
`albumType` 仅由深链 `UserCollectNavParam.albumType` 指定。

点击卡片时两种语料的 id 语义不同：`fav` 用内层 `actualAlbumId`，`upload` 用 `item.id`。

---

## 8. 测试与门禁

- 见 [docs/testing.md](testing.md)：仓储直调；ViewModel 用调度器三件套（`Dispatchers.setMain` +
  `runTest(testDispatcher)` + `MockApi.api(testDispatcher)`）。
- 收藏系行为测试**必须真实执行**。`shared/build.gradle.kts` 的 `excludeTestsMatching` 会静默吞掉断言，
  新增排除必须登记到 [testing.md 的隔离表](testing.md#被隔离的测试-quarantine-registry)，
  由 `scripts/check-ui-rules.ps1` 的 `TEST-QUARANTINE` 规则机械校验。
- 提交前可本地跑 `pwsh -File scripts/check-ui-rules.ps1`（纯静态，**不需要 Gradle**）。
