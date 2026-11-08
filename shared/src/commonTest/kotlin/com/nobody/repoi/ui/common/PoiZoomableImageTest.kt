package com.nobody.repoi.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [PoiZoomableImage] 的数学内核单测（纯 JVM，不触碰 Compose UI）。
 *
 * 覆盖三块可在无设备环境下验证的逻辑：滚轮增量→倍率、捏合阻尼/上下界、
 * 指针锚点换算与平移边界钳制。期望值按公式独立手算，不复用实现本身。
 */
class PoiZoomableImageTest {

    // ── 滚轮增量 → 缩放倍率 ─────────────────────────────────

    @Test
    fun wheelZoomFactor_zeroDelta_keepsScale() {
        assertEquals(1f, wheelZoomFactor(0f))
    }

    @Test
    fun wheelZoomFactor_wheelUp_zoomsIn() {
        // 向上滚 delta = -1 → 1 - (-1 * 0.12) = 1.12
        assertEquals(1.12f, wheelZoomFactor(-1f), 0.0001f)
    }

    @Test
    fun wheelZoomFactor_wheelDown_zoomsOut() {
        // 向下滚 delta = +1 → 1 - 0.12 = 0.88
        assertEquals(0.88f, wheelZoomFactor(1f), 0.0001f)
    }

    @Test
    fun wheelZoomFactor_hugeUpDelta_clampsToMaxFactor() {
        assertEquals(POI_WHEEL_ZOOM_MAX_FACTOR, wheelZoomFactor(-100f))
        assertEquals(POI_WHEEL_ZOOM_MAX_FACTOR, wheelZoomFactor(-3f))
    }

    @Test
    fun wheelZoomFactor_hugeDownDelta_clampsToMinFactor() {
        assertEquals(POI_WHEEL_ZOOM_MIN_FACTOR, wheelZoomFactor(100f))
        assertEquals(POI_WHEEL_ZOOM_MIN_FACTOR, wheelZoomFactor(2f))
    }

    @Test
    fun wheelZoomFactor_trackpadPartialDelta_staysInsideRange() {
        val factor = wheelZoomFactor(-0.5f)
        assertTrue(factor > 1f, "向上滚必须放大，实际 $factor")
        assertTrue(factor < POI_WHEEL_ZOOM_MAX_FACTOR, "未超单格上限，实际 $factor")
    }

    @Test
    fun wheelZoomFactor_nanAndInfinity_areGuarded() {
        assertEquals(1f, wheelZoomFactor(Float.NaN))
        assertEquals(POI_WHEEL_ZOOM_MIN_FACTOR, wheelZoomFactor(Float.POSITIVE_INFINITY))
        assertEquals(POI_WHEEL_ZOOM_MAX_FACTOR, wheelZoomFactor(Float.NEGATIVE_INFINITY))
    }

    // ── 捏合缩放：夹取与阻尼 ────────────────────────────────

    @Test
    fun dampedScale_withinBounds_returnsRaw() {
        assertEquals(2f, dampedScale(raw = 2f, current = 1f, minScale = 1f, maxScale = 4f))
    }

    @Test
    fun dampedScale_belowMin_clampsToMin() {
        assertEquals(1f, dampedScale(raw = 0.5f, current = 2f, minScale = 1f, maxScale = 4f))
    }

    @Test
    fun dampedScale_aboveMax_dampsByQuarter() {
        // 4 + (8 - 4) * 0.25 = 5，即只走出溢出量的四分之一
        assertEquals(5f, dampedScale(raw = 8f, current = 4f, minScale = 1f, maxScale = 4f))
    }

    @Test
    fun dampedScale_aboveMaxFromMin_keepsRubberBandInsideReasonableRange() {
        // 1 + (9 * 0.25) = 3.25：超上界时不会一跃到 10 倍
        assertEquals(3.25f, dampedScale(raw = 10f, current = 1f, minScale = 1f, maxScale = 4f))
    }

