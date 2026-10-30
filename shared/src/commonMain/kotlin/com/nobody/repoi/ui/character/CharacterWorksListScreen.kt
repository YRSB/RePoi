package com.nobody.repoi.ui.character

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.repoi.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.nobody.repoi.data.remote.model.CharacterPerformerItem
import com.nobody.repoi.data.remote.model.CharacterWorksItem
import com.nobody.repoi.ui.home.components.shimmerBackground
import com.nobody.repoi.ui.navigation.CharacterWorksListNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import com.nobody.repoi.ui.theme.ExpressiveElevatedCard
import com.nobody.repoi.ui.theme.ExpressiveTokens
import com.nobody.repoi.ui.theme.LocalIsDarkTheme
import com.nobody.repoi.util.CdnUrlUtils

/**
 * 角色专属登场作品列表页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterWorksListScreen(
    param: CharacterWorksListNavParam,
    onBack: () -> Unit,
    onNavigateToWorks: (WorksNavParam) -> Unit,
    onNavigateToPerson: (PersonNavParam) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CharacterWorksListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current

    LaunchedEffect(param) {
        viewModel.loadInitial(param)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = uiState.title.ifBlank { param.title.ifBlank { "登场作品" } },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(PaddingValues(top = innerPadding.calculateTopPadding()))
        ) {
            Crossfade(
                targetState = when {
                    uiState.isInitialLoading && uiState.works.isEmpty() -> ScreenState.Loading
                    uiState.errorMessage != null && uiState.works.isEmpty() -> ScreenState.Error
                    uiState.works.isEmpty() -> ScreenState.Empty
                    else -> ScreenState.Content
                },
                label = "CharacterWorksListState"
            ) { state ->
                when (state) {
                    ScreenState.Loading -> ShimmerHost {
                        CharacterWorksSkeletonList(
                            bottomPadding = innerPadding.calculateBottomPadding()
                        )
                    }

                    ScreenState.Error -> {
                        CharacterWorksErrorState(
                            message = uiState.errorMessage ?: "加载失败",
                            onRetry = { viewModel.retry() }
                        )
                    }

                    ScreenState.Empty -> {
                        CharacterWorksEmptyState(
                            onRetry = { viewModel.refresh() }
                        )
                    }

                    ScreenState.Content -> {
                        val gridState = rememberLazyGridState()

                        // 触底加载更多监听
                        val shouldLoadMore by remember {
                            derivedStateOf {
                                val totalItems = gridState.layoutInfo.totalItemsCount
                                val lastVisibleItemIndex =
                                    gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                totalItems > 0 && lastVisibleItemIndex >= totalItems - 4
                            }
                        }

                        LaunchedEffect(shouldLoadMore) {
                            if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore && !uiState.isRefreshing) {
                                viewModel.loadMore()
                            }
                        }

                        CharacterWorksContentList(
                            works = uiState.works,
                            gridState = gridState,
                            hasMore = uiState.hasMore,
                            isLoadingMore = uiState.isLoadingMore,
                            isDark = isDark,
                            bottomPadding = innerPadding.calculateBottomPadding(),
                            modifier = Modifier
                                .fillMaxSize()
                                .consumeWindowInsets(PaddingValues(bottom = innerPadding.calculateBottomPadding())),
                            onWorkClick = { work ->
                                onNavigateToWorks(
                                    WorksNavParam(
                                        worksId = work.worksId,
                                        initialName = work.displayName,
                                        initialCover = work.wCover
                                    )
                                )
                            },
                            onPersonClick = { personId, personName, personCover ->
                                onNavigateToPerson(
                                    PersonNavParam(
                                        personId = personId,
                                        initialName = personName,
                                        initialCover = personCover
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

private enum class ScreenState {
    Loading,
    Error,
    Empty,
    Content
}

@Composable
private fun CharacterWorksContentList(
    works: List<CharacterWorksItem>,
    gridState: LazyGridState,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    isDark: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onWorkClick: (CharacterWorksItem) -> Unit,
    onPersonClick: (Long, String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        state = gridState,
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 10.dp,
            bottom = bottomPadding + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = works,
            key = { it.worksId }
        ) { work ->
            CharacterWorksListItemCard(
                work = work,
                isDark = isDark,
                onWorkClick = { onWorkClick(work) },
                onPersonClick = onPersonClick
            )
        }

        // 分页加载与触底状态
        item(
            span = { GridItemSpan(maxLineSpan) },
            key = "footer_state"
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingMore) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "正在加载更多作品...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (!hasMore && works.isNotEmpty()) {
                    Text(
                        text = "已展示全部 ${works.size} 部作品",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

/**
 * 单部登场作品卡片，包含封面、标题、发售日期、角色定位、演职员 CV 列表。
 */
