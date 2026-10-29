package com.nobody.poishelf.ui.resale.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobody.poishelf.data.remote.model.ResaleSearchItem
import com.nobody.poishelf.platform.LocalAppMessages
import com.nobody.poishelf.ui.common.plainTextClipEntry
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import kotlinx.coroutines.launch

/**
 * 外部链接跳转与剪贴板统一处理工具。
 *
 * KMP 移植说明：原生 `Context.getSystemService(CLIPBOARD_SERVICE)` +
 * `Intent(ACTION_VIEW)` 双轨改为 Compose `LocalClipboard` +
 * `LocalUriHandler`；调用方改为 composable 内 `rememberExternalLinkOpener()`。
 */
@Composable
fun rememberExternalLinkOpener(): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val messages = LocalAppMessages.current
    return remember(clipboard, uriHandler) { { link: String ->
        if (link.isNotBlank()) {
            scope.launch { clipboard.setClipEntry(plainTextClipEntry(link)) }
            val url = if (!link.startsWith("http://") && !link.startsWith("https://")) {
                "https://$link"
            } else {
                link
            }
            try {
                uriHandler.openUri(url)
                messages.show("已复制淘口令/链接并正在跳转")
            } catch (_: Exception) {
                messages.show("淘口令/链接已复制到剪贴板")
            }
        }
    } }
}

/**
 * 二手卡片三点更多操作弹窗（截图 2 规范）：
 * - 同款二手
 * - 外部链接（存在外链时展示，否则隐去）
 * - 举报
 * - 取消
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResaleMoreBottomSheet(
    item: ResaleSearchItem,
    onDismiss: () -> Unit,
    onSelectSameResale: (ResaleSearchItem) -> Unit,
    onSelectReport: (ResaleSearchItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val openExternalLink = rememberExternalLinkOpener()
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // 1. 同款二手
            ListItem(
                headlineContent = { Text("同款二手") },
                leadingContent = {
                    Icon(imageVector = Icons.Filled.ShoppingBag, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    onSelectSameResale(item)
                }
            )

            // 2. 外部链接（仅在外链存在时展示）
            if (item.hasExternalLink) {
                val link = item.link.orEmpty()
                ListItem(
                    headlineContent = { Text("外部链接") },
                    supportingContent = {
                        Text(
                            text = link,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingContent = {
                        Icon(imageVector = Icons.Filled.Link, contentDescription = null)
                    },
                    modifier = Modifier.clickable {
                        onDismiss()
                        openExternalLink(link)
                    }
                )
            }

            // 3. 举报
            ListItem(
                headlineContent = { Text("举报") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Report,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    onSelectReport(item)
                }
            )

            // 4. 取消
            ListItem(
                headlineContent = { Text("取消", color = MaterialTheme.colorScheme.outline) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Filled.Cancel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                },
                modifier = Modifier.clickable {
                    onDismiss()
                }
            )
        }
    }
}

/**
 * 二手举报理由输入对话框。
 */
@Composable
fun ResaleReportDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("举报二手交易", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "请填写举报理由（违规售卖、虚假信息等）：",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = { Text("输入详细原因...") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(reason.trim()) },
                enabled = !isSubmitting && reason.isNotBlank()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("提交举报")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("取消")
            }
        }
    )
}

/**
 * 具有下拉展开指示箭头的 FilterChip，对齐项目设计系统令牌。
 */
@Composable
fun FilterDropdownChip(
    label: String,
    isSelected: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "DropdownArrowRotation"
    )

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        shape = ExpressiveTokens.ChipShape,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = null,
        modifier = modifier
    )
}
