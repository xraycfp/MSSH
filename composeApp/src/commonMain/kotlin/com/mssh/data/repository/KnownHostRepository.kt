package com.mssh.data.repository

import com.mssh.data.model.KnownHost
import kotlinx.coroutines.flow.Flow

interface KnownHostRepository {
    fun getAllKnownHosts(): Flow<List<KnownHost>>
    suspend fun getKnownHostsByHostPort(host: String, port: Int): List<KnownHost>
    suspend fun addKnownHost(knownHost: KnownHost)
    suspend fun deleteKnownHost(host: String, port: Int)
}
