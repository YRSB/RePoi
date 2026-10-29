package com.nobody.poishelf.ui.vendor

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalUriHandler
import com.nobody.poishelf.platform.LocalAppMessages
import com.nobody.poishelf.ui.common.AdaptiveChunkedGrid
import com.nobody.poishelf.ui.common.ExpressiveFloatingToolbar
import com.nobody.poishelf.ui.common.ExpressiveToolbarButton
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import com.nobody.poishelf.ui.home.components.ShimmerHost
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.nobody.poishelf.data.remote.model.ActionItem
import com.nobody.poishelf.data.remote.model.CompanyActionItem
import com.nobody.poishelf.data.remote.model.CompanyActionRelateItem
import com.nobody.poishelf.data.remote.model.CompanyData
import com.nobody.poishelf.data.remote.model.CompanySeriesItem
import com.nobody.poishelf.data.remote.model.CompanyTagItem
import com.nobody.poishelf.data.remote.model.CompanyTagVoteItem
import com.nobody.poishelf.data.remote.model.RelatedHobbyItem
import com.nobody.poishelf.ui.home.components.ActionCard
import com.nobody.poishelf.ui.home.components.ActionCardSkeleton
import com.nobody.poishelf.ui.home.components.shimmerBackground
import com.nobody.poishelf.ui.theme.ExpressiveElevation
import com.nobody.poishelf.ui.theme.ExpressiveElevatedCard
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.util.CdnUrlUtils



