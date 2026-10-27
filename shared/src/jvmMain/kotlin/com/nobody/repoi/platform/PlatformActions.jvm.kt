package com.nobody.repoi.platform

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.nio.file.Files

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

    override suspend fun pickImage(): PickedImage? {
        return runCatching {
            val dialog = FileDialog(null as Frame?, "选择图片", FileDialog.LOAD).apply {
                filenameFilter = { _, name ->
                    name.endsWith(".jpg", ignoreCase = true) ||
                        name.endsWith(".jpeg", ignoreCase = true) ||
                        name.endsWith(".png", ignoreCase = true) ||
                        name.endsWith(".webp", ignoreCase = true) ||
                        name.endsWith(".gif", ignoreCase = true) ||
                        name.endsWith(".bmp", ignoreCase = true)
                }
                isVisible = true
            }
            val dir = dialog.directory ?: return null
            val file = dialog.file ?: return null
            val bytes = File(dir, file).readBytes()
            val mime = Files.probeContentType(File(dir, file).toPath())
                ?: guessMimeType(file)
            PickedImage(bytes = bytes, displayName = file, mimeType = mime)
        }.getOrNull()
    }

    override suspend fun saveImage(bytes: ByteArray, displayName: String): Result<String> =
        runCatching {
            val pictures = File(System.getProperty("user.home"), "Pictures/RePoi").apply { mkdirs() }
            val fileName = displayName.ifBlank { "repoi_${System.currentTimeMillis()}.jpg" }
            val file = pictures.resolve(fileName)
            file.writeBytes(bytes)
            file.absolutePath
        }

    override suspend fun shareTextFile(fileName: String, content: String) {
        val file = File(System.getProperty("java.io.tmpdir"), "repoi-share/$fileName").apply {
            parentFile?.mkdirs()
            writeText(content)
        }
        runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
            }
        }
    }

    override fun appVersionName(): String = "1.0.0"

    private fun guessMimeType(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> "image/jpeg"
    }
}
