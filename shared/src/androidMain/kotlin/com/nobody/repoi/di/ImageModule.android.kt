package com.nobody.repoi.di

import coil3.ImageLoader
import coil3.PlatformContext
import com.nobody.repoi.platform.poiShelfAppContext

actual fun platformCoilContext(): PlatformContext = poiShelfAppContext()

actual fun ImageLoader.Builder.poiShelfPlatformComponents() {
}
