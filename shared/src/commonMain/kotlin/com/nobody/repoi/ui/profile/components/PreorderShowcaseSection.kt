package com.nobody.repoi.ui.profile.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nobody.repoi.data.media.ImagePurpose
import com.nobody.repoi.data.media.MediaCategory
import com.nobody.repoi.data.media.MediaPipeline
import com.nobody.repoi.data.remote.model.CollectTopItem
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderFinanceSummary
import com.nobody.repoi.ui.common.PoiIcons
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.util.PriceFormatter
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

internal data class ShowcaseStatSpec(
    val title: String,
    val value: String,
    val weight: Float = 1f,
    val accentColor: Color,
    val containerColor: Color? = null,
    val onClick: (() -> Unit)? = null
)

@Composable
fun PreorderShowcaseSection(
    order: OrderCount?,
    finance: OrderFinanceSummary?,
    preorderItems: List<CollectTopItem>,
    isLoggedIn: Boolean,
    onNavigateToCollect: (String) -> Unit,
    onMoneyClick: () -> Unit,
    onItemClick: (DetailNavParam) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val careCount = order?.care ?: 0
    val preorderCount = order?.preorder ?: preorderItems.size
    val supplementedRmb = formatRmb(finance?.supplementedTotalRmb)
    val colorScheme = MaterialTheme.colorScheme

    SegmentedShowcasePanel(
        stats = listOf(
            ShowcaseStatSpec(
                title = "想买",
                value = careCount.toString(),
                accentColor = colorScheme.onSurface,
                onClick = {
                    if (isLoggedIn) onNavigateToCollect("want") else onLoginClick()
                }
            ),
            ShowcaseStatSpec(
                title = "预定",
                value = preorderCount.toString(),
                accentColor = colorScheme.onSurface,
                onClick = {
                    if (isLoggedIn) onNavigateToCollect("preorder") else onLoginClick()
                }
            ),
            ShowcaseStatSpec(
                title = "待补款",
                value = supplementedRmb,
                weight = 1.25f,
                accentColor = colorScheme.onPrimaryContainer,
                containerColor = colorScheme.primaryContainer,
                onClick = {
                    if (isLoggedIn) onMoneyClick() else onLoginClick()
                }
            )
        ),
        items = preorderItems,
        onItemClick = onItemClick,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SegmentedShowcasePanel(
    stats: List<ShowcaseStatSpec>,
    items: List<CollectTopItem>,
    onItemClick: (DetailNavParam) -> Unit,
    modifier: Modifier = Modifier
) {
    val gap = ListItemDefaults.SegmentedGap
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            stats.forEach { spec ->
                ShowcaseStatItem(
                    title = spec.title,
                    value = spec.value,
                    accentColor = spec.accentColor,
                    containerColor = spec.containerColor ?: MaterialTheme.colorScheme.surfaceContainerLow,
                    onClick = spec.onClick,
                    modifier = Modifier
                        .weight(spec.weight)
                        .fillMaxHeight()
                )
            }
        }

        if (items.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                val carouselState = rememberCarouselState { items.size }
                HorizontalUncontainedCarousel(
                    state = carouselState,
                    itemWidth = 100.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .height(144.dp),
                    itemSpacing = 8.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) { index ->
                    val item = items[index]
                    val coverUrl = remember(item.cover) {
                        MediaPipeline.Default.resolveUrl(
                            item.cover,
                            MediaCategory.HOBBY,
                            ImagePurpose.CARD_STANDARD
                        )
                    }
                    val itemShape = MaterialTheme.shapes.medium
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .maskClip(itemShape)
                            .maskBorder(
                                border = BorderStroke(
                                    width = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                ),
                                shape = itemShape
                            )
                            .clickable {
                                onItemClick(
                                    DetailNavParam(
                                        itemId = item.itemId,
                                        itemType = item.itemType ?: "hobby",
                                        initialName = item.nameCN?.ifBlank { null } ?: item.name,
                                        initialCover = item.cover
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = item.nameCN ?: item.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(PoiIcons.Filled.AccountBox),
                                        contentDescription = item.nameCN ?: item.name,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShowcaseStatItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.onSurface,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null
) {
    val contentBlock: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = if (containerColor == MaterialTheme.colorScheme.surfaceContainerLow) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    accentColor.copy(alpha = 0.8f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            modifier = modifier,
            shapes = ListItemDefaults.shapes(),
            colors = ListItemDefaults.segmentedColors(
                containerColor = containerColor
            ),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            content = contentBlock
        )
    } else {
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.extraSmall,
            color = containerColor
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                contentBlock()
            }
        }
    }
}

internal fun formatRmb(amount: Double?): String {
    val value = amount ?: 0.0
    return if (abs(value) < 1e-5) {
        "¥0"
    } else if (value % 1.0 == 0.0) {
        "¥" + formatGrouped(value.toLong())
    } else {
        "¥" + formatGrouped(value)
    }
}

internal fun formatGrouped(value: Long): String = formatGrouped(value.toDouble())

internal fun formatGrouped(value: Double): String {
    val negative = value < 0
    val absValue = abs(value)
    val intPart = absValue.toLong()
    val frac = absValue - intPart
    val intGrouped = intPart.toString().reversed().chunked(3).joinToString(",").reversed()
    if (frac < 0.005) return (if (negative) "-" else "") + intGrouped
    val fracStr = PriceFormatter.formatFixed(absValue - intPart, 2).substringAfter(".", "")
        .trimEnd('0')
    return (if (negative) "-" else "") + intGrouped + if (fracStr.isEmpty()) "" else ".$fracStr"
}
