package com.nobody.repoi.ui.common

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

actual fun plainTextClipEntry(text: String): ClipEntry =
    ClipEntry(StringSelection(text))

/**
 * Desktop 纯文本提取：从 AWT `Transferable` 取字符串并 trim。
 */
@OptIn(ExperimentalComposeUiApi::class)
actual fun ClipEntry.plainText(): String? = runCatching {
    val transferable = nativeClipEntry as? java.awt.datatransfer.Transferable ?: return null
    if (!transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) return null
    (transferable.getTransferData(DataFlavor.stringFlavor) as? String)?.trim()?.ifBlank { null }
}.getOrNull()
