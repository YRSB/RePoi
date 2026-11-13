package com.nobody.repoi.ui.common.loading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Material 3 Expressive 统一全页面/局部区块加载指示器（ADR-0018）。
 *
 * 替代旧版骨架屏，居中承载官方 [LoadingIndicator]。
 * 默认撑满父容器居中；亦可由调用方传入外部 Modifier（如局部子区块传入 `Modifier.fillMaxWidth().padding(32.dp)`）。
 */
@Composable
fun PoiLoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = if (modifier === Modifier) Modifier.fillMaxSize() else modifier,
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator()
    }
}
