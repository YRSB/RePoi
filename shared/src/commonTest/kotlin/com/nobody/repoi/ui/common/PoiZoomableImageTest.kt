package com.nobody.repoi.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PoiZoomableImageTest {

    @Test
    fun wheelZoomFactor_zeroDelta_keepsScale() {
        assertEquals(1f, wheelZoomFactor(0f))
    }

    @Test
    fun wheelZoomFactor_wheelUp_zoomsIn() {

        assertEquals(1.12f, wheelZoomFactor(-1f), 0.0001f)
    }

    @Test
    fun wheelZoomFactor_wheelDown_zoomsOut() {

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

        assertEquals(5f, dampedScale(raw = 8f, current = 4f, minScale = 1f, maxScale = 4f))
    }

    @Test
    fun dampedScale_aboveMaxFromMin_keepsRubberBandInsideReasonableRange() {

        assertEquals(3.25f, dampedScale(raw = 10f, current = 1f, minScale = 1f, maxScale = 4f))
    }

    @Test
    fun dampedScale_nanInputs_areGuarded() {
        assertEquals(2f, dampedScale(raw = Float.NaN, current = 2f, minScale = 1f, maxScale = 4f))
        assertEquals(1f, dampedScale(raw = 2f, current = Float.NaN, minScale = 1f, maxScale = 4f))
    }

    @Test
    fun anchorAdjustedOffset_factorOne_keepsOffset() {
        val offset = Offset(12f, -8f)
        assertEquals(offset, anchorAdjustedOffset(offset, factor = 1f, anchor = Offset(40f, 40f)))
    }

    @Test
    fun anchorAdjustedOffset_zoomingAroundRightAnchor_movesContentLeft() {

        assertEquals(
            Offset(-50f, 0f),
            anchorAdjustedOffset(Offset.Zero, factor = 2f, anchor = Offset(50f, 0f))
        )
    }

    @Test
    fun anchorAdjustedOffset_existingOffset_scalesThenShifts() {

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

    @Test
    fun clampPan_portraitImageAtTwoTimes_clampsToOverflowHalf() {
        val viewport = Size(1080f, 1920f)
        val portrait = Size(1080f, 1920f)

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

    @Test
    fun isLongImageRatio_tallImage_isDetected() {

        assertTrue(isLongImageRatio(width = 100f, height = 500f))
    }

    @Test
    fun isLongImageRatio_wideImage_isNotDetected() {

        assertFalse(isLongImageRatio(width = 500f, height = 100f))
    }

    @Test
    fun isLongImageRatio_thresholdBoundary_isExclusive() {

        assertFalse(isLongImageRatio(width = 100f, height = 300f))
        assertTrue(isLongImageRatio(width = 100f, height = 301f))

        assertTrue(isLongImageRatio(width = 100f, height = 250f, threshold = 2f))
    }

    @Test
    fun isLongImageRatio_degenerateSizes_areNotDetected() {

        assertFalse(isLongImageRatio(width = Float.NaN, height = 500f))
        assertFalse(isLongImageRatio(width = 100f, height = Float.NaN))
        assertFalse(isLongImageRatio(width = 0f, height = 500f))
        assertFalse(isLongImageRatio(width = 100f, height = 0f))
        assertFalse(isLongImageRatio(width = 100f, height = -500f))
        assertFalse(isLongImageRatio(width = Float.POSITIVE_INFINITY, height = 500f))
    }

    @Test
    fun clampPan_fillWidthLongImage_allowsVerticalOverflowOnly() {
        val viewport = Size(1080f, 1920f)
        val longImage = Size(100f, 500f)

        val clamped = clampPan(
            Offset(300f, -9000f),
            scale = 1f,
            viewport = viewport,
            contentSize = longImage,
            panContentScale = PanContentScale.FILL_WIDTH
        )
        assertEquals(0f, clamped.x)
        assertEquals(-1740f, clamped.y)
    }

    @Test
    fun clampPan_fitLongImage_locksBothAxesWhenFit() {
        val viewport = Size(1080f, 1920f)
        val longImage = Size(100f, 500f)

        val clamped = clampPan(
            Offset(300f, -9000f),
            scale = 1f,
            viewport = viewport,
            contentSize = longImage
        )

        assertTrue(clamped.x == 0f && clamped.y == 0f, "Fit 语义下 1 倍时不可平移，实际 $clamped")
    }

    @Test
    fun zoomDefaults_matchSpec() {

        assertEquals(4096, POI_DEFAULT_MAX_DECODE_WIDTH_PX)
        assertEquals(3f, POI_LONG_IMAGE_THRESHOLD)
        assertEquals(1.02f, POI_ZOOMED_THRESHOLD)
    }

    @Test
    fun maxVerticalPan_longImage_fillWidthOverflowHalf() {

        val maxY = maxVerticalPan(
            scale = 1f,
            viewport = Size(100f, 100f),
            contentSize = Size(100f, 1000f),
            panContentScale = PanContentScale.FILL_WIDTH
        )
        assertEquals(450f, maxY, 0.0001f)
    }

    @Test
    fun maxVerticalPan_fitLongImage_alsoPositive() {

        val maxY = maxVerticalPan(
            scale = 1f,
            viewport = Size(100f, 100f),
            contentSize = Size(100f, 1000f),
            panContentScale = PanContentScale.FIT
        )
        assertEquals(0f, maxY, 0.0001f)
    }

    @Test
    fun maxVerticalPan_degenerateInputs_returnZero() {
        assertEquals(0f, maxVerticalPan(1f, Size(100f, 100f), Size.Zero), 0.0001f)
        assertEquals(0f, maxVerticalPan(1f, Size.Zero, Size(100f, 1000f)), 0.0001f)
        assertEquals(0f, maxVerticalPan(0f, Size(100f, 100f), Size(100f, 1000f)), 0.0001f)
        assertEquals(0f, maxVerticalPan(Float.NaN, Size(100f, 100f), Size(100f, 1000f)), 0.0001f)
    }

    @Test
    fun maxVerticalPan_wideImage_returnsZero() {

        val maxY = maxVerticalPan(
            scale = 1f,
            viewport = Size(100f, 100f),
            contentSize = Size(400f, 100f),
            panContentScale = PanContentScale.FILL_WIDTH
        )
        assertEquals(0f, maxY, 0.0001f)
    }

    @Test
    fun longImageAnchorOffset_top_usesPositiveMaxY() {

        assertEquals(Offset(0f, 300f), longImageAnchorOffset(Offset.Zero, 300f, LongImageAnchor.TOP))
    }

    @Test
    fun longImageAnchorOffset_bottom_usesNegativeMaxY() {
        assertEquals(Offset(0f, -300f), longImageAnchorOffset(Offset.Zero, 300f, LongImageAnchor.BOTTOM))
    }

    @Test
    fun longImageAnchorOffset_noOverflow_returnsZero() {

        assertEquals(Offset.Zero, longImageAnchorOffset(Offset(0f, 50f), 0f, LongImageAnchor.TOP))
        assertEquals(Offset.Zero, longImageAnchorOffset(Offset(0f, 50f), Float.NaN, LongImageAnchor.BOTTOM))
    }

    @Test
    fun nextLongImageAnchor_fromNearBottom_jumpsToTop() {

        assertEquals(LongImageAnchor.TOP, nextLongImageAnchor(Offset.Zero))
        assertEquals(LongImageAnchor.TOP, nextLongImageAnchor(Offset(0f, -200f)))
    }

    @Test
    fun nextLongImageAnchor_fromTop_jumpsToBottom() {
        assertEquals(LongImageAnchor.BOTTOM, nextLongImageAnchor(Offset(0f, 200f)))
    }
}
