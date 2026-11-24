package com.nobody.repoi.ui.profile.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp, 16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = "预定与手账",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "${preorderCount}笔预定",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (isLoggedIn) onMoneyClick() else onLoginClick()
                        },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "折算明细",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            painter = painterResource(PoiIcons.Filled.ChevronRight),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShowcaseStatItem(
                        title = "想买",
                        value = careCount.toString(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (isLoggedIn) onNavigateToCollect("want") else onLoginClick()
                        }
                    )

                    ShowcaseStatItem(
                        title = "预定",
                        value = preorderCount.toString(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (isLoggedIn) onNavigateToCollect("preorder") else onLoginClick()
                        }
                    )

                    ShowcaseStatItem(
                        title = "待补款 ▾",
                        value = supplementedRmb,
                        accentColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1.2f),
                        onClick = {
                            if (isLoggedIn) onMoneyClick() else onLoginClick()
                        }
                    )
                }
            }

            if (preorderItems.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(
                        items = preorderItems,
                        key = { it.itemId },
                        contentType = { "preorder_figure" }
                    ) { item ->
                        ShowcaseFigureCard(
                            item = item,
                            onClick = {
                                onItemClick(
                                    DetailNavParam(
                                        itemId = item.itemId,
                                        itemType = item.itemType ?: "hobby",
                                        initialName = item.nameCN?.ifBlank { null } ?: item.name,
                                        initialCover = item.cover
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShowcaseStatItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
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

@Composable
internal fun ShowcaseFigureCard(
    item: CollectTopItem,
    onClick: () -> Unit
) {
    val coverUrl = remember(item.cover) {
        MediaPipeline.Default.resolveUrl(item.cover, MediaCategory.HOBBY, ImagePurpose.THUMBNAIL)
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(78.dp)
            .height(96.dp),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
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
                Icon(
                    painter = painterResource(PoiIcons.Filled.AccountBox),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
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
