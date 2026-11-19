# 测试规范 (Testing)

本项目单元测试由 CI `Shared Tests` 任务执行，按依赖选择位置：

- **纯逻辑**（仓储、ViewModel、主题决策树、工具函数）放 `shared/src/commonTest`，JVM 与 Android-host 双目标都会跑；
- **Compose UI 测试**（`runComposeUiTest` / `mainClock`）放 `shared/src/jvmTest`，只在 `:shared:jvmTest` 执行（先例：`ThemeMotionTest`、`ThemeDynamicRegressionTest`、`PoiAsyncImageLayoutTest`）。

写 Compose 状态测试时显式声明枚举泛型：`mutableStateOf<PaletteStyle>(PaletteStyle.TonalSpot)`。
`mutableStateOf(PaletteStyle.TonalSpot)` 会被推断成**枚举字面量类型**，之后赋同枚举的其他成员即编译失败
（issue #103 的修正提交 `77762b2` 即此坑；`scripts/check-ui-rules.ps1` 的 `ENUM-STATE-INFERENCE` 拦这一类）。
仓储测试模板与 ViewModel 测试模板不同，混用是 flaky 主因。

---

## 仓储测试：直接调仓储

直接构造仓储、`runTest` 内调用挂起函数断言 `Result`。`MockApi.api()` 可省略 dispatcher 参数，
引擎跑真实线程不影响直调（已有 `SettingsRepositoryTest` 等 20+ 用例为证）。

```kotlin
@Test
fun loadsBindingStatus() = runTest {
    val mock = MockApi()
    mock.enqueue(mock.ok("""{...}"""))
    val repository = SettingsRepository(mock.api())

    val result = repository.checkAccount()

    assertTrue(result.isSuccess)
}
```

---

## ViewModel 测试：调度器三件套

ViewModel 经 `viewModelScope.launch` 发请求，`advanceUntilIdle` 只能推进虚拟调度器上的任务。
MockEngine 默认 dispatcher 是真实线程，请求体跑在虚拟时间之外——症状是 `requestCount=0` 且
同代码时绿时红。根治三件套缺一不可：

1. `Dispatchers.setMain(testDispatcher)` 接管 `viewModelScope` 的 Main；
2. `runTest(testDispatcher)` 让 `advanceUntilIdle` 推进同一调度器；
3. `mock.api(testDispatcher)` 让引擎响应分发也落在该调度器上。

```kotlin
private val testDispatcher = StandardTestDispatcher()

@BeforeTest
fun setUpMain() {
    Dispatchers.setMain(testDispatcher)
}

@AfterTest
fun tearDownMain() {
    // 先取消仍排在 Main 上的任务，再交还平台 dispatcher；否则 reset 后恢复的续体会抛
    // "Dispatchers.Main was accessed ... after resetMain()"，并污染下一个测试。
    testDispatcher.cancelChildren()
    Dispatchers.resetMain()
}

@Test
fun initialState_loadsBindingStatus() = runTest(testDispatcher) {
    val mock = MockApi()
    mock.enqueue(mock.ok("""{...}"""))
    val session = FakeUserSession("utoken-test")
    val repository = SettingsRepository(mock.api(testDispatcher))
    val viewModel = SettingsAccountViewModel(repository, session)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
}
```

调度器选型：默认 `StandardTestDispatcher`；仅当用例依赖 launch 体立即执行
（构造即断言、连续状态机推进）才换 `UnconfinedTestDispatcher`（先例：`SettingsAccountViewModelTest`）。

### 有界协程生命周期契约 (Bounded Coroutine Lifecycle)

状态机、分页调度器与异步控制器内部，**严禁在 `init` 构造块中启动无界 `Flow.collect` 或无限常驻循环协程**。
- **症状**：测试中调用 `advanceUntilIdle()` 时挂起超时，或工作线程被未终结的收集器持续占用导致任务饥饿；
- **根治**：异步加载统一收敛为显式触发的挂起函数或受控单次 `Job`（例如 `currentJob?.cancel(); currentJob = scope.launch { ... }`）；
- **验证判据**：在注入 `StandardTestDispatcher` 的环境下，任何动作派发后 `advanceUntilIdle()` 必须能在有限虚拟时间内平稳落定至 Idle 状态。

---

## DataStore 测试：文件实例与内存实例

`TestDataStore` 提供两类实例，按测试目的选择：

| 目的 | 实例 |
| --- | --- |
| 冷启动恢复、防抖落盘、跨实例预热 | `TestDataStore.coverFreshnessPrefs()` 等真实文件实例 |
| ViewModel / UI 编排 | `TestDataStore.inMemory()` |

- **真实文件实例**的写入跑在 DataStore 自己的 IO scope 上，`runTest` 的虚拟时间推不动它。
  断言落盘前必须 `awaitPendingWrites()`（空 `edit {}` 作为同步屏障），否则 `data.first()`
  可能读到内存缓存而看不到已入队写入；跨防抖窗口用 `data/repository/TestCoverFreshnessRepository.kt`
  的 `awaitPersistedCover()`，它只做有界屏障，不替代独立的防抖边界断言。
