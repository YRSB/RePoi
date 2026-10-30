package com.nobody.repoi.di

import com.nobody.repoi.ui.debug.DebugApiViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * dev-only 调试 ViewModel 模块（androidMain-only，M3 自 M2 common 迁入）。
 *
 * dev-only，永不进生产导航：M6 root 不注册 DebugApi 入口。
 */
val debugViewModelModule = module {
    viewModel { DebugApiViewModel(get()) }
}