// ─────────────────────────────────────────────────────────────
// 区块 0：厂商主体头部 (VendorHeaderSection)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun VendorHeaderSection(
    company: CompanyData?,
    initialName: String?,
    initialCover: String?,
    isIntroExpanded: Boolean = true,
    onToggleIntro: () -> Unit = {},
    onShowDetailInfo: () -> Unit = {},
    isDark: Boolean
) {
    val coverUrl = company?.cover ?: initialCover
    val displayName = company?.displayName?.ifBlank { null } ?: initialName ?: "厂商详情"
    val subName = company?.subName
    val ratingText = company?.ratingScoreText ?: "--"
    val detailText = company?.detailPlainText.orEmpty()
    val score = company?.hobbyRating ?: 0.0

    ExpressiveElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 上半部分：Logo 封面 + 中英文名称 + 评分 + 胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Logo 封面
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f))
                ) {
                    if (!coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = CdnUrlUtils.getRawCoverUrl(coverUrl),
                            contentDescription = displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // 右侧：中文品名 / 主标题 + 外文原名 + 评分 + 属性胶囊
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
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 胶囊列表：评分、周边款数、地区
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        // 1. 评分简化为单个胶囊，放置在“xx款周边”前
                        if (score > 0.0) {
                            Surface(
                                shape = ExpressiveTokens.PillShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = ratingText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        // 2. 周边款数胶囊
                        if (company != null && company.hobbyCount > 0) {
                            Surface(
                                shape = ExpressiveTokens.PillShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${company.hobbyCount} 款周边",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // 3. 地区胶囊
                        if (!company?.region.isNullOrBlank()) {
                            Surface(
                                shape = ExpressiveTokens.PillShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Text(
                                    text = company?.region.orEmpty(),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 中部：厂商简介（点击可查看厂商详情抽屉）
            if (detailText.isNotBlank()) {
                Surface(
                    shape = ExpressiveTokens.MediumCardShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onShowDetailInfo)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = detailText,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "详细信息 ›",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 区块 1：动态卡片 (VendorActionSection)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun VendorActionSection(
    actions: List<CompanyActionItem>,
    isLoading: Boolean,
    onActionClick: (ActionItem) -> Unit,
    onViewMore: () -> Unit,
    onImageClick: ((imageUrls: List<String>, initialIndex: Int) -> Unit)? = null
) {
    ExpressiveElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题栏 + 查看更多 >
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "动态",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "查看更多 ›",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onViewMore)
                )
            }

            if (isLoading && actions.isEmpty()) {
                ShimmerHost { ActionCardSkeleton() }
            } else if (actions.isEmpty()) {
                // 空状态占位
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无最新动态情报",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                // 展示最新的一条动态，无缝复用动态页与关注页现有统一 ActionCard
                val latest = actions.first()
                ActionCard(
                    actionItem = latest.toActionItem(),
                    onItemClick = onActionClick,
                    onImageClick = onImageClick
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 区块 2：品牌标签 (VendorTagsSection)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VendorTagsSection(
    tags: List<CompanyTagItem>,
    isLoading: Boolean,
    onMyImpressionClick: () -> Unit,
    isDark: Boolean
) {
    ExpressiveElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题与“我的印象 ›”
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "品牌标签",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "我的印象 ›",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onMyImpressionClick)
                )
            }

            // 标签流
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 点击可增加印象的快捷胶囊
                Surface(
                    shape = ExpressiveTokens.PillShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable(onClick = onMyImpressionClick)
                ) {
                    Text(
                        text = "+ 我的印象",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                tags.forEach { tag ->
                    val label = tag.displayTag
                    if (label.isNotBlank()) {
                        Surface(
                            shape = ExpressiveTokens.PillShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = if (tag.count > 0) "$label (${tag.count})" else label,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 区块 3：产品 (3x3 九宫格推荐, VendorProductsSection)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun VendorProductsSection(
    products: List<RelatedHobbyItem>,
    isLoading: Boolean,
    onProductClick: (RelatedHobbyItem) -> Unit,
    onViewMore: () -> Unit,
    isDark: Boolean
) {
    ExpressiveElevatedCard(
        modifier = Modifier
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
                    text = "产品",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "查看更多 ›",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onViewMore)
                )
            }

            if (isLoading && products.isEmpty()) {
                // 九宫格骨架占位
                ShimmerHost {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.85f)
                                        .clip(ExpressiveTokens.MediumCardShape)
                                        .shimmerBackground()
                                )
                            }
                        }
                    }
                }
                }
            } else if (products.isEmpty()) {
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
                // 自适应网格排布（手机3列，平板4~6列）
                AdaptiveChunkedGrid(
                    items = products,
                    maxRows = 3,
                    modifier = Modifier.fillMaxWidth()
                ) { item ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.8f),
                        shape = ExpressiveTokens.MediumCardShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { onProductClick(item) }
                    ) {
                        if (!item.cover.isNullOrBlank()) {
                            AsyncImage(
                                model = CdnUrlUtils.getRawCoverUrl(item.cover),
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 区块 4：旗下系列 (VendorSeriesSection)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun VendorSeriesSection(
    seriesList: List<CompanySeriesItem>,
    isLoading: Boolean,
    onSeriesClick: (CompanySeriesItem) -> Unit,
    onViewMore: () -> Unit,
    isDark: Boolean
) {
    ExpressiveElevatedCard(
        modifier = Modifier
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
                    text = "系列",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "查看更多 ›",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onViewMore)
                )
            }

            if (isLoading && seriesList.isEmpty()) {
                ShimmerHost {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(ExpressiveTokens.CardShape)
                            .shimmerBackground()
                    )
                }
            } else if (seriesList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无旗下独立系列",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    seriesList.forEach { series ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpressiveTokens.CardShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            onClick = { onSeriesClick(series) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 系列 Logo/封面
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(ExpressiveTokens.SmallCardShape)
                                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
                                ) {
                                    if (!series.cover.isNullOrBlank()) {
                                        AsyncImage(
                                            model = CdnUrlUtils.getRawCoverUrl(series.cover),
                                            contentDescription = series.displayName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = series.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (series.hobbyCount > 0) {
                                        Text(
                                            text = "${series.hobbyCount} 款系列手办",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 底部悬浮操作胶囊 (VendorFloatingActionBar)
// ─────────────────────────────────────────────────────────────

@Composable
internal fun VendorFloatingActionBar(
    collectCount: Long,
    isCollected: Boolean,
    onToggleCollect: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    commentCount: Int = 0,
    showCommentButton: Boolean = true
) {
    ExpressiveFloatingToolbar(modifier = modifier) {
        // 1. 收藏（只显示数字，无数字时只显示图标）
        ExpressiveToolbarButton(
            onClick = onToggleCollect,
            text = if (collectCount > 0) "$collectCount" else null,
            icon = if (isCollected) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isCollected) "已关注" else "关注",
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
// 骨架屏加载态与错误重试
// ─────────────────────────────────────────────────────────────

@Composable
internal fun VendorDetailSkeleton(
    onBack: () -> Unit,
    isDark: Boolean
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 70.dp, start = 14.dp, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 头部卡片骨架
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(ExpressiveTokens.LargeCardShape)
                    .shimmerBackground()
            )

            // 动态卡片骨架
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(ExpressiveTokens.LargeCardShape)
                    .shimmerBackground()
            )

            // 产品九宫格骨架
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(ExpressiveTokens.LargeCardShape)
                    .shimmerBackground()
            )
        }

        // 返回按键
        FilledTonalIconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(14.dp)
                .size(38.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)
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
}

@Composable
internal fun VendorErrorState(
    errorMessage: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    isDark: Boolean
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ExpressiveElevatedCard(
            modifier = Modifier.padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "厂商详情加载失败",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = ExpressiveTokens.ButtonShape
                    ) {
                        Text("返回")
                    }

                    Button(
                        onClick = onRetry,
                        shape = ExpressiveTokens.ButtonShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

/**
 * 厂商“我的印象”打分与品牌标签选择底部弹窗 (ModalBottomSheet)。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun VendorImpressionBottomSheet(
    visible: Boolean,
    isLoading: Boolean,
    isSubmitting: Boolean,
    allTags: List<CompanyTagVoteItem>,
    selectedNormalTagIds: Set<Long>,
    scoreVotes: Map<Long, Double>,
    onToggleNormalTag: (Long) -> Unit,
    onScoreChange: (tagId: Long, score: Double) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = ExpressiveTokens.BottomSheetShape,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶栏：取消、标题、提交按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "取消",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )

                Text(
                    text = "我的印象",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Button(
                        onClick = onSubmit,
                        enabled = !isLoading,
                        shape = ExpressiveTokens.PillShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("提交", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                val scoreTags = remember(allTags) { allTags.filter { it.isScoreTag } }
                val normalTags = remember(allTags) { allTags.filter { !it.isScoreTag } }

                // ── 评分维度区块 ──
                if (scoreTags.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "维度打分",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        ExpressiveElevatedCard(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                scoreTags.forEach { tag ->
                                    val currentScore = scoreVotes[tag.id] ?: 0.0
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tag.name,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        StarRatingBar(
                                            rating = currentScore,
                                            onRatingSelected = { star ->
                                                onScoreChange(tag.id, star)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── 印象标签多选区块 ──
                if (normalTags.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "印象标签（多选）",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            normalTags.forEach { tag ->
                                val isSelected = selectedNormalTagIds.contains(tag.id)
                                Surface(
                                    shape = ExpressiveTokens.PillShape,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    modifier = Modifier.clickable {
                                        onToggleNormalTag(tag.id)
                                    }
                                ) {
                                    Text(
                                        text = tag.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 交互式 5 颗星星打分条。
 */
@Composable
private fun StarRatingBar(
    rating: Double,
    onRatingSelected: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (1..5).forEach { starIndex ->
            val isFull = rating >= starIndex
            Icon(
                imageVector = if (isFull) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "$starIndex 星",
                tint = if (isFull) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .size(24.dp)
                    .clickable {
                        onRatingSelected(starIndex.toDouble())
                    }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 区块 8：厂商详细信息底部抽屉 (VendorInfoBottomSheet)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VendorInfoBottomSheet(
    company: CompanyData?,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val uriHandler = LocalUriHandler.current
    val messages = LocalAppMessages.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = ExpressiveTokens.BottomSheetShape,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 顶栏：标题 + 关闭按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "厂商详情",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 属性表格卡片
            ExpressiveElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 别名
                    val aliasesText = company?.aliases?.filter { it.isNotBlank() }?.joinToString(" / ")
                    VendorInfoRow(
                        label = "别名",
                        value = aliasesText?.ifBlank { null } ?: "暂无"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 官网
                    val rawWebsite = company?.website?.trim().orEmpty()
                    val isNoneWebsite = rawWebsite.isBlank() ||
                        rawWebsite.equals("暂无", ignoreCase = true) ||
                        rawWebsite.equals("无", ignoreCase = true) ||
                        rawWebsite.equals("null", ignoreCase = true) ||
                        rawWebsite.equals("none", ignoreCase = true)
                    val hasValidWebsite = !isNoneWebsite && (
                        rawWebsite.startsWith("http://", ignoreCase = true) ||
                        rawWebsite.startsWith("https://", ignoreCase = true) ||
                        rawWebsite.contains(".")
                    )

                    VendorInfoRow(
                        label = "官网",
                        value = if (hasValidWebsite) rawWebsite else "暂无",
                        isLink = hasValidWebsite,
                        onClick = if (hasValidWebsite) {
                            {
                                val url = if (!rawWebsite.startsWith("http://", ignoreCase = true) && !rawWebsite.startsWith("https://", ignoreCase = true)) {
                                    "https://$rawWebsite"
                                } else rawWebsite
                                try {
                                    uriHandler.openUri(url)
                                } catch (_: Exception) {
                                    messages.show("无法打开官网链接")
                                }
                            }
                        } else null
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 地区
                    val region = company?.region?.ifBlank { null }
                    VendorInfoRow(
                        label = "地区",
                        value = region ?: "暂无"
                    )

                    // 扩展属性（如推特以及 otherInfo 中剩余项）
                    val otherItems = company?.parsedOtherInfo.orEmpty().filter { it.key != "地区" && !it.value.isNullOrBlank() }
                    if (otherItems.isNotEmpty()) {
                        otherItems.forEach { item ->
                            val key = item.key.orEmpty()
                            val value = item.value.orEmpty().trim()
                            val isNoneValue = value.isBlank() ||
                                value.equals("暂无", ignoreCase = true) ||
                                value.equals("无", ignoreCase = true) ||
                                value.equals("null", ignoreCase = true) ||
                                value.equals("none", ignoreCase = true)
                            val isTwitter = key.contains("推特", ignoreCase = true) || key.contains("twitter", ignoreCase = true)
                            val isUrl = !isNoneValue && (
                                value.startsWith("http://", ignoreCase = true) ||
                                value.startsWith("https://", ignoreCase = true) ||
                                isTwitter
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            VendorInfoRow(
                                label = key,
                                value = when {
                                    isNoneValue -> "暂无"
                                    isTwitter && !value.startsWith("http", ignoreCase = true) -> "点击进入"
                                    else -> value
                                },
                                isLink = isUrl,
                                onClick = if (isUrl) {
                                    {
                                        val targetUrl = when {
                                            value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true) -> value
                                            isTwitter -> {
                                                val handle = value.removePrefix("@")
                                                "https://x.com/$handle"
                                            }
                                            else -> "https://$value"
                                        }
                                        try {
                                            uriHandler.openUri(targetUrl)
                                        } catch (_: Exception) {
                                            messages.show("无法打开链接")
                                        }
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            // 简介全文
            val detailText = company?.detailPlainText.orEmpty()
            if (detailText.isNotBlank()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "详细介绍",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = ExpressiveTokens.MediumCardShape,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = detailText,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VendorInfoRow(
    label: String,
    value: String,
    isLink: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(68.dp)
        )

        val isNone = value == "暂无"
        if (isLink && !isNone && onClick != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
            ) {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "↗",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Text(
                text = value,
                fontSize = 13.sp,
                color = if (isNone) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

