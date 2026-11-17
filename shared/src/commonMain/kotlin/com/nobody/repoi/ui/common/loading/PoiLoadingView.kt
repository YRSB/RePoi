package com.nobody.repoi.ui.common.loading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Material 3 Expressive 统一全页面/局部区块加载指示器（ADR-0018）。
 *
 * 替代旧版骨架屏，居中承载官方 [LoadingIndicator]。
 * 默认撑满父容器居中；亦可由调用方传入外部 Modifier（如局部子区块传入 `Modifier.fillMaxWidth().padding(32.dp)`）。
 *
 * @param indicatorSize 指示器自身的尺寸；`null` 表示沿用 [LoadingIndicator] 默认尺寸。
 *   分页页脚等紧凑场景传 `28.dp`（ADR-0018 的 28dp 紧凑指示器），确保与外层 `Modifier.size()` 一致。
 */
@Composable
fun PoiLoadingView(
    modifier: Modifier = Modifier,
    indicatorSize: Dp? = null
) {
    Box(
        modifier = if (modifier === Modifier) Modifier.fillMaxSize() else modifier,
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(
            modifier = if (indicatorSize != null) Modifier.size(indicatorSize) else Modifier
        )
    }
}