- **内存实例**没有文件与内部写协程，`edit` 在调用方协程内完成。ViewModel 测试默认用它，
  避免真实 I/O 的续体在 `tearDownMain()` 之后恢复并污染下一个测试。
- 真实文件实例需要显式回收时，把测试自有 scope 传给 `TestDataStore.create(..., scope)`，
  并在 `@AfterTest` 取消它。
- 封面新鲜度仓储统一用 `testCoverFreshnessRepository(...)` 构造；构造签名变化只改该工厂。

### `ViewPreferencesManager` 预热竞态：已修复的 flake 根因

`ViewPreferencesManager` 用「DataStore 单一真相源 + atomics 内存镜像」：构造时启动一次性
`preheatJob` 把 DataStore 快照写进内存镜像，其余读写入口都是同步镜像。历史上预热协程可能与
用户写入并发——预热后落定会用**旧快照覆盖刚写入的内存镜像**，表现为 `feed_sub_type` 偶发
从 `recommend` 变回 `allInfo`、`ViewPreferencesManagerTest` 在慢机上偶发失败。

修复动机与做法（#150 审查 D3 记录，属有意修复而非 scope creep）：

1. 预热仍是**一次性** job（不做持续 collect）——这一点改动前即已成立；本次真正修根因的是把
   `preheatJob` 提升为可 join 的 `val`，并在**所有会改写镜像的入口**前置 `awaitPreheat()`，
   从根上消除「写入被预热协程的旧快照覆盖」的竞态；
2. 读取侧同样需要屏障：首页 ViewModel 的冷启动偏好（一级/二级/三级）改为在 `awaitPreheat()`
   之后读取，避免镜像尚未填充时读到默认值并静默丢弃上次选择；
3. `awaitPreheat()` 公开为**有界等待点**，测试用它替代 `delay(10)` + 2s 真实时间轮询
   （`feedZoneBoard_preheatsFromDataStoreOnColdStart`），不引入重试/睡眠式 flake 掩盖。

判定口径：这类失败是真实时序竞态，不能用「CI 重跑通过」掩盖；修根因后同用例不再依赖真实时钟。

---

## 反馈回路与本地测试

允许在本地运行 Gradle 单元测试（例如针对指定类或方法运行 `./gradlew :shared:jvmTest --tests ...` 或跑全量单测 `./gradlew :shared:jvmTest`），作为快速红绿迭代与定位逻辑回归的手段。但全量构建、Android APK 与桌面端安装包打包依然严禁在本地执行，统一由 CI 权威执行。
本机 `JAVA_HOME` 可能指向失效的 scoop `current` 目录；Gradle 命令统一加 `env -u JAVA_HOME ` 前缀，或在 `pwsh` 中先置 `$env:JAVA_HOME = $null`，避免重复排查 JDK 路径。

诊断打印用 `println("DIAG-<tag> ...")` 输出，
CI 失败后经 `test-reports` 产物下载 XML 提取（`gh run download <run-id> -n test-reports`），
`system-out` 节点内即测试标准输出。诊断合入前必须删除打印。

---

## MockApi 测试桩与反序列化陷阱

本项目单元测试中 `MockApi` 响应体走真实 `kotlinx.serialization` 反序列化（配置为 `ignoreUnknownKeys = true`）。
当测试桩 JSON 数据与模型定义存在微小偏差时，序列化器不会抛出异常，而是**静默降级或赋空值**，造成难以排查的断言失败。测试编写与 Mock 维护必须遵守以下契约：

1. **讨论置顶识别 (`PostItem`)**：
   - `PostItem` 模型**没有 `isPinned` 序列化字段**，其置顶状态由计算属性 `val isPinned: Boolean get() = topTime > 0L` 动态判定；
   - 在 Mock JSON 中书写 `"isPinned": true` 会被忽略；**必须显式提供非零 `topTime`**（如 `"topTime": 1000`）。
2. **专区分类结构 (`ZoneCategoriesPayload`)**：
   - `HpoiApi.getZoneCategories` 返回洁净载荷 `ZoneCategoriesPayload`（传输信封由网络管道内部剥离）；
   - Payload 为包装对象 `{ "visible": true, "categories": [{ "categoryId": 1, "name": "手办" }] }`；
   - **严禁**将响应 `data` mock 为平铺数组 `[{ ... }]`，否则反序列化为空，导致分类标签无法挂载。
3. **相册与列表分页根键 (`AlbumListItem` / `PagedListLoader`)**：
   - 相册列表响应键名为 `"list"`（`{ "data": { "list": [...] } }`），而非 `"items"`。
