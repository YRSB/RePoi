package com.nobody.repoi.di

import coil3.ImageLoader
import coil3.PlatformContext

actual fun platformCoilContext(): PlatformContext = PlatformContext.INSTANCE

actual fun ImageLoader.Builder.poiShelfPlatformComponents() {
}
