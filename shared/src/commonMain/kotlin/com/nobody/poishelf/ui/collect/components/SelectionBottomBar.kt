package com.nobody.poishelf.ui.collect.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import com.nobody.poishelf.ui.common.ExpressiveFloatingToolbar
import com.nobody.poishelf.ui.theme.ExpressiveTokens

// ── 多选模式底部操作条 ──────────────────────────────────────

/**
 * 多选模式底部悬浮操作条（对标原版周边页/相册页 `cancel_layout`：全选 / 取消 / 取消收藏N）。
 *
 * 周边页与相册页共用本组件。置灰规则：
 * - 「全选」在未选中任何条目或已全选时置灰；
 * - 「取消」「取消收藏(N)」在选中数为 0 时置灰。
 *
 * 遵循 M3 Expressive 悬浮胶囊规范：在居中容器内限制最大宽度（560dp），保证大屏/横屏下不被无限横向拉伸。
 * 挂载到 `Scaffold(bottomBar = { ... })` 时本组件已内置 `navigationBarsPadding()`；
 * 若改为 Box 叠加方式挂在内容之上，请自行移除该修饰以免重复内边距。
 *
 * @param selectedCount 当前选中条目数（多选集合存**外层收藏节点 id**，勿用 actualAlbumId）
 * @param totalCount 当前列表可见条目总数，用于判断是否已全选
 * @param onSelectAll 全选
 * @param onCancelSelection 取消多选
 * @param onCancelCollect 批量取消收藏，调用方负责二次确认与提交
 */
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
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        ExpressiveFloatingToolbar(modifier = Modifier.widthIn(max = 560.dp)) {
            // 选中数徽标
            Badge(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(
                    text = "$selectedCount",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            SelectionActionPill(
                text = "全选",
                enabled = canSelectAll,
                onClick = onSelectAll
            )

            SelectionActionPill(
                text = "取消",
                enabled = canCancel,
                onClick = onCancelSelection
            )

            SelectionActionPill(
                text = "取消收藏($selectedCount)",
                enabled = canCancel,
                prominent = true,
                onClick = onCancelCollect
            )
        }
    }
}

/**
 * 底部操作条内的胶囊动作按钮，置灰时不可点击且前景色降透明度。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionActionPill(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false
) {
    val pillColor = when {
        !enabled -> Color.Transparent
        prominent -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val pillContentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        prominent -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 40.dp),
        shape = ExpressiveTokens.PillShape,
        color = pillColor,
        contentColor = pillContentColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = pillContentColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}