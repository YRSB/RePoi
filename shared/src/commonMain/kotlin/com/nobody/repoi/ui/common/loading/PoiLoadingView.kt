package com.nobody.repoi.ui.common.loading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

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
