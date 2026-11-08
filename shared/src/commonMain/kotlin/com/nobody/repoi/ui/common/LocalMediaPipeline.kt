package com.nobody.repoi.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.nobody.repoi.data.media.MediaPipeline

/**
 * 组合树内的 [MediaPipeline] 提供点。
 *
 * 由根脚手架（`RePoiApp`）以 Koin 单例注入；无 Provider 时（Preview、单测、孤立组件预览）
 * 回落到 [MediaPipeline.Default]，与 DI 注册的是同一对象，行为完全一致。
 *
 * 之所以用 `staticCompositionLocalOf`：管道是无状态纯计算深服务，实例在整个应用生命周期内
 * 不变，静态局部可避免每次读取都建立可观测快照。
 */
val LocalMediaPipeline = staticCompositionLocalOf<MediaPipeline> { MediaPipeline.Default }
