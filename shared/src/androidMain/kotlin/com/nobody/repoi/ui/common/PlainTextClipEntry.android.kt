package com.nobody.repoi.ui.common

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

actual fun plainTextClipEntry(text: String): ClipEntry =
    ClipEntry(ClipData.newPlainText("poishelf", text))

actual fun ClipEntry.plainText(): String? = clipData
    .takeIf { it.itemCount > 0 }
    ?.getItemAt(0)
    ?.text
    ?.toString()
    ?.trim()
    ?.ifBlank { null }
