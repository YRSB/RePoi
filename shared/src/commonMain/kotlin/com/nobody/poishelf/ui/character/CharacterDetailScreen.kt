package com.nobody.poishelf.ui.character

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import com.nobody.poishelf.ui.home.components.ShimmerHost
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobody.poishelf.platform.LocalAppMessages
import com.nobody.poishelf.platform.rememberTextSharing
import org.koin.compose.viewmodel.koinViewModel
import com.nobody.poishelf.ui.album.AlbumNavParam
import com.nobody.poishelf.ui.comment.CommentDetailNavParam
import com.nobody.poishelf.ui.comment.HobbyCommentsNavParam
import com.nobody.poishelf.ui.common.DetailTopNavigation
import com.nobody.poishelf.ui.common.LocalImageViewer
import com.nobody.poishelf.ui.common.comment.AdaptiveDetailDefaults
import com.nobody.poishelf.ui.common.comment.AdaptiveDetailLazyColumnCommentScaffold
import com.nobody.poishelf.ui.common.comment.CommentFeedState
import com.nobody.poishelf.ui.common.comment.CommentSectionInteraction
import com.nobody.poishelf.ui.detail.DetailNavParam
import com.nobody.poishelf.ui.item.list.HobbyListNavParam
import com.nobody.poishelf.ui.navigation.CharacterAlbumListNavParam
import com.nobody.poishelf.ui.navigation.CharacterNavParam
import com.nobody.poishelf.ui.navigation.CharacterWorksListNavParam
import com.nobody.poishelf.ui.person.PersonNavParam
import com.nobody.poishelf.ui.theme.LocalIsDarkTheme
import com.nobody.poishelf.ui.works.WorksNavParam
import com.nobody.poishelf.util.CdnUrlUtils

