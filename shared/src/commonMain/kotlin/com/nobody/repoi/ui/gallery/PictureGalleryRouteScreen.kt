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
import com.nobody.repoi.platform.openUrl
import com.nobody.repoi.ui.album.AlbumNavParam
import com.nobody.repoi.ui.detail.DetailNavParam
import com.nobody.repoi.ui.navigation.PictureGalleryNavParam
import org.koin.compose.viewmodel.koinViewModel

/**
 * 收藏-图片 全屏大图画廊的 Nav3 路由宿主。
 *
 * 画廊从「我的收藏」的组合内覆盖层升级为独立路由后：
 * 1. 返回语义完全交由 Nav3 出栈（系统返回 / 侧滑 = 关闭画廊回到收藏页，预测性返回可用）；
 * 2. 列表数据由 [PictureGalleryViewModel] 按收藏页同一接口/排序自加载（不再依赖收藏页组合内的临时状态）；
 * 3. 从画廊进入关联相册/手办详情再返回时，画廊 Entry 与其 ViewModel 仍在栈中，可原地恢复。
 *
 * @param param 路由参数（用户、目标图片、初始下标、排序档）
 * @param onBack 关闭画廊（Nav3 出栈）
 * @param onNavigateToAlbum 关联条目为相册时的跳转
 * @param onNavigateToHobby 关联条目为手办时的跳转
 */
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
    // 以普通局部变量承接一次快照，避免对 delegated property 做 smart cast
    val state = listState

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
                onNavigateToArticle = { relate -> openArticleInBrowser(relate) }
            )
        }
    }
}

/**
 * 关联条目为「文章」时的兜底跳转：本工程暂无文章详情页，
 * 对齐原版网页路径 `article/{itemId}` 交给系统浏览器打开。
 */
private fun openArticleInBrowser(relate: PictureRelateItem) {
    val articleId = if (relate.itemId > 0L) relate.itemId else relate.id
    if (articleId <= 0L) return
    openUrl(CommonParams.BASE_URL + "article/" + articleId)
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
