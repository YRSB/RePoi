package com.nobody.repoi.ui.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.remote.model.CollectTopItem
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderFinanceSummary
import com.nobody.repoi.ui.common.PoiIcons
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.util.PriceFormatter
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

@Composable
fun AssetShowcaseSection(
    order: OrderCount?,
    finance: OrderFinanceSummary?,
    buyItems: List<CollectTopItem>,
    isLoggedIn: Boolean,
    onNavigateToCollect: (String) -> Unit,
    onMoneyClick: () -> Unit,
    onItemClick: (DetailNavParam) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buyCount = order?.buy ?: buyItems.size
    val historyRmb = formatCompactRmb(finance?.historyTotalRmb)
    val fluctuation = finance?.amazonFluctuation ?: "0%"
    val fluctuationColor = when {
        fluctuation.startsWith("+") -> MaterialTheme.colorScheme.error
        fluctuation.startsWith("-") -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

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
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                    Text(
                        text = "已入与资产",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "${buyCount}件典藏",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
                        title = "已入柜",
                        value = buyCount.toString(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (isLoggedIn) onNavigateToCollect("buy") else onLoginClick()
                        }
                    )

                    ShowcaseStatItem(
                        title = "总投入 ▾",
                        value = historyRmb,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1.2f),
                        onClick = {
                            if (isLoggedIn) onMoneyClick() else onLoginClick()
                        }
                    )

                    ShowcaseStatItem(
                        title = "日亚浮动",
                        value = fluctuation,
                        accentColor = fluctuationColor,
                        modifier = Modifier.weight(1f),
                        onClick = null
                    )
                }
            }

            if (buyItems.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(
                        items = buyItems,
                        key = { it.itemId },
                        contentType = { "buy_figure" }
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

internal fun formatCompactRmb(amount: Double?): String {
    val value = amount ?: 0.0
    return when {
        abs(value) < 1e-5 -> "¥0"
        value >= 10000.0 -> {
            val wan = value / 10000.0
            "¥" + PriceFormatter.formatFixed(wan, 1).trimEnd('0').trimEnd('.') + "万"
        }
        value % 1.0 == 0.0 -> "¥" + formatGrouped(value.toLong())
        else -> "¥" + formatGrouped(value)
    }
}
