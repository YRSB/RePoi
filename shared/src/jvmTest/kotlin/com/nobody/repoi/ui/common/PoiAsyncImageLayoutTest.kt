package com.nobody.repoi.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, coil3.annotation.DelicateCoilApi::class)
class PoiAsyncImageLayoutTest {

    @BeforeTest
    fun installOfflineImageLoader() {

        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(PlatformContext.INSTANCE).build()
        )
    }

    @AfterTest
    fun clearAspectRatioCache() {

        com.nobody.repoi.util.ImageAspectRatioCache.clear()
    }

    @Test
    fun adaptiveRatio_withWidthFixed_derivesHeightFromRatio() = runComposeUiTest {
        setContent {
            PoiAsyncImage(
                path = null,
                category = MediaCategory.HOBBY,
                purpose = ImagePurpose.CARD_STANDARD,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG),
                adaptiveRatio = true,
                fallbackRatio = 2f
            )
        }

        val bounds = onNodeWithTag(TAG).getUnclippedBoundsInRoot()
        val width = bounds.width.value
        val height = bounds.height.value
        assertTrue(width > 0f, "宽度应有确定值，实际 $width")
        assertEquals(width / 2f, height, 1f, "比例 2:1 时高度应为宽度的一半（实际 w=$width h=$height）")
    }

    @Test
    fun adaptiveRatio_withHeightFixed_derivesWidthFromRatio() = runComposeUiTest {
        setContent {
            PoiAsyncImage(
                path = null,
                category = MediaCategory.HOBBY,
                purpose = ImagePurpose.THUMBNAIL,
                contentDescription = null,
                modifier = Modifier
                    .height(120.dp)
                    .testTag(TAG),
                adaptiveRatio = true,
                fallbackRatio = 2f
            )
        }

        val bounds = onNodeWithTag(TAG).getUnclippedBoundsInRoot()
        assertEquals(120f, bounds.height.value, 1f, "高度应被外部约束钉死为 120dp")
        assertEquals(240f, bounds.width.value, 2f, "比例 2:1 时宽度应为高度的两倍")
    }

    @Test
    fun fallbackRatio_drivesInitialPlaceholder() = runComposeUiTest {
        setContent {
            PoiAsyncImage(
                path = null,
                category = MediaCategory.HOBBY,
                purpose = ImagePurpose.ADAPTIVE_GRID,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG),
                adaptiveRatio = true,
                fallbackRatio = 0.5f
            )
        }

        val bounds = onNodeWithTag(TAG).getUnclippedBoundsInRoot()
        val width = bounds.width.value
        val height = bounds.height.value
        assertEquals(width * 2f, height, 1f, "比例 0.5 时高度应为宽度的两倍（实际 w=$width h=$height）")
    }

    @Test
    fun adaptiveRatioDisabled_respectsExternalSizeOnly() = runComposeUiTest {
        setContent {
            Box(modifier = Modifier.size(120.dp)) {
                PoiAsyncImage(
                    path = null,
                    category = MediaCategory.HOBBY,
                    purpose = ImagePurpose.FULLSCREEN_RAW,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .testTag(TAG),
                    adaptiveRatio = false
                )
            }
        }

        val bounds = onNodeWithTag(TAG).getUnclippedBoundsInRoot()
        assertEquals(60f, bounds.width.value, 1f)
        assertEquals(60f, bounds.height.value, 1f)
    }

    private companion object {
        const val TAG = "poi_async_image_probe"
    }
}
