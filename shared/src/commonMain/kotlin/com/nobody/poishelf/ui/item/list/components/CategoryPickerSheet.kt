package com.nobody.poishelf.ui.item.list.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.SheetValue
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
import com.nobody.poishelf.data.local.CategoryItem
import com.nobody.poishelf.ui.theme.ExpressiveTokens

/**
 * 周边分类与子分类一体化双栏级联选择底板：
 * 左栏切换大分类，右栏即点即生效选中具体子类（或当前大类的全部），一步关闭完成筛选。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    selectedCategoryId: Int,
    selectedSubCategoryId: Int?,
    getSubCategories: (parentId: Int) -> List<CategoryItem>,
    onCategorySelected: (categoryId: Int, subCategoryId: Int?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    // 左侧选中的大类，默认定位到当前激活的大类
    var previewPrimaryId by remember { mutableIntStateOf(selectedCategoryId) }

    val primaryCategories = remember {
        listOf(
            CategoryItem(10000, "全部周边"),
            CategoryItem(100, "手办"),
            CategoryItem(200, "动漫模型"),
            CategoryItem(500, "真实模型"),
            CategoryItem(300, "娃娃人偶"),
            CategoryItem(400, "毛绒布偶"),
            CategoryItem(900, "动漫周边")
        )
    }

    // 选中的大类名称
    val activePrimary = primaryCategories.find { it.id == previewPrimaryId } ?: primaryCategories.first()

    // 右侧子分类列表
    val subList = remember(previewPrimaryId) {
        if (previewPrimaryId == 10000) {
            emptyList()
        } else {
            getSubCategories(previewPrimaryId).filter { it.id != previewPrimaryId }
        }
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
            // 1. 顶栏：标题 + 关闭按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择周边分类",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // 2. 双栏级联内容区（固定高度 360dp，保证视觉舒适度）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                // ── 左栏：大分类列表 ──
                LazyColumn(
                    modifier = Modifier
                        .width(112.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    items(primaryCategories, key = { it.id }) { cat ->
                        val isCurrentPrimary = cat.id == previewPrimaryId
                        val isApplied = cat.id == selectedCategoryId

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { previewPrimaryId = cat.id }
                                .background(
                                    if (isCurrentPrimary) {
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
                                if (isCurrentPrimary) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 3.dp, height = 14.dp)
                                            .clip(ExpressiveTokens.PillShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.width(3.dp))
                                }

                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrentPrimary || isApplied) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrentPrimary) {
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

                // ── 右栏：子分类列表（即点即生效） ──
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    // 第 1 项：全部 [大分类] 项
                    item(key = "all_${activePrimary.id}") {
                        val isAllSelected = (activePrimary.id == 10000 && selectedCategoryId == 10000) ||
                                (selectedCategoryId == activePrimary.id && selectedSubCategoryId == null)

                        CategorySubItemRow(
                            name = if (activePrimary.id == 10000) "全部周边" else "全部${activePrimary.name}",
                            isSelected = isAllSelected,
                            onClick = {
                                onCategorySelected(activePrimary.id, null)
                                onDismiss()
                            }
                        )
                    }

                    // 后续项：各个具体子分类
                    items(subList, key = { it.id }) { sub ->
                        val isSelected = selectedCategoryId == activePrimary.id && selectedSubCategoryId == sub.id

                        CategorySubItemRow(
                            name = sub.name,
                            isSelected = isSelected,
                            onClick = {
                                onCategorySelected(activePrimary.id, sub.id)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 右栏单项排版组件
 */
@Composable
private fun CategorySubItemRow(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ExpressiveTokens.SmallCardShape)
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
                imageVector = Icons.Filled.Done,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
