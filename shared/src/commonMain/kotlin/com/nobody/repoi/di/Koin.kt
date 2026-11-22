package com.nobody.repoi.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

fun initKoin(appDeclaration: KoinAppDeclaration = {}) = startKoin {
    appDeclaration()
    modules(
        networkModule,
        dataStoreModule,
        managerModule,
        repositoryModule,
        viewModelModule,
        imageModule,
    )
}

expect val dataStoreModule: Module
