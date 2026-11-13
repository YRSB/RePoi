package com.nobody.repoi.ui.common

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

actual fun plainTextClipEntry(text: String): ClipEntry =
    ClipEntry(ClipData.newPlainText("poishelf", text))

/**
 * Android 纯文本提取：取 `ClipData` 首条文本并 trim。
 */
actual fun ClipEntry.plainText(): String? = clipData
    .takeIf { it.itemCount > 0 }
    ?.getItemAt(0)
    ?.text
    ?.toString()
    ?.trim()
    ?.ifBlank { null }
