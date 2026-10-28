package com.nobody.poishelf.di

import com.nobody.poishelf.data.repository.AlbumDetailRepository
import com.nobody.poishelf.data.repository.AlbumRepository
import com.nobody.poishelf.data.repository.CharacterDetailRepository
import com.nobody.poishelf.data.repository.CollectRepository
import com.nobody.poishelf.data.repository.CommentRepository
import com.nobody.poishelf.data.repository.ExHobbyRepository
import com.nobody.poishelf.data.repository.FollowRepository
import com.nobody.poishelf.data.repository.ForumRepository
import com.nobody.poishelf.data.repository.HobbyDetailRepository
import com.nobody.poishelf.data.repository.HomeRepository
import com.nobody.poishelf.data.repository.ItemRepository
import com.nobody.poishelf.data.repository.MessageRepository
import com.nobody.poishelf.data.repository.PersonDetailRepository
import com.nobody.poishelf.data.repository.PictureListRepository
import com.nobody.poishelf.data.repository.PostRepository
import com.nobody.poishelf.data.repository.ResaleRepository
import com.nobody.poishelf.data.repository.SearchRepository
import com.nobody.poishelf.data.repository.SeriesDetailRepository
import com.nobody.poishelf.data.repository.StickerRepository
import com.nobody.poishelf.data.repository.UserRepository
import com.nobody.poishelf.data.repository.VendorDetailRepository
import com.nobody.poishelf.data.repository.WorksDetailRepository
import com.nobody.poishelf.data.repository.ZoneRepository
import com.nobody.poishelf.ui.common.comment.CommentInteractionDelegate
import org.koin.dsl.module

/**
 * 仓储模块：23 repositories + CommentInteractionDelegate（factory，贴合原 Hilt 非单例语义）。
 * `CollectRepository` 声明在前（Person/Series/Vendor 依赖它；Koin 无序但可读性优先）。
 */
val repositoryModule = module {
    single { CollectRepository(get(), get()) }
    single { AlbumRepository(get()) }
    single { AlbumDetailRepository(get()) }
    single { CharacterDetailRepository(get()) }
    single { CommentRepository(get(), get()) }
    single { ExHobbyRepository(get()) }
    single { FollowRepository(get(), get()) }
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
    single { StickerRepository(get()) }
    single { UserRepository(get(), get()) }
    single { VendorDetailRepository(get(), get()) }
    single { WorksDetailRepository(get()) }
    single { ZoneRepository(get(), get()) }
    factory { CommentInteractionDelegate(get(), get(), get(), get()) }
}
