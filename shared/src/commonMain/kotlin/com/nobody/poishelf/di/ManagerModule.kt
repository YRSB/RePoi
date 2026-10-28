package com.nobody.poishelf.di

import com.nobody.poishelf.data.local.CategoryManager
import com.nobody.poishelf.data.local.SearchHistoryManager
import com.nobody.poishelf.data.local.ThemeManager
import com.nobody.poishelf.data.local.TokenManager
import com.nobody.poishelf.data.local.ViewPreferencesManager
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * 管理器模块：5 managers（DataStore 由平台 `dataStoreModule` 具名提供）。
 */
val managerModule = module {
    single { TokenManager(get(named("user_prefs"))) }
    single { ThemeManager(get(named("theme_prefs"))) }
    single { SearchHistoryManager(get(named("search_history_prefs")), get()) }
    single { ViewPreferencesManager(get(named("view_prefs"))) }
    single { CategoryManager(get()) }
}
