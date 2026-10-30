package com.nobody.poishelf.ui.resale

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.nobody.poishelf.data.remote.model.ResaleDetailHobbyItem
import com.nobody.poishelf.data.remote.model.ResaleDetailItem
import com.nobody.poishelf.platform.LocalAppMessages
import com.nobody.poishelf.ui.common.LocalImageViewer
import com.nobody.poishelf.ui.resale.components.rememberExternalLinkOpener
import com.nobody.poishelf.ui.resale.components.ResaleReportDialog
import com.nobody.poishelf.ui.theme.ExpressiveTokens
import com.nobody.poishelf.util.CdnUrlUtils

/**
 * 二手出售详情页（对应逆向 ResaleDetailActivity 规范与截图 1）。
 *
 * 界面组成：
 * 1. 顶栏：返回键、卖家头像、昵称、等级勋章与举报入口；
 * 2. 主体区：价格大字高亮、成色、交易描述正文、发布时间与城市、实物大图列表、关联手办卡片；
 * 3. 底部悬浮栏：聊一聊（私信）与外部链接（快捷跳转与自动复制淘口令）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResaleDetailScreen(
    resaleId: Long,
    onBack: () -> Unit,
    onNavigateToHobby: (Long) -> Unit,
    onNavigateToUser: (Long) -> Unit,
    onNavigateToMessage: ((Long, Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ResaleDetailViewModel = koinViewModel()
) {
    val messages = LocalAppMessages.current
    val openExternalLink = rememberExternalLinkOpener()
    val imageViewer = LocalImageViewer.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showReportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(resaleId) {
        viewModel.setResaleId(resaleId)
    }

    val pullRefreshState = rememberPullToRefreshState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    val user = uiState.detail?.user
                    if (user != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(ExpressiveTokens.SmallCardShape)
                                .clickable {
                                    if (user.userId > 0L) {
                                        onNavigateToUser(user.userId)
                                    }
                                }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            // 卖家头像
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                                val avatarUrl = remember(user.header) { CdnUrlUtils.getHeadUrl(user.header) }
                                if (!avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = user.nickname,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }

                            // 卖家昵称
                            Text(
                                text = user.nickname?.ifBlank { null } ?: "胶友",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // 等级胶囊 (如 Lv.3)
                            if (user.level > 0) {
                                Surface(
                                    shape = ExpressiveTokens.ExtraSmallShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "Lv.${user.level}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
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
                    IconButton(onClick = { showReportDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Report,
                            contentDescription = "举报",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            val detail = uiState.detail
            if (detail != null) {
                ResaleDetailBottomBar(
                    detail = detail,
                    onContactClick = {
                        val targetUserId = detail.user?.userId ?: 0L
                        if (targetUserId > 0L && onNavigateToMessage != null) {
                            onNavigateToMessage(targetUserId, detail.id)
                        } else {
                            messages.show("私信功能接入中")
                        }
                    },
                    onExternalLinkClick = { link ->
                        openExternalLink(link)
                    }
                )
            }
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding())
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading && uiState.detail == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                uiState.errorMessage != null && uiState.detail == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = uiState.errorMessage ?: "加载失败",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(onClick = { viewModel.refresh() }) {
                                Text("点击重试")
                            }
                        }
                    }
                }
                uiState.detail != null -> {
                    val detail = uiState.detail!!
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 96.dp // 留足底部常驻栏高度
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. 价格与成色行
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // 价格大字
                                Text(
                                    text = detail.displayPriceText,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // 成色标识
                                val purity = detail.purityStr?.trim()?.ifBlank { null }
                                if (purity != null) {
                                    Text(
                                        text = purity,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // 2. 交易详细描述
                        item {
                            val desc = detail.detail?.trim()?.ifBlank { null }
                            if (desc != null) {
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodyLarge,
                                    lineHeight = 24.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 3. 发布地点与时间
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = detail.location?.ifBlank { null } ?: "未知地区",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )

                                val dateText = detail.addDateStr?.ifBlank { null }
                                    ?: detail.addDate?.ifBlank { null }
                                    ?: ""
                                if (dateText.isNotBlank()) {
                                    Text(
                                        text = dateText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }

                        // 4. 柔和分割线
                        item {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }

                        // 5. 关联手办条目卡片 (linkItems)
                        val hobby = detail.firstHobby
                        if (hobby != null) {
                            item {
                                ResaleLinkedHobbyCard(
                                    item = hobby,
                                    onClick = {
                                        val hobbyId = hobby.targetItemId
                                        if (hobbyId > 0L) {
                                            onNavigateToHobby(hobbyId)
                                        }
                                    }
                                )
                            }
                        }

                        // 6. 卖家实物配图列表
                        if (detail.userPics.isNotEmpty()) {
                            itemsIndexed(detail.userPics) { index, picUrl ->
                                Surface(
                                    shape = ExpressiveTokens.CardShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(ExpressiveTokens.CardShape)
                                        .clickable {
                                            imageViewer.show(detail.userPics, index)
                                        }
                                ) {
                                    AsyncImage(
                                        model = picUrl,
                                        contentDescription = "实物配图 ${index + 1}",
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 举报原因弹窗（复用公共组件）
    if (showReportDialog) {
        ResaleReportDialog(
            isSubmitting = uiState.isReporting,
            onDismiss = { showReportDialog = false },
            onSubmit = { reason ->
                viewModel.submitReport(reason) { success, msg ->
                    messages.show(msg)
                    if (success) {
                        showReportDialog = false
                    }
                }
            }
        )
    }
}

/**
 * 关联手办条目卡片。
 */
@Composable
private fun ResaleLinkedHobbyCard(
    item: ResaleDetailHobbyItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        shape = ExpressiveTokens.CardShape,
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(
            defaultElevation = ExpressiveTokens.ElevationDefault
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 手办缩略封面
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(ExpressiveTokens.SmallCardShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                val coverUrl = remember(item.cover) {
                    item.cover?.takeIf { it.isNotBlank() }?.let { CdnUrlUtils.getCoverUrl(it) }
                }
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 手办名称
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "点击查看手办详情",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 底部常驻悬浮栏（聊一聊 + 外部链接）。
 */
@Composable
private fun ResaleDetailBottomBar(
    detail: ResaleDetailItem,
    onContactClick: () -> Unit,
    onExternalLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val navPadding = WindowInsets.navigationBars.asPaddingValues()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .padding(bottom = navPadding.calculateBottomPadding()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // 聊一聊按钮
            Button(
                onClick = onContactClick,
                shape = ExpressiveTokens.PillShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.surface
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 20.dp,
                    vertical = 10.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "聊一聊",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // 外部链接按钮 (若包含外链)
            if (detail.hasExternalLink) {
                Spacer(modifier = Modifier.width(12.dp))
                val link = detail.link.orEmpty()
                Button(
                    onClick = { onExternalLinkClick(link) },
                    shape = ExpressiveTokens.PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 20.dp,
                        vertical = 10.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "外部链接",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
