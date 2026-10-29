package com.nobody.poishelf.ui.zone.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobody.poishelf.data.remote.model.PostItem
import com.nobody.poishelf.ui.theme.ExpressiveTokens

/**
 * 专区讨论置顶贴条目展示栏。
 *
 * 遵循 Material 3 Expressive 设计规范与交互契约：
 * 1. 采用 M3 ElevatedCard 容器（surfaceContainerLow 搭配 1dp 标高与 CardShape 圆角），与信息流卡片阶梯深度一致；
 * 2. 置顶标签采用 M3 语义化 Primary Tonal 徽章（primaryContainer 底色 + onPrimaryContainer 高对比度文字），克制典雅且完美自适应深浅色与动态取色；
 * 3. 默认展示前 2 条置顶条目；当置顶贴数量超过 2 条时，第 2 条右侧展示下拉三角形折叠箭头；
 * 4. 点击折叠箭头可平滑展开剩余置顶项（最多 5 项），展开后最后一项右侧呈现向上折叠箭头，支持再次收起；
 * 5. 点击具体置顶条目跳转至帖子详情。
 */
@Composable
fun ZonePinnedPostsBar(
    pinnedPosts: List<PostItem>,
    onPostClick: (PostItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pinnedPosts.isEmpty()) return

    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val hasMore = pinnedPosts.size > 2

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
        Column(modifier = Modifier.fillMaxWidth()) {
            // 前 2 条常驻展示项
            val primaryPosts = pinnedPosts.take(2)
            primaryPosts.forEachIndexed { index, post ->
                val showToggle = hasMore && index == 1 && !isExpanded
                PinnedPostRow(
                    post = post,
                    onPostClick = onPostClick,
                    showToggle = showToggle,
                    isExpanded = false,
                    onToggle = { isExpanded = true }
                )

                if (index < primaryPosts.lastIndex || (hasMore && isExpanded)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }

            // 超过 2 条的折叠展示项（第 3~5 项）
            if (hasMore) {
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val remainingPosts = pinnedPosts.drop(2)
                        remainingPosts.forEachIndexed { dropIndex, post ->
                            val isLast = dropIndex == remainingPosts.lastIndex
                            PinnedPostRow(
                                post = post,
                                onPostClick = onPostClick,
                                showToggle = isLast,
                                isExpanded = true,
                                onToggle = { isExpanded = false }
                            )

                            if (!isLast) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinnedPostRow(
    post: PostItem,
    onPostClick: (PostItem) -> Unit,
    showToggle: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPostClick(post) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // “置顶” M3 Primary Tonal 徽章
        Box(
            modifier = Modifier
                .clip(ExpressiveTokens.ExtraSmallShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "置顶",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // 置顶贴标题
        Text(
            text = post.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // 展开/收起小三角形指示箭头
        if (showToggle) {
            Box(
                modifier = Modifier
                    .clip(ExpressiveTokens.ExtraSmallShape)
                    .clickable { onToggle() }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = if (isExpanded) "收起置顶贴" else "展开更多置顶贴",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
