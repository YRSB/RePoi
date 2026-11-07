package com.nobody.repoi.di

import coil3.ImageLoader
import coil3.PlatformContext
import com.nobody.repoi.platform.poiShelfAppContext

/** Coil 上下文（android）：应用 Context（即 `PlatformContext`）。 */
actual fun platformCoilContext(): PlatformContext = poiShelfAppContext()

/**
 * 平台图片组件（android）：`coil-gif` 未引入本模块依赖，保持空实现；
 * 位图默认解码 + SvgDecoder（common）已覆盖手办站图片格式。
 */
actual fun ImageLoader.Builder.poiShelfPlatformComponents() {
}
