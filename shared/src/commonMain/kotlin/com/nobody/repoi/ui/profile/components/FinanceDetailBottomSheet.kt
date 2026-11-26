package com.nobody.repoi.ui.profile.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.nobody.repoi.data.remote.model.CategoryMoneyGroup
import com.nobody.repoi.data.remote.model.CurrencyType
import com.nobody.repoi.data.remote.model.OrderFinanceSummary
import com.nobody.repoi.ui.common.PoiIcons
import com.nobody.repoi.ui.theme.bottomSheet
import com.nobody.repoi.util.PriceFormatter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceDetailBottomSheet(
    finance: OrderFinanceSummary,
    initialTab: Int = 0,
    isExpandedScreen: Boolean = false,
    onDismissRequest: () -> Unit
) {
    if (isExpandedScreen) {
        Dialog(onDismissRequest = onDismissRequest) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .padding(vertical = 16.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                FinanceDetailContent(
                    finance = finance,
                    initialTab = initialTab,
                    onDismissRequest = onDismissRequest,
                    modifier = Modifier.padding(top = 18.dp, bottom = 16.dp)
                )
            }
        }
    } else {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            shape = MaterialTheme.shapes.bottomSheet,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            FinanceDetailContent(
                finance = finance,
                initialTab = initialTab,
                onDismissRequest = onDismissRequest,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinanceDetailContent(
    finance: OrderFinanceSummary,
    initialTab: Int,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabTitles = listOf("待补款明细", "已入总额明细")
    val pagerState = rememberPagerState(
        initialPage = initialTab.coerceIn(0, tabTitles.lastIndex),
        pageCount = { tabTitles.size }
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(initialTab) {
        val target = initialTab.coerceIn(0, tabTitles.lastIndex)
        if (pagerState.currentPage != target && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(target)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "资产与订单金额明细",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                onClick = onDismissRequest,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(PoiIcons.Filled.Close),
                    contentDescription = "关闭",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            divider = {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                val isSelected = pagerState.currentPage == index
                Tab(
                    selected = isSelected,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
            verticalAlignment = Alignment.Top
        ) { page ->
            val totalRmb = if (page == 0) finance.supplementedTotalRmb else finance.historyTotalRmb
            val groups = if (page == 0) finance.supplementedGroups else finance.historyGroups
            val summaryLabel = if (page == 0) "待补款总折合" else "已入总投入"

            FinanceDetailPage(
                page = page,
                summaryLabel = summaryLabel,
                totalRmb = totalRmb,
                groups = groups
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FinanceDetailPage(
    page: Int,
    summaryLabel: String,
    totalRmb: Double,
    groups: List<CategoryMoneyGroup>
) {
    val positiveGroups = groups
        .filter { it.totalRmb > 0.0 || it.items.any { item -> item.money > 0.0 } }
        .sortedByDescending { it.totalRmb }
    val hasForeignCurrency = positiveGroups.any { group ->
        group.items.any { it.money > 0.0 && it.currencyType != CurrencyType.CNY }
    }
    val positiveTotal = positiveGroups.sumOf { it.totalRmb }
    val showSharePercent = positiveGroups.size > 1 && positiveTotal > 0.0

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
    ) {
        item(
            key = "summary_$page",
            contentType = "summary"
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = summaryLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatRmb(totalRmb),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFeatureSettings = "tnum"
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (hasForeignCurrency) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "注：外币折算按近似固定汇率估算（JPY×0.06、USD×6.4、EUR×6.9、HKD×0.92）",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        if (positiveGroups.isEmpty()) {
            item(
                key = "empty_$page",
                contentType = "empty"
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无对应金额记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        } else {
            itemsIndexed(
                items = positiveGroups,
                key = { index, group -> "${page}_${index}_${group.categoryKey}" },
                contentType = { _, _ -> "category_group" }
            ) { index, group ->
                val sharePercent = if (showSharePercent) {
                    ((group.totalRmb / positiveTotal) * 100.0).roundToInt()
                } else {
                    null
                }
                FinanceCategoryCard(
                    group = group,
                    sharePercent = sharePercent,
                    shape = segmentedCardShape(index = index, count = positiveGroups.size)
                )
            }
        }
    }
}

private fun segmentedCardShape(index: Int, count: Int): Shape {
    val outer = 16.dp
    val inner = 4.dp
    return when {
        count <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
        else -> RoundedCornerShape(inner)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinanceCategoryCard(
    group: CategoryMoneyGroup,
    sharePercent: Int?,
    shape: Shape
) {
    val positiveItems = group.items.filter { it.money > 0.0 }
    val isSingleCny = positiveItems.size == 1 && positiveItems.first().currencyType == CurrencyType.CNY

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = group.categoryName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (sharePercent != null) {
                        Text(
                            text = "${sharePercent}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = if (isSingleCny) formatRmb(group.totalRmb) else "折合 " + formatRmb(group.totalRmb),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!isSingleCny && positiveItems.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    positiveItems.forEach { currencyItem ->
                        val formattedCurrency = PriceFormatter.formatCurrency(currencyItem.currency)
                        val moneyText = if (currencyItem.money % 1.0 == 0.0) {
                            formatGrouped(currencyItem.money.toLong())
                        } else {
                            formatGrouped(currencyItem.money)
                        }
                        val displayText = if (formattedCurrency.isNotBlank()) {
                            "$formattedCurrency: $moneyText"
                        } else {
                            moneyText
                        }
                        Text(
                            text = displayText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
