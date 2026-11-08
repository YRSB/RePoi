package com.nobody.repoi.di

import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.SearchHistoryManager
import com.nobody.repoi.data.local.ThemeManager
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.data.session.UserSessionImpl
import com.nobody.repoi.util.CollectInvalidationBus
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * 管理器模块：5 managers（DataStore 由平台 `dataStoreModule` 具名提供）+ 跨路由失效事件总线。
 *
 * [UserSession] 是唯一对外鉴权接缝；[TokenManager] 仅作为其内部实现注册（后续票据移除公开注入）。
 */
val managerModule = module {
    single { TokenManager(get(named("user_prefs"))) }
    single<UserSession> { UserSessionImpl(get(), get()) }
    single { ThemeManager(get(named("theme_prefs"))) }
    single { SearchHistoryManager(get(named("search_history_prefs")), get()) }
    single { ViewPreferencesManager(get(named("view_prefs"))) }
    single { CategoryManager(get()) }
    single { CollectInvalidationBus() }
}