    @Test
    fun dampedScale_nanInputs_areGuarded() {
        assertEquals(2f, dampedScale(raw = Float.NaN, current = 2f, minScale = 1f, maxScale = 4f))
        assertEquals(1f, dampedScale(raw = 2f, current = Float.NaN, minScale = 1f, maxScale = 4f))
    }

    // ── 指针锚点换算 ────────────────────────────────────────

    @Test
    fun anchorAdjustedOffset_factorOne_keepsOffset() {
        val offset = Offset(12f, -8f)
        assertEquals(offset, anchorAdjustedOffset(offset, factor = 1f, anchor = Offset(40f, 40f)))
    }

    @Test
    fun anchorAdjustedOffset_zoomingAroundRightAnchor_movesContentLeft() {
        // t' = 0 * 2 + (1 - 2) * 50 = -50，锚点右侧的画面点保持在指针下
        assertEquals(
            Offset(-50f, 0f),
            anchorAdjustedOffset(Offset.Zero, factor = 2f, anchor = Offset(50f, 0f))
        )
    }

    @Test
    fun anchorAdjustedOffset_existingOffset_scalesThenShifts() {
        // t' = (10, 20) * 2 + (1 - 2) * (50, 100) = (-30, -60)
        assertEquals(
            Offset(-30f, -60f),
            anchorAdjustedOffset(Offset(10f, 20f), factor = 2f, anchor = Offset(50f, 100f))
        )
    }

    @Test
    fun anchorAdjustedOffset_nanFactor_keepsOffset() {
        val offset = Offset(3f, 4f)
        assertEquals(offset, anchorAdjustedOffset(offset, factor = Float.NaN, anchor = Offset.Zero))
    }

    // ── 平移边界钳制 ────────────────────────────────────────

    @Test
    fun clampPan_portraitImageAtTwoTimes_clampsToOverflowHalf() {
        val viewport = Size(1080f, 1920f)
        val portrait = Size(1080f, 1920f)
        // 2 倍后显示 2160x3840，单轴可移动量 = (显示尺寸 - 视口) / 2 = (540, 960)
        assertEquals(
            Offset(540f, -960f),
            clampPan(Offset(1000f, -2000f), scale = 2f, viewport = viewport, contentSize = portrait)
        )
    }

    @Test
    fun clampPan_withinBounds_keepsOffset() {
        val viewport = Size(1080f, 1920f)
        val portrait = Size(1080f, 1920f)
        assertEquals(
            Offset(-100f, 200f),
            clampPan(Offset(-100f, 200f), scale = 2f, viewport = viewport, contentSize = portrait)
        )
    }

    @Test
    fun clampPan_landscapeLetterboxed_locksVerticalPan() {
        val viewport = Size(1080f, 1920f)
        val landscape = Size(1920f, 1080f)
        // Fit 后显示 1080x607.5，2 倍后 2160x1215：横向可移动 (2160-1080)/2 = 540，纵向仍不足一屏
        val clamped = clampPan(
            Offset(100f, 400f),
            scale = 2f,
            viewport = viewport,
            contentSize = landscape
        )
        assertEquals(0f, clamped.y)
        assertEquals(100f, clamped.x)
    }

    @Test
    fun clampPan_landscapeImage_clampsBothAxesToOverflowHalf() {
        val viewport = Size(1080f, 1920f)
        // 4:3 横图 Fit 后 1080x810，3 倍后 3240x2430：横向可移动 (3240-1080)/2 = 1080
        val landscape = Size(1600f, 1200f)
        val clamped = clampPan(
            Offset(5000f, 5000f),
            scale = 3f,
            viewport = viewport,
            contentSize = landscape
        )
        assertEquals(1080f, clamped.x)
        assertEquals((2430f - 1920f) / 2f, clamped.y)
    }

    @Test
    fun clampPan_unzoomedCenteredImage_returnsZero() {
        val viewport = Size(1080f, 1920f)
        val portrait = Size(1080f, 1920f)
        assertEquals(
            Offset.Zero,
            clampPan(Offset(30f, 30f), scale = 1f, viewport = viewport, contentSize = portrait)
        )
    }

