package com.nobody.repoi.platform

/**
 * 跨平台能力接口。老项目中散落的 `Toast`/`LocalContext`/`startActivity`/`Intent`/
 * 剪贴板/`MediaStore`/`FileProvider`/ActivityResult 调用点全部收敛到此接口；
 * UI 经 Koin `inject()` 获取，不再直触平台 API。
 *
 * Wave 0 定稿接口 + 最小实现（toast/openUrl/clipboard/version）；
 * 图片选择/保存/分享链路在 Wave 1 随 data/local 实现。
 */
interface PlatformActions {
    fun showToast(message: String)
    fun openUrl(url: String)
    fun copyToClipboard(label: String, text: String)
    suspend fun pickImage(): PickedImage?
    suspend fun saveImage(bytes: ByteArray, displayName: String): Result<String>
    suspend fun shareTextFile(fileName: String, content: String)
    fun shareText(title: String, text: String)
    fun appVersionName(): String
}

/**
 * 图片选择结果（跨平台 bytes 形态）。
 */
data class PickedImage(
    val bytes: ByteArray,
    val displayName: String,
    val mimeType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PickedImage) return false
        return displayName == other.displayName &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
