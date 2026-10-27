package com.nobody.repoi.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast

class AndroidPlatformActions(
    private val context: Context
) : PlatformActions {

    override fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    override fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    // TODO(Wave 1): MainActivity 注册 PickVisualMedia launcher 后接入。
    override suspend fun pickImage(): PickedImage? = null

    // TODO(Wave 1): MediaStore 落盘实现。
    override suspend fun saveImage(bytes: ByteArray, displayName: String): Result<String> =
        Result.failure(UnsupportedOperationException("saveImage lands in Wave 1"))

    // TODO(Wave 1): FileProvider 分享实现。
    override suspend fun shareTextFile(fileName: String, content: String): Unit =
        throw UnsupportedOperationException("shareTextFile lands in Wave 1")

    override fun appVersionName(): String {
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "unknown"
        }.getOrDefault("unknown")
    }
}
