package com.nobody.repoi.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nobody.repoi.data.remote.interceptor.CommonParams
import com.nobody.repoi.data.remote.model.PictureRelateItem
import com.nobody.repoi.platform.LocalAppMessages
import com.nobody.repoi.platform.openUrl
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.navigation.PictureGalleryNavParam
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PictureGalleryRouteScreen(
    param: PictureGalleryNavParam,
    onBack: () -> Unit,
    onNavigateToAlbum: (AlbumNavParam) -> Unit,
    onNavigateToHobby: (DetailNavParam) -> Unit,
    viewModel: PictureGalleryViewModel = koinViewModel()
) {
    LaunchedEffect(param) {
        viewModel.ensureInitial(
            userId = param.userId,
            order = param.order,
            targetPicId = param.picId,
            targetIndex = param.initialIndex
        )
    }

    val listState by viewModel.listState.collectAsStateWithLifecycle()

    val state = listState
    val messages = LocalAppMessages.current

    LaunchedEffect(state.targetMissing) {
        if (state.targetMissing) {
            messages.show("该图片已不在收藏列表中")
            onBack()
        }
    }

    when {
        state.isLoading && state.items.isEmpty() -> {
            GalleryLoadingState()
        }

        state.errorMessage != null && state.items.isEmpty() -> {
            GalleryErrorState(
                message = state.errorMessage.orEmpty(),
                onRetry = viewModel::retryList,
                onBack = onBack
            )
        }

        state.targetMissing -> {
            GalleryLoadingState()
        }

        state.items.isEmpty() -> {
            GalleryLoadingState()
        }

        else -> {
            PictureGalleryScreen(
                items = state.items,
                initialIndex = state.initialIndex,
                hasMore = state.hasMore,
                isLoadingMore = state.isLoadingMore,
                onLoadMore = viewModel::loadMore,
                onBack = onBack,
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToHobby = onNavigateToHobby,
                onNavigateToArticle = { relate ->
                    if (!openArticleInBrowser(relate)) {
                        messages.show("无法打开文章链接")
                    }
                }
            )
        }
    }
}

private fun openArticleInBrowser(relate: PictureRelateItem): Boolean {
    val articleId = if (relate.itemId > 0L) relate.itemId else relate.id
    if (articleId <= 0L) return false
    return openUrl(CommonParams.WEB_BASE_URL + "article/" + articleId)
}

@Composable
private fun GalleryLoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun GalleryErrorState(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = message, color = Color.White)
            TextButton(onClick = onRetry) {
                Text(text = "重试", color = Color.White)
            }
            TextButton(onClick = onBack) {
                Text(text = "返回", color = Color.White)
            }
        }
    }
}
