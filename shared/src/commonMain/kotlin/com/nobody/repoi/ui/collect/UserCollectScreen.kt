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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.nobody.repoi.ui.navigation.CharacterNavParam
import com.nobody.repoi.ui.navigation.PictureGalleryNavParam
import com.nobody.repoi.ui.navigation.UserCollectNavParam
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam

/**
 * 全量用户收藏中枢界面（对标 Hpoi 2.39 原版架构）。
 * 支持 8 大实体大类联动切换、手办双模视图（网格/资产账本卡）、5 档收藏状态胶囊、排序与多维高级筛选抽屉。
 *
 * 本文件仅承载宿主层：大类 Pager 分发、TopAppBar、snackbar、大图画廊跳转与筛选抽屉挂载。
 * 图片全屏大图画廊已升级为独立 Nav3 路由（`PictureGalleryNavKey`），不再以组合内覆盖层形式挂在本页。
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
    /** 打开图片全屏大图画廊（独立 Nav3 路由） */
    onNavigateToPictureGallery: ((PictureGalleryNavParam) -> Unit)? = null,
    viewModel: UserCollectViewModel = koinViewModel()
) {
    LaunchedEffect(param) {
        viewModel.initParam(param)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 恢复感知：从图片画廊（独立 Nav3 路由）出栈回来时，若画廊内动过收藏，
    // 由仓储脏位（galleryMutationTicks）驱动图片大类静默刷新。
    // resumeCount > 1 跳过首次组合，避免刚进入页面就多发一次请求。
    var resumeCount by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        resumeCount++
        if (resumeCount > 1) viewModel.onIntent(UserCollectIntent.OnResume)
    }

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

    // 错误消息浮层
    LaunchedEffect(uiState.errorMessage) {
        val error = uiState.errorMessage
        if (error != null && uiState.currentHasItems) {
            snackbarHostState.showSnackbar(error)
            viewModel.onIntent(UserCollectIntent.DismissError(uiState.selectedCategory))
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
                        IconButton(onClick = { viewModel.onIntent(UserCollectIntent.ToggleViewMode) }) {
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
                                viewModel.onIntent(UserCollectIntent.SwitchCategory(cat))
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
                            onRefresh = { viewModel.onIntent(UserCollectIntent.Refresh(UserCollectCategory.HOBBY)) },
                            onLoadNextPage = { viewModel.onIntent(UserCollectIntent.LoadNextPage(UserCollectCategory.HOBBY)) },
                            onSwitchCollectState = { viewModel.onIntent(UserCollectIntent.SwitchCollectState(it)) },
                            onSwitchOrder = { viewModel.onIntent(UserCollectIntent.SwitchOrder(it)) },
                            onSwitchSortDirection = { viewModel.onIntent(UserCollectIntent.SwitchSortDirection(it)) },
                            onOpenFilter = { viewModel.onIntent(UserCollectIntent.SetFilterSheetVisible(true)) },
                            // 多选与批量取消收藏：入参为外层收藏节点 id
                            onToggleSelect = { viewModel.onIntent(UserCollectIntent.ToggleSelect(it)) },
                            onSelectAll = { viewModel.onIntent(UserCollectIntent.SelectAll(UserCollectCategory.HOBBY)) },
                            onClearSelection = { viewModel.onIntent(UserCollectIntent.ClearSelection(UserCollectCategory.HOBBY)) },
                            onCancelSelectedCollect = { viewModel.onIntent(UserCollectIntent.ConfirmBatchCancel) },
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
                            onRetry = { viewModel.onIntent(UserCollectIntent.LoadFirstPage(UserCollectCategory.HOBBY)) }
                        )
                    }

                    UserCollectCategory.ALBUM -> {
                        AlbumCollectSubPage(
                            subPageData = uiState.albumData,
                            navBarBottomPadding = navBarBottomPadding,
                            currentOrder = uiState.albumOrder,
                            albumType = uiState.albumType,
                            isSelectionMode = uiState.albumSelectionMode,
                            selectedIds = uiState.albumSelectedIds,
                            isSelf = uiState.isSelf,
                            isCancellingBatch = uiState.isCancellingBatch,
                            onRefresh = { viewModel.onIntent(UserCollectIntent.Refresh(UserCollectCategory.ALBUM)) },
                            onLoadNextPage = { viewModel.onIntent(UserCollectIntent.LoadNextPage(UserCollectCategory.ALBUM)) },
                            onSwitchOrder = { viewModel.onIntent(UserCollectIntent.SwitchAlbumOrder(it)) },
                            onSwitchAlbumType = { viewModel.onIntent(UserCollectIntent.SwitchAlbumType(it)) },
                            onItemClick = { item ->
                                onNavigateToAlbum?.invoke(
                                    AlbumNavParam(
                                        // 「我收藏的」用内层真实相册 id；「我创建的」接口的 id 本身就是相册 id
                                        albumId = if (uiState.albumType == AlbumTypeOption.UPLOAD) {
                                            item.id
                                        } else {
                                            item.actualAlbumId
                                        },
                                        initialTitle = item.actualTitle,
                                        initialCover = item.actualCover
                                    )
                                )
                            },
                            // 多选：入参为外层收藏节点 id
                            onToggleSelect = { viewModel.onIntent(UserCollectIntent.ToggleSelect(it)) },
                            onSelectAll = { viewModel.onIntent(UserCollectIntent.SelectAll(UserCollectCategory.ALBUM)) },
                            onCancelSelection = { viewModel.onIntent(UserCollectIntent.ClearSelection(UserCollectCategory.ALBUM)) },
                            onCancelCollect = { viewModel.onIntent(UserCollectIntent.ConfirmBatchCancel) },
                            onRetry = { viewModel.onIntent(UserCollectIntent.LoadFirstPage(UserCollectCategory.ALBUM)) }
                        )
                    }

                    UserCollectCategory.PICTURE -> {
                        PictureCollectSubPage(
                            subPageData = uiState.picData,
                            navBarBottomPadding = navBarBottomPadding,
                            currentOrder = uiState.picOrder,
                            onRefresh = { viewModel.onIntent(UserCollectIntent.Refresh(UserCollectCategory.PICTURE)) },
                            onLoadNextPage = { viewModel.onIntent(UserCollectIntent.LoadNextPage(UserCollectCategory.PICTURE)) },
                            onSwitchOrder = { viewModel.onIntent(UserCollectIntent.SwitchPicOrder(it)) },
                            // 画廊是独立 Nav3 路由：只传「图片 id + 收藏页当前下标 + 排序档」，
                            // 由画廊路由自行按同一接口/排序加载列表并定位，返回时由 back stack 出栈。
                            onItemClick = { index ->
                                val item = uiState.picData.items.getOrNull(index)
                                if (item != null) {
                                    // 兼容极少数未透传 userId 的收藏入口：回落到 ViewModel 解析出的用户 id
                                    val galleryUserId = param.userId.takeIf { it > 0L } ?: viewModel.userId
                                    onNavigateToPictureGallery?.invoke(
                                        PictureGalleryNavParam(
                                            userId = galleryUserId,
                                            picId = item.id,
                                            initialIndex = index,
                                            order = uiState.picOrder
                                        )
                                    )
                                }
                            },
                            onRetry = { viewModel.onIntent(UserCollectIntent.LoadFirstPage(UserCollectCategory.PICTURE)) }
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
                            onRefresh = { viewModel.onIntent(UserCollectIntent.Refresh(UserCollectCategory.WORKS)) },
                            onLoadNextPage = { viewModel.onIntent(UserCollectIntent.LoadNextPage(UserCollectCategory.WORKS)) },
                            onSwitchState = { viewModel.onIntent(UserCollectIntent.SwitchWorksState(it)) },
                            onSwitchOrderAsc = { viewModel.onIntent(UserCollectIntent.SwitchWorksOrderAsc(it)) },
                            onOpenFilter = { viewModel.onIntent(UserCollectIntent.SetWorksFilterSheetVisible(true)) },
                            onSelectTag = { viewModel.onIntent(UserCollectIntent.SelectWorksTag(it)) },
                            onResetTag = { viewModel.onIntent(UserCollectIntent.ResetWorksTag) },
                            onDismissFilter = { viewModel.onIntent(UserCollectIntent.SetWorksFilterSheetVisible(false)) },
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
                            onRetry = { viewModel.onIntent(UserCollectIntent.LoadFirstPage(UserCollectCategory.WORKS)) }
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
                            onRefresh = { viewModel.onIntent(UserCollectIntent.Refresh(category)) },
                            onLoadNextPage = { viewModel.onIntent(UserCollectIntent.LoadNextPage(category)) },
                            onSwitchOrder = { viewModel.onIntent(UserCollectIntent.SwitchCommonOrder(it)) },
                            onToggleViewMode = { viewModel.onIntent(UserCollectIntent.ToggleViewMode) },
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
                            onRetry = { viewModel.onIntent(UserCollectIntent.LoadFirstPage(category)) }
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
                viewModel.onIntent(UserCollectIntent.SelectSubCategory(subCategoryId))
                viewModel.onIntent(UserCollectIntent.SelectTag(tag))
            },
            onReset = { viewModel.onIntent(UserCollectIntent.ResetFilter) },
            onDismiss = { viewModel.onIntent(UserCollectIntent.SetFilterSheetVisible(false)) }
        )
    }
}