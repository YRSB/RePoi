package com.nobody.repoi.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

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
