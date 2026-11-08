package com.nobody.repoi.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nobody.repoi.data.media.DefaultMediaPipeline
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import kotlin.math.abs

/** 未命中缓存、无回退比例且非自适应场景下的默认纵横比（正方形占位，防布局跳动）。 */
private const val DEFAULT_ASPECT_RATIO = 1.0f

/** 本地比例写回的阈值：变化小于该值时不触发状态更新，避免无谓重组。 */
private const val RATIO_UPDATE_THRESHOLD = 0.01f

/** 加载失败占位图标边长（固定 28dp 折中值，理由见 `PoiAsyncImageCore` 内注释）。 */
private val BrokenImageIconSize = 28.dp

/**
 * 多媒体图片呈现基元（语义化重载）。
 *
 * 调用端只需提供「资源路径 + 分类 + 用途」，由 [MediaPipeline.resolveUrl] 统一裁决真实 CDN 变体，
 * 调用端不再关心目录差异、404 避坑与纵横比缓存。
 *
 * 行为约定：
 * - [path] 为空、空白或命中官方缺省图（`none.jpg`）时**不请求**该路径，直接展示
 *   [MediaPipeline.getDefaultCoverUrl] 给出的官方缺省图；
 * - 请求统一经 [rememberListImageRequest] 生成，缓存键区分「分类 + 用途 + 缩放模式 + 自适应与否」，
 *   防止缩略图与原图缓存串味；
 * - [adaptiveRatio] 为真时用 `Modifier.aspectRatio` 自行撑开高度（调用方只需给出宽度约束），
 *   首帧同步读缓存占位，加载成功后再回写；
 * - [enableFullscreenPreview] 为真时点击唤起全屏查看器；若同时提供 [onClick]，则 [onClick] 优先。
 *
 * @param path Hpoi 资源路径（相对路径或绝对 URL，可为空）
 * @param category 资源所属业务分类
 * @param purpose 期望的呈现用途（管道会在服务端缺档时自动降级）
 * @param contentDescription 无障碍描述
 * @param modifier 外部尺寸与样式约束
 * @param contentScale 内容缩放模式，默认裁切填充
 * @param adaptiveRatio 是否按图片真实纵横比自适应高度
 * @param fallbackRatio 纵横比缓存未命中时的回退比例（如相册无图卡片 17:24），缺省 1.0f
 * @param enableFullscreenPreview 是否在点击时唤起全屏大图查看器
 * @param onClick 自定义点击回调，非空时不再唤起全屏查看器
 * @param alignment 图片在容器内的对齐方式，默认居中；海报类卡片可传 `Alignment.TopCenter` 顶部对齐
 */
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
    alignment: Alignment = Alignment.Center
) {
    val pipeline = MediaPipeline.Default
    val resolvedUrl = remember(path, category, purpose) { pipeline.resolveUrl(path, category, purpose) }
    // 空路径与官方缺省图不发网络请求，直接落到同一用途档位的吉祥物缺省图
    val needsRequest = remember(path, resolvedUrl) { shouldLoadFromNetwork(path, resolvedUrl) }
    val displayUrl = remember(needsRequest, resolvedUrl, purpose) {
        if (needsRequest) resolvedUrl else pipeline.getDefaultCoverUrl(purpose)
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
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        adaptiveRatio = adaptiveRatio,
        fallbackRatio = fallbackRatio,
        enableFullscreenPreview = enableFullscreenPreview,
        onClick = onClick,
        alignment = alignment
    )
}

/**
 * 多媒体图片呈现基元（通用重载）。
 *
 * 用于「URL 已在别处解析完毕」的场景：不触碰 CDN 规则，[model] 原样交给 Coil
 * （可传绝对 URL、任意 `ImageRequest` 或其它 Coil 可识别类型）。
 *
 * @param model Coil 模型：绝对 URL / [ImageRequest] 直通，不做任何 CDN 拼接
 * @param contentDescription 无障碍描述
 * @param modifier 外部尺寸与样式约束
 * @param contentScale 内容缩放模式，默认裁切填充
 * @param adaptiveRatio 是否按图片真实纵横比自适应高度
 * @param ratioKey 纵横比缓存键，缺省按 [model] 的字符串形式反推
 * @param fallbackRatio 纵横比缓存未命中时的回退比例，缺省 1.0f
 * @param enableFullscreenPreview 是否在点击时唤起全屏大图查看器（仅纯字符串 URL 可用）
 * @param onClick 自定义点击回调，非空时不再唤起全屏查看器
 * @param alignment 图片在容器内的对齐方式，默认居中；海报类卡片可传 `Alignment.TopCenter` 顶部对齐
 */
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
    alignment: Alignment = Alignment.Center
) {
    val urlText = remember(model) { modelTextOf(model) }
    val bucket = remember(contentScale, adaptiveRatio) {
        "raw:${contentScaleBucketLabel(contentScale)}:${if (adaptiveRatio) "adaptive" else "static"}"
    }

    PoiAsyncImageCore(
        model = model,
        urlText = urlText,
        // 仅有纯字符串（绝对 URL）才可交给全屏查看器；ImageRequest 等复合模型不外送
        previewUrl = if (model is String) model.ifBlank { null } else null,
        ratioKey = ratioKey ?: ratioCacheKey(null, urlText, bucket),
        bucket = bucket,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        adaptiveRatio = adaptiveRatio,
        fallbackRatio = fallbackRatio,
        enableFullscreenPreview = enableFullscreenPreview,
        onClick = onClick,
        alignment = alignment
    )
}

