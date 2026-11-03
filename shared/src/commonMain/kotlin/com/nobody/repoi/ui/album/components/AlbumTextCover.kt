package com.nobody.repoi.ui.album.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.nobody.repoi.ui.theme.ExpressiveTokens

/**
 * Hpoi 官方原版相册纯文本封面组件 (titleCover)。
 *
 * 官方策略：相册在无封面（cover 为空或为 none.jpg）时，从不展示吉祥物哭泣图（none.jpg），
 * 而是展示 17:24 黄金比例的浅天青色纯文本卡片，居中高对比展示相册标题。
 *
 * 官方标准参数（来自 net.hpoi.utils.extend.f.c / f.g）：
 * - 亮色底色：#D1EFFF（官方 bgTextCover）
 * - 亮色文字色：#1A354D / #333333（官方 textMainNoNight）
 * - 暗色底色适配：#16324F（深蓝灰石底），文字色：#D1EFFF
 * - 对齐：居中对齐 (Gravity.CENTER)
 */
@Composable
fun AlbumTextCover(
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = ExpressiveTokens.SmallCardShape,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
    maxLines: Int = 4,
    contentAlignment: Alignment = Alignment.Center
) {
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) Color(0xFF16324F) else Color(0xFFD1EFFF)
    val contentColor = if (isDark) Color(0xFFD1EFFF) else Color(0xFF1A354D)

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
