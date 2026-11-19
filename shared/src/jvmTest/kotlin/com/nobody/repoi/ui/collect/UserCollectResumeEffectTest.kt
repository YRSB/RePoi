package com.nobody.repoi.ui.collect

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 恢复感知触发器的组合生命周期回归。
 *
 * 二级页覆盖会让收藏页退出组合，出栈返回后重新进入。若 UI 侧用普通 `remember` 计数跳过首次
 * ON_RESUME，退出组合时计数归零，返回时的 ON_RESUME 会被门槛吞掉，列表因此保持陈旧。
 * 幂等改由 ViewModel 的代数游标保证，因此每次进入组合都必须派发。
 */
@OptIn(ExperimentalTestApi::class)
class UserCollectResumeEffectTest {
    @Test
    fun recompositionAfterDetailPop_deliversOnResume() = runComposeUiTest {
        var calls = 0
        val mounted = mutableStateOf(true)

        setContent {
            if (mounted.value) {
                CollectResumeEffect(onResume = { calls++ })
            }
        }

        runOnIdle { assertEquals(1, calls, "首次进入也派发，幂等由 ViewModel 的代数游标保证") }

        // 模拟二级页覆盖 → 退出组合 → 出栈返回 → 重新进入组合
        runOnIdle { mounted.value = false }
        runOnIdle { assertEquals(1, calls) }
        runOnIdle { mounted.value = true }

        runOnIdle { assertEquals(2, calls, "返回收藏页必须再派发一次 OnResume") }
    }
}
