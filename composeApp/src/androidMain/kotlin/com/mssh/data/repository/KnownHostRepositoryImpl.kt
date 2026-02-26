package com.mssh.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mssh.data.db.MsshDatabase
import com.mssh.data.model.KnownHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class KnownHostRepositoryImpl(
    private val database: MsshDatabase
) : KnownHostRepository {

    private val queries get() = database.knownHostQueries

    override fun getAllKnownHosts(): Flow<List<KnownHost>> {
        return queries.selectAll().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { it.toKnownHost() }
        }
    }

    override suspend fun getKnownHostsByHostPort(host: String, port: Int): List<KnownHost> =
        withContext(Dispatchers.IO) {
            queries.selectByHostPort(host, port.toLong()).executeAsList().map { it.toKnownHost() }
        }

    override suspend fun addKnownHost(knownHost: KnownHost) = withContext(Dispatchers.IO) {
        queries.insert(
            host = knownHost.host,
            port = knownHost.port.toLong(),
            key_algorithm = knownHost.keyAlgorithm,
            host_key = knownHost.hostKey,
            added_at = knownHost.addedAt
        )
    }

    override suspend fun deleteKnownHost(host: String, port: Int) = withContext(Dispatchers.IO) {
        queries.deleteByHostPort(host, port.toLong())
    }

    private fun com.mssh.data.db.KnownHost.toKnownHost(): KnownHost {
        return KnownHost(
            id = id,
            host = host,
            port = port.toInt(),
            keyAlgorithm = key_algorithm,
            hostKey = host_key,
            addedAt = added_at
        )
    }
}
