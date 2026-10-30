package com.nobody.repoi.ui.person

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.nobody.repoi.ui.common.ExpressiveFloatingToolbar
import com.nobody.repoi.ui.common.ExpressiveToolbarButton
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.nobody.repoi.ui.home.components.ShimmerHost
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.nobody.repoi.data.remote.model.PersonData
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.data.remote.model.PersonHobbyEntry
import com.nobody.repoi.data.remote.model.PersonWorksEntry
import com.nobody.repoi.data.remote.model.PersonWorksItem
import com.nobody.repoi.ui.common.AdaptiveChunkedGrid
import com.nobody.repoi.ui.home.components.shimmerBackground
import com.nobody.repoi.ui.theme.ExpressiveElevation
import com.nobody.repoi.ui.theme.ExpressiveElevatedCard
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.util.CdnUrlUtils

// ─────────────────────────────────────────────────────────────


// ─────────────────────────────────────────────────────────────
// 区块 0：主体综合资料卡 (PersonHeaderSection)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonHeaderSection(
    person: PersonData?,
    initialName: String?,
    initialCover: String?,
    isIntroExpanded: Boolean,
    onToggleIntro: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val messages = LocalAppMessages.current
    val displayName = person?.displayName?.ifBlank { null } ?: initialName ?: "人物详情"
    val subName = person?.subName
    val cover = person?.cover?.ifBlank { null } ?: initialCover
    val coverUrl = CdnUrlUtils.getCoverUrl(cover)

    val twitterUrl = person?.parsedOtherInfo?.firstOrNull {
        it.key?.contains("推特", ignoreCase = true) == true ||
            it.key?.contains("twitter", ignoreCase = true) == true
    }?.value

    val intro = person?.detailPlainText.orEmpty()

    ExpressiveElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 头像与基本资料
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName.firstOrNull()?.toString() ?: "人",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!subName.isNullOrBlank()) {
                        Text(
                            text = subName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 属性胶囊（性别、生日）
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val sexText = person?.sexText
                        if (!sexText.isNullOrBlank() && sexText != "未知") {
                            Surface(
                                shape = ExpressiveTokens.PillShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = "性别 $sexText",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        val bDay = person?.birthdayText
                        if (!bDay.isNullOrBlank() && bDay != "未知") {
                            Surface(
                                shape = ExpressiveTokens.PillShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = "生日 $bDay",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // 统计数据（收藏数、周边数）
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "收藏 ${person?.collect ?: 0}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "周边 ${person?.hobbyCount ?: 0}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 社交平台外链胶囊（推特等）
            if (!twitterUrl.isNullOrBlank()) {
                Surface(
                    shape = ExpressiveTokens.PillShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable {
                        try {
                            uriHandler.openUri(twitterUrl)
                        } catch (_: Exception) {
                            messages.show("无法打开推特链接")
                        }
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Twitter / X ↗",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 简介内容（支持折叠/展开）
            if (intro.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                ) {
                    Text(
                        text = intro,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isIntroExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isIntroExpanded) "收起 ▲" else "展开全文 ▼",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onToggleIntro)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 区块 1：参与周边 (精选九宫格, PersonHobbiesSection)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun PersonHobbiesSection(
    hobbies: List<PersonHobbyEntry>,
    isLoading: Boolean,
    onHobbyClick: (PersonHobbyEntry) -> Unit,
    onViewMore: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    ExpressiveElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题 + 查看更多 ›
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "参与周边",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onViewMore) {
                    Text(text = "查看更多")
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isLoading && hobbies.isEmpty()) {
                // 九宫格骨架
                ShimmerHost {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.8f)
                                        .clip(ExpressiveTokens.MediumCardShape)
                                        .shimmerBackground()
                                )
                            }
                        }
                    }
                }
                }
            } else if (hobbies.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无代表周边手办",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                // 自适应网格（手机3列，折叠屏4列，宽屏6列）
                AdaptiveChunkedGrid(
                    items = hobbies,
                    maxRows = 3,
                    modifier = Modifier.fillMaxWidth()
                ) { entry ->
                    val item = entry.h
                    val coverUrl = CdnUrlUtils.getCoverUrl(item.cover)

                    ExpressiveElevatedCard(
                        onClick = { onHobbyClick(entry) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(9f / 13f),
                        shape = ExpressiveTokens.SmallCardShape,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (coverUrl.isNotBlank()) {
                                AsyncImage(
                                    model = coverUrl,
                                    contentDescription = item.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // 职责微标（如 原型师、原画、涂装）
                            val job = entry.jobName.firstOrNull()
                            if (!job.isNullOrBlank()) {
                                Surface(
                                    shape = ExpressiveTokens.ExtraSmallShape,
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(top = 4.dp, start = 4.dp)
                                ) {
                                    Text(
                                        text = job,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 若内容丰富，底部提供显式的更多条目入口
                if (hobbies.size >= 9) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(onClick = onViewMore) {
                            Text(text = "查看全部周边")
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 区块 2：参与作品 (PersonWorksSection)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun PersonWorksSection(
    works: List<PersonWorksEntry>,
    isLoading: Boolean,
    onWorkClick: (PersonWorksItem) -> Unit,
    onWorkLongClick: (PersonWorksItem) -> Unit,
    onViewMore: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoading && works.isEmpty()) return

    ExpressiveElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "参与作品",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onViewMore) {
                    Text(text = "查看更多")
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isLoading && works.isEmpty()) {
                ShimmerHost {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(ExpressiveTokens.CardShape)
                            .shimmerBackground()
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    works.take(6).forEach { entry ->
                        val item = entry.w
                        val coverUrl = CdnUrlUtils.getCoverUrl(item.cover)

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveTokens.CardShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            onClick = { onWorkClick(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(ExpressiveTokens.SmallCardShape)
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                ) {
                                    if (coverUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = coverUrl,
                                            contentDescription = item.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = item.displayName.ifBlank { "未知作品" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (!item.itemType.isNullOrBlank()) {
                                        Text(
                                            text = item.itemType,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = "搜索 ›",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 区块 3：出演角色 (PersonCharactersSection)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun PersonCharactersSection(
    characters: List<PersonWorksEntry>,
    isLoading: Boolean,
    onCharacterClick: (PersonWorksItem) -> Unit,
    onCharacterLongClick: (PersonWorksItem) -> Unit,
    onViewMore: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isLoading && characters.isEmpty()) return

    ExpressiveElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "出演角色",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onViewMore) {
                    Text(text = "查看更多")
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isLoading && characters.isEmpty()) {
                ShimmerHost {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(ExpressiveTokens.CardShape)
                            .shimmerBackground()
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    characters.take(6).forEach { entry ->
                        val item = entry.w
                        val coverUrl = CdnUrlUtils.getCoverUrl(item.cover)

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveTokens.CardShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            onClick = { onCharacterClick(item) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(ExpressiveTokens.SmallCardShape)
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                ) {
                                    if (coverUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = coverUrl,
                                            contentDescription = item.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = item.displayName.ifBlank { "未知角色" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (!item.itemType.isNullOrBlank()) {
                                        Text(
                                            text = item.itemType,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Text(
                                    text = "搜索 ›",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 底部常驻悬浮操作胶囊 (PersonFloatingActionBar)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun PersonFloatingActionBar(
    collectCount: Long,
    isCollected: Boolean,
    onCommentClick: () -> Unit,
    onToggleCollect: () -> Unit,
    onShareClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    commentCount: Int = 0,
    showCommentButton: Boolean = true
) {
    ExpressiveFloatingToolbar(modifier = modifier) {
        // 1. 收藏人物（只显示数字）
        ExpressiveToolbarButton(
            onClick = onToggleCollect,
            text = if (collectCount > 0) "$collectCount" else null,
            icon = if (isCollected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isCollected) "已收藏" else "收藏",
            isProminent = isCollected,
            containerColor = if (isCollected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (isCollected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            iconTint = if (isCollected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 2. 评论（小屏显示，统一显示数字，没有则显示0；大屏隐藏）
        if (showCommentButton) {
            ExpressiveToolbarButton(
                onClick = onCommentClick,
                text = "${commentCount.coerceAtLeast(0)}",
                icon = Icons.AutoMirrored.Filled.Comment,
                contentDescription = "评论",
                isProminent = !isCollected
            )
        }

        // 3. 分享（次级操作，纯图标）
        ExpressiveToolbarButton(
            onClick = onShareClick,
            icon = Icons.Filled.Share,
            contentDescription = "分享",
            isProminent = false
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 状态占位组件 (骨架与错误)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun PersonDetailSkeleton(
    onBack: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .shimmerBackground()
                .align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .width(140.dp)
                .height(22.dp)
                .clip(ExpressiveTokens.SmallCardShape)
                .shimmerBackground()
                .align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(20.dp))

        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clip(ExpressiveTokens.CardShape)
                    .shimmerBackground()
            )
        }
    }
}

@Composable
internal fun PersonErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                modifier = Modifier.size(38.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(text = "重试")
            }
        }
    }
}
