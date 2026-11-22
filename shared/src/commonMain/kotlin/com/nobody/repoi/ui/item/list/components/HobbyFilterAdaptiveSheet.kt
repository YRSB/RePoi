package com.nobody.repoi.ui.item.list.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.nobody.repoi.ui.item.list.FilterLabels
import com.nobody.repoi.ui.item.list.HobbyListNavParam
import com.nobody.repoi.ui.common.ModalSideSheet
import androidx.compose.foundation.shape.CircleShape
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HobbyFilterAdaptiveSheet(
    filterParam: HobbyAdvancedFilterParam,
    onDismiss: () -> Unit,
    onApply: (HobbyAdvancedFilterParam) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isWideScreen = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)

    if (isWideScreen) {
        ModalSideSheet(
            onDismissRequest = onDismiss,
            modifier = modifier
        ) {
            HobbyFilterSheetContent(
                filterParam = filterParam,
                onDismiss = onDismiss,
                onApply = onApply,
                onReset = onReset,
                showCloseButton = true
            )
        }
    } else {
        val sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier
        ) {
            HobbyFilterSheetContent(
                filterParam = filterParam,
                onDismiss = onDismiss,
                onApply = onApply,
                onReset = onReset,
                showCloseButton = false
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HobbyFilterSheetContent(
    filterParam: HobbyAdvancedFilterParam,
    onDismiss: () -> Unit,
    onApply: (HobbyAdvancedFilterParam) -> Unit,
    onReset: () -> Unit,
    showCloseButton: Boolean
) {
    var selectedSex by remember { mutableStateOf(filterParam.sex) }
    var selectedSpecs by remember { mutableStateOf(HobbyListNavParam.parseSpecsSet(filterParam.specs)) }
    var selectedScale by remember { mutableStateOf(filterParam.scale) }
    var selectedR18 by remember { mutableStateOf(filterParam.r18) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "高级多维筛选",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (showCloseButton) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(PoiIcons.Filled.Close),
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            val sexList = listOf(null to "不限") + FilterLabels.sexOrder.map { it to (FilterLabels.sexNames[it] ?: it.toString()) }
            FlowFilterSection(
                title = "性别",
                items = sexList,
                isSelected = { (sexVal, _) -> selectedSex == sexVal },
                onSelect = { (sexVal, _) -> selectedSex = if (selectedSex == sexVal) null else sexVal },
                label = { it.second }
            )

            val specsList = FilterLabels.specsOrder.map { it to (FilterLabels.specsNames[it] ?: it) }
            FlowFilterSection(
                title = "属性",
                items = specsList,
                isSelected = { (specsVal, _) -> specsVal in selectedSpecs },
                onSelect = { (specsVal, _) ->
                    selectedSpecs = if (specsVal in selectedSpecs) {
                        selectedSpecs - specsVal
                    } else {
                        selectedSpecs + specsVal
                    }
                },
                label = { it.second }
            )

            val scaleList = listOf(null to "不限") + FilterLabels.scaleOrder.map { code ->
                code to (FilterLabels.scaleName(code) ?: code)
            }
            FlowFilterSection(
                title = "比例",
                items = scaleList,
                isSelected = { (scaleVal, _) -> selectedScale == scaleVal },
                onSelect = { (scaleVal, _) -> selectedScale = if (selectedScale == scaleVal) null else scaleVal },
                label = { it.second }
            )

            val r18List = listOf(null to "不限") + FilterLabels.r18Order.map { it to (FilterLabels.r18Names[it] ?: it.toString()) }
            FlowFilterSection(
                title = "观感",
                items = r18List,
                isSelected = { (r18Val, _) -> selectedR18 == r18Val },
                onSelect = { (r18Val, _) -> selectedR18 = if (selectedR18 == r18Val) null else r18Val },
                label = { it.second }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    selectedSex = null
                    selectedSpecs = emptySet()
                    selectedScale = null
                    selectedR18 = null
                    onReset()
                },
                modifier = Modifier.weight(1f),
                shape = CircleShape
            ) {
                Text(
                    text = "重置",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Button(
                onClick = {
                    onApply(
                        HobbyAdvancedFilterParam(
                            sex = selectedSex,
                            specs = HobbyListNavParam.joinSpecs(selectedSpecs),
                            scale = selectedScale,
                            r18 = selectedR18
                        )
                    )
                },
                modifier = Modifier.weight(1f),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "确定",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FlowFilterSection(
    title: String,
    items: List<T>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items.forEach { item ->
                val selected = isSelected(item)
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(item) },
                    label = {
                        Text(
                            text = label(item),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                painter = painterResource(PoiIcons.Filled.Done),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else null,
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = null
                )
            }
        }
    }
}
