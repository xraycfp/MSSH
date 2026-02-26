package com.mssh.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mssh.data.db.MsshDatabase
import com.mssh.data.model.KeyType
import com.mssh.data.model.SshKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SshKeyRepositoryImpl(
    private val database: MsshDatabase
) : SshKeyRepository {

    private val queries get() = database.sshKeyQueries

    override fun getAllKeys(): Flow<List<SshKey>> {
        return queries.selectAll().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { it.toSshKey() }
        }
    }

    override suspend fun getKeyById(id: Long): SshKey? = withContext(Dispatchers.IO) {
        queries.selectById(id).executeAsOneOrNull()?.toSshKey()
    }

    override suspend fun insertKey(key: SshKey): Long = withContext(Dispatchers.IO) {
        queries.insert(
            name = key.name,
            type = key.type.name,
            public_key = key.publicKey,
            private_key = key.privateKey,
            passphrase = key.passphrase,
            fingerprint = key.fingerprint,
            created_at = key.createdAt
        )
        queries.lastInsertId().executeAsOne()
    }

    override suspend fun updateKey(id: Long, name: String, passphrase: String?) = withContext(Dispatchers.IO) {
        queries.update(name = name, passphrase = passphrase, id = id)
    }

    override suspend fun deleteKey(id: Long) = withContext(Dispatchers.IO) {
        queries.deleteById(id)
    }

    private fun com.mssh.data.db.SshKey.toSshKey(): SshKey {
        return SshKey(
            id = id,
            name = name,
            type = try { KeyType.valueOf(type) } catch (_: Exception) { KeyType.ED25519 },
            publicKey = public_key,
            privateKey = private_key,
            passphrase = passphrase,
            fingerprint = fingerprint,
            createdAt = created_at
        )
    }
}
