package com.nobody.repoi.ui.collect.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import com.nobody.repoi.ui.common.ExpressiveFloatingToolbar
import com.nobody.repoi.ui.navigation.rememberBottomBarContentPadding
import androidx.compose.foundation.shape.CircleShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SelectionBottomBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onCancelSelection: () -> Unit,
    onCancelCollect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canSelectAll = selectedCount > 0 && selectedCount < totalCount
    val canCancel = selectedCount > 0

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = rememberBottomBarContentPadding(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        ExpressiveFloatingToolbar(modifier = Modifier.widthIn(max = 560.dp)) {
            SelectionActionButton(
                text = "全选",
                enabled = canSelectAll,
                onClick = onSelectAll
            )

            SelectionActionButton(
                text = "取消",
                enabled = canCancel,
                onClick = onCancelSelection
            )

            SelectionActionButton(
                text = "取消收藏($selectedCount)",
                enabled = canCancel,
                contentColor = MaterialTheme.colorScheme.error,
                onClick = onCancelCollect
            )
        }
    }
}

@Composable
private fun SelectionActionButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)

    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(
            contentColor = contentColor,
            disabledContentColor = disabledContentColor
        ),
        contentPadding = contentPadding
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
