package com.nobody.repoi.ui.person

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobody.repoi.platform.PlatformActions
import com.nobody.repoi.ui.comment.CommentDetailNavParam
import com.nobody.repoi.ui.comment.HobbyCommentsNavParam
import com.nobody.repoi.ui.common.DetailTopNavigation
import com.nobody.repoi.ui.common.comment.AdaptiveDetailDefaults
import com.nobody.repoi.ui.common.comment.AdaptiveDetailLazyColumnCommentScaffold
import com.nobody.repoi.ui.common.comment.CommentFeedState
import com.nobody.repoi.ui.common.comment.CommentSectionInteraction
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.home.components.ShimmerHost
import com.nobody.repoi.ui.item.list.HobbyListNavParam
import com.nobody.repoi.ui.navigation.CharacterNavParam
import com.nobody.repoi.ui.theme.LocalIsDarkTheme
import com.nobody.repoi.ui.works.WorksNavParam
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    navParam: PersonNavParam,
    onBack: () -> Unit,
    onNavigateToHobby: (DetailNavParam) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToHobbyList: ((HobbyListNavParam) -> Unit)? = null,
    onNavigateToSearch: ((String) -> Unit)? = null,
    onNavigateToWorks: ((WorksNavParam) -> Unit)? = null,
    onNavigateToCharacter: ((CharacterNavParam) -> Unit)? = null,
    onNavigateToCommentDetail: ((CommentDetailNavParam) -> Unit)? = null,
    onNavigateToComments: ((HobbyCommentsNavParam) -> Unit)? = null,
    viewModel: PersonDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    val platformActions = koinInject<PlatformActions>()

    // 初始化加载
    LaunchedEffect(navParam) {
        viewModel.loadDetail(navParam)
    }

    // 评论互动一次性提示（点赞/回复结果）
    val commentActionMessage = uiState.commentActionMessage
    LaunchedEffect(commentActionMessage) {
        if (commentActionMessage != null) {
            platformActions.showToast(commentActionMessage)
            viewModel.consumeCommentActionMessage()
        }
    }

    var commentUserId by remember { mutableStateOf(viewModel.currentCommentUserId) }
    LaunchedEffect(Unit) { commentUserId = viewModel.resolveCommentUserId() }

    // 分享人物处理
    val onSharePerson: () -> Unit = {
        val personName = uiState.person?.displayName ?: navParam.initialName ?: "人物"
        val shareText = "【RePoi 人物分享】$personName\n了解更多作品与参与手办周边情报！"
        platformActions.shareText("分享人物", shareText)
    }

    when {
        uiState.isInitialLoading && uiState.person == null -> {
            Box(modifier = modifier.fillMaxSize()) {
                ShimmerHost { PersonDetailSkeleton(onBack = onBack, isDark = isDark) }
            }
        }

        uiState.errorMessage != null && uiState.person == null -> {
            Box(modifier = modifier.fillMaxSize()) {
                PersonErrorState(
                    errorMessage = uiState.errorMessage ?: "加载失败",
                    onRetry = { viewModel.loadDetail(navParam) },
                    onBack = onBack,
                    isDark = isDark
                )
            }
        }

        else -> {
            val person = uiState.person
            val commentInteraction = remember(uiState.pendingPraiseIds, uiState.personNodeId, commentUserId) {
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
                                relateNodeId = uiState.personNodeId,
                                snapshot = comment
                            )
                        )
                    }
                )
            }

            val commentFeedState = remember(uiState, person) {
                CommentFeedState(
                    totalCommentCount = person?.commentCount
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
                relateNodeId = uiState.personNodeId,
                contentPadding = AdaptiveDetailDefaults.nonHeroContentPadding(),
                modifier = modifier,
                topBar = {
                    DetailTopNavigation(
                        isScrolled = isScrolledPastHeader,
                        title = uiState.person?.displayName ?: uiState.initialName ?: "人物详情",
                        onBack = onBack,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                floatingToolbar = {
                    if (uiState.person != null || !uiState.isInitialLoading) {
                        val baseCollect = person?.collect?.toLong() ?: 0L
                        val displayCollect = (baseCollect + uiState.collectCountDelta).coerceAtLeast(0L)
                        PersonFloatingActionBar(
                            collectCount = displayCollect,
                            isCollected = uiState.isCollected,
                            commentCount = person?.commentCount ?: (uiState.hotComments.size + uiState.latestComments.size),
                            showCommentButton = !isWidePane,
                            onToggleCollect = {
                                viewModel.toggleCollect()
                                val text = if (!uiState.isCollected) "已收藏人物 ♥" else "已取消收藏"
                                platformActions.showToast(text)
                            },
                            onCommentClick = {
                                val nodeId = if (uiState.personNodeId > 0L) uiState.personNodeId else navParam.personId
                                if (nodeId <= 0L) return@PersonFloatingActionBar
                                onNavigateToComments?.invoke(
                                    HobbyCommentsNavParam(
                                        nodeId = nodeId,
                                        itemType = "person",
                                        title = person?.displayName ?: navParam.initialName ?: "评论",
                                        initialCommentCount = person?.commentCount ?: 0
                                    )
                                )
                            },
                            onShareClick = onSharePerson,
                            isDark = isDark
                        )
                    }
                }
            ) {
                // ── 区块 0：主体综合资料卡 ──
                item(key = "section_header") {
                    PersonHeaderSection(
                        person = person,
                        initialName = navParam.initialName,
                        initialCover = navParam.initialCover,
                        isIntroExpanded = uiState.isIntroExpanded,
                        onToggleIntro = { viewModel.toggleIntroExpanded() },
                        isDark = isDark
                    )
                }

                // ── 区块 1：参与周边 (精选九宫格网格) ──
                item(key = "section_hobbies") {
                    PersonHobbiesSection(
                        hobbies = uiState.hobbies,
                        isLoading = uiState.isHobbiesLoading,
                        onHobbyClick = { entry ->
                            onNavigateToHobby(
                                DetailNavParam(
                                    itemId = entry.h.itemId,
                                    initialName = entry.h.displayName,
                                    initialCover = entry.h.cover
                                )
                            )
                        },
                        onViewMore = {
                            val nodeId = uiState.personNodeId
                            if (nodeId > 0L && onNavigateToHobbyList != null) {
                                onNavigateToHobbyList(
                                    HobbyListNavParam(
                                        title = "${person?.displayName ?: "人物"}的周边",
                                        workers = nodeId.toString()
                                    )
                                )
                            } else {
                                val keyword = person?.displayName ?: navParam.initialName.orEmpty()
                                if (keyword.isNotBlank()) {
                                    onNavigateToSearch?.invoke(keyword)
                                } else {
                                    platformActions.showToast("正在检索中...")
                                }
                            }
                        },
                        isDark = isDark
                    )
                }

                // ── 区块 2：参与作品 ──
                if (uiState.isWorksLoading || uiState.works.isNotEmpty()) {
                    item(key = "section_works") {
                        PersonWorksSection(
                            works = uiState.works,
                            isLoading = uiState.isWorksLoading,
                            onWorkClick = { work ->
                                val worksId = if (work.itemId > 0L) work.itemId else work.id
                                if (onNavigateToWorks != null && worksId > 0L) {
                                    onNavigateToWorks(
                                        WorksNavParam(
                                            worksId = worksId,
                                            initialName = work.displayName,
                                            initialCover = work.cover
                                        )
                                    )
                                } else if (onNavigateToSearch != null) {
                                    onNavigateToSearch(work.displayName)
                                } else {
                                    platformActions.copyToClipboard("repoi", work.displayName)
                                    platformActions.showToast("已复制：${work.displayName}")
                                }
                            },
                            onWorkLongClick = { work ->
                                platformActions.copyToClipboard("repoi", work.displayName)
                                platformActions.showToast("已复制作品名：${work.displayName}")
                            },
                            onViewMore = {
                                val keyword = person?.displayName ?: navParam.initialName.orEmpty()
                                onNavigateToSearch?.invoke(keyword)
                            },
                            isDark = isDark
                        )
                    }
                }

                // ── 区块 3：出演角色 ──
                if (uiState.isCharactersLoading || uiState.characters.isNotEmpty()) {
                    item(key = "section_characters") {
                        PersonCharactersSection(
                            characters = uiState.characters,
                            isLoading = uiState.isCharactersLoading,
                            onCharacterClick = { character ->
                                val charactarId = if (character.itemId > 0L) character.itemId else character.id
                                if (onNavigateToCharacter != null && charactarId > 0L) {
                                    onNavigateToCharacter(
                                        CharacterNavParam(
                                            characterId = charactarId,
                                            initialName = character.displayName,
                                            initialCover = character.cover
                                        )
                                    )
                                } else if (onNavigateToWorks != null && charactarId > 0L) {
                                    onNavigateToWorks(
                                        WorksNavParam(
                                            worksId = charactarId,
                                            initialName = character.displayName,
                                            initialCover = character.cover
                                        )
                                    )
                                } else if (onNavigateToSearch != null) {
                                    onNavigateToSearch(character.displayName)
                                } else {
                                    platformActions.copyToClipboard("repoi", character.displayName)
                                    platformActions.showToast("已复制：${character.displayName}")
                                }
                            },
                            onCharacterLongClick = { character ->
                                platformActions.copyToClipboard("repoi", character.displayName)
                                platformActions.showToast("已复制角色名：${character.displayName}")
                            },
                            onViewMore = {
                                val keyword = person?.displayName ?: navParam.initialName.orEmpty()
                                onNavigateToSearch?.invoke(keyword)
                            },
                            isDark = isDark
                        )
                    }
                }
            }
        }
    }
}
