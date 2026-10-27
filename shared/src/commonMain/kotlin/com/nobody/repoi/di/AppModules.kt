package com.nobody.repoi.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import coil3.ImageLoader
import com.nobody.repoi.data.local.CategoryApi
import com.nobody.repoi.data.local.CategoryManager
import com.nobody.repoi.data.local.SearchHistoryManager
import com.nobody.repoi.data.local.ThemeManager
import com.nobody.repoi.data.local.TokenManager
import com.nobody.repoi.data.local.ViewPreferencesManager
import com.nobody.repoi.data.remote.api.ExHobbyApi
import com.nobody.repoi.data.remote.api.HpoiApi
import com.nobody.repoi.network.appJson
import com.nobody.repoi.network.createExHobbyClient
import com.nobody.repoi.network.createHpoiClient
import com.nobody.repoi.network.createImageClient
import com.nobody.repoi.platform.createDataStore
import com.nobody.repoi.platform.createImageLoader
import com.nobody.repoi.platform.platformContext
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * 网络模块：Json + 三个 HttpClient + Coil ImageLoader。
 *
 * `HpoiApi`/`ExHobbyApi` 挂在各自 client 上（Wave 3）。
 */
val networkModule = module {
    single<Json> { appJson() }

    single<() -> String>(named("tokenProvider")) { { get<TokenManager>().currentToken } }

    single<HttpClient>(named("hpoi")) {
        createHpoiClient(json = get(), tokenProvider = get(named("tokenProvider")))
    }
    single<HttpClient>(named("image")) {
        createImageClient()
    }
    single<HttpClient>(named("exhobby")) {
        createExHobbyClient(json = get())
    }

    single { HpoiApi(client = get(named("hpoi")), json = get()) }
    single { ExHobbyApi(client = get(named("exhobby")), json = get()) }

    single<ImageLoader> {
        createImageLoader(
            context = platformContext(),
            imageHttpClient = get(named("image"))
        )
    }
}

/**
 * 数据模块：DataStore / Managers / Repositories。
 */
val dataModule = module {
    single<DataStore<Preferences>>(named("user_prefs")) { createDataStore("user_prefs.preferences_pb") }
    single<DataStore<Preferences>>(named("theme_prefs")) { createDataStore("theme_prefs.preferences_pb") }
    single<DataStore<Preferences>>(named("search_history_prefs")) { createDataStore("search_history_prefs.preferences_pb") }
    single<DataStore<Preferences>>(named("view_prefs")) { createDataStore("view_prefs.preferences_pb") }

    single { TokenManager(dataStore = get(named("user_prefs"))) }
    single { ThemeManager(dataStore = get(named("theme_prefs"))) }
    single { SearchHistoryManager(dataStore = get(named("search_history_prefs")), json = get()) }
    single { ViewPreferencesManager(dataStore = get(named("view_prefs"))) }

    single<CategoryApi> { CategoryApi { get<HpoiApi>().getAllCategories() } }
    single { CategoryManager(apiService = get()) }
}

/**
 * 各端平台模块：PlatformActions、Context（Android）等。
 */
expect val platformModule: Module
