package com.mssh.di

import com.mssh.ui.hosts.HostEditViewModel
import com.mssh.ui.hosts.HostListViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val commonModule = module {
    // ViewModels
    viewModel { HostListViewModel(get()) }
    viewModel { params -> HostEditViewModel(get(), get(), params.get()) }
}