/**
 * 两个公开重载的公共渲染核：负责请求构建、自适应纵横比、失败态与点击交互。
 *
 * 与公开重载的差别仅在于「URL 已解析完毕」，因此不做任何 CDN 判定。
 */
@Composable
private fun PoiAsyncImageCore(
    model: Any?,
    urlText: String,
    previewUrl: String?,
    ratioKey: String,
    bucket: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    adaptiveRatio: Boolean,
    fallbackRatio: Float?,
    enableFullscreenPreview: Boolean,
    onClick: (() -> Unit)?,
    alignment: Alignment
) {
    val pipeline = MediaPipeline.Default
    // ImageRequest 直通；其余模型统一经列表请求工厂生成带独立缓存键的请求
    val listRequest = if (model !is ImageRequest && urlText.isNotBlank()) {
        rememberListImageRequest(urlText, urlText, bucket)
    } else {
        null
    }
    val request: Any? = if (model is ImageRequest) model else listRequest

    var loadFailed by remember(urlText) { mutableStateOf(false) }

    var aspectRatio by remember(ratioKey, adaptiveRatio, fallbackRatio) {
        mutableFloatStateOf(
            if (!adaptiveRatio) {
                DEFAULT_ASPECT_RATIO
            } else {
                // 缓存可能由旧代码写入（历史上界 2.0f/5.0f 混杂），读取后一律按管道区间规整
                pipeline.getAspectRatio(ratioKey)?.let(::clampRatio)
                    ?: fallbackRatio?.let(::clampRatio)
                    ?: DEFAULT_ASPECT_RATIO
            }
        )
    }

    val viewer = LocalImageViewer.current
    val previewTarget = previewUrl?.takeIf { enableFullscreenPreview }
    // 未提供任何点击语义时不加 clickable，避免抢走外层卡片的手势；
    // 传了 onClick 就只执行它，不再唤起查看器（避免双触发）
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
            // 失败/无模型态：大容器（详情、全屏入口）与 72dp 级缩略图各占一半概率，
            // 因此取 28dp 折中而不引入 BoxWithConstraints（后者在 Lazy 列表逐项测量有额外开销）
            Icon(
                imageVector = Icons.Filled.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(BrokenImageIconSize)
            )
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
                    // 矢量/未指定尺寸时 width/height 为 NaN，比较结果为 false，天然被过滤
                    if (size.width > 0f && size.height > 0f) {
                        val ratio = clampRatio(size.width / size.height)
                        pipeline.recordAspectRatio(ratioKey, ratio)
                        if (abs(ratio - aspectRatio) > RATIO_UPDATE_THRESHOLD) {
                            aspectRatio = ratio
                        }
                    }
                },
                onError = { loadFailed = true }
            )
        }
    }
}

/**
 * 是否需要从网络请求该路径。
 *
 * 空/空白路径与官方缺省吉祥物视为「无可请求资源」，由调用方改用缺省图。
 */
internal fun shouldLoadFromNetwork(path: String?, resolvedUrl: String): Boolean =
    resolvedUrl.isNotBlank() && !MediaPipeline.Default.isPlaceholder(path)

/**
 * 构造纵横比缓存键与列表缓存键共用的复合键。
 *
 * 以资源的**最后一段文件名**（无则回落完整 URL）作为基座，再拼上区分桶，保证同一张图在不同
 * 用途/缩放模式下的缓存互不串味。
 */
internal fun ratioCacheKey(path: String?, url: String, bucket: String): String =
    listImageKey(path, url, bucket)

/**
 * 构造列表请求的区分桶：分类 + 用途 + 缩放模式 + 是否自适应。
 *
 * 例：`album:adaptive_grid:crop:adaptive`。
 */
internal fun imageBucketKey(
    category: MediaCategory,
    purpose: ImagePurpose,
    contentScaleLabel: String,
    adaptiveRatio: Boolean
): String = "${category.name.lowercase()}:${purpose.name.lowercase()}:$contentScaleLabel:" +
    if (adaptiveRatio) "adaptive" else "static"

/**
 * 把原始宽高比截断到管道允许的 `[MIN_ASPECT_RATIO, MAX_ASPECT_RATIO]`。
 *
 * 非有限值（NaN / 非正值）返回默认 1.0f，避免 `Modifier.aspectRatio` 收到非法值。
 */
internal fun clampRatio(raw: Float): Float = when {
    raw.isNaN() || raw <= 0f -> DEFAULT_ASPECT_RATIO
    else -> raw.coerceIn(DefaultMediaPipeline.MIN_ASPECT_RATIO, DefaultMediaPipeline.MAX_ASPECT_RATIO)
}

/** 把缩放模式映射为稳定、可读的缓存桶标签。 */
private fun contentScaleBucketLabel(scale: ContentScale): String = when (scale) {
    ContentScale.Crop -> "crop"
    ContentScale.Fit -> "fit"
    // `ContentScale` 具体变体在每个平台以独立单例对象暴露，跨平台无法穷举，
    // 因此只对业务用到的三种给出稳定标签，其余统一归入 `other`（仍可区分桶，不影响正确性）。
    else -> "other"
}

/** 从 Coil 模型反推可用作文本键的字符串（绝对 URL 即其本身）。 */
private fun modelTextOf(model: Any?): String = when (model) {
    null -> ""
    is String -> model
    else -> model.toString()
}
