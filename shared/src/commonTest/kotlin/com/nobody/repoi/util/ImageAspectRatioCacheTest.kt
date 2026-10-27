package com.nobody.repoi.util

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.BeforeTest
import kotlin.test.Test

class ImageAspectRatioCacheTest {

    @BeforeTest
    fun setUp() {
        ImageAspectRatioCache.clear()
    }

    @Test
    fun getAndSet_storesAndRetrievesRatio() {
        assertNull(ImageAspectRatioCache["https://rfx.hpoi.net/gk/cover/sr/1.jpg"])

        ImageAspectRatioCache["https://rfx.hpoi.net/gk/cover/sr/1.jpg"] = 0.75f
        assertEquals(0.75f, ImageAspectRatioCache["https://rfx.hpoi.net/gk/cover/sr/1.jpg"] ?: 0f, 0.0001f)
    }

    @Test
    fun clear_removesAllEntries() {
        ImageAspectRatioCache["https://rfx.hpoi.net/gk/cover/sr/1.jpg"] = 0.75f
        ImageAspectRatioCache["https://rfx.hpoi.net/gk/cover/sr/2.jpg"] = 1.2f

        ImageAspectRatioCache.clear()

        assertNull(ImageAspectRatioCache["https://rfx.hpoi.net/gk/cover/sr/1.jpg"])
        assertNull(ImageAspectRatioCache["https://rfx.hpoi.net/gk/cover/sr/2.jpg"])
    }

    @Test
    fun capacityLimit_evictsEldestWhenExceedingMaxEntries() {
        for (i in 1..505) {
            ImageAspectRatioCache["key_$i"] = i.toFloat()
        }

        assertEquals(500, ImageAspectRatioCache.size())
        assertNull(ImageAspectRatioCache["key_1"])
        assertNull(ImageAspectRatioCache["key_5"])
        assertEquals(6.0f, ImageAspectRatioCache["key_6"] ?: 0f, 0.0001f)
        assertEquals(505.0f, ImageAspectRatioCache["key_505"] ?: 0f, 0.0001f)
    }
}
