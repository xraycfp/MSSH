package com.mssh.ssh

/**
 * Represents the state of an SSH connection.
 */
sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Connecting : ConnectionState()
    data object Authenticating : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String, val cause: Throwable? = null) : ConnectionState()
    data object Disconnected : ConnectionState()
}
