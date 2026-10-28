package com.nobody.poishelf.ui.collect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import coil3.compose.AsyncImage
import com.nobody.poishelf.data.remote.model.CollectConstants
import com.nobody.poishelf.ui.navigation.CollectEditNavParam
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.util.CdnUrlUtils
import com.nobody.poishelf.platform.DateTimeFormats
import com.nobody.poishelf.platform.nowMillis

/** 收藏日期格式化：`yyyy-MM-dd` 经由 [DateTimeFormats.ymdFromMillis]（KMP datetime，无 SDF 单例）。 */

/**
 * 收藏管理与编辑主界面。
 * 驱动 5 态互斥切换、入手信息动态展示、评分与标签选择、保存与取消收藏全生命周期。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectEditScreen(
    param: CollectEditNavParam,
    onBack: () -> Unit,
    viewModel: CollectEditViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 初始化加载数据
    LaunchedEffect(param) {
        viewModel.initData(param)
    }

    // 操作结果副作用分发：静默返回，由目标页面的响应式流驱动视觉反馈
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onBack()
        }
    }

    LaunchedEffect(uiState.deleteSuccess) {
        if (uiState.deleteSuccess) {
            onBack()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isAlreadyCollected) "编辑收藏" else "添加收藏",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    if (uiState.isAlreadyCollected) {
                        IconButton(
                            onClick = { viewModel.setDeleteConfirmDialogVisible(true) },
                            enabled = !uiState.isSaving && !uiState.isDeleting
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "取消收藏",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    TextButton(
                        onClick = { viewModel.saveCollect() },
                        enabled = !uiState.isSaving && !uiState.isDeleting && !uiState.isLoading && !uiState.loadFailed
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "保存",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 1. 条目头部微缩卡片
                if (uiState.loadFailed) {
                    item(key = "load_error") {
                        CollectCardSection {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "加载失败，表单为默认值，保存已禁用",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = { viewModel.retryLoad(param) }) {
                                    Text("重试")
                                }
                            }
                        }
                    }
                }
                item(key = "header") {
                    CollectItemHeader(
                        name = uiState.name,
                        cover = uiState.cover,
                        currentState = uiState.selectedState,
                        isAlreadyCollected = uiState.isAlreadyCollected
                    )
                }

                // 2. 5 种互斥分类状态卡片选择栏
                item(key = "state_selector") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "选择状态",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        CollectStateGrid(
                            selectedState = uiState.selectedState,
                            countData = uiState.countData,
                            onSelectState = { viewModel.selectState(it) }
                        )
                    }
                }

                // 3. 评分面板 (0..5 星级)
                item(key = "rating_panel") {
                    CollectRatingSection(
                        rating = uiState.rating,
                        onRatingChange = { viewModel.setRating(it) },
                        onClearRating = { viewModel.clearRating() }
                    )
                }

                // 4. 入手/动态详情区（根据 selectedState 动态展开）
                item(key = "order_info_panel") {
                    AnimatedVisibility(
                        visible = uiState.selectedState in listOf(
                            CollectConstants.STATE_PREORDER,
                            CollectConstants.STATE_BUY,
                            CollectConstants.STATE_RESELL
                        ),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        CollectOrderDetailsSection(
                            state = uiState.selectedState,
                            orderTime = uiState.orderTime,
                            orderMoney = uiState.orderMoney,
                            orderMoneyCurrency = uiState.orderMoneyCurrency,
                            orderWay = uiState.orderWay,
                            buyNum = uiState.buyNum,
                            orderForm = uiState.orderForm,
                            onOrderTimeClick = { viewModel.setDatePickerVisible(true) },
                            onOrderMoneyChange = { viewModel.setOrderMoney(it) },
                            onCurrencyChange = { viewModel.setCurrency(it) },
                            onOrderWayChange = { viewModel.setOrderWay(it) },
                            onBuyNumChange = { viewModel.setBuyNum(it) },
                            onOrderFormChange = { viewModel.setOrderForm(it) }
                        )
                    }
                }

                // 5. 标签区（我的标签 + 热门备选标签池）
                item(key = "tags_panel") {
                    CollectTagsSection(
                        myTags = uiState.myTags,
                        suggestedTags = uiState.suggestedTags,
                        onAddTagClick = { viewModel.setAddTagDialogVisible(true) },
                        onRemoveTag = { viewModel.requestDeleteTag(it) },
                        onSelectSuggestedTag = { viewModel.addTag(it) }
                    )
                }

                // 6. 评价简评输入框
                item(key = "comment_panel") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "简评",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = uiState.comment,
                            onValueChange = { viewModel.setComment(it) },
                            placeholder = { Text("(选填)填写您的简评") },
                            supportingText = {
                                Text(
                                    text = "${uiState.commentLength} / 500",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.End,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                            shape = ExpressiveTokens.CardShape
                        )
                    }
                }

                // 7. 底部主操作区
                item(key = "actions") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.saveCollect() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = ExpressiveTokens.PillShape,
                            enabled = !uiState.isSaving && !uiState.isDeleting && !uiState.isLoading && !uiState.loadFailed
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(
                                    text = if (uiState.isAlreadyCollected) "保存修改" else "加入收藏",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        if (uiState.isAlreadyCollected) {
                            OutlinedButton(
                                onClick = { viewModel.setDeleteConfirmDialogVisible(true) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = ExpressiveTokens.PillShape,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                enabled = !uiState.isSaving && !uiState.isDeleting && !uiState.loadFailed
                            ) {
                                if (uiState.isDeleting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Text(
                                        text = "取消收藏",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 弹窗集合 ──

    // 1. 日期选择器弹窗
    if (uiState.showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = runCatching {
                DateTimeFormats.parseServerDateTime(uiState.orderTime)
            }.getOrNull() ?: nowMillis()
        )
        DatePickerDialog(
            onDismissRequest = { viewModel.setDatePickerVisible(false) },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selectedDate = DateTimeFormats.ymdFromMillis(millis)
                        viewModel.setOrderTime(selectedDate)
                    }
                    viewModel.setDatePickerVisible(false)
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setDatePickerVisible(false) }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // 2. 添加标签弹窗
    if (uiState.showAddTagDialog) {
        var inputTag by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.setAddTagDialogVisible(false) },
            title = { Text("添加标签") },
            text = {
                OutlinedTextField(
                    value = inputTag,
                    onValueChange = { inputTag = it },
                    placeholder = { Text("输入自定义标签内容") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (inputTag.isNotBlank()) {
                            viewModel.addTag(inputTag)
                        }
                        viewModel.setAddTagDialogVisible(false)
                    }
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setAddTagDialogVisible(false) }) {
                    Text("取消")
                }
            }
        )
    }

    // 3. 取消收藏二次确认对话框
    if (uiState.showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setDeleteConfirmDialogVisible(false) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("取消收藏") },
            text = { Text("确定要取消收藏该手办吗？已填写的入手信息与评分记录将被清除。") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteCollect() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确定取消")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setDeleteConfirmDialogVisible(false) }) {
                    Text("保留收藏")
                }
            }
        )
    }

    // 4. 删除已有标签二次确认对话框
    if (uiState.showDeleteTagDialog && uiState.tagToDelete != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteTagDialog() },
            shape = ExpressiveTokens.DialogShape,
            title = { Text("删除标签") },
            text = { Text("确定要删除标签「${uiState.tagToDelete}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmDeleteTag() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确定删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteTagDialog() }) {
                    Text("取消")
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 子组件：条目头部摘要卡片 (CollectItemHeader)
// ─────────────────────────────────────────────────────────────

@Composable
private fun CollectItemHeader(
    name: String?,
    cover: String?,
    currentState: String,
    isAlreadyCollected: Boolean,
    modifier: Modifier = Modifier
) {
    CollectCardSection(
        modifier = modifier,
        tonalElevation = ExpressiveTokens.ElevationLevel1
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val coverUrl = cover?.let { CdnUrlUtils.getCoverUrl(it) } ?: ""
            AsyncImage(
                model = coverUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 56.dp, height = 76.dp)
                    .clip(ExpressiveTokens.SmallCardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = name ?: "手办详情",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = ExpressiveTokens.ExtraSmallShape,
                        color = if (isAlreadyCollected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    ) {
                        Text(
                            text = if (isAlreadyCollected) "已收藏 · ${CollectConstants.getStateLabel(currentState)}" else "未收藏",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isAlreadyCollected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 子组件：5 态分类网格 (CollectStateGrid)
// ─────────────────────────────────────────────────────────────

@Composable
private fun CollectStateGrid(
    selectedState: String,
    countData: com.nobody.poishelf.data.remote.model.CollectCountData,
    onSelectState: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CollectConstants.ORDERED_STATES.forEach { stateKey ->
            val isSelected = selectedState == stateKey
            val label = CollectConstants.getStateLabel(stateKey)
            val count = countData.getCountForState(stateKey)

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(ExpressiveTokens.MediumCardShape)
                    .clickable { onSelectState(stateKey) },
                shape = ExpressiveTokens.MediumCardShape,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary
                    )
                } else null
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = "$count",
                        fontSize = 11.sp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 子组件：评分星级面板 (CollectRatingSection)
// ─────────────────────────────────────────────────────────────

@Composable
private fun CollectRatingSection(
    rating: Int,
    onRatingChange: (Int) -> Unit,
    onClearRating: () -> Unit,
    modifier: Modifier = Modifier
) {
    CollectCardSection(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "手办评分",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (rating > 0) "${CollectConstants.getRatingLabel(rating)} ($rating 星)" else "点击星星打分",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (rating > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (rating > 0) FontWeight.Bold else FontWeight.Normal
                    )
                    if (rating > 0) {
                        TextButton(
                            onClick = onClearRating,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                        ) {
                            Text("清除评分", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                (1..5).forEach { star ->
                    val isFilled = star <= rating
                    IconButton(onClick = { onRatingChange(star) }) {
                        Icon(
                            imageVector = if (isFilled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "$star 星",
                            tint = if (isFilled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 子组件：入手/预订详情表单 (CollectOrderDetailsSection)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectOrderDetailsSection(
    state: String,
    orderTime: String,
    orderMoney: String,
    orderMoneyCurrency: String,
    orderWay: String,
    buyNum: Int,
    orderForm: Int,
    onOrderTimeClick: () -> Unit,
    onOrderMoneyChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onOrderWayChange: (String) -> Unit,
    onBuyNumChange: (Int) -> Unit,
    onOrderFormChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isPreorder = state == CollectConstants.STATE_PREORDER
    val isBuy = state == CollectConstants.STATE_BUY
    val isResell = state == CollectConstants.STATE_RESELL

    val sectionTitle = when {
        isPreorder -> "预定信息"
        isBuy -> "入手信息"
        else -> "出掉信息"
    }

    val dateLabel = if (isPreorder) "预定日期" else "入手日期"
    val priceLabel = if (isPreorder) "补款预定金额" else "入手价格"
    val channelLabel = if (isPreorder) "预定途径" else if (isBuy) "入手途径" else "出掉途径"

    CollectCardSection(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 1. 日期选择
            OutlinedCard(
                onClick = onOrderTimeClick,
                modifier = Modifier.fillMaxWidth(),
                shape = ExpressiveTokens.MediumCardShape
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = orderTime.ifBlank { "点击选择日期" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. 金额与币种选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = orderMoney,
                    onValueChange = onOrderMoneyChange,
                    label = { Text(priceLabel) },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    shape = ExpressiveTokens.MediumCardShape,
                    singleLine = true
                )

                // 币种下拉选择
                var currencyExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = currencyExpanded,
                    onExpandedChange = { currencyExpanded = it },
                    modifier = Modifier.width(110.dp)
                ) {
                    OutlinedTextField(
                        value = CollectConstants.getCurrencyLabel(orderMoneyCurrency),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("币种") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                        // KMP CMP 1.12.1：menuAnchor() 无 AnchorType 参数（material3 旧 ExposedDropdownMenu API 已更名）。
                        modifier = Modifier.menuAnchor(),
                        shape = ExpressiveTokens.MediumCardShape,
                        singleLine = true
                    )
                    // KMP CMP 1.12.1：旧 ExposedDropdownMenu 更名为 DropdownMenu（同 Box 作用域 API）。
                    androidx.compose.material3.DropdownMenu(
                        expanded = currencyExpanded,
                        onDismissRequest = { currencyExpanded = false }
                    ) {
                        CollectConstants.CURRENCIES.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onCurrencyChange(code)
                                    currencyExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // 3. 入手途径
            OutlinedTextField(
                value = orderWay,
                onValueChange = onOrderWayChange,
                label = { Text(channelLabel) },
                placeholder = { Text(if (isPreorder) "如：官方旗舰店、AmiAmi、淘宝" else "如：现货现拍、漫展收物、闲鱼") },
                modifier = Modifier.fillMaxWidth(),
                shape = ExpressiveTokens.MediumCardShape,
                singleLine = true
            )

            // 4. 购买数量（仅已入状态支持）
            if (isBuy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "入手数量",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onBuyNumChange((buyNum - 1).coerceAtLeast(1)) },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape
                        ) {
                            Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "$buyNum",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        OutlinedButton(
                            onClick = { onBuyNumChange(buyNum + 1) },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape
                        ) {
                            Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 5. 入手形式 Chips（已入与有过支持）
            if (isBuy || isResell) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "入手形式",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CollectConstants.ORDER_FORMS.forEach { (formId, formLabel) ->
                            val isSelected = orderForm == formId
                            FilterChip(
                                selected = isSelected,
                                onClick = { onOrderFormChange(formId) },
                                label = { Text(formLabel, fontSize = 12.sp) },
                                shape = ExpressiveTokens.ChipShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 子组件：标签管理面板 (CollectTagsSection)
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectTagsSection(
    myTags: List<String>,
    suggestedTags: List<String>,
    onAddTagClick: () -> Unit,
    onRemoveTag: (String) -> Unit,
    onSelectSuggestedTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    CollectCardSection(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "我的标签",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = onAddTagClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加自定义标签", fontSize = 12.sp)
                }
            }

            // 我的已有标签列表
            if (myTags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    myTags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = {},
                            label = { Text(tag, fontSize = 12.sp) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "删除",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onRemoveTag(tag) }
                                )
                            },
                            shape = ExpressiveTokens.ChipShape,
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }
            } else {
                Text(
                    text = "暂无标签，可点击下方推荐标签快速添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 热门推荐标签池
            if (suggestedTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "推荐候选标签",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestedTags.forEach { tag ->
                        val alreadyAdded = myTags.contains(tag)
                        SuggestionChip(
                            onClick = { if (!alreadyAdded) onSelectSuggestedTag(tag) },
                            label = { Text(tag, fontSize = 12.sp) },
                            icon = if (alreadyAdded) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            enabled = !alreadyAdded,
                            shape = ExpressiveTokens.ChipShape
                        )
                    }
                }
            }
        }
    }
}

// 收敛多处卡片容器样板，避免形状与层级分散定义
@Composable
private fun CollectCardSection(
    modifier: Modifier = Modifier,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveTokens.CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = tonalElevation
    ) {
        content()
    }
}
