# 收藏域实现规范 (Collect)

收藏是 RePoi 最容易改坏的领域：**8 个列表 × 6 档收藏状态 × 3 种排序 × 写侧跨页同步**。
本文只记录代码里读不出来、但改错就会静默失效的约定。设计动因见
[ADR-0011](adr/0011-collect-architecture-eventbus-decoupling.md)。

---

## 1. 单一数据源：`CollectRepository`

`data/repository/CollectRepository.kt` 是收藏业务的**唯一权威数据源**。
`UserRepository` 不含任何收藏职责；若你在那里找收藏列表，说明你在读旧代码。

### 1.1 条目状态池

```kotlin
fun observeCollectState(nodeId: Long): StateFlow<CollectData?>   // 非挂起，同 nodeId 必返回同一实例
suspend fun refreshCollectState(nodeId: Long): Result<CollectData?>  // 网络拉取 + 成功才写池
val galleryMutationCount: StateFlow<Long>                        // 画廊写操作单调计数
fun markGalleryMutation()
```

**并发模型是 CAS 不可变快照，不是 Mutex**：

```kotlin
private val stateFlows = MutableStateFlow<Map<Long, MutableStateFlow<CollectData?>>>(emptyMap())
```

- commonMain 没有 `ConcurrentHashMap`；`Mutex` 做不到**读无锁**，而观察是热路径。
- 创建新条目的 `getOrPut` 必须放在**同一个** `flows.update { }` 闭包内，否则并发首次观察会造出两个实例，
  破坏「同 nodeId 同实例」这条被单测锁定的契约。

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

## 4. 跨页同步：脏位 + 恢复感知（无事件总线）

画廊写操作会改动画廊自己的列表，但**不会**自动让收藏页的图片列表与服务端排序对齐。
链路：

```text
PictureGalleryViewModel.toggleCollect 成功
  → collectRepository.markGalleryMutation()          // 计数 +1
返回收藏页（Nav3 出栈）→ LifecycleEventEffect(ON_RESUME)
  → viewModel.onIntent(OnResume)
  → 比对 galleryMutationCount 与已消费值
  → 变大 → 对图片页静默 refresh()
```

两条硬约束：

- **不要用共享流 / Channel 做这件事**。ADR-0011 明确拒绝事件总线；计数是**状态**，可重读、不丢、无订阅者也能观察。
- `consumedGalleryMutationCount` 必须落 `savedState`，否则配置变更重建 ViewModel 后会重复刷新（或漏刷新）。

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
