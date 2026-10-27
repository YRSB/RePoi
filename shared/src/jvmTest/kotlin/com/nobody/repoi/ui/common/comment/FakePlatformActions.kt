package com.nobody.repoi.ui.common.comment

import com.nobody.repoi.platform.PickedImage
import com.nobody.repoi.platform.PlatformActions
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * jvmTest 用的 [PlatformActions] 空实现：UI 测试只渲染不触发平台能力，
 * toast/剪贴板等调用安全吞掉，版本号返回固定测试串。
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

/**
 * 为单个 UI 测试启动仅含 [PlatformActions] 的最小 Koin 图。
 * 调用方在测试末尾（或 `@AfterTest`）执行 [stopTestKoin]。
 */
fun startTestKoin(actions: FakePlatformActions = FakePlatformActions()) {
    runCatching { stopKoin() }
    startKoin {
        modules(
            module {
                single<PlatformActions> { actions }
            }
        )
    }
}

fun stopTestKoin() {
    runCatching { stopKoin() }
}
