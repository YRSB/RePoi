package com.nobody.repoi.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

/**
 * 文本分享器（S1）JVM 实际：
 * - URL 文本 → 系统默认浏览器打开；
 * - 普通文本 → AWT 系统剪贴板（调用方经 `onMessage` 提示“已复制链接”）。
 */
@Composable
actual fun rememberTextSharing(): TextSharing = remember {
    object : TextSharing {
        override fun share(text: String) {
            runCatching {
                val trimmed = text.trim()
                if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    openUrl(trimmed)
                } else {
                    Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(StringSelection(text), null)
                }
            }
        }
    }
}

/** 打开外部链接（S2）：`Desktop.browse`，桌面环境不支持时返回 false。 */
actual fun openUrl(url: String): Boolean = runCatching {
    val normalized = url.trim().let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
    }
    if (!Desktop.isDesktopSupported()) return@runCatching false
    val desktop = Desktop.getDesktop()
    if (!desktop.isSupported(Desktop.Action.BROWSE)) return@runCatching false
    desktop.browse(URI(normalized))
    true
}.getOrDefault(false)
