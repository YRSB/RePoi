package com.nobody.repoi.di

import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.SearchHistoryManager
import com.nobody.repoi.data.local.ThemeManager
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.session.UserSession
import com.nobody.repoi.data.session.createUserSession
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * 管理器模块：5 managers（DataStore 由平台 `dataStoreModule` 具名提供）。
 *
 * [UserSession] 是唯一对外鉴权接缝；底层凭据存储仅由会话层工厂内部构造，
 * 不再注册为可独立注入的单例。
 */
val managerModule = module {
    single<UserSession> { createUserSession(get(), get(named("user_prefs"))) }
    single { ThemeManager(get(named("theme_prefs"))) }
    single { SearchHistoryManager(get(named("search_history_prefs")), get()) }
    single { ViewPreferencesManager(get(named("view_prefs"))) }
    single { CategoryManager(get()) }
}
