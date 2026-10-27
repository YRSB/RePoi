package com.nobody.repoi.di

import com.nobody.repoi.ui.home.HomeFeedViewModel
import com.nobody.repoi.ui.search.SearchViewModel
import com.nobody.repoi.ui.item.home.ItemHomeViewModel
import com.nobody.repoi.ui.item.list.HobbyListViewModel
import com.nobody.repoi.ui.detail.HobbyDetailViewModel
import com.nobody.repoi.ui.detail.album.HobbyAlbumListViewModel
import com.nobody.repoi.ui.gallery.PictureGalleryViewModel
import com.nobody.repoi.ui.picture.PictureListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Wave 6 发现域 ViewModel 模块。
 */
val discoveryModule = module {
    viewModel { HomeFeedViewModel(homeRepository = get(), zoneRepository = get(), viewPreferencesManager = get()) }
    viewModel { SearchViewModel(searchRepository = get(), categoryManager = get()) }
    viewModel { ItemHomeViewModel(itemRepository = get(), categoryManager = get()) }
    viewModel { HobbyListViewModel(itemRepository = get(), categoryManager = get(), viewPreferencesManager = get()) }
    viewModel {
        HobbyDetailViewModel(
            repository = get(),
            collectRepository = get(),
            commentDelegate = get(),
            exHobbyRepository = get(),
            viewPreferencesManager = get(),
            userRepository = get()
        )
    }
    viewModel { HobbyAlbumListViewModel(repository = get()) }
    viewModel {
        PictureGalleryViewModel(
            collectRepository = get(),
            imageHttpClient = get(named("image")),
            platformActions = get()
        )
    }
    viewModel { PictureListViewModel(repository = get()) }
}
