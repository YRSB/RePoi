package com.nobody.repoi.ui.common

import com.nobody.repoi.platform.PoiShelfBackHandler
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
import kotlinx.coroutines.delay
import com.nobody.repoi.ui.theme.sideSheet

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

                    }
                }
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.sideSheet
                )
        ) {
            Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
                content()
            }
        }
    }
}
