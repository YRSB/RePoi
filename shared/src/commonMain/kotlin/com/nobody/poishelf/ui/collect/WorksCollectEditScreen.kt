package com.nobody.poishelf.ui.collect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.util.CdnUrlUtils

/**
 * 作品收藏编辑页（原版 `net.hpoi.ui.collect.CollectWorksActivity`）。
 *
 * 整链走作品专属接口 `api/works/glean/get|add|del` + `api/user/glean/textTag/get`，
 * 与周边收藏编辑页（`CollectEditScreen`，`api/collect` 系列接口）**完全是两套接口**，故单独成页。
 *
 * 功能对齐：
 * - 三档收藏状态 RadioGroup，`categoryId == 10200`（游戏）时文案变体为「想玩/在玩/玩过」；
 * - 官方标签候选池 + 自定义标签（单标签 1~8 字、最多 15 个、去重，超限提示「单个作品最多设置15个标签」）；
 * - 简评选填，上限 500 字；
 * - 保存 / 取消收藏（二次确认「是否取消收藏后将移出收藏中心取消收藏」）。
 *
 * 保存成功后通过 [onResult] 回传当前收藏状态，供作品详情页刷新收藏标识。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorksCollectEditScreen(
    param: WorksCollectEditNavParam,
    onBack: () -> Unit,
    onResult: (worksId: Long, state: Int) -> Unit = { _, _ -> },
    viewModel: WorksCollectEditViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(param) {
        viewModel.initData(param)
    }

    // 保存成功：回传结果后退出，由作品详情页刷新收藏态
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onResult(uiState.worksId, uiState.selectedState)
            onBack()
        }
    }

    // 取消收藏成功：state 回传 0 表示「已取消」（对齐原版 `CollectWorksActivity$a` 的 `putExtra("state", 0)`）
    LaunchedEffect(uiState.deleteSuccess) {
        if (uiState.deleteSuccess) {
            onResult(uiState.worksId, 0)
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
                        text = if (uiState.isAlreadyCollected) "编辑收藏" else "收藏作品",
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
                    // 原版 `CollectWorksActivity.java:469-474`：未收藏时隐藏删除入口
                    if (uiState.hasDelete) {
                        IconButton(
                            onClick = { viewModel.setDeleteConfirmDialogVisible(true) },
                            enabled = !uiState.isSaving && !uiState.isDeleting
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
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
                if (uiState.loadFailed) {
                    item(key = "load_error") {
                        WorksCollectEditSection {
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
                                TextButton(onClick = { viewModel.initData(param, force = true) }) {
                                    Text("重试")
                                }
                            }
                        }
                    }
                }

                item(key = "header") {
                    WorksCollectEditHeader(
                        name = uiState.initialName,
                        cover = uiState.initialCover,
                        isGameVariant = uiState.isGameVariant
                    )
                }

                // 三档收藏状态
                item(key = "state_selector") {
                    WorksCollectEditSection {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (uiState.isGameVariant) "游戏进度" else "观看状态",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            WorksStateOption.ALL_STATES.forEach { state ->
                                val label = uiState.stateLabel(state)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectState(state) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = uiState.selectedState == state,
                                        onClick = { viewModel.selectState(state) }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // 官方标签流 + 自定义标签
                item(key = "tags_panel") {
                    WorksCollectTagsSection(
                        myTags = uiState.myTags,
                        officialTags = uiState.selectableOfficialTags,
                        onAddTagClick = { viewModel.setAddTagDialogVisible(true) },
                        onRemoveTag = { viewModel.requestRemoveTag(it) },
                        onSelectOfficialTag = { viewModel.addTag(it) }
                    )
                }

                // 简评（选填，500 字上限）
                item(key = "comment_panel") {
                    WorksCollectEditSection {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                                        text = "${uiState.commentLength} / $MAX_WORKS_COMMENT_LENGTH",
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
                }

                // 保存 / 取消收藏
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
                                    text = if (uiState.isAlreadyCollected) "保存修改" else "收藏作品",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        if (uiState.hasDelete) {
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
                                    Text(text = "取消收藏", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 弹窗集合 ──

    // 1. 添加自定义标签
    if (uiState.showAddTagDialog) {
        var inputTag by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.setAddTagDialogVisible(false) },
            shape = ExpressiveTokens.DialogShape,
            title = { Text("添加标签") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = inputTag,
                        onValueChange = { inputTag = it },
                        placeholder = { Text("请输入标签（1~8 字）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "单个作品最多设置$MAX_WORKS_TAG_COUNT 个标签，已选 ${uiState.myTags.size} 个",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 校验失败时由 ViewModel 通过 errorMessage 提示原因，弹窗保持打开便于改正
                        if (viewModel.addTag(inputTag)) {
                            inputTag = ""
                        }
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

    // 2. 删除标签二次确认
    if (uiState.tagToDelete != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRemoveTag() },
            shape = ExpressiveTokens.DialogShape,
            title = { Text("删除标签") },
            text = { Text("确定要删除标签「${uiState.tagToDelete}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmRemoveTag() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确定删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRemoveTag() }) {
                    Text("取消")
                }
            }
        )
    }

    // 3. 取消收藏二次确认（原版 strings.xml:1202 / :1205）
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
            shape = ExpressiveTokens.DialogShape,
            title = { Text("是否取消收藏") },
            text = { Text("是否取消收藏后将移出收藏中心取消收藏") },
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
}

// ── 子组件 ────────────────────────────────────────────────

/** 通用内容分区卡片。 */
@Composable
private fun WorksCollectEditSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = ExpressiveTokens.CardShape
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

/** 作品摘要头部（封面 + 名称）。 */
@Composable
private fun WorksCollectEditHeader(
    name: String?,
    cover: String?,
    isGameVariant: Boolean,
    modifier: Modifier = Modifier
) {
    val coverUrl = remember(cover) { CdnUrlUtils.getCoverSmallUrl(cover) }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(ExpressiveTokens.SmallCardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name ?: "作品",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (isGameVariant) "游戏作品收藏" else "作品收藏",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 标签区：已选标签 + 官方标签流 + 自定义标签入口。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorksCollectTagsSection(
    myTags: List<String>,
    officialTags: List<String>,
    onAddTagClick: () -> Unit,
    onRemoveTag: (String) -> Unit,
    onSelectOfficialTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    WorksCollectEditSection(modifier = modifier) {
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
                    text = "我的标签（${myTags.size}/$MAX_WORKS_TAG_COUNT）",
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
                    text = "暂无标签，可点击下方官方标签快速添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (officialTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "官方标签",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    officialTags.forEach { tag ->
                        SuggestionChip(
                            onClick = { onSelectOfficialTag(tag) },
                            label = { Text(tag, fontSize = 12.sp) },
                            shape = ExpressiveTokens.ChipShape
                        )
                    }
                }
            } else {
                Text(
                    text = "暂无官方标签",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}