    @Test
    fun clampPan_degenerateInputs_doNotCrash() {
        val viewport = Size(1080f, 1920f)
        assertEquals(
            Offset.Zero,
            clampPan(Offset(1f, 1f), scale = 2f, viewport = viewport, contentSize = Size.Zero)
        )
        assertEquals(
            Offset.Zero,
            clampPan(Offset(1f, 1f), scale = 2f, viewport = Size.Zero, contentSize = Size(10f, 10f))
        )
        assertEquals(
            Offset.Zero,
            clampPan(Offset(Float.NaN, 0f), scale = 2f, viewport = viewport, contentSize = Size(10f, 10f))
        )
    }

    // ── 超长图判定（纵横比） ────────────────────────────────

    @Test
    fun isLongImageRatio_tallImage_isDetected() {
        // 宽 100、高 500 → 高宽比 5 > 3，命中超长图
        assertTrue(isLongImageRatio(width = 100f, height = 500f))
    }

    @Test
    fun isLongImageRatio_wideImage_isNotDetected() {
        // 宽 500、高 100 → 高宽比 0.2，绝不命中（旧「高度超过上限倍数」判据在这里会误报为空判）
        assertFalse(isLongImageRatio(width = 500f, height = 100f))
    }

    @Test
    fun isLongImageRatio_thresholdBoundary_isExclusive() {
        // 恰好 3 倍不算超长图，必须严格大于阈值
        assertFalse(isLongImageRatio(width = 100f, height = 300f))
        assertTrue(isLongImageRatio(width = 100f, height = 301f))
        // 阈值可覆盖（测试替换实现时用）
        assertTrue(isLongImageRatio(width = 100f, height = 250f, threshold = 2f))
    }

    @Test
    fun isLongImageRatio_degenerateSizes_areNotDetected() {
        // 矢量图 / 未指定尺寸的 painter 会出现 NaN、0 或负值，一律不判长图
        assertFalse(isLongImageRatio(width = Float.NaN, height = 500f))
        assertFalse(isLongImageRatio(width = 100f, height = Float.NaN))
        assertFalse(isLongImageRatio(width = 0f, height = 500f))
        assertFalse(isLongImageRatio(width = 100f, height = 0f))
        assertFalse(isLongImageRatio(width = 100f, height = -500f))
        assertFalse(isLongImageRatio(width = Float.POSITIVE_INFINITY, height = 500f))
    }

    // ── 长图纵向浏览：FillWidth 拟合下的平移钳制 ────────────

    @Test
    fun clampPan_fillWidthLongImage_allowsVerticalOverflowOnly() {
        val viewport = Size(1080f, 1920f)
        val longImage = Size(100f, 500f)
        // FillWidth：显示 1080x5400，纵向可移动 (5400-1920)/2 = 1740，纵向溢出而横向刚好铺满
        val clamped = clampPan(
            Offset(300f, -9000f),
            scale = 1f,
            viewport = viewport,
            contentSize = longImage,
            panContentScale = PanContentScale.FILL_WIDTH
        )
        assertEquals(0f, clamped.x)
        assertEquals(1740f, clamped.y)
    }

    @Test
    fun clampPan_fitLongImage_locksBothAxesWhenFit() {
        val viewport = Size(1080f, 1920f)
        val longImage = Size(100f, 500f)
        // 对照：Fit 语义下 100x500 被等比缩到 384x1920，正好塞满高度，1 倍时不可平移
        assertEquals(
            Offset.Zero,
            clampPan(
                Offset(300f, -9000f),
                scale = 1f,
                viewport = viewport,
                contentSize = longImage
            )
        )
    }

    // ── 常量契约 ────────────────────────────────────────────

    @Test
    fun zoomDefaults_matchSpec() {
        // 解码宽度上限 4096、超长图阈值 3 倍、放大判定阈值 1.02
        assertEquals(4096, POI_DEFAULT_MAX_DECODE_WIDTH_PX)
        assertEquals(3f, POI_LONG_IMAGE_THRESHOLD)
        assertEquals(1.02f, POI_ZOOMED_THRESHOLD)
    }
}
