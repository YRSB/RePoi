package com.nobody.poishelf.di

import coil3.ImageLoader
import coil3.PlatformContext

/** Coil 上下文（jvm）：`PlatformContext.INSTANCE`。 */
actual fun platformCoilContext(): PlatformContext = PlatformContext.INSTANCE

/** 平台图片组件（jvm）：no-op（SvgDecoder + Ktor 网络拉取 common 侧已装配）。 */
actual fun ImageLoader.Builder.poiShelfPlatformComponents() {
}
