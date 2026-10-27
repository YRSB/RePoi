package com.nobody.repoi.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * 单元测试主调度器 helper（commonTest 版），负责在测试生命周期中挂载和重置主线程调度器。
 * 移植自老项目 `MainDispatcherRule`（JUnit Rule → beforeTest/afterTest 手动调用，
 * 因 commonTest 无 JUnit Rule 支持）。
 *
 * 用法：
 * ```
 * private val dispatchers = TestDispatchers()
 *
 * @BeforeTest
 * fun setUp() = dispatchers.setUp()
 *
 * @AfterTest
 * fun tearDown() = dispatchers.tearDown()
 * ```
 *
 * 注意：测试体必须通过 `runTest(dispatchers.testDispatcher)` 与 Main 共享同一调度器，
 * 否则 ViewModel 协程挂起后无人驱动（调度器分裂）；且每个测试末尾必须 `viewModelScope.cancel()`，
 * 否则泄漏的协程会在 `tearDown → resetMain()` 之后恢复并污染后续测试。
 *
 * 每个测试必须使用全新的 dispatcher 实例（本类在 setUp() 中重建）：
 * StandardTestDispatcher 持有虚拟时钟与任务队列，跨测试复用会导致旧任务/
 * 旧虚拟时间污染新测试（官方文档明确禁止跨测试共享）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatchers {
    lateinit var testDispatcher: TestDispatcher
        private set
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    fun tearDown() {
        Dispatchers.resetMain()
    }
}
