package com.nobody.poishelf.ui.common.comment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nobody.poishelf.ui.comment.CommentDetailNavParam

/**
 * M3 评论详情屏占位（M5 `CommentDetailScreen.kt:56` 完整实现移植前占位）。
 *
 * 背景：`AdaptiveDetailCommentPane` 的楼中楼覆盖层引用 M5 F40 评论详情屏；
 * M3 仅保留调用点签名编译通过，展示降级为占位文本。
 * M5 落地时删除本文件、改由 `ui.comment` 完整实现 import（TODO(M5)）。
 */
@Composable
fun CommentDetailScreen(
    navParam: CommentDetailNavParam,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "评论详情（#${navParam.commentId}，完整版 M5 落地）",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
