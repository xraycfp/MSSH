package com.mssh.di

import com.mssh.data.db.DatabaseFactory
import com.mssh.data.db.MsshDatabase
import com.mssh.data.repository.HostRepository
import com.mssh.data.repository.HostRepositoryImpl
import com.mssh.data.repository.KnownHostRepository
import com.mssh.data.repository.KnownHostRepositoryImpl
import com.mssh.data.repository.SshKeyRepository
import com.mssh.data.repository.SshKeyRepositoryImpl
import com.mssh.ssh.SshConnectionManager
import com.mssh.ssh.SshKeyGenerator
import com.mssh.ui.keys.KeyManagerViewModel
import com.mssh.ui.terminal.TerminalViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val androidModule = module {
    // Database
    single<MsshDatabase> { DatabaseFactory.create(androidContext()) }

    // Repositories
    single<HostRepository> { HostRepositoryImpl(get()) }
    single<SshKeyRepository> { SshKeyRepositoryImpl(get()) }
    single<KnownHostRepository> { KnownHostRepositoryImpl(get()) }

    // SSH
    single { SshConnectionManager() }
    single { SshKeyGenerator() }

    // ViewModels (Android-specific)
    viewModel { KeyManagerViewModel(get(), get()) }
    viewModel { params -> TerminalViewModel(params.get(), get(), get(), get()) }
}
