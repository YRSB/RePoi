package com.nobody.repoi.ui.collect

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

        runOnIdle { mounted.value = false }
        runOnIdle { assertEquals(1, calls) }
        runOnIdle { mounted.value = true }

        runOnIdle { assertEquals(2, calls, "返回收藏页必须再派发一次 OnResume") }
    }
}
