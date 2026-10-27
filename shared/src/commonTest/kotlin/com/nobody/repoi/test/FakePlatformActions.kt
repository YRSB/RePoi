package com.nobody.repoi.test

import com.nobody.repoi.platform.PickedImage
import com.nobody.repoi.platform.PlatformActions

/**
 * commonTest 用的 [PlatformActions] 空实现（jvmTest 的同名 Fake 无法被 commonTest 引用，
 * 此处复制一份供 ViewModel 测试构造之用）。
 */
class FakePlatformActions(
    val toasts: MutableList<String> = mutableListOf()
) : PlatformActions {
    override fun showToast(message: String) {
        toasts.add(message)
    }
    override fun openUrl(url: String) = Unit
    override fun copyToClipboard(label: String, text: String) = Unit
    override suspend fun pickImage(): PickedImage? = null
    override suspend fun saveImage(bytes: ByteArray, displayName: String): Result<String> =
        Result.success("fake-path")
    override suspend fun shareTextFile(fileName: String, content: String) = Unit
    override fun appVersionName(): String = "test"
}
