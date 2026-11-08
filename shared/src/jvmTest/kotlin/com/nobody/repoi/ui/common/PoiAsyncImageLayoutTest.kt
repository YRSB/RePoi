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

/**
 * [PoiAsyncImage] 的**真实组件**约束语义测试（Compose UI，桌面 JVM）。
 *
 * 只验证「比例是否真的参与布局」，不验证图片像素：路径留空时组件走官方缺省图分支，
 * 但 `Modifier.aspectRatio` 与外部约束的交互和真实加载时完全一致。
 *
 * 这组用例锁住一个真实踩过的坑：`adaptiveRatio = true` 依赖至少一轴自由伸缩，
 * 若父容器把宽高都锁死，`aspectRatio` 会静默失效——编译、纯 JVM 单测、旧 CI 全绿也发现不了。
 * 文本静态检查无法解析父容器约束（实测全是假阳性），只有真正布局一次才能验证。
 *
 * **零网络**：测试前把 Coil 单例换成不带任何 fetcher 的 `ImageLoader`，
 * 请求会立即失败（组件落到破损图标分支），因此不引入 `coil-test` 依赖、不访问 CDN。
 *
 * 各用例刻意使用**不同的** `purpose`，使内部比例缓存键（含用途桶）互不相同，避免互相污染。
 */
@OptIn(ExperimentalTestApi::class)
class PoiAsyncImageLayoutTest {

    @BeforeTest
    fun installOfflineImageLoader() {
        // 空组件注册表 = 无网络 fetcher、无解码器 → 请求快速失败，绝不出网
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(PlatformContext.INSTANCE).build()
        )
    }

    @AfterTest
    fun clearAspectRatioCache() {
        // 比例缓存为全局单例，测试之间必须清空，否则用例互相影响
        com.nobody.repoi.util.ImageAspectRatioCache.clear()
    }

    /** 比例 2:1，宽度由外部撑满 → 高度应为宽度的一半。 */
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

    /** 高度钉死 120dp、宽度不定 → 比例 2:1 时宽度应为 240dp。 */
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

    /** `fallbackRatio` 必须真正参与首帧占位：0.5 → 高是宽的两倍。 */
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

    /** `adaptiveRatio = false` 时不应追加比例约束：尺寸完全由外部决定。 */
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
