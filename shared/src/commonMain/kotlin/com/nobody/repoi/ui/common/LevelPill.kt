package com.nobody.repoi.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobody.repoi.ui.theme.ExpressiveTokens

// 用户等级徽章（与动态页一致：按等级分档配色，10 级以上展示 MASTER）。
@Composable
fun LevelPill(
    level: Int,
    modifier: Modifier = Modifier
) {
    if (level <= 0) return
    val (text, containerColor, contentColor) = when {
        level >= 10 -> Triple("MASTER", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        level in 7..9 -> Triple("Lv.$level", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        level in 4..6 -> Triple("Lv.$level", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        else -> Triple("Lv.$level", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(
        shape = ExpressiveTokens.PillShape,
        color = containerColor,
        modifier = modifier
    ) {
        Text(
            text = text,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 8.5.sp,
            lineHeight = 10.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp)
        )
    }
}
