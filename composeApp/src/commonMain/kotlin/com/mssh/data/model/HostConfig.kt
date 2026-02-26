package com.mssh.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class AuthType {
    PASSWORD,
    PUBLIC_KEY
}

@Serializable
data class HostConfig(
    val id: Long = 0,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: AuthType = AuthType.PASSWORD,
    val password: String? = null,
    val keyId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
