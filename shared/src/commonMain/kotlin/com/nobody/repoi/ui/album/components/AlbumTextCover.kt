package com.nobody.repoi.ui.album.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 相册纯文本封面组件 (titleCover)。
 *
 * 当相册无封面（cover 为空或无图）时，居中高对比展示相册标题。
 * 遵循 Material 3 Expressive 规范，默认采用 [MaterialTheme.colorScheme.primaryContainer] 动态容器色
 * 与 [MaterialTheme.colorScheme.onPrimaryContainer] 文字色，跟随系统 Monet 动态主题自动着色。
 */
@Composable
fun AlbumTextCover(
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.small,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
    maxLines: Int = 4,
    contentAlignment: Alignment = Alignment.Center
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = contentAlignment
    ) {
        Text(
            text = title.ifBlank { "相册" },
            style = textStyle,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}