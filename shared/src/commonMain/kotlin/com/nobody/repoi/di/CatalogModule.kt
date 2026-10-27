package com.nobody.repoi.di

import com.nobody.repoi.ui.person.PersonDetailViewModel
import com.nobody.repoi.ui.series.SeriesDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Wave 7 目录域 ViewModel 模块（album + series + person）。
 */
val catalogModule = module {
    viewModel { PersonDetailViewModel(repository = get(), commentDelegate = get()) }
    viewModel { SeriesDetailViewModel(repository = get(), commentDelegate = get()) }
}
