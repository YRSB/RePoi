package com.nobody.repoi.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.nobody.repoi.data.media.MediaPipeline

val LocalMediaPipeline = staticCompositionLocalOf<MediaPipeline> { MediaPipeline.Default }