4. **板块筛选与排序键值有效性**：
   - 排序与筛选参数有严格的领域白名单（见 `HobbyBoardOptions.ORDERS` 与 `ResaleBoardOptions`）：
     - 手办排序合法键：`hits7Day`, `hitsDay`, `hits`, `add`, `release`, `rating`（**无** `"release_desc"`）；
     - 二手成色合法键：`"10"`, `"99"`, `"95"`, `"9"`, `"8"`（9成新标准键为 `"9"`）。
5. **强类型桩推荐**：
   - 优先使用 `MockApi.routeJson` / `MockApi.replaceRouteJson` 辅助函数生成桩数据，利用编译器类型检查杜绝手写 JSON 偏差。

### 按需测试 CI（TDD 内环）

`.github/workflows/tests.yml` 只跑 `:shared:jvmTest` 与 `:shared:testAndroidHostTest`，不构建 APK/桌面产物：

```bash
gh workflow run tests.yml --ref <branch>
gh run list --workflow Tests --branch <branch> --limit 1
gh run watch <run-id> --exit-status
```

`workflow_dispatch` 使用目标分支上的工作流定义，实施分支需先包含 `tests.yml`（从 main 切出即满足）。

该流水线先单独跑 `:shared:compileTestKotlinJvm`（fast failure），再跑测试任务：测试源的编译错误（类型推断、缺 import）十几秒内就返回，不必等整个矩阵跑完。

### 定向测试 CI（快速内环）

改完单个仓储/ViewModel 测试时，给同一份 `tests.yml` 传 `filter`，只跑匹配的 JVM 用例
（实测约 1 分钟；完整两目标约 2–3 分钟）：

```bash
gh workflow run tests.yml --ref <branch> -f filter='*CoverFreshness*'
gh run list --workflow Tests --branch <branch> --limit 1
gh run watch <run-id> --exit-status
```

`filter` 是 Gradle `--tests` 通配模式（可按类或方法）；留空或不传即完整 `:shared:jvmTest`
与 `:shared:testAndroidHostTest`。定向模式只做快速反馈，**不是合并门禁**：
交付前仍必须以不带 `filter` 的 `tests.yml`（或 PR 的 `ci.yml`）全量绿为准。

> 为什么不做成独立 workflow：GitHub 只允许 `workflow_dispatch` 触发默认分支上已存在的
> workflow；新文件要等合并进 main 后才可手动派发，临时分支无法立即用。扩展现有 `tests.yml`
> 的 `filter` 模式可以当天生效，也避免两份渐变的工作流逻辑。


---

## 被隔离的测试（quarantine registry）

`shared/build.gradle.kts` 的 `excludeTestsMatching` 是**隐形门禁**：被排除的测试类根本不执行，
CI 依然全绿。因此每条排除必须在下方登记，`scripts/check-ui-rules.ps1` 的 `TEST-QUARANTINE`
规则会机械校验「构建脚本里出现的每个 pattern 都能在本表找到」。

> 新增排除前先问：能不能用调度器三件套把它修好？本项目历史上正是靠「先修再摘除」把
> `UserCollectViewModelTest`、`HobbyDetailViewModelCollectTest` 与 `HomeFeedViewModelTest`
> 从空绿拉回了真实执行（`HomeFeedViewModelTest` 注入内存 DataStore 后已回归）。

| pattern | 原因 | 恢复条件 |
|---|---|---|
| `*AlbumRankListViewModelTest*` | M7 遗留：依赖 MockApi 真实线程与异步时序竞争 | 改为 Fake 仓储 + Turbine 纯状态机断言 |
| `*AlbumViewModelTest*` | 同上 | 同上 |
| `*HobbyDetailViewModelPagingTest*` | 详情页分页仍依赖异步时序 | 分页装载抽到可单测的 loader 后 |
| `*HobbyDetailViewModelRelatedTest*` | 关联条目并发加载时序不稳 | 用虚拟调度器固定并发顺序后 |
| `*HobbyDetailViewModelTimelineTest*` | 时间轴多阶段加载时序不稳 | 同上 |
| `*HobbyDetailViewModelExHobbyTest*` | ExHobby 图库按需补录时序不稳 | 同上 |
| `*LoginViewModelTest*` | 登录链路含真实会话预热 | 会话层改为 Fake 后 |
| `*MessageBlockViewModelTest*` | 消息拉黑列表时序不稳 | 调度器三件套改造后 |
| `*MessageCenterViewModelTest*` | 消息中心多 Tab 并发时序不稳 | 同上 |
| `*MessageChatViewModelTest*` | 聊天长轮询依赖真实延迟 | 注入可暂停的时钟后 |
| `*MessageNoticeViewModelTest*` | 通知列表分页时序不稳 | 同上 |
| `*SettingsViewModelTest*` | 设置页多 DataStore 并发读 | 内存 DataStore 后 |

> 注意：`*HobbyDetailViewModel*` 曾以整类通配排除，会把收藏联动断言一并吞掉。
> 现行做法是按兄弟类逐个收窄，**收藏链路（`HobbyDetailViewModelCollectTest`）必须真实执行**。
