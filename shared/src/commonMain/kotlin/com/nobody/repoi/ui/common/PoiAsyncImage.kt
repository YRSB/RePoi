package com.nobody.repoi.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nobody.repoi.data.media.DefaultMediaPipeline
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import kotlin.math.abs
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

private const val DEFAULT_ASPECT_RATIO = 1.0f

internal const val HOBBY_FALLBACK_ASPECT_RATIO = 17f / 24f

internal const val DEFAULT_CATEGORY_ASPECT_RATIO = 1.0f

private const val RATIO_UPDATE_THRESHOLD = 0.01f

private val BrokenImageIconSize = 28.dp

@Composable
fun PoiAsyncImage(
    path: String?,
    category: MediaCategory,
    purpose: ImagePurpose,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    adaptiveRatio: Boolean = false,
    fallbackRatio: Float? = null,
    enableFullscreenPreview: Boolean = false,
    onClick: (() -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    onError: (() -> Unit)? = null
) {
    val pipeline = LocalMediaPipeline.current
    val resolvedUrl = remember(path, category, purpose) { pipeline.resolveUrl(path, category, purpose) }

    val needsRequest = remember(path, resolvedUrl) { shouldLoadFromNetwork(path, resolvedUrl, pipeline) }
    val displayUrl = remember(needsRequest, resolvedUrl, category, purpose) {
        if (needsRequest) resolvedUrl else placeholderUrlFor(pipeline, category, purpose)
    }
    val bucket = remember(category, purpose, contentScale, adaptiveRatio) {
        imageBucketKey(category, purpose, contentScaleBucketLabel(contentScale), adaptiveRatio)
    }

    PoiAsyncImageCore(
        model = null,
        urlText = displayUrl,
        previewUrl = if (needsRequest) resolvedUrl else null,
        ratioKey = ratioCacheKey(path, resolvedUrl, bucket),
        bucket = bucket,
        category = category,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        adaptiveRatio = adaptiveRatio,
        fallbackRatio = fallbackRatio,
        enableFullscreenPreview = enableFullscreenPreview,
        onClick = onClick,
        alignment = alignment,
        onError = onError
    )
}

@Composable
fun PoiAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    adaptiveRatio: Boolean = false,
    ratioKey: String? = null,
    fallbackRatio: Float? = null,
    enableFullscreenPreview: Boolean = false,
    onClick: (() -> Unit)? = null,
    alignment: Alignment = Alignment.Center,
    onError: (() -> Unit)? = null
) {
    val urlText = remember(model) { modelTextOf(model) }
    val bucket = remember(contentScale, adaptiveRatio) {
        "raw:${contentScaleBucketLabel(contentScale)}:${if (adaptiveRatio) "adaptive" else "static"}"
    }

    PoiAsyncImageCore(
        model = model,
        urlText = urlText,

        previewUrl = if (model is String) model.ifBlank { null } else null,
        ratioKey = ratioKey ?: ratioCacheKey(null, urlText, bucket),
        bucket = bucket,
        category = null,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        adaptiveRatio = adaptiveRatio,
        fallbackRatio = fallbackRatio,
        enableFullscreenPreview = enableFullscreenPreview,
        onClick = onClick,
        alignment = alignment,
        onError = onError
    )
}

