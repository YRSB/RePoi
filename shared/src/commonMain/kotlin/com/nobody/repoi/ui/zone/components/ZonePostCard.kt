package com.nobody.repoi.ui.zone.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.PostItem
import com.nobody.repoi.ui.common.LevelPill
import com.nobody.repoi.ui.common.PoiAsyncImage
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

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
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.dp
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                PoiAsyncImage(
                    path = post.userHeader,
                    category = MediaCategory.AVATAR,
                    purpose = ImagePurpose.THUMBNAIL,
                    contentDescription = post.userName,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(enabled = onUserClick != null && post.userId > 0L) {
                            onUserClick?.invoke(post.userId)
                        }
                )

                Spacer(modifier = Modifier.width(10.dp))

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

                if (!post.forumName.isNullOrBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
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

            if (post.displayContent.isNotBlank()) {
                Text(
                    text = post.displayContent,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val picList = post.picList
            if (picList.isNotEmpty()) {

                val allRawUrls = picList.map {
                    MediaPipeline.Default.resolveRawUrl(it, MediaCategory.POST)
                }

                if (picList.size == 1) {
                    val picPath = picList[0]

                    Box(
                        modifier = Modifier
                            .height(150.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                onImageClick(allRawUrls, 0)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        PoiAsyncImage(
                            path = picPath,
                            category = MediaCategory.POST,
                            purpose = ImagePurpose.CARD_STANDARD,
                            contentDescription = "帖子配图",

                            modifier = Modifier.height(150.dp),
                            adaptiveRatio = true,
                            fallbackRatio = 0.75f
                        )
                    }
                } else {

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
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable {
                                        onImageClick(allRawUrls, index)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                PoiAsyncImage(
                                    path = picPath,
                                    category = MediaCategory.POST,
                                    purpose = ImagePurpose.CARD_STANDARD,
                                    contentDescription = "帖子配图",
                                    modifier = Modifier.fillMaxSize()
                                )

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
                            painter = painterResource(PoiIcons.Outlined.Image),
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
                        painter = painterResource(PoiIcons.Outlined.ChatBubbleOutline),
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
