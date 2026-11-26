package com.nobody.repoi.ui.collect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.nobody.repoi.ui.navigation.rememberBottomBarContentPadding
import com.nobody.repoi.ui.navigation.rememberBottomBarExtraPadding
import com.nobody.repoi.ui.person.PersonNavParam
import com.nobody.repoi.ui.series.SeriesNavParam
import com.nobody.repoi.ui.vendor.VendorNavParam
import com.nobody.repoi.ui.works.WorksNavParam
import com.nobody.repoi.ui.common.PoiIcons
import org.jetbrains.compose.resources.painterResource

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

    onNavigateToPictureGallery: ((PictureGalleryNavParam) -> Unit)? = null,
    showBackButton: Boolean = true,
    viewModel: UserCollectViewModel = koinViewModel()
) {
    LaunchedEffect(param) {
        viewModel.initParam(param)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    CollectResumeEffect { viewModel.onIntent(UserCollectIntent.OnResume) }

    val categories = remember { UserCollectCategory.entries }
    val initialIndex = remember(param.initialCategory) {
        categories.indexOfFirst { it.key == param.initialCategory }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialIndex) { categories.size }

    LaunchedEffect(uiState.selectedCategory, uiState.isInitialized) {
        if (!uiState.isInitialized) return@LaunchedEffect
        val targetIndex = categories.indexOf(uiState.selectedCategory)
        if (targetIndex >= 0 && pagerState.currentPage != targetIndex) {
            pagerState.scrollToPage(targetIndex)
        }
    }

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
                    if (showBackButton) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                painter = painterResource(PoiIcons.AutoMirrored.Filled.ArrowBack),
                                contentDescription = "返回",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                actions = {

                    if (uiState.selectedCategory == UserCollectCategory.HOBBY) {
                        IconButton(onClick = { viewModel.onIntent(UserCollectIntent.ToggleViewMode) }) {
                            Icon(
                                painter = painterResource(if (uiState.isListView) PoiIcons.GridView else PoiIcons.AutoMirrored.Filled.List),
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
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = rememberBottomBarExtraPadding())
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        val navBarBottomPadding = maxOf(
            innerPadding.calculateBottomPadding(),
            rememberBottomBarContentPadding(0.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(MaterialTheme.colorScheme.background)
        ) {

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
                            onItemClick = { item ->
                                onNavigateToAlbum?.invoke(
                                    AlbumNavParam(

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

                            onItemClick = { index ->
                                val item = uiState.picData.items.getOrNull(index)
                                if (item != null) {

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

@Composable
internal fun CollectResumeEffect(onResume: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        onResume()
    }
}
