package com.nobody.poishelf.platform

import androidx.compose.runtime.Composable

/**
 * 文本分享器（S1）。
 */
interface TextSharing {
    fun share(text: String)
}

@Composable
expect fun rememberTextSharing(): TextSharing

/**
 * 打开外部链接（S2）。返回是否成功调起。
 */
expect fun openUrl(url: String): Boolean
