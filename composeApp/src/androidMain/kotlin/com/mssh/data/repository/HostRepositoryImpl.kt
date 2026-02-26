package com.mssh.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.mssh.data.db.MsshDatabase
import com.mssh.data.model.AuthType
import com.mssh.data.model.HostConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class HostRepositoryImpl(
    private val database: MsshDatabase
) : HostRepository {

    private val queries get() = database.hostQueries

    override fun getAllHosts(): Flow<List<HostConfig>> {
        return queries.selectAll().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { it.toHostConfig() }
        }
    }

    override suspend fun getHostById(id: Long): HostConfig? = withContext(Dispatchers.IO) {
        queries.selectById(id).executeAsOneOrNull()?.toHostConfig()
    }

    override suspend fun insertHost(host: HostConfig): Long = withContext(Dispatchers.IO) {
        queries.insert(
            name = host.name,
            host = host.host,
            port = host.port.toLong(),
            username = host.username,
            auth_type = host.authType.name,
            password = host.password,
            key_id = host.keyId,
            created_at = host.createdAt,
            updated_at = host.updatedAt
        )
        queries.lastInsertId().executeAsOne()
    }

    override suspend fun updateHost(host: HostConfig) = withContext(Dispatchers.IO) {
        queries.update(
            name = host.name,
            host = host.host,
            port = host.port.toLong(),
            username = host.username,
            auth_type = host.authType.name,
            password = host.password,
            key_id = host.keyId,
            updated_at = System.currentTimeMillis(),
            id = host.id
        )
    }

    override suspend fun deleteHost(id: Long) = withContext(Dispatchers.IO) {
        queries.deleteById(id)
    }

    override fun searchHosts(query: String): Flow<List<HostConfig>> {
        val pattern = "%$query%"
        return queries.search(pattern, pattern, pattern).asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { it.toHostConfig() }
        }
    }

    private fun com.mssh.data.db.Host.toHostConfig(): HostConfig {
        return HostConfig(
            id = id,
            name = name,
            host = host,
            port = port.toInt(),
            username = username,
            authType = try { AuthType.valueOf(auth_type) } catch (_: Exception) { AuthType.PASSWORD },
            password = password,
            keyId = key_id,
            createdAt = created_at,
            updatedAt = updated_at
        )
    }
}