/**
 * 角色/要素维基详情页 (CharacterDetailScreen)。
 * 严格遵循单流 LazyColumn、自适应双窗格、悬浮胶囊底栏与多阶段加载规范。
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    navParam: CharacterNavParam,
    onBack: () -> Unit,
    onNavigateToHobby: (DetailNavParam) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToHobbyList: ((HobbyListNavParam) -> Unit)? = null,
    onNavigateToSearch: ((String) -> Unit)? = null,
    onNavigateToWorks: ((WorksNavParam) -> Unit)? = null,
    onNavigateToWorksList: ((CharacterWorksListNavParam) -> Unit)? = null,
    onNavigateToPerson: ((PersonNavParam) -> Unit)? = null,
    onNavigateToCharacter: ((CharacterNavParam) -> Unit)? = null,
    onNavigateToAlbum: ((AlbumNavParam) -> Unit)? = null,
    onNavigateToAlbumList: ((CharacterAlbumListNavParam) -> Unit)? = null,
    onNavigateToCommentDetail: ((CommentDetailNavParam) -> Unit)? = null,
    onNavigateToComments: ((HobbyCommentsNavParam) -> Unit)? = null,
    viewModel: CharacterDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    val messages = LocalAppMessages.current
    val textSharing = rememberTextSharing()
    val imageViewer = LocalImageViewer.current

    // 初始化多阶段加载
    LaunchedEffect(navParam) {
        viewModel.loadDetail(navParam)
    }

    // 评论互动一次性提示
    val commentActionMessage = uiState.commentActionMessage
    LaunchedEffect(commentActionMessage) {
        if (commentActionMessage != null) {
            messages.show(commentActionMessage)
            viewModel.consumeCommentActionMessage()
        }
    }

    var commentUserId by remember { mutableStateOf(viewModel.currentCommentUserId) }
    LaunchedEffect(Unit) { commentUserId = viewModel.resolveCommentUserId() }

    // 分享角色
    val character = uiState.character
    val onShareCharacter: () -> Unit = {
        val characterName = character?.displayName?.ifBlank { null } ?: navParam.initialName ?: "角色"
        textSharing.share("【PoiShelf 角色维基分享】$characterName\n登场作品与手办周边全收录！")
    }

    when {
        uiState.isInitialLoading && uiState.character == null -> {
            Box(modifier = modifier.fillMaxSize()) {
                ShimmerHost { CharacterDetailSkeleton(onBack = onBack, isDark = isDark) }
            }
        }

        uiState.errorMessage != null && uiState.character == null -> {
            Box(modifier = modifier.fillMaxSize()) {
                CharacterErrorState(
                    errorMessage = uiState.errorMessage ?: "加载失败",
                    onRetry = { viewModel.loadDetail(navParam) },
                    onBack = onBack,
                    isDark = isDark
                )
            }
        }

        else -> {
            val commentInteraction = remember(uiState.pendingPraiseIds, uiState.characterNodeId, commentUserId) {
                CommentSectionInteraction(
                    pendingPraiseIds = uiState.pendingPraiseIds,
                    currentUserId = commentUserId,
                    onPraiseClick = { viewModel.toggleCommentPraise(it) },
                    onReplySubmit = { comment, target, content, stickers ->
                        viewModel.submitCommentReply(comment.id, target, content, stickers)
                    },
                    onDeleteComment = { viewModel.deleteComment(it) },
                    onDeleteReply = { comment, reply -> viewModel.deleteReply(comment, reply) },
                    onAddComment = { content, stickers ->
                        viewModel.submitTopLevelComment(content, stickers)
                    },
                    onLoadStickerPacks = { viewModel.loadStickerPacks() },
                    onViewAllReplies = { comment ->
                        onNavigateToCommentDetail?.invoke(
                            CommentDetailNavParam(
                                commentId = comment.id,
                                relateNodeId = uiState.characterNodeId,
                                snapshot = comment
                            )
                        )
                    }
                )
            }

            val commentFeedState = remember(uiState, character) {
                CommentFeedState(
                    totalCommentCount = character?.commentCount
                        ?: (uiState.hotComments.size + uiState.latestComments.size),
                    hotComments = uiState.hotComments,
                    latestComments = uiState.latestComments,
                    isCommentsLoading = uiState.isCommentsLoading,
                    isLoadingMoreComments = uiState.isLoadingMoreComments,
                    hasMoreComments = uiState.hasMoreComments,
                    commentsError = uiState.commentsError
                )
            }

            AdaptiveDetailLazyColumnCommentScaffold(
                commentState = commentFeedState,
                onLoadMoreComments = { viewModel.loadNextCommentsPage() },
                onRefreshComments = { viewModel.refreshComments() },
                interaction = commentInteraction,
                relateNodeId = uiState.characterNodeId,
                contentPadding = AdaptiveDetailDefaults.nonHeroContentPadding(),
                modifier = modifier,
                topBar = {
                    DetailTopNavigation(
                        isScrolled = isScrolledPastHeader,
                        title = uiState.character?.displayName?.ifBlank { null } ?: uiState.initialName ?: "角色详情",
                        onBack = onBack,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                floatingToolbar = {
                    if (uiState.character != null || !uiState.isInitialLoading) {
                        val baseCollect = character?.collect?.toLong() ?: 0L
                        val displayCollect = (baseCollect + uiState.collectCountDelta).coerceAtLeast(0L)
                        CharacterFloatingActionBar(
                            collectCount = displayCollect,
                            isCollected = uiState.isCollected,
                            commentCount = character?.commentCount ?: (uiState.hotComments.size + uiState.latestComments.size),
                            showCommentButton = !isWidePane,
                            onToggleCollect = {
                                viewModel.toggleCollect()
                                val text = if (!uiState.isCollected) "已收藏角色 ♥" else "已取消收藏"
                                messages.show(text)
                            },
                            onCommentClick = {
                                val nodeId = if (uiState.characterNodeId > 0L) uiState.characterNodeId else navParam.characterId
                                if (nodeId <= 0L) return@CharacterFloatingActionBar
                                onNavigateToComments?.invoke(
                                    HobbyCommentsNavParam(
                                        nodeId = nodeId,
                                        itemType = "charactar",
                                        title = character?.displayName?.ifBlank { null } ?: navParam.initialName ?: "评论",
                                        initialCommentCount = character?.commentCount ?: 0
                                    )
                                )
                            },
                            onShareClick = onShareCharacter,
                            isDark = isDark
                        )
                    }
                }
            ) {
                // ── 区块 0：角色综合档案卡 ──
                item(key = "section_header") {
                    CharacterHeaderSection(
                        character = character,
                        initialName = navParam.initialName,
                        initialCover = navParam.initialCover,
                        zones = uiState.zones,
                        isZonesLoading = uiState.isZonesLoading,
                        isIntroExpanded = uiState.isIntroExpanded,
                        onToggleIntro = { viewModel.toggleIntroExpanded() },
                        onCoverClick = {
                            val cover = character?.cover?.ifBlank { null } ?: navParam.initialCover
                            val raw = if (!cover.isNullOrBlank()) CdnUrlUtils.getRawCoverUrl(cover) else ""
                            if (raw.isNotBlank()) imageViewer.show(raw)
                        },
                        onTagClick = { tag -> onNavigateToSearch?.invoke(tag) },
                        isDark = isDark
                    )
                }

                // ── 区块 1：登场作品 ──
                if (uiState.isWorksLoading || uiState.works.isNotEmpty()) {
                    item(key = "section_works") {
                        CharacterWorksSection(
                            works = uiState.works,
                            isLoading = uiState.isWorksLoading,
                            onWorkClick = { work ->
                                if (onNavigateToWorks != null && work.worksId > 0L) {
                                    onNavigateToWorks(
                                        WorksNavParam(
                                            worksId = work.worksId,
                                            initialName = work.displayName,
                                            initialCover = work.wCover
                                        )
                                    )
                                } else {
                                    onNavigateToSearch?.invoke(work.displayName)
                                }
                            },
                            onPersonClick = { personId, personName, personCover ->
                                if (onNavigateToPerson != null && personId > 0L) {
                                    onNavigateToPerson(
                                        PersonNavParam(
                                            personId = personId,
                                            initialName = personName,
                                            initialCover = personCover
                                        )
                                    )
                                } else {
                                    onNavigateToSearch?.invoke(personName)
                                }
                            },
                            onViewMore = {
                                val id = navParam.characterId.takeIf { it > 0L } ?: character?.actualItemId ?: 0L
                                val name = character?.displayName?.ifBlank { null } ?: navParam.initialName ?: "角色"
                                if (id > 0L && onNavigateToWorksList != null) {
                                    onNavigateToWorksList(
                                        CharacterWorksListNavParam(
                                            characterId = id,
                                            title = "${name}的登场作品"
                                        )
                                    )
                                } else {
                                    val keyword = character?.displayName?.ifBlank { null } ?: navParam.initialName.orEmpty()
                                    onNavigateToSearch?.invoke(keyword)
                                }
                            },
                            isDark = isDark
                        )
                    }
                }

                // ── 区块 2：周边手办 ──
                if (uiState.isHobbysLoading || uiState.hobbys.isNotEmpty()) {
                    item(key = "section_hobbys") {
                        CharacterHobbysSection(
                            hobbys = uiState.hobbys,
                            isLoading = uiState.isHobbysLoading,
                            onHobbyClick = { hobby ->
                                onNavigateToHobby(
                                    DetailNavParam(
                                        itemId = hobby.itemId,
                                        initialName = hobby.displayName,
                                        initialCover = hobby.cover
                                    )
                                )
                            },
                            onViewMore = {
                                val nodeId = uiState.characterNodeId
                                if (nodeId > 0L && onNavigateToHobbyList != null) {
                                    val name = character?.displayName?.ifBlank { null } ?: navParam.initialName ?: "角色"
                                    onNavigateToHobbyList(
                                        HobbyListNavParam(
                                            title = "${name}的周边",
                                            character = nodeId.toString()
                                        )
                                    )
                                } else {
                                    val keyword = character?.displayName?.ifBlank { null } ?: navParam.initialName.orEmpty()
                                    onNavigateToSearch?.invoke(keyword)
                                }
                            },
                            isDark = isDark
                        )
                    }
                }

                // ── 区块 3：相关相册 ──
                if (uiState.isAlbumsLoading || uiState.albums.isNotEmpty()) {
                    item(key = "section_albums") {
                        CharacterAlbumsSection(
                            albums = uiState.albums,
                            isLoading = uiState.isAlbumsLoading,
                            onAlbumClick = { album ->
                                if (onNavigateToAlbum != null && album.id > 0L) {
                                    onNavigateToAlbum(
                                        AlbumNavParam(
                                            albumId = album.id,
                                            initialTitle = album.name,
                                            initialCover = album.cover
                                        )
                                    )
                                } else {
                                    onNavigateToSearch?.invoke(album.name ?: "相册")
                                }
                            },
                            onViewMore = {
                                val id = navParam.characterId.takeIf { it > 0L } ?: character?.actualItemId ?: 0L
                                val name = character?.displayName?.ifBlank { null } ?: navParam.initialName ?: "角色"
                                if (id > 0L && onNavigateToAlbumList != null) {
                                    onNavigateToAlbumList(
                                        CharacterAlbumListNavParam(
                                            characterId = id,
                                            title = "${name}的相关相册"
                                        )
                                    )
                                } else {
                                    val keyword = character?.displayName?.ifBlank { null } ?: navParam.initialName.orEmpty()
                                    onNavigateToSearch?.invoke(keyword)
                                }
                            },
                            isDark = isDark
                        )
                    }
                }

                // ── 区块 4：推荐要素 ──
                if (uiState.isRelevantLoading || uiState.relevantCharacters.isNotEmpty()) {
                    item(key = "section_relevant") {
                        CharacterRelevantSection(
                            characters = uiState.relevantCharacters,
                            isLoading = uiState.isRelevantLoading,
                            onCharacterClick = { relevant ->
                                val id = relevant.actualItemId
                                if (id > 0L && onNavigateToCharacter != null) {
                                    onNavigateToCharacter(
                                        CharacterNavParam(
                                            characterId = id,
                                            characterNodeId = relevant.id,
                                            initialName = relevant.displayName,
                                            initialCover = relevant.cover
                                        )
                                    )
                                } else {
                                    onNavigateToSearch?.invoke(relevant.displayName)
                                }
                            },
                            isDark = isDark
                        )
                    }
                }
            }
        }
    }
}
