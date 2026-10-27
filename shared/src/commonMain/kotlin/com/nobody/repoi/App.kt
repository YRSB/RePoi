package com.nobody.repoi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * Wave 0 占位 App：验证双端壳 + Koin 启动 + 主题渲染。
 * Wave 5 起接入 PoiShelfApp / AppNavDisplay。
 */
@Composable
@Preview
fun App() {
    MaterialTheme {
        Box(
            modifier = Modifier
                .safeContentPadding()
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("RePoi · Wave 0")
        }
    }
}
