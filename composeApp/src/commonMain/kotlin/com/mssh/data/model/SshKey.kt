package com.mssh.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class KeyType {
    RSA_4096,
    ED25519,
    ECDSA_256,
    ECDSA_384
}

@Serializable
data class SshKey(
    val id: Long = 0,
    val name: String,
    val type: KeyType,
    val publicKey: String,
    val privateKey: String,
    val passphrase: String? = null,
    val fingerprint: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
