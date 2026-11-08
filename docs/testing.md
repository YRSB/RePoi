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

默认本地不执行 Gradle 测试任务；仅当用户显式豁免时允许本地运行（优先 `:shared:jvmTest`）。诊断打印用 `println("DIAG-<tag> ...")` 输出，
CI 失败后经 `test-reports` 产物下载 XML 提取（`gh run download <run-id> -n test-reports`），
`system-out` 节点内即测试标准输出。诊断合入前必须删除打印。

### Windows 本地 hostTest 已知失败

`:shared:testAndroidHostTest` 在 Windows 本机存在与改动无关的既有失败：

- DataStore 持久化用例因 `preferences_pb.tmp` 重命名抛 `IOException`（`SearchHistoryManagerTest`、`ThemeManagerTest`、`TokenManagerTest`、`ViewPreferencesManagerTest`、`SearchRepositoryTest`）；
- `ZoneRepositoryTest` 的既有断言漂移。

以 CI Linux 的 `Shared Tests` 结果为准，不要据此定位业务回归。
