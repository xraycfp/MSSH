package com.mssh.data.repository

import com.mssh.data.model.HostConfig
import kotlinx.coroutines.flow.Flow

interface HostRepository {
    fun getAllHosts(): Flow<List<HostConfig>>
    suspend fun getHostById(id: Long): HostConfig?
    suspend fun insertHost(host: HostConfig): Long
    suspend fun updateHost(host: HostConfig)
    suspend fun deleteHost(id: Long)
    fun searchHosts(query: String): Flow<List<HostConfig>>
}
