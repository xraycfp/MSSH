package com.mssh.data.model

import kotlinx.serialization.Serializable

@Serializable
data class KnownHost(
    val id: Long = 0,
    val host: String,
    val port: Int,
    val keyAlgorithm: String,
    val hostKey: String,
    val addedAt: Long = System.currentTimeMillis()
)
