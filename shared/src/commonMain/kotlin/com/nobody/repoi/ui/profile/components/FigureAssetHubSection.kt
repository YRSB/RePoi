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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
    BUY("已入", "buy"),
    WANT("想买", "want")
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
    val wantCount = order?.actualWant ?: 0
    val density = LocalDensity.current
    var lockedContentHeightPx by remember(isLoggedIn) { mutableIntStateOf(0) }
    val lockedContentMinHeight = if (lockedContentHeightPx > 0) {
        with(density) { lockedContentHeightPx.toDp() }
    } else {
        0.dp
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AssetHubTabRow(
            tabs = tabs,
            selectedIndex = selectedTabIndex,
            counts = listOf(preorderCount, buyCount, wantCount),
            onSelect = { selectedTabIndex = it }
        )

        val gap = ListItemDefaults.SegmentedGap
        val colorScheme = MaterialTheme.colorScheme

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { base ->
                        if (lockedContentMinHeight > 0.dp) {
                            base.heightIn(min = lockedContentMinHeight)
                        } else {
                            base
                        }
                    }
                    .onSizeChanged { size ->
                        if (tab != AssetHubTab.WANT && size.height > lockedContentHeightPx) {
                            lockedContentHeightPx = size.height
                        }
                    }
                    .clip(MaterialTheme.shapes.large),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                when (tab) {
                    AssetHubTab.PREORDER -> {
                        val totalAmount = finance?.supplementedTotalRmb ?: 0.0
                        val groups = finance?.supplementedGroups.orEmpty()
                        AssetMetricDualRow(
                            moneyTitle = "待补款总折合",
                            moneyValue = if (isLoggedIn) formatRmb(totalAmount) else "—",
                            isMoneyActive = isLoggedIn && totalAmount > 0.0,
                            countTitle = "已预定",
                            countValue = if (isLoggedIn) preorderCount.toString() else "—",
                            subNote = null,
                            reserveSubNoteSlot = isLoggedIn,
                            subNoteColor = colorScheme.onSurfaceVariant,
                            onMoneyClick = {
                                if (isLoggedIn) onMoneyClick(0) else onLoginClick()
                            },
                            onCountClick = {
                                if (isLoggedIn) onNavigateToCollect(tab.collectStateKey) else onLoginClick()
                            }
                        )
                        if (isLoggedIn) {
                            CategoryBreakdownStrip(
                                groups = groups,
                                onClick = { onMoneyClick(0) }
                            )
                        }
                    }

                    AssetHubTab.BUY -> {
                        val totalAmount = finance?.historyTotalRmb ?: 0.0
                        val groups = finance?.historyGroups.orEmpty()
                        val fluctuation = finance?.amazonFluctuation ?: "0%"
                        val fluctuationColor = when {
                            fluctuation.startsWith("+") -> colorScheme.error
                            fluctuation.startsWith("-") -> colorScheme.primary
                            else -> colorScheme.onSurfaceVariant
                        }
                        AssetMetricDualRow(
                            moneyTitle = "历史总投入",
                            moneyValue = if (isLoggedIn) formatRmb(totalAmount) else "—",
                            isMoneyActive = isLoggedIn && totalAmount > 0.0,
                            countTitle = "已入",
                            countValue = if (isLoggedIn) buyCount.toString() else "—",
                            subNote = if (isLoggedIn) "日亚浮动 $fluctuation" else null,
                            reserveSubNoteSlot = isLoggedIn,
                            subNoteColor = fluctuationColor,
                            onMoneyClick = {
                                if (isLoggedIn) onMoneyClick(1) else onLoginClick()
                            },
                            onCountClick = {
                                if (isLoggedIn) onNavigateToCollect(tab.collectStateKey) else onLoginClick()
                            }
                        )
                        if (isLoggedIn) {
                            CategoryBreakdownStrip(
                                groups = groups,
                                onClick = { onMoneyClick(1) }
                            )
                        }
                    }

                    AssetHubTab.WANT -> {
                        WantSummaryRow(
                            wantCount = wantCount,
                            isLoggedIn = isLoggedIn,
                            minHeight = lockedContentMinHeight,
                            onClick = {
                                if (isLoggedIn) onNavigateToCollect(tab.collectStateKey) else onLoginClick()
                            }
                        )
                    }
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
    counts: List<Int>,
    onSelect: (Int) -> Unit
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
            val countText = counts.getOrElse(index) { 0 }
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) onSelect(index) },
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
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
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
private fun AssetMetricDualRow(
    moneyTitle: String,
    moneyValue: String,
    isMoneyActive: Boolean,
    countTitle: String,
    countValue: String,
    subNote: String?,
    reserveSubNoteSlot: Boolean,
    subNoteColor: Color,
    onMoneyClick: () -> Unit,
    onCountClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val gap = ListItemDefaults.SegmentedGap

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        SegmentedListItem(
            onClick = onMoneyClick,
            modifier = Modifier
                .weight(1.48f)
                .fillMaxHeight(),
            shapes = ListItemDefaults.shapes(),
            colors = ListItemDefaults.segmentedColors(
                containerColor = colorScheme.surfaceContainerLowest
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = moneyTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "明细",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = colorScheme.primary
                            )
                            Icon(
                                painter = painterResource(PoiIcons.ChevronRight),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = moneyValue,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        fontWeight = FontWeight.Bold,
                        color = if (isMoneyActive) colorScheme.primary else colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )

        SegmentedListItem(
            onClick = onCountClick,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shapes = ListItemDefaults.shapes(),
            colors = ListItemDefaults.segmentedColors(
                containerColor = colorScheme.surfaceContainerLowest
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = countTitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            painter = painterResource(PoiIcons.ChevronRight),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = countValue,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (countValue != "—") {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "件",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }

                    if (!subNote.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subNote,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            color = subNoteColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (reserveSubNoteSlot) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = " ",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.clearAndSetSemantics {}
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun CategoryBreakdownStrip(
    groups: List<CategoryMoneyGroup>,
    onClick: () -> Unit
) {
    val positiveGroups = groups
        .filter { it.totalRmb > 0.0 }
        .sortedByDescending { it.totalRmb }

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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraSmall,
        color = colorScheme.surfaceContainerLowest
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (positiveGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(MaterialTheme.shapes.pill)
                            .background(colorScheme.surfaceContainerHighest)
                    )
                } else {
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
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (positiveGroups.isEmpty()) {
                    Text(
                        text = "暂无品类金额明细",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        visibleLegendGroups.forEachIndexed { index, group ->
                            val color = segmentColors[index % segmentColors.size]
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
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
    }
}

@Composable
private fun WantSummaryRow(
    wantCount: Int,
    isLoggedIn: Boolean,
    minHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .let { base ->
                if (minHeight > 0.dp) base.heightIn(min = minHeight) else base
            },
        shape = MaterialTheme.shapes.large,
        color = colorScheme.surfaceContainerLowest
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "想买心愿清单",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = if (isLoggedIn) wantCount.toString() else "—",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        if (isLoggedIn) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "件",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                    }
                    if (!isLoggedIn) {
                        Text(
                            text = "登录后同步想买收藏与发售提醒",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    shape = MaterialTheme.shapes.pill,
                    color = colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (isLoggedIn) "查看清单" else "去登录",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSecondaryContainer
                        )
                        Icon(
                            painter = painterResource(PoiIcons.ChevronRight),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colorScheme.onSecondaryContainer
                        )
                    }
                }
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
