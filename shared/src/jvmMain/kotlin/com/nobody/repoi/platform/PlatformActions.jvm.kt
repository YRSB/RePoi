package com.nobody.repoi.platform

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

class JvmPlatformActions : PlatformActions {

    override fun showToast(message: String) {
        // 桌面端无系统 Toast：落 stderr，Wave 14 可换桌面通知。
        System.err.println("[toast] $message")
    }

    override fun openUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }

    override fun copyToClipboard(label: String, text: String) {
        runCatching {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(text), null)
        }
    }

    // TODO(Wave 1): AWT FileDialog 实现。
    override suspend fun pickImage(): PickedImage? = null

    // TODO(Wave 1): ~/Pictures/RePoi 落盘实现。
    override suspend fun saveImage(bytes: ByteArray, displayName: String): Result<String> =
        Result.failure(UnsupportedOperationException("saveImage lands in Wave 1"))

    // TODO(Wave 1): Desktop.open fallback 实现。
    override suspend fun shareTextFile(fileName: String, content: String): Unit =
        throw UnsupportedOperationException("shareTextFile lands in Wave 1")

    override fun appVersionName(): String = "1.0.0"
}
