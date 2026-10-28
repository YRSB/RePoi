package com.nobody.poishelf.ui.exhobby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nobody.poishelf.data.remote.model.ExHobbyImageItem

/**
 * M3 图包预览区占位（M5 `ExHobbyPreviewSection.kt:51` 完整实现移植前占位）。
 *
 * 背景：`AlbumDetailScreen:543` / `HobbyDetailScreen:497` 的扩展图库横滑流
 * 引用 M5 F42 图包区组件；M3 仅保留调用点签名编译通过，展示降级为计数文本。
 * M5 落地时删除本文件、改由 `ui.exhobby` 完整实现 import（TODO(M5)）。
 */
@Composable
fun ExHobbyPreviewSection(
    pics: List<ExHobbyImageItem>,
    onPicClick: (index: Int) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "图包预览（${pics.size} 张，完整版 M5 落地）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
