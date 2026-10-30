package com.nobody.repoi.ui.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith

/**
 * Material 3 Expressive 风格的共享轴（Shared Axis）与淡入淡出转场规范。
 *
 * 为 Navigation 3 的 NavDisplay 提供标准化的进出场动效与预测性返回适配。
 */
object MaterialSharedAxis {
    private val MotionEmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private const val DefaultDuration = 350
    private const val ExitDuration = 250

    object FadeThroughProvider {
        val EnterTransition: EnterTransition = fadeIn(
            animationSpec = tween(DefaultDuration, easing = FastOutSlowInEasing)
        )
        val ExitTransition: ExitTransition = fadeOut(
            animationSpec = tween(ExitDuration, easing = FastOutSlowInEasing)
        )
    }

    object ScaleProvider {
        val EnterTransition: EnterTransition = scaleIn(
            initialScale = 0.92f,
            animationSpec = tween(DefaultDuration, easing = MotionEmphasizedEasing)
        )
        val ExitTransition: ExitTransition = scaleOut(
            targetScale = 1.08f,
            animationSpec = tween(ExitDuration, easing = MotionEmphasizedEasing)
        )
        val PopEnterTransition: EnterTransition = scaleIn(
            initialScale = 1.08f,
            animationSpec = tween(DefaultDuration, easing = MotionEmphasizedEasing)
        )
        val PopExitTransition: ExitTransition = scaleOut(
            targetScale = 0.92f,
            animationSpec = tween(ExitDuration, easing = MotionEmphasizedEasing)
        )
    }

    /**
     * Z 轴层级缩放转场：适用于列表进入详情、详情多级深入及页面出栈。
     */
    object Z {
        val TransitionSpec: ContentTransform =
            (ScaleProvider.EnterTransition + FadeThroughProvider.EnterTransition) togetherWith
                (ScaleProvider.ExitTransition + FadeThroughProvider.ExitTransition)

        val PopTransitionSpec: ContentTransform =
            (ScaleProvider.PopEnterTransition + FadeThroughProvider.EnterTransition) togetherWith
                (ScaleProvider.PopExitTransition + FadeThroughProvider.ExitTransition)
    }

    /**
     * 纯淡入淡出转场：适用于顶层 5 个主 Tab 之间的平级切换。
     */
    object Fade {
        val TransitionSpec: ContentTransform =
            FadeThroughProvider.EnterTransition togetherWith FadeThroughProvider.ExitTransition
    }
}
