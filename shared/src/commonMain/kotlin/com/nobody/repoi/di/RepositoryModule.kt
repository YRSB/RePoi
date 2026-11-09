package com.nobody.repoi.di

import com.nobody.repoi.data.repository.AlbumDetailRepository
import com.nobody.repoi.data.repository.AlbumRepository
import com.nobody.repoi.data.repository.CharacterDetailRepository
import com.nobody.repoi.data.repository.CollectRepository
import com.nobody.repoi.data.repository.CommentRepository
import com.nobody.repoi.data.repository.ExHobbyRepository
import com.nobody.repoi.data.repository.FollowRepository
import com.nobody.repoi.data.repository.ForumRepository
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
import com.nobody.repoi.domain.usecase.collect.ToggleItemCollectUseCase
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import org.koin.dsl.module

/**
 * 仓储模块：25 repositories + CommentInteractionDelegate（factory，贴合原 Hilt 非单例语义）。
 * `CollectRepository` 声明在前（Person/Series/Vendor 依赖它；Koin 无序但可读性优先）。
 *
 * 模块尾部同时挂载收藏写侧 Domain UseCases（`factory`，无状态、随用随建）：
 * 它们只依赖仓储与会话，承担收藏领域的业务校验与数据清洗，避免校验散落各 ViewModel。
 */
val repositoryModule = module {
    single { CollectRepository(get(), get(), get()) }
    single { AlbumRepository(get()) }
    single { AlbumDetailRepository(get()) }
    single { CharacterDetailRepository(get(), get()) }
    single { CommentRepository(get(), get()) }
    single { ExHobbyRepository(get()) }
    single { FollowRepository(get(), get(), get()) }
    single { ForumRepository(get(), get()) }
    single { HobbyDetailRepository(get()) }
    single { HomeRepository(get()) }
    single { ItemRepository(get()) }
    single { MessageRepository(get(), get()) }
    single { PersonDetailRepository(get(), get()) }
    single { PictureListRepository(get()) }
    single { PostRepository(get(), get()) }
    single { ResaleRepository(get()) }
    single { SearchRepository(get(), get()) }
    single { SeriesDetailRepository(get(), get()) }
    single { SettingsRepository(get(), get()) }
    single { StickerRepository(get()) }
    single { UserHistoryRepository(get(), get()) }
    single { UserFinanceRepository(get(), get()) }
    single { UserRepository(get(), get(), get(), get()) }
    single { VendorDetailRepository(get(), get()) }
    single { WorksDetailRepository(get()) }
    single { ZoneRepository(get(), get()) }
    factory { CommentInteractionDelegate(get(), get(), get()) }

    // ── 收藏写侧 Domain UseCases（无状态，factory 即取即用） ──
    factory { ToggleItemCollectUseCase(get()) }
    factory { SaveHobbyCollectUseCase(get()) }
    factory { DeleteHobbyCollectUseCase(get()) }
    factory { BatchCancelCollectUseCase(get(), get()) }
    factory { SaveWorksCollectUseCase(get()) }
    factory { DeleteWorksCollectUseCase(get()) }
}
