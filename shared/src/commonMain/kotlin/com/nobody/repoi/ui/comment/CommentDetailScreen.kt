package com.nobody.repoi.ui.comment

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 评论详情页占位（Wave 11 迁移真身）。
 *
 * 签名与老项目 CommentDetailScreen(navParam, onBack, modifier) 一致（去掉 Hilt viewModel 默认参），
 * 供宽屏副栏 SupportingCommentPane 引用编译。
 */
@Composable
fun CommentDetailScreen(
    navParam: CommentDetailNavParam,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    TODO("Wave 11: migrate CommentDetailScreen")
}
