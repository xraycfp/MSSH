package com.mssh.data.repository

import com.mssh.data.model.SshKey
import kotlinx.coroutines.flow.Flow

interface SshKeyRepository {
    fun getAllKeys(): Flow<List<SshKey>>
    suspend fun getKeyById(id: Long): SshKey?
    suspend fun insertKey(key: SshKey): Long
    suspend fun updateKey(id: Long, name: String, passphrase: String?)
    suspend fun deleteKey(id: Long)
}
