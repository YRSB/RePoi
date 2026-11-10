# 测试规范 (Testing)

本项目单元测试位于 `shared/src/commonTest`，由 CI `Shared Tests` 任务在 JVM 与 Android-host 双目标执行。
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
    val repository = SettingsRepository(mock.api(), FakeUserSession("utoken-test"))

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
    Dispatchers.resetMain()
}

@Test
fun initialState_loadsBindingStatus() = runTest(testDispatcher) {
    val mock = MockApi()
    mock.enqueue(mock.ok("""{...}"""))
    val session = FakeUserSession("utoken-test")
    val repository = SettingsRepository(mock.api(testDispatcher), session)
    val viewModel = SettingsAccountViewModel(repository, session)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
}
```

调度器选型：默认 `StandardTestDispatcher`；仅当用例依赖 launch 体立即执行
（构造即断言、连续状态机推进）才换 `UnconfinedTestDispatcher`（先例：`SettingsAccountViewModelTest`）。

---

## 反馈回路

本地严禁执行 Gradle 测试任务。诊断打印用 `println("DIAG-<tag> ...")` 输出，
CI 失败后经 `test-reports` 产物下载 XML 提取（`gh run download <run-id> -n test-reports`），
`system-out` 节点内即测试标准输出。诊断合入前必须删除打印。

### 按需测试 CI（TDD 内环）

`.github/workflows/tests.yml` 只跑 `:shared:jvmTest` 与 `:shared:testAndroidHostTest`，不构建 APK/桌面产物：

```bash
gh workflow run tests.yml --ref <branch>
gh run list --workflow Tests --branch <branch> --limit 1
gh run watch <run-id> --exit-status
```

`workflow_dispatch` 使用目标分支上的工作流定义，实施分支需先包含 `tests.yml`（从 main 切出即满足）。


---

## 被隔离的测试（quarantine registry）

`shared/build.gradle.kts` 的 `excludeTestsMatching` 是**隐形门禁**：被排除的测试类根本不执行，
CI 依然全绿。因此每条排除必须在下方登记，`scripts/check-ui-rules.ps1` 的 `TEST-QUARANTINE`
规则会机械校验「构建脚本里出现的每个 pattern 都能在本表找到」。

> 新增排除前先问：能不能用调度器三件套把它修好？本项目历史上正是靠「先修再摘除」把
> `UserCollectViewModelTest` 与 `HobbyDetailViewModelCollectTest` 从空绿拉回了真实执行。

| pattern | 原因 | 恢复条件 |
|---|---|---|
| `*AlbumRankListViewModelTest*` | M7 遗留：依赖 MockApi 真实线程与异步时序竞争 | 改为 Fake 仓储 + Turbine 纯状态机断言 |
| `*AlbumViewModelTest*` | 同上 | 同上 |
| `*HobbyDetailViewModelPagingTest*` | 详情页分页仍依赖异步时序 | 分页装载抽到可单测的 loader 后 |
| `*HobbyDetailViewModelRelatedTest*` | 关联条目并发加载时序不稳 | 用虚拟调度器固定并发顺序后 |
| `*HobbyDetailViewModelTimelineTest*` | 时间轴多阶段加载时序不稳 | 同上 |
| `*HobbyDetailViewModelExHobbyTest*` | ExHobby 图库按需补录时序不稳 | 同上 |
| `*HomeFeedViewModelTest*` | 首页多源聚合 + 真实 DataStore IO | 注入内存 DataStore 后 |
| `*LoginViewModelTest*` | 登录链路含真实会话预热 | 会话层改为 Fake 后 |
| `*MessageBlockViewModelTest*` | 消息拉黑列表时序不稳 | 调度器三件套改造后 |
| `*MessageCenterViewModelTest*` | 消息中心多 Tab 并发时序不稳 | 同上 |
| `*MessageChatViewModelTest*` | 聊天长轮询依赖真实延迟 | 注入可暂停的时钟后 |
| `*MessageNoticeViewModelTest*` | 通知列表分页时序不稳 | 同上 |
| `*SearchViewModelTest*` | 搜索防抖依赖真实时间 | 注入 TestDispatcher 时间后 |
| `*SettingsViewModelTest*` | 设置页多 DataStore 并发读 | 内存 DataStore 后 |

> 注意：`*HobbyDetailViewModel*` 曾以整类通配排除，会把收藏联动断言一并吞掉。
> 现行做法是按兄弟类逐个收窄，**收藏链路（`HobbyDetailViewModelCollectTest`）必须真实执行**。
