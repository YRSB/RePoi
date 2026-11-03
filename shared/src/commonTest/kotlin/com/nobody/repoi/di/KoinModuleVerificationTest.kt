package com.nobody.repoi.di

import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.SearchHistoryManager
import com.nobody.repoi.data.local.TestDataStore
import com.nobody.repoi.data.local.ThemeManager
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.ExHobbyApi
import com.nobody.repoi.data.remote.api.HpoiApi
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
import com.nobody.repoi.data.repository.StickerRepository
import com.nobody.repoi.data.repository.UserRepository
import com.nobody.repoi.data.repository.VendorDetailRepository
import com.nobody.repoi.data.repository.WorksDetailRepository
import com.nobody.repoi.data.repository.ZoneRepository
import com.nobody.repoi.ui.album.AlbumViewModel
import com.nobody.repoi.ui.common.comment.CommentInteractionDelegate
import com.nobody.repoi.ui.follow.FollowViewModel
import com.nobody.repoi.ui.home.HomeFeedViewModel
import com.nobody.repoi.ui.login.LoginViewModel
import com.nobody.repoi.ui.settings.SettingsViewModel
import com.nobody.repoi.util.CollectInvalidationBus
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test

/**
 * Koin 依赖注入完整性与依赖图验证测试。
 *
 * 验证核心网络模块、管理器模块、仓储模块及无参 ViewModel 的依赖绑定图完整解析，
 * 防止由于构造参数增删遗漏在运行时抛出 NoDefinitionFoundException。
 */
class KoinModuleVerificationTest {

    private val testDataStoreModule = module {
        single(named("user_prefs")) { TestDataStore.userPrefs() }
        single(named("theme_prefs")) { TestDataStore.themePrefs() }
        single(named("search_history_prefs")) { TestDataStore.searchHistoryPrefs() }
        single(named("view_prefs")) { TestDataStore.viewPrefs() }
    }

    @Test
    fun verifyManagersAndRepositoriesResolveSuccessfully() {
        val app = koinApplication {
            modules(
                networkModule,
                testDataStoreModule,
                managerModule,
                repositoryModule,
            )
        }

        try {
            val koin = app.koin

            // 1. 基础工具与 API
            koin.getOrNull<Json>().shouldNotBeNull()
            koin.getOrNull<HpoiApi>().shouldNotBeNull()
            koin.getOrNull<ExHobbyApi>().shouldNotBeNull()

            // 2. 本地状态与业务管理器
            koin.getOrNull<TokenManager>().shouldNotBeNull()
            koin.getOrNull<ThemeManager>().shouldNotBeNull()
            koin.getOrNull<SearchHistoryManager>().shouldNotBeNull()
            koin.getOrNull<ViewPreferencesManager>().shouldNotBeNull()
            koin.getOrNull<CategoryManager>().shouldNotBeNull()
            koin.getOrNull<CollectInvalidationBus>().shouldNotBeNull()

            // 3. 全量 23 个 Repository 及其交互代理
            koin.getOrNull<CollectRepository>().shouldNotBeNull()
            koin.getOrNull<AlbumRepository>().shouldNotBeNull()
            koin.getOrNull<AlbumDetailRepository>().shouldNotBeNull()
            koin.getOrNull<CharacterDetailRepository>().shouldNotBeNull()
            koin.getOrNull<CommentRepository>().shouldNotBeNull()
            koin.getOrNull<ExHobbyRepository>().shouldNotBeNull()
            koin.getOrNull<FollowRepository>().shouldNotBeNull()
            koin.getOrNull<ForumRepository>().shouldNotBeNull()
            koin.getOrNull<HobbyDetailRepository>().shouldNotBeNull()
            koin.getOrNull<HomeRepository>().shouldNotBeNull()
            koin.getOrNull<ItemRepository>().shouldNotBeNull()
            koin.getOrNull<MessageRepository>().shouldNotBeNull()
            koin.getOrNull<PersonDetailRepository>().shouldNotBeNull()
            koin.getOrNull<PictureListRepository>().shouldNotBeNull()
            koin.getOrNull<PostRepository>().shouldNotBeNull()
            koin.getOrNull<ResaleRepository>().shouldNotBeNull()
            koin.getOrNull<SearchRepository>().shouldNotBeNull()
            koin.getOrNull<SeriesDetailRepository>().shouldNotBeNull()
            koin.getOrNull<StickerRepository>().shouldNotBeNull()
            koin.getOrNull<UserRepository>().shouldNotBeNull()
            koin.getOrNull<VendorDetailRepository>().shouldNotBeNull()
            koin.getOrNull<WorksDetailRepository>().shouldNotBeNull()
            koin.getOrNull<ZoneRepository>().shouldNotBeNull()
            koin.getOrNull<CommentInteractionDelegate>().shouldNotBeNull()
        } finally {
            app.close()
        }
    }

    @Test
    fun verifyCoreViewModelsResolveSuccessfully() {
        val app = koinApplication {
            modules(
                networkModule,
                testDataStoreModule,
                managerModule,
                repositoryModule,
                viewModelModule,
            )
        }

        try {
            val koin = app.koin
            // 验证无需动态 Navigation 参数的核心独立 ViewModel
            koin.getOrNull<HomeFeedViewModel>().shouldNotBeNull()
            koin.getOrNull<SettingsViewModel>().shouldNotBeNull()
            koin.getOrNull<LoginViewModel>().shouldNotBeNull()
            koin.getOrNull<AlbumViewModel>().shouldNotBeNull()
            koin.getOrNull<FollowViewModel>().shouldNotBeNull()
        } finally {
            app.close()
        }
    }
}