@Composable
private fun PoiAsyncImageCore(
    model: Any?,
    urlText: String,
    previewUrl: String?,
    ratioKey: String,
    bucket: String,
    category: MediaCategory?,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    adaptiveRatio: Boolean,
    fallbackRatio: Float?,
    enableFullscreenPreview: Boolean,
    onClick: (() -> Unit)?,
    alignment: Alignment,
    onError: (() -> Unit)?
) {
    val pipeline = LocalMediaPipeline.current

    val listRequest = if (model !is ImageRequest && urlText.isNotBlank()) {
        rememberListImageRequest(urlText, urlText, bucket)
    } else {
        null
    }
    val request: Any? = if (model is ImageRequest) model else listRequest

    var loadFailed by remember(urlText) { mutableStateOf(false) }

    var aspectRatio by remember(ratioKey, adaptiveRatio, fallbackRatio, category) {
        mutableFloatStateOf(
            if (!adaptiveRatio) {
                DEFAULT_ASPECT_RATIO
            } else {

                pipeline.getAspectRatio(ratioKey)?.let(::clampRatio)
                    ?: clampRatio(fallbackRatioFor(category, fallbackRatio))
            }
        )
    }

    val viewer = LocalImageViewer.current
    val previewTarget = previewUrl?.takeIf { enableFullscreenPreview }

    val clickModifier: Modifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else if (previewTarget != null) {
        Modifier.clickable { viewer.show(previewTarget) }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(if (adaptiveRatio) Modifier.aspectRatio(aspectRatio) else Modifier)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        if (request == null || loadFailed) {
            val fallbackSemantics = if (contentDescription != null) {
                Modifier.semantics {
                    this.contentDescription = contentDescription
                    this.role = Role.Image
                }
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(fallbackSemantics),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(PoiIcons.Filled.BrokenImage),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(BrokenImageIconSize)
                )
            }
        } else {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                alignment = alignment,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { state ->
                    if (!adaptiveRatio) {
                        return@AsyncImage
                    }
                    val size = state.painter.intrinsicSize

                    if (size.width > 0f && size.height > 0f) {
                        val ratio = clampRatio(size.width / size.height)
                        pipeline.recordAspectRatio(ratioKey, ratio)
                        if (abs(ratio - aspectRatio) > RATIO_UPDATE_THRESHOLD) {
                            aspectRatio = ratio
                        }
                    }
                },
                onError = {
                    loadFailed = true
                    onError?.invoke()
                }
            )
        }
    }
}

internal fun shouldLoadFromNetwork(
    path: String?,
    resolvedUrl: String,
    pipeline: MediaPipeline = MediaPipeline.Default
): Boolean = resolvedUrl.isNotBlank() && !pipeline.isPlaceholder(path)

internal fun placeholderUrlFor(
    pipeline: MediaPipeline,
    category: MediaCategory,
    purpose: ImagePurpose
): String = when (category) {
    MediaCategory.ALBUM ->
        pipeline.resolveUrl(DefaultMediaPipeline.DEFAULT_COVER_PATH, MediaCategory.ALBUM, purpose)
    else -> pipeline.getDefaultCoverUrl(purpose)
}

internal fun fallbackRatioFor(category: MediaCategory?, explicit: Float?): Float = when {
    explicit != null -> explicit
    category == MediaCategory.HOBBY -> HOBBY_FALLBACK_ASPECT_RATIO
    else -> DEFAULT_CATEGORY_ASPECT_RATIO
}

internal fun ratioCacheKey(path: String?, url: String, bucket: String): String {
    val base = path?.takeIf { it.isNotBlank() } ?: url
    return "$base:$bucket"
}

internal fun imageBucketKey(
    category: MediaCategory,
    purpose: ImagePurpose,
    contentScaleLabel: String,
    adaptiveRatio: Boolean
): String = "${category.name.lowercase()}:${purpose.name.lowercase()}:$contentScaleLabel:" +
    if (adaptiveRatio) "adaptive" else "static"

internal fun clampRatio(raw: Float): Float = when {
    raw.isNaN() || raw <= 0f -> DEFAULT_ASPECT_RATIO
    else -> raw.coerceIn(DefaultMediaPipeline.MIN_ASPECT_RATIO, DefaultMediaPipeline.MAX_ASPECT_RATIO)
}

private fun contentScaleBucketLabel(scale: ContentScale): String = when (scale) {
    ContentScale.Crop -> "crop"
    ContentScale.Fit -> "fit"

    ContentScale.FillBounds -> "fill_bounds"

    else -> "other"
}

private fun modelTextOf(model: Any?): String = when (model) {
    null -> ""
    is String -> model
    else -> model.toString()
}