@Composable
private fun CharacterWorksListItemCard(
    work: CharacterWorksItem,
    isDark: Boolean,
    onWorkClick: () -> Unit,
    onPersonClick: (Long, String, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val coverUrl = CdnUrlUtils.getCoverUrl(work.wCover)

    ExpressiveElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 作品主信息区域（点击直达作品详情）
            Surface(
                onClick = onWorkClick,
                shape = ExpressiveTokens.CardShape,
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // 作品封面
                    Box(
                        modifier = Modifier
                            .width(84.dp)
                            .height(112.dp)
                            .clip(ExpressiveTokens.CardShape)
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.08f)
                                else Color.Black.copy(alpha = 0.05f)
                            )
                    ) {
                        if (coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = work.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // 文本信息列
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = work.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // 角色在作品中的定位徽章
                        work.characterRoleText?.let { role ->
                            Surface(
                                shape = ExpressiveTokens.SmallCardShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = role,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // 发售日期
                        val releaseDateText = work.formattedReleaseDate
                        if (!releaseDateText.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "发售日期: $releaseDateText",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "查看作品详情",
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 演职员 / 配音（CV）胶囊横滑列表
            if (work.validPerformers.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "主要演职员 / 配音",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(work.validPerformers, key = { it.personId }) { performer ->
                            PerformerPill(
                                performer = performer,
                                isDark = isDark,
                                onClick = {
                                    onPersonClick(
                                        performer.personId,
                                        performer.displayName,
                                        performer.pCover
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 演职员（CV）胶囊药丸
 */
@Composable
private fun PerformerPill(
    performer: CharacterPerformerItem,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val avatarUrl = CdnUrlUtils.getCoverSmallUrl(performer.pCover)

    Surface(
        onClick = onClick,
        shape = ExpressiveTokens.ButtonShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f))
            ) {
                if (avatarUrl.isNotBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = performer.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = "${performer.careerLabel}: ${performer.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (!performer.rwRemark.isNullOrBlank()) {
                    Text(
                        text = performer.rwRemark,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterWorksSkeletonList(
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 10.dp,
            bottom = bottomPadding + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        items(6) {
            ExpressiveElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(84.dp)
                                .height(112.dp)
                                .clip(ExpressiveTokens.CardShape)
                                .shimmerBackground()
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(18.dp)
                                    .clip(ExpressiveTokens.SmallCardShape)
                                    .shimmerBackground()
                            )
                            Box(
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(14.dp)
                                    .clip(ExpressiveTokens.SmallCardShape)
                                    .shimmerBackground()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(12.dp)
                                    .clip(ExpressiveTokens.SmallCardShape)
                                    .shimmerBackground()
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(28.dp)
                            .clip(ExpressiveTokens.ButtonShape)
                            .shimmerBackground()
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterWorksEmptyState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = "暂无登场作品",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "该角色当前尚未关联登场作品或尚未收录",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("刷新")
            }
        }
    }
}

@Composable
private fun CharacterWorksErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text(
                text = "获取作品列表失败",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("重试")
            }
        }
    }
}
