package com.nobody.repoi.ui.profile.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.remote.model.CategoryMoneyGroup
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderFinanceSummary
import com.nobody.repoi.ui.common.PoiIcons
import com.nobody.repoi.ui.theme.pill
import com.nobody.repoi.util.PriceFormatter
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

private enum class AssetHubTab(
    val label: String,
    val collectStateKey: String
) {
    PREORDER("预定", "preorder"),
    BUY("已入", "buy")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FigureAssetHubSection(
    order: OrderCount?,
    finance: OrderFinanceSummary?,
    isLoggedIn: Boolean,
    onNavigateToCollect: (String) -> Unit,
    onMoneyClick: (Int) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    val tabs = AssetHubTab.entries
    val activeTab = tabs.getOrElse(selectedTabIndex) { AssetHubTab.PREORDER }

    val preorderCount = order?.preorder ?: 0
    val buyCount = order?.buy ?: 0

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AssetHubTabRow(
            tabs = tabs,
            selectedIndex = selectedTabIndex,
            tabCounts = listOf(preorderCount, buyCount),
            onSelectTab = { selectedTabIndex = it }
        )

        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                (fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) +
                    slideInVertically(
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                    ) { height -> direction * (height / 8) })
                    .togetherWith(
                        fadeOut(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)) +
                            slideOutVertically(
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                            ) { height -> -direction * (height / 8) }
                    )
                    .using(SizeTransform(clip = true))
            },
            label = "asset_hub_content"
        ) { tab ->
            when (tab) {
                AssetHubTab.PREORDER -> {
                    val totalAmount = finance?.supplementedTotalRmb ?: 0.0
                    val groups = finance?.supplementedGroups.orEmpty()
                    AssetStackedDetailRows(
                        countTitle = "已预定",
                        countValue = if (isLoggedIn) preorderCount.toString() else "—",
                        moneyTitle = "待补款总折合",
                        moneyValue = if (isLoggedIn) formatRmb(totalAmount) else "—",
                        isMoneyActive = isLoggedIn && totalAmount > 0.0,
                        groups = groups,
                        isLoggedIn = isLoggedIn,
                        onCountClick = {
                            if (isLoggedIn) onNavigateToCollect(tab.collectStateKey) else onLoginClick()
                        },
                        onMoneyClick = {
                            if (isLoggedIn) onMoneyClick(0) else onLoginClick()
                        }
                    )
                }

                AssetHubTab.BUY -> {
                    val totalAmount = finance?.historyTotalRmb ?: 0.0
                    val groups = finance?.historyGroups.orEmpty()
                    AssetStackedDetailRows(
                        countTitle = "已入",
                        countValue = if (isLoggedIn) buyCount.toString() else "—",
                        moneyTitle = "历史总投入",
                        moneyValue = if (isLoggedIn) formatRmb(totalAmount) else "—",
                        isMoneyActive = isLoggedIn && totalAmount > 0.0,
                        groups = groups,
                        isLoggedIn = isLoggedIn,
                        onCountClick = {
                            if (isLoggedIn) onNavigateToCollect(tab.collectStateKey) else onLoginClick()
                        },
                        onMoneyClick = {
                            if (isLoggedIn) onMoneyClick(1) else onLoginClick()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AssetHubTabRow(
    tabs: List<AssetHubTab>,
    selectedIndex: Int,
    tabCounts: List<Int>,
    onSelectTab: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            val checked = index == selectedIndex
            val countText = tabCounts.getOrElse(index) { 0 }
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) onSelectTab(index) },
                colors = if (checked) {
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = colorScheme.primaryContainer,
                        contentColor = colorScheme.onPrimaryContainer
                    )
                } else {
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = colorScheme.surfaceContainerLowest,
                        contentColor = colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        role = Role.RadioButton
                        this.selected = checked
                    },
                shapes = if (index == 0) {
                    ButtonGroupDefaults.connectedLeadingButtonShapes()
                } else {
                    ButtonGroupDefaults.connectedTrailingButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "${tab.label} $countText",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AssetStackedDetailRows(
    countTitle: String,
    countValue: String,
    moneyTitle: String,
    moneyValue: String,
    isMoneyActive: Boolean,
    groups: List<CategoryMoneyGroup>,
    isLoggedIn: Boolean,
    onCountClick: () -> Unit,
    onMoneyClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val gap = ListItemDefaults.SegmentedGap
    val positiveGroups = groups
        .filter { it.totalRmb > 0.0 }
        .sortedByDescending { it.totalRmb }
    val showCategoryStrip = isLoggedIn && positiveGroups.size > 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large),
        verticalArrangement = Arrangement.spacedBy(gap)
    ) {
        SegmentedListItem(
            onClick = onCountClick,
            modifier = Modifier.fillMaxWidth(),
            shapes = ListItemDefaults.shapes(),
            colors = ListItemDefaults.segmentedColors(
                containerColor = colorScheme.surfaceContainerLowest
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            content = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = countTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = countValue,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                                maxLines = 1
                            )
                            if (countValue != "—") {
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "件",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 1.dp)
                                )
                            }
                        }
                        Icon(
                            painter = painterResource(PoiIcons.ChevronRight),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )

        SegmentedListItem(
            onClick = onMoneyClick,
            modifier = Modifier.fillMaxWidth(),
            shapes = ListItemDefaults.shapes(),
            colors = ListItemDefaults.segmentedColors(
                containerColor = colorScheme.surfaceContainerLowest
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = moneyTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = moneyValue,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFeatureSettings = "tnum"
                                ),
                                fontWeight = FontWeight.Bold,
                                color = if (isMoneyActive) colorScheme.primary else colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                painter = painterResource(PoiIcons.ChevronRight),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isMoneyActive) colorScheme.primary else colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (showCategoryStrip) {
                        CategoryBreakdownInline(positiveGroups = positiveGroups)
                    }
                }
            }
        )
    }
}

@Composable
private fun CategoryBreakdownInline(
    positiveGroups: List<CategoryMoneyGroup>
) {
    val total = positiveGroups.sumOf { it.totalRmb }.coerceAtLeast(1e-5)
    val colorScheme = MaterialTheme.colorScheme
    val segmentColors = listOf(
        colorScheme.primary,
        colorScheme.tertiary,
        colorScheme.secondary,
        colorScheme.primary.copy(alpha = 0.45f),
        colorScheme.outlineVariant
    )
    val visibleLegendGroups = positiveGroups.take(2)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            positiveGroups.forEachIndexed { index, group ->
                val fraction = (group.totalRmb / total).toFloat().coerceAtLeast(0.04f)
                val color = segmentColors[index % segmentColors.size]
                Box(
                    modifier = Modifier
                        .weight(fraction)
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.pill)
                        .background(color)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                visibleLegendGroups.forEachIndexed { index, group ->
                    val color = segmentColors[index % segmentColors.size]
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Text(
                            text = "${group.categoryName} ${formatRmb(group.totalRmb)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (positiveGroups.size > visibleLegendGroups.size) {
                Text(
                    text = "等 ${positiveGroups.size} 类",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
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
