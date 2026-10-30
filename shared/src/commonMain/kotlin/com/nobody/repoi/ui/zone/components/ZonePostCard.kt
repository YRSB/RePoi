package com.nobody.repoi.ui.zone.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.ui.common.LevelPill
import com.nobody.repoi.ui.home.components.shimmerBackground
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.CdnUrlUtils
import com.nobody.repoi.util.ImageAspectRatioCache
import kotlin.math.abs

/**
 * 讨论帖图片缩略图宽高比内存缓存，避免反复重组或滚动时产生布局跳跃。
 *
 * KMP 移植说明：原生 `android.util.LruCache` 改为共用
 * [ImageAspectRatioCache]（Mutex 保护的 LinkedHashMap LRU，上限 500 项）。
 */
private object PostThumbnailAspectRatioCache {
    operator fun get(url: String): Float? = ImageAspectRatioCache[url]
    operator fun set(url: String, ratio: Float) {
        ImageAspectRatioCache[url] = ratio
    }
}

/**
 * 专区讨论分类下的帖子卡片（风格对齐信息流 ActionCard，严格限制配图高度）。
 */
@Composable
fun ZonePostCard(
    post: PostItem,
    onPostClick: (PostItem) -> Unit,
    onImageClick: (rawUrls: List<String>, initialIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    onUserClick: ((Long) -> Unit)? = null
) {
    ElevatedCard(
        onClick = { onPostClick(post) },
        shape = ExpressiveTokens.CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = ExpressiveTokens.ElevationDefault
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. 用户信息头部行：头像、昵称、等级徽章、板块标签、时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 用户头像
                AsyncImage(
                    model = CdnUrlUtils.getHeadSmallUrl(post.userHeader),
                    contentDescription = post.userName,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(enabled = onUserClick != null && post.userId > 0L) {
                            onUserClick?.invoke(post.userId)
                        },
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(10.dp))

                // 昵称与时间
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = post.userName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(enabled = onUserClick != null && post.userId > 0L) {
                                onUserClick?.invoke(post.userId)
                            }
                        )

                        if (post.userLevel > 0) {
                            LevelPill(level = post.userLevel)
                        }
                    }

                    if (post.formattedTimeText.isNotBlank()) {
                        Text(
                            text = post.formattedTimeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // 右上角所属板块/论坛标签
                if (!post.forumName.isNullOrBlank()) {
                    Surface(
                        shape = ExpressiveTokens.ExtraSmallShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
                    ) {
                        Text(
                            text = post.forumName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 2. 帖子标题
            if (post.title.isNotBlank()) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 3. 正文纯文本摘要
            if (post.displayContent.isNotBlank()) {
                Text(
                    text = post.displayContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 4. 图片缩略图（对齐首页动态卡片排版规范：统一规整矩阵，Crop充满杜绝上下左右留白）
            val picList = post.picList
            if (picList.isNotEmpty()) {
                val allRawUrls = picList.map { CdnUrlUtils.getPostsRawUrl(it) }

                if (picList.size == 1) {
                    val picPath = picList[0]
                    var singleRatio by remember(picPath) {
                        mutableFloatStateOf(PostThumbnailAspectRatioCache[picPath] ?: 0.75f)
                    }

                    // 单张图对齐首页动态信息流海报规范：限高 150dp，宽度自适应，Crop 充满彻底杜绝留白
                    val effectiveRatio = singleRatio.coerceIn(0.65f, 1.6f)
                    val targetHeight = 150.dp
                    val targetWidth = targetHeight * effectiveRatio

                    Box(
                        modifier = Modifier
                            .size(width = targetWidth, height = targetHeight)
                            .clip(ExpressiveTokens.SmallCardShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                onImageClick(allRawUrls, 0)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        PostThumbImage(
                            imageUrl = CdnUrlUtils.getPostsUrl(picPath),
                            modifier = Modifier.fillMaxSize(),
                            onSuccessRatio = { r ->
                                if (r in 0.2f..5.0f && abs(r - singleRatio) > 0.01f) {
                                    singleRatio = r
                                    PostThumbnailAspectRatioCache[picPath] = r
                                }
                            }
                        )
                    }
                } else {
                    // 多张附图（最多展示3张）：对齐首页动态卡片附图矩阵，统一 104dp 正方形并使用 Crop 充满，彻底杜绝留白
                    val displayPics = picList.take(3)
                    val remainingCount = picList.size - 3

                    Row(
                        modifier = Modifier.wrapContentWidth(Alignment.Start),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        displayPics.forEachIndexed { index, picPath ->
                            Box(
                                modifier = Modifier
                                    .size(104.dp)
                                    .aspectRatio(1f)
                                    .clip(ExpressiveTokens.SmallCardShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable {
                                        onImageClick(allRawUrls, index)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                PostThumbImage(
                                    imageUrl = CdnUrlUtils.getPostsUrl(picPath),
                                    modifier = Modifier.fillMaxSize()
                                )

                                // 若多于 3 张，在第 3 张图上展示半透明浮层 +N 提示
                                if (index == 2 && remainingCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+$remainingCount",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. 底栏指示器（图片张数 + 评论数）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (picList.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Image,
                            contentDescription = "图片数量",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "${picList.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "回复数量",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "${post.discussCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * 帖子缩略图（带加载失败显式占位）。
 * 对标原版加载失败态：图片 404/损坏时展示破图图标，而非静默灰块。
 */
@Composable
private fun PostThumbImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
    onSuccessRatio: ((Float) -> Unit)? = null
) {
    var loadFailed by remember(imageUrl) { mutableStateOf(false) }
    if (!loadFailed) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "帖子配图",
            modifier = modifier,
            contentScale = ContentScale.Crop,
            onSuccess = { state ->
                val size = state.painter.intrinsicSize
                if (size.width > 0 && size.height > 0 && onSuccessRatio != null) {
                    onSuccessRatio(size.width / size.height)
                }
            },
            onError = { loadFailed = true }
        )
    } else {
        Icon(
            imageVector = Icons.Outlined.BrokenImage,
            contentDescription = "图片加载失败",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = modifier
                .fillMaxSize()
                .padding(28.dp)
        )
    }
}

/**
 * 讨论帖卡片骨架屏（高度和结构与真卡片严格对齐）。
 */
@Composable
fun ZonePostCardSkeleton(modifier: Modifier = Modifier) {
    ElevatedCard(
        shape = ExpressiveTokens.CardShape,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = ExpressiveTokens.ElevationDefault
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .shimmerBackground()
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 80.dp, height = 14.dp)
                            .clip(ExpressiveTokens.ExtraSmallShape)
                            .shimmerBackground()
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 120.dp, height = 10.dp)
                            .clip(ExpressiveTokens.ExtraSmallShape)
                            .shimmerBackground()
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(18.dp)
                    .clip(ExpressiveTokens.ExtraSmallShape)
                    .shimmerBackground()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(ExpressiveTokens.ExtraSmallShape)
                    .shimmerBackground()
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(ExpressiveTokens.ExtraSmallShape)
                    .shimmerBackground()
            )

            Row(
                modifier = Modifier.wrapContentWidth(Alignment.Start),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .clip(ExpressiveTokens.SmallCardShape)
                            .shimmerBackground()
                    )
                }
            }
        }
    }
}
