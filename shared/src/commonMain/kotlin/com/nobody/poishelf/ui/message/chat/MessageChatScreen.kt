package com.nobody.poishelf.ui.message.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nobody.poishelf.data.remote.model.ChatMessageItem
import com.nobody.poishelf.platform.DateTimeFormats
import com.nobody.poishelf.platform.plainTextClipEntry
import com.nobody.poishelf.ui.common.LocalImageViewer
import com.nobody.poishelf.ui.navigation.MessageChatNavParam
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.util.CdnUrlUtils
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * 1v1 私信聊天主界面：左右气泡流、表情选择、图片发送与轮询。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageChatScreen(
    param: MessageChatNavParam,
    onNavigateBack: () -> Unit = {},
    onNavigateToUser: (Long) -> Unit = {},
    onNavigateToHobby: (Long) -> Unit = {},
    viewModel: MessageChatViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var showMenu by remember { mutableStateOf(false) }
    val imageViewer = LocalImageViewer.current

    // 图片选择器（FileKit 跨平台；读取字节后经 VM 字节上传 API 发送）
    val imagePickerLauncher = rememberFilePickerLauncher(
        type = FileKitType.Image
    ) { file ->
        file?.let { picked ->
            coroutineScope.launch {
                runCatching {
                    val bytes = picked.readBytes()
                    val name = picked.name.ifBlank { "chat_img.jpg" }
                    viewModel.sendPicture(name, bytes)
                }.onFailure {
                    snackbarHostState.showSnackbar("读取选中图片失败")
                }
            }
        }
    }

    LaunchedEffect(param) {
        viewModel.loadInitial(param)
    }

    // 长轮询由 VM 内的 viewModelScope 驱动（3 秒间隔）；界面进入即启动、离开即停止。
    // jetbrains-lifecycle 的 LocalLifecycleOwner 在桌面目标不可用，故用 DisposableEffect 兜底。
    DisposableEffect(param) {
        viewModel.startPolling()
        onDispose {
            viewModel.stopPolling()
        }
    }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
        }
    }

    // 当收到新消息时滚动到底部
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = CdnUrlUtils.getCoverSmallUrl(uiState.targetUserHeader ?: ""),
                            contentDescription = uiState.targetUserName,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { onNavigateToUser(uiState.targetUserId) },
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = uiState.targetUserName.ifBlank { "用户 ${uiState.targetUserId}" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("查看主页") },
                                onClick = {
                                    showMenu = false
                                    onNavigateToUser(uiState.targetUserId)
                                },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Filled.Person, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (uiState.isBlocked) "解除屏蔽" else "加入黑名单",
                                        color = if (uiState.isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    if (uiState.isBlocked) viewModel.unblockUser() else viewModel.blockUser()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Block,
                                        contentDescription = null,
                                        tint = if (uiState.isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            ChatBottomInputBar(
                inputContent = uiState.inputContent,
                isSending = uiState.isSending,
                isUploadingPicture = uiState.isUploadingPicture,
                isEmojiPanelVisible = uiState.isEmojiPanelVisible,
                isBlocked = uiState.isBlocked,
                onContentChange = { viewModel.onInputContentChange(it) },
                onSend = { viewModel.sendTextMessage() },
                onPickImage = { imagePickerLauncher.launch() },
                onToggleEmoji = { viewModel.toggleEmojiPanel() },
                onSelectEmoji = { viewModel.appendEmoji(it) },
                onUnblock = { viewModel.unblockUser() }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading && uiState.messages.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.Center)
                )
            } else if (uiState.messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "开始与 ${uiState.targetUserName.ifBlank { "对方" }} 聊天吧",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (uiState.hasMore) {
                        item(key = "chat_load_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                TextButton(
                                    onClick = { viewModel.loadMoreHistory() },
                                    enabled = !uiState.isLoadingMore
                                ) {
                                    if (uiState.isLoadingMore) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                    } else {
                                        Text("加载更多历史消息", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 消息气泡流
                    val sortedMessages = uiState.messages.sortedBy { it.date }
                    items(
                        items = sortedMessages,
                        key = { it.actualMessageId }
                    ) { message ->
                        val isSelf = message.fromId != uiState.targetUserId
                        ChatBubbleItem(
                            item = message,
                            isSelf = isSelf,
                            targetUserHeader = uiState.targetUserHeader,
                            onImageClick = { url -> imageViewer.show(url) },
                            onWithdraw = { viewModel.withdrawMessage(message) },
                            onNavigateToHobby = onNavigateToHobby
                        )
                    }
                }
            }
        }
    }
}

/**
 * 二手商品/条目关联预览卡片。
 */
