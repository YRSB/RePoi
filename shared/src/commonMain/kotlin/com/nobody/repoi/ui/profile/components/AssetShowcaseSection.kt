package com.nobody.repoi.ui.profile.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nobody.repoi.data.remote.model.CollectTopItem
import com.nobody.repoi.data.remote.model.OrderCount
import com.nobody.repoi.data.remote.model.OrderFinanceSummary
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.util.PriceFormatter
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
    val colorScheme = MaterialTheme.colorScheme
    val fluctuationColor = when {
        fluctuation.startsWith("+") -> colorScheme.error
        fluctuation.startsWith("-") -> colorScheme.primary
        else -> colorScheme.onSurfaceVariant
    }

    SegmentedShowcasePanel(
        stats = listOf(
            ShowcaseStatSpec(
                title = "已入",
                value = buyCount.toString(),
                valueColor = colorScheme.onSurface,
                onClick = {
                    if (isLoggedIn) onNavigateToCollect("buy") else onLoginClick()
                }
            ),
            ShowcaseStatSpec(
                title = "总投入",
                value = historyRmb,
                weight = 1.25f,
                titleColor = colorScheme.secondary.copy(alpha = 0.85f),
                valueColor = colorScheme.secondary,
                onClick = {
                    if (isLoggedIn) onMoneyClick() else onLoginClick()
                }
            ),
            ShowcaseStatSpec(
                title = "日亚浮动",
                value = fluctuation,
                valueColor = fluctuationColor,
                onClick = null
            )
        ),
        items = buyItems,
        onItemClick = onItemClick,
        modifier = modifier
    )
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
