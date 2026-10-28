package com.nobody.poishelf.ui.common

import com.nobody.poishelf.platform.PoiShelfBackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import kotlinx.coroutines.delay

/**
 * Material Design 3 Modal Side Sheet 组件：
 * 专为平板与横屏大屏设计，从屏幕右侧平滑滑入，具备背景半透明遮罩与返回键手势关闭。
 *
 * KMP 移植版（原 `ui/search/components/SearchFilterSheet.kt:171`，M3 先行提取为 common；
 * M5 Search 侧改由本 common 实现 import）。
 *
 * @param SIDE_SHEET_ANIMATION_DURATION_MS 侧边抽屉动画时长（毫秒）
 */
private const val SIDE_SHEET_ANIMATION_DURATION_MS = 260

@Composable
fun ModalSideSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isClosing by remember { mutableStateOf(false) }
    var hasEntered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasEntered = true
    }

    val transition = updateTransition(targetState = hasEntered && !isClosing, label = "ModalSideSheetTransition")
    val offsetX by transition.animateFloat(
        transitionSpec = { tween(durationMillis = SIDE_SHEET_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing) },
        label = "SideSheetOffset"
    ) { visible ->
        if (visible) 0f else 1f
    }
    val scrimAlpha by transition.animateFloat(
        transitionSpec = { tween(durationMillis = SIDE_SHEET_ANIMATION_DURATION_MS, easing = FastOutSlowInEasing) },
        label = "SideSheetScrimAlpha"
    ) { visible ->
        if (visible) 0.36f else 0f
    }

    val closeWithAnimation: () -> Unit = remember {
        {
            if (!isClosing) {
                isClosing = true
            }
        }
    }

    LaunchedEffect(isClosing) {
        if (isClosing) {
            delay(SIDE_SHEET_ANIMATION_DURATION_MS.toLong())
            onDismissRequest()
        }
    }

    PoiShelfBackHandler(enabled = true, onBack = closeWithAnimation)

    val sideSheetNestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // 全额消费抽屉内可滚动视图未消费的剩余滚动位移，防止过滚动事件穿透泄漏至底层
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return available
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
    ) {
        // 1. 全屏半透明遮罩 (Scrim)：全屏铺满压暗背景，抽屉圆角外切角处自然透出暗色遮罩，彻底消除白色直角
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .testTag("modal_side_sheet_scrim")
                .semantics(mergeDescendants = true) {
                    contentDescription = "关闭遮罩"
                    onClick {
                        closeWithAnimation()
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { closeWithAnimation() }
                    )
                }
        )

        // 2. 右侧抽屉面板：居右停靠浮于遮罩之上，自身消费空白处点击防止击穿到底层遮罩
        Box(
            modifier = modifier
                .align(Alignment.CenterEnd)
                .width(380.dp)
                .fillMaxHeight()
                .graphicsLayer {
                    translationX = size.width * offsetX
                }
                .nestedScroll(sideSheetNestedScrollConnection)
                .pointerInput(Unit) {
                    detectTapGestures {
                        // 消费抽屉空白处的点击，防止事件击穿透传到底层遮罩
                    }
                }
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = ExpressiveTokens.SideSheetShape
                )
        ) {
            Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                content()
            }
        }
    }
}