@Composable
private fun ChatCommodityCard(
    dataMap: com.nobody.poishelf.data.remote.model.ChatMessageDataMap,
    onNavigateToHobby: (Long) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clickable {
                val targetId = dataMap.resaleId ?: 0L
                if (targetId > 0L) onNavigateToHobby(targetId)
            },
        shape = ExpressiveTokens.SmallCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val pic = dataMap.pic
            if (!pic.isNullOrBlank()) {
                AsyncImage(
                    model = CdnUrlUtils.getCoverSmallUrl(pic),
                    contentDescription = dataMap.title,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ExpressiveTokens.ExtraSmallShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dataMap.title ?: "关联条目",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                dataMap.price?.let { p ->
                    Text(
                        text = "¥$p",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 单条聊天消息气泡项。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChatBubbleItem(
    item: ChatMessageItem,
    isSelf: Boolean,
    targetUserHeader: String?,
    onImageClick: (String) -> Unit,
    onWithdraw: () -> Unit,
    onNavigateToHobby: (Long) -> Unit
) {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
    ) {
        // 时间戳
        if (item.date > 0L) {
            val formattedTime = remember(item.date) {
                DateTimeFormats.hmFromMillis(item.date)
            }
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        Row(
            horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (!isSelf) {
                AsyncImage(
                    model = CdnUrlUtils.getCoverSmallUrl(targetUserHeader ?: ""),
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box {
                Surface(
                    shape = if (isSelf) {
                        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                    } else {
                        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                    },
                    color = if (isSelf) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                if (item.isPicture) {
                                    val fullUrl = CdnUrlUtils.getRawCoverUrl(item.content)
                                    onImageClick(fullUrl)
                                }
                            },
                            onLongClick = { showMenu = true }
                        )
                ) {
                    if (item.isPicture) {
                        AsyncImage(
                            model = CdnUrlUtils.getCoverSrUrl(item.content),
                            contentDescription = "图片消息",
                            modifier = Modifier
                                .size(width = 160.dp, height = 200.dp)
                                .clip(
                                    if (isSelf) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                                    else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                                ),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            item.dataMap?.let { dm ->
                                if (!dm.title.isNullOrBlank() || (dm.resaleId ?: 0L) > 0L) {
                                    ChatCommodityCard(dataMap = dm, onNavigateToHobby = onNavigateToHobby)
                                }
                            }
                            Text(
                                text = item.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelf) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    if (!item.isPicture) {
                        DropdownMenuItem(
                            text = { Text("复制") },
                            onClick = {
                                showMenu = false
                                coroutineScope.launch {
                                    clipboard.setClipEntry(plainTextClipEntry(item.content))
                                }
                            }
                        )
                    }
                    if (isSelf) {
                        DropdownMenuItem(
                            text = { Text("撤回", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onWithdraw()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 底部私信输入与富功能控制栏。
 */
@Composable
private fun ChatBottomInputBar(
    inputContent: String,
    isSending: Boolean,
    isUploadingPicture: Boolean,
    isEmojiPanelVisible: Boolean,
    isBlocked: Boolean,
    onContentChange: (String) -> Unit,
    onSend: () -> Unit,
    onPickImage: () -> Unit,
    onToggleEmoji: () -> Unit,
    onSelectEmoji: (String) -> Unit,
    onUnblock: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        if (isBlocked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "该用户已被加入黑名单，无法发送消息",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = onUnblock) {
                    Text("解除屏蔽")
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图片选择按钮
                IconButton(
                    onClick = onPickImage,
                    enabled = !isUploadingPicture
                ) {
                    if (isUploadingPicture) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = "发送图片",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 表情切换按钮
                IconButton(onClick = onToggleEmoji) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEmotions,
                        contentDescription = "表情",
                        tint = if (isEmojiPanelVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 输入框
                OutlinedTextField(
                    value = inputContent,
                    onValueChange = onContentChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp, max = 110.dp),
                    placeholder = { Text("发送私信...", fontSize = 14.sp) },
                    shape = ExpressiveTokens.LargeCardShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(4.dp))

                // 发送按钮
                IconButton(
                    onClick = onSend,
                    enabled = inputContent.isNotBlank() && !isSending
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (inputContent.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // 常用表情面板（仅 20 项静态表情，用 Row 避免 LazyRow 懒加载开销）
            AnimatedVisibility(visible = isEmojiPanelVisible) {
                val emojiList = listOf(
                    "😊", "😂", "😍", "😭", "👍", "❤️", "🎉", "🔥", "👏", "🥺",
                    "🤔", "🥳", "✨", "😳", "🤣", "😅", "🥰", "💪", "😎", "🤝"
                )
                val emojiScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(emojiScrollState)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    emojiList.forEach { emoji ->
                        key(emoji) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(ExpressiveTokens.SmallCardShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .clickable { onSelectEmoji(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
