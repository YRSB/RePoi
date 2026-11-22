package com.nobody.repoi.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nobody.repoi.ui.home.ZoneSwitcherGroup
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

private const val ZONE_SWITCHER_CONTENT_DESCRIPTION = "切换专区"

@Composable
internal fun ZoneSwitcherAnchor(
    currentTitle: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = ZONE_SWITCHER_CONTENT_DESCRIPTION }
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = currentTitle,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Icon(
            painter = painterResource(if (expanded) PoiIcons.Filled.ExpandLess else PoiIcons.Filled.ExpandMore),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ZoneSwitcherContent(
    groups: List<ZoneSwitcherGroup>,
    selectedTabKey: String,
    onSelect: (tabKey: String) -> Unit,
    onBrowseAllZones: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(vertical = 8.dp)
    ) {
        groups.forEach { group ->
            group.title?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                )
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                group.items.forEach { item ->
                    val selected = item.tabKey == selectedTabKey
                    FilterChip(
                        selected = selected,
                        onClick = { onSelect(item.tabKey) },
                        label = {
                            Text(
                                text = item.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        },
                        modifier = Modifier.semantics { role = Role.RadioButton }
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onBrowseAllZones,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text("浏览全部专区")
            }
            FilledTonalButton(
                onClick = onOpenHomeSettings,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text("首页设置")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ZoneSwitcherSheet(
    groups: List<ZoneSwitcherGroup>,
    selectedTabKey: String,
    onSelect: (tabKey: String) -> Unit,
    onBrowseAllZones: () -> Unit,
    onOpenHomeSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height

    val maxContentHeight: Dp = if (windowHeightPx > 0) {
        with(density) { (windowHeightPx * 0.7f).toDp() }
    } else {
        Dp.Infinity
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        ZoneSwitcherContent(
            groups = groups,
            selectedTabKey = selectedTabKey,
            onSelect = onSelect,
            onBrowseAllZones = onBrowseAllZones,
            onOpenHomeSettings = onOpenHomeSettings,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxContentHeight)
                .verticalScroll(rememberScrollState())
        )
    }
}

@Composable
internal fun ZoneSwitcherDropdown(
    currentTitle: String,
    groups: List<ZoneSwitcherGroup>,
    selectedTabKey: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (tabKey: String) -> Unit,
    onBrowseAllZones: () -> Unit,
    onOpenHomeSettings: () -> Unit
) {
    Box {
        ZoneSwitcherAnchor(
            currentTitle = currentTitle,
            expanded = expanded,
            onClick = { onExpandedChange(!expanded) }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,

            modifier = Modifier.widthIn(min = 360.dp)
        ) {
            ZoneSwitcherContent(
                groups = groups,
                selectedTabKey = selectedTabKey,
                onSelect = onSelect,
                onBrowseAllZones = onBrowseAllZones,
                onOpenHomeSettings = onOpenHomeSettings,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
