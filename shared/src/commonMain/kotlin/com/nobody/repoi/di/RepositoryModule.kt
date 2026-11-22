package com.nobody.repoi.di

import com.nobody.repoi.data.repository.AlbumDetailRepository
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.CharacterDetailRepository
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.CoverFreshnessRepository
import com.nobody.repoi.data.repository.ExHobbyRepository
import com.nobody.repoi.data.repository.FollowRepository
import com.nobody.repoi.data.repository.HobbyDetailRepository
import com.nobody.repoi.data.repository.HomeRepository
import com.nobody.repoi.data.repository.ItemRepository
import com.nobody.repoi.data.repository.MessageRepository
import com.nobody.repoi.data.repository.PersonDetailRepository
import com.nobody.repoi.data.repository.PictureListRepository
import com.nobody.repoi.data.repository.PostRepository
import com.nobody.repoi.data.repository.ResaleRepository
import com.nobody.repoi.data.repository.SearchRepository
import com.nobody.repoi.data.repository.SeriesDetailRepository
import com.nobody.repoi.data.repository.SettingsRepository
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.repository.UserFinanceRepository
import com.nobody.repoi.data.repository.UserHistoryRepository
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.data.repository.VendorDetailRepository
import com.nobody.repoi.data.repository.WorksDetailRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.domain.usecase.collect.BatchCancelCollectUseCase
import com.nobody.repoi.domain.usecase.collect.DeleteHobbyCollectUseCase
import com.nobody.repoi.domain.usecase.collect.DeleteWorksCollectUseCase
import com.nobody.repoi.domain.usecase.collect.SaveHobbyCollectUseCase
import com.nobody.repoi.domain.usecase.collect.SaveWorksCollectUseCase
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose

val repositoryModule = module {
    singleOf(::CollectRepository)
    single { AlbumRepository(get()) }
    singleOf(::AlbumDetailRepository)
    singleOf(::CharacterDetailRepository)
    single { CommentRepository(get()) }
    single { ExHobbyRepository(get()) }
    single { FollowRepository(get(), get()) }
    singleOf(::HobbyDetailRepository)
    single { HomeRepository(get()) }
    single { ItemRepository(get()) }
    single { MessageRepository(get()) }
    singleOf(::PersonDetailRepository)
    single { PictureListRepository(get()) }
    single { PostRepository(get()) }
    single { ResaleRepository(get()) }
    single { SearchRepository(get(), get()) }
    singleOf(::SeriesDetailRepository)
    single { SettingsRepository(get()) }
    single { StickerRepository(get()) }
    single { UserHistoryRepository(get(), get()) }

    single(named("cover_freshness_scope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }.onClose { scope -> scope?.cancel() }
    single {
        CoverFreshnessRepository(
            hobbyDetailRepository = get(),
            albumDetailRepository = get(),
            preferences = get(named("cover_freshness_prefs")),
            scope = get(named("cover_freshness_scope")),
        )
    }
    single { UserFinanceRepository(get(), get()) }
    singleOf(::UserRepository)
    singleOf(::VendorDetailRepository)
    single { WorksDetailRepository(get()) }
    single { ZoneRepository(get(), get()) }
    factory { CommentInteractionDelegate(get(), get(), get()) }

    factoryOf(::SaveHobbyCollectUseCase)
    factoryOf(::DeleteHobbyCollectUseCase)
    factoryOf(::BatchCancelCollectUseCase)
    factoryOf(::SaveWorksCollectUseCase)
    factoryOf(::DeleteWorksCollectUseCase)
}
