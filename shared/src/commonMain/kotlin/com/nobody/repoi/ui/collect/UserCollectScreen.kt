package com.nobody.repoi.ui.collect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobody.repoi.data.remote.model.UserCollectCategory
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.collect.components.CollectFilterBottomSheet
import com.nobody.repoi.ui.collect.components.worksDetailItemId
import com.nobody.repoi.ui.collect.subpage.AlbumCollectSubPage
import com.nobody.repoi.ui.collect.subpage.CommonCollectSubPage
import com.nobody.repoi.ui.collect.subpage.HobbyCollectSubPage
import com.nobody.repoi.ui.collect.subpage.PictureCollectSubPage
import com.nobody.repoi.ui.collect.subpage.WorksCollectSubPage
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.gallery.PictureGalleryScreen
import com.nobody.repoi.ui.navigation.CharacterNavParam
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import kotlinx.coroutines.launch

/**
 * 全量用户收藏中枢界面（对标 Hpoi 2.39 原版架构）。
 * 支持 8 大实体大类联动切换、手办双模视图（网格/资产账本卡）、5 档收藏状态胶囊、排序与多维高级筛选抽屉。
 *
 * 本文件仅承载宿主层：大类 Pager 分发、TopAppBar、snackbar、大图查看器与筛选抽屉挂载。
 * 各子页实现位于 `ui/collect/subpage/`，共享组件位于 `ui/collect/components/`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserCollectScreen(
    param: UserCollectNavParam,
    onNavigateBack: () -> Unit,
    onNavigateToHobby: (DetailNavParam) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToVendor: ((VendorNavParam) -> Unit)? = null,
    onNavigateToSeries: ((SeriesNavParam) -> Unit)? = null,
    onNavigateToWorks: ((WorksNavParam) -> Unit)? = null,
    onNavigateToPerson: ((PersonNavParam) -> Unit)? = null,
    onNavigateToAlbum: ((AlbumNavParam) -> Unit)? = null,
    onNavigateToCharacter: ((CharacterNavParam) -> Unit)? = null,
    viewModel: UserCollectViewModel = koinViewModel()
) {
    LaunchedEffect(param) {
        viewModel.initParam(param)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val categories = remember { UserCollectCategory.entries }
    val initialIndex = remember(param.initialCategory) {
        categories.indexOfFirst { it.key == param.initialCategory }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialIndex) { categories.size }

    // 监听 ViewModel 分类切换，单向驱动 Pager 瞬时跳转。
    // 严禁添加反向 LaunchedEffect(pagerState.currentPage)，避免形成双向事件死循环振荡（Ping-Pong livelock）。
    LaunchedEffect(uiState.selectedCategory, uiState.isInitialized) {
        if (!uiState.isInitialized) return@LaunchedEffect
        val targetIndex = categories.indexOf(uiState.selectedCategory)
        if (targetIndex >= 0 && pagerState.currentPage != targetIndex) {
            pagerState.scrollToPage(targetIndex)
        }
    }

    // 图片大图画廊的起始下标（null 表示未打开）
    var galleryIndex by remember { mutableStateOf<Int?>(null) }

    // 错误消息浮层
    LaunchedEffect(uiState.errorMessage) {
        val error = uiState.errorMessage
        if (error != null && uiState.currentHasItems) {
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "我的收藏",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // 仅手办周边页显示网格/列表切换按钮
                    if (uiState.selectedCategory == UserCollectCategory.HOBBY) {
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(
                                imageVector = if (uiState.isListView) Icons.Default.GridView else Icons.AutoMirrored.Filled.List,
                                contentDescription = if (uiState.isListView) "切换为网格视图" else "切换为列表视图",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        val navBarBottomPadding = innerPadding.calculateBottomPadding()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 8 大实体大类横向滑动 TabRow
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                val selectedTabIndex = if (uiState.isInitialized) {
                    categories.indexOf(uiState.selectedCategory).coerceAtLeast(0)
                } else {
                    initialIndex
                }

                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 12.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
                ) {
                    categories.forEachIndexed { index, cat ->
                        val isSelected = selectedTabIndex == index
                        Tab(
                            selected = isSelected,
                            onClick = {
                                viewModel.switchCategory(cat)
                            },
                            text = {
                                Text(
                                    text = cat.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                        )
                    }
                }
            }

            // 各大类 Pager 页面
            // userScrollEnabled = false：禁用左右滑动切换大类，只能点顶部 Tab 切换。
            // 原版 `UserCollectActivity` 的 ViewPager2 未开滑动（仅 MagicIndicator 点击 + setCurrentItem）。
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (categories[page]) {
                    UserCollectCategory.HOBBY -> {
                        HobbyCollectSubPage(
                            uiState = uiState,
                            navBarBottomPadding = navBarBottomPadding,
                            onRefresh = { viewModel.refresh(UserCollectCategory.HOBBY) },
                            onLoadNextPage = { viewModel.loadNextPage(UserCollectCategory.HOBBY) },
                            onSwitchCollectState = { viewModel.switchCollectState(it) },
                            onSwitchOrder = { viewModel.switchOrder(it) },
                            onSwitchSortDirection = { viewModel.switchSortDirection(it) },
                            onOpenFilter = { viewModel.setFilterSheetVisible(true) },
                            // 多选与批量取消收藏：入参为外层收藏节点 id
                            onToggleSelect = { viewModel.toggleSelect(it) },
                            onSelectAll = { viewModel.selectAll() },
                            onClearSelection = { viewModel.clearSelection() },
                            onCancelSelectedCollect = { viewModel.cancelSelectedCollect() },
                            onItemClick = { item ->
                                onNavigateToHobby(
                                    DetailNavParam(
                                        itemId = item.actualItemId,
                                        itemType = "hobby",
                                        initialName = item.displayName,
                                        initialCover = item.actualCover
                                    )
                                )
                            },
                            onRetry = { viewModel.loadFirstPage(UserCollectCategory.HOBBY) }
                        )
                    }

                    UserCollectCategory.ALBUM -> {
                        AlbumCollectSubPage(
                            subPageData = uiState.albumData,
                            navBarBottomPadding = navBarBottomPadding,
                            currentOrder = uiState.albumOrder,
                            isSelectionMode = uiState.albumSelectionMode,
                            selectedIds = uiState.albumSelectedIds,
                            isSelf = uiState.isSelf,
                            isCancellingBatch = uiState.isCancellingBatch,
                            onRefresh = { viewModel.refresh(UserCollectCategory.ALBUM) },
                            onLoadNextPage = { viewModel.loadNextPage(UserCollectCategory.ALBUM) },
                            onSwitchOrder = { viewModel.switchAlbumOrder(it) },
                            onItemClick = { item ->
                                onNavigateToAlbum?.invoke(
                                    AlbumNavParam(
                                        albumId = item.actualAlbumId,
                                        initialTitle = item.actualTitle,
                                        initialCover = item.actualCover
                                    )
                                )
                            },
                            // 多选：入参为外层收藏节点 id
                            onToggleSelect = { viewModel.toggleSelect(it) },
                            onSelectAll = { viewModel.selectAll() },
                            onCancelSelection = { viewModel.clearSelection() },
                            onCancelCollect = { viewModel.cancelSelectedCollect() },
                            onRetry = { viewModel.loadFirstPage(UserCollectCategory.ALBUM) }
                        )
                    }

                    UserCollectCategory.PICTURE -> {
                        PictureCollectSubPage(
                            subPageData = uiState.picData,
                            navBarBottomPadding = navBarBottomPadding,
                            currentOrder = uiState.picOrder,
                            onRefresh = { viewModel.refresh(UserCollectCategory.PICTURE) },
                            onLoadNextPage = { viewModel.loadNextPage(UserCollectCategory.PICTURE) },
                            onSwitchOrder = { viewModel.switchPicOrder(it) },
                            onItemClick = { index -> galleryIndex = index },
                            onRetry = { viewModel.loadFirstPage(UserCollectCategory.PICTURE) }
                        )
                    }

                    UserCollectCategory.WORKS -> {
                        // 作品页在原版是独立实现：3 档收藏状态 + 收藏时间升降序 + 标签筛选
                        WorksCollectSubPage(
                            subPageData = uiState.worksData,
                            worksState = uiState.worksState,
                            worksOrderAsc = uiState.worksOrderAsc,
                            worksTag = uiState.worksTag,
                            worksAvailableTags = uiState.worksAvailableTags,
                            isSelf = uiState.isSelf,
                            showFilterSheet = uiState.showWorksFilterSheet,
                            navBarBottomPadding = navBarBottomPadding,
                            onRefresh = { viewModel.refresh(UserCollectCategory.WORKS) },
                            onLoadNextPage = { viewModel.loadNextPage(UserCollectCategory.WORKS) },
                            onSwitchState = { viewModel.switchWorksState(it) },
                            onSwitchOrderAsc = { viewModel.switchWorksOrderAsc(it) },
                            onOpenFilter = { viewModel.setWorksFilterSheetVisible(true) },
                            onSelectTag = { viewModel.selectWorksTag(it) },
                            onResetTag = { viewModel.resetWorksTag() },
                            onDismissFilter = { viewModel.setWorksFilterSheetVisible(false) },
                            onItemClick = { item ->
                                // 嵌套实体无法确定真实条目 id 时，子页已禁掉点击，此处兜底再判一次
                                if (worksDetailItemId(item) > 0L) {
                                    onNavigateToWorks?.invoke(
                                        WorksNavParam(
                                            worksId = worksDetailItemId(item),
                                            initialName = item.displayName,
                                            initialCover = item.actualCover
                                        )
                                    )
                                }
                            },
                            onRetry = { viewModel.loadFirstPage(UserCollectCategory.WORKS) }
                        )
                    }

                    UserCollectCategory.VENDOR,
                    UserCollectCategory.SERIES,
                    UserCollectCategory.CHARACTER,
                    UserCollectCategory.PERSON -> {
                        val category = categories[page]
                        val subPageData = when (category) {
                            UserCollectCategory.VENDOR -> uiState.vendorData
                            UserCollectCategory.SERIES -> uiState.seriesData
                            UserCollectCategory.CHARACTER -> uiState.characterData
                            UserCollectCategory.PERSON -> uiState.personData
                            else -> uiState.vendorData
                        }
                        CommonCollectSubPage(
                            category = category,
                            subPageData = subPageData,
                            commonOrder = uiState.commonOrder,
                            isListView = uiState.isListView,
                            navBarBottomPadding = navBarBottomPadding,
                            onRefresh = { viewModel.refresh(category) },
                            onLoadNextPage = { viewModel.loadNextPage(category) },
                            onSwitchOrder = { viewModel.switchCommonOrder(it) },
                            onToggleViewMode = { viewModel.toggleViewMode() },
                            // 系列已建站条目走单例入口（对标原版 CommonItemListAdapter.java:51-52）
                            onSeriesSubPageClick = { item ->
                                onNavigateToSeries?.invoke(
                                    SeriesNavParam(
                                        seriesId = item.actualItemId,
                                        seriesNodeId = item.id,
                                        initialName = item.displayName,
                                        initialCover = item.actualCover
                                    )
                                )
                            },
                            // 角色页跳转（对标原版 u2.java:1650-1651 charactar -> CharacterDetailActivity）
                            onCharacterClick = { item ->
                                onNavigateToCharacter?.invoke(
                                    CharacterNavParam(
                                        characterId = item.actualItemId,
                                        characterNodeId = item.id,
                                        initialName = item.displayName,
                                        initialCover = item.actualCover
                                    )
                                )
                            },
                            onItemClick = { item ->
                                when (category) {
                                    UserCollectCategory.VENDOR -> onNavigateToVendor?.invoke(
                                        VendorNavParam(
                                            companyId = item.actualItemId,
                                            companyNodeId = item.id,
                                            initialName = item.displayName,
                                            initialCover = item.actualCover
                                        )
                                    )
                                    UserCollectCategory.SERIES -> onNavigateToSeries?.invoke(
                                        SeriesNavParam(
                                            seriesId = item.actualItemId,
                                            seriesNodeId = item.id,
                                            initialName = item.displayName,
                                            initialCover = item.actualCover
                                        )
                                    )
                                    UserCollectCategory.PERSON -> onNavigateToPerson?.invoke(
                                        PersonNavParam(
                                            personId = item.actualItemId,
                                            personNodeId = item.id,
                                            initialName = item.displayName,
                                            initialCover = item.actualCover
                                        )
                                    )
                                    else -> Unit
                                }
                            },
                            onRetry = { viewModel.loadFirstPage(category) }
                        )
                    }
                }
            }
        }
    }

    // 周边页筛选抽屉（暂存-确定语义：点「确定」才提交）
    if (uiState.showFilterSheet) {
        CollectFilterBottomSheet(
            subCategoryId = uiState.subCategoryId,
            selectedTag = uiState.selectedTag,
            availableTags = uiState.availableTags,
            onConfirm = { subCategoryId, tag ->
                viewModel.selectSubCategory(subCategoryId)
                viewModel.selectTag(tag)
            },
            onReset = { viewModel.resetFilter() },
            onDismiss = { viewModel.setFilterSheetVisible(false) }
        )
    }

    // 全屏大图画廊（含收藏/下载/举报/关联条目操作层与画廊内续拉分页）
    galleryIndex?.let { idx ->
        PictureGalleryScreen(
            items = uiState.picData.items,
            initialIndex = idx,
            hasMore = uiState.picData.hasMore,
            isLoadingMore = uiState.picData.isPaging,
            onLoadMore = { viewModel.loadNextPage(UserCollectCategory.PICTURE) },
            onBack = { galleryIndex = null },
            onNavigateToAlbum = { nav -> onNavigateToAlbum?.invoke(nav) },
            onNavigateToHobby = { nav -> onNavigateToHobby(nav) }
        )
    }
}