package com.nobody.repoi.ui.resale.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nobody.repoi.data.local.ResaleProvince
import com.nobody.repoi.data.local.ResaleRegionData
import androidx.compose.foundation.shape.CircleShape
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResaleRegionPickerSheet(
    selectedRegionId: String?,
    onRegionSelected: (regionId: String?, label: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    val allProvinceList = remember {
        listOf(ResaleProvince(id = 0, name = "全部区域")) + ResaleRegionData.provinces
    }

    val initialProvinceId = remember(selectedRegionId) {
        val selId = selectedRegionId?.toIntOrNull()
        ResaleRegionData.findProvinceId(selId)
    }

    var previewProvinceId by remember { mutableIntStateOf(initialProvinceId) }

    val activeProvince = remember(previewProvinceId) {
        allProvinceList.find { it.id == previewProvinceId } ?: allProvinceList.first()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择交易地区",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(PoiIcons.Filled.Close),
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {

                LazyColumn(
                    modifier = Modifier
                        .width(112.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    items(allProvinceList, key = { it.id }) { prov ->
                        val isCurrent = prov.id == previewProvinceId
                        val isApplied = if (prov.id == 0) {
                            selectedRegionId.isNullOrBlank() || selectedRegionId == "0"
                        } else {
                            selectedRegionId == prov.id.toString() || prov.cities.any { it.id.toString() == selectedRegionId }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { previewProvinceId = prov.id }
                                .background(
                                    if (isCurrent) {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    }
                                )
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isCurrent) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 3.dp, height = 14.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(3.dp))
                                }

                                Text(
                                    text = prov.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent || isApplied) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.primary
                                    } else if (isApplied) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    if (activeProvince.id == 0) {

                        item(key = "region_all_china") {
                            val isAllSelected = selectedRegionId.isNullOrBlank() || selectedRegionId == "0"
                            RegionSubItemRow(
                                name = "全国",
                                isSelected = isAllSelected,
                                onClick = {
                                    onRegionSelected(null, "区域")
                                    onDismiss()
                                }
                            )
                        }
                    } else {

                        val allLabel = when {
                            activeProvince.name.endsWith("市") -> "全市"
                            activeProvince.name.endsWith("省") -> "全省"
                            else -> "全部${activeProvince.name}"
                        }
                        item(key = "region_prov_all_${activeProvince.id}") {
                            val isProvAllSelected = selectedRegionId == activeProvince.id.toString()
                            RegionSubItemRow(
                                name = allLabel,
                                isSelected = isProvAllSelected,
                                onClick = {
                                    onRegionSelected(activeProvince.id.toString(), activeProvince.name)
                                    onDismiss()
                                }
                            )
                        }

                        items(activeProvince.cities, key = { it.id }) { city ->
                            val isSelected = selectedRegionId == city.id.toString()
                            RegionSubItemRow(
                                name = city.name,
                                isSelected = isSelected,
                                onClick = {
                                    onRegionSelected(city.id.toString(), city.name)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionSubItemRow(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (isSelected) {
            Icon(
                painter = painterResource(PoiIcons.Filled.Done),
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
