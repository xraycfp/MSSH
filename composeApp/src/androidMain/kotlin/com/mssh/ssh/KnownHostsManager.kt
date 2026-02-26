package com.mssh.ssh

import android.util.Base64
import com.mssh.data.model.KnownHost
import com.mssh.data.repository.KnownHostRepository
import com.trilead.ssh2.ServerHostKeyVerifier
import java.security.MessageDigest

/**
 * Result of host key verification.
 */
sealed class HostKeyVerifyResult {
    data object Trusted : HostKeyVerifyResult()
    data object Unknown : HostKeyVerifyResult()
    data class Changed(val oldFingerprint: String, val newFingerprint: String) : HostKeyVerifyResult()
}

/**
 * Callback interface for when user approval is needed.
 */
interface HostKeyApprovalCallback {
    /**
     * Called when a host key needs user approval.
     * @return true to accept, false to reject
     */
    suspend fun onApprovalNeeded(
        host: String,
        port: Int,
        algorithm: String,
        fingerprint: String,
        result: HostKeyVerifyResult
    ): Boolean
}

/**
 * Manages known hosts for SSH host key verification.
 */
class KnownHostsManager(
    private val knownHostRepository: KnownHostRepository
) {
    private var approvalCallback: HostKeyApprovalCallback? = null

    fun setApprovalCallback(callback: HostKeyApprovalCallback) {
        this.approvalCallback = callback
    }

    /**
     * Create a ServerHostKeyVerifier that checks against known hosts.
     */
    fun createVerifier(host: String, port: Int): ServerHostKeyVerifier {
        return ServerHostKeyVerifier { hostname, p, serverHostKeyAlgorithm, serverHostKey ->
            verifyHostKey(host, port, serverHostKeyAlgorithm, serverHostKey)
        }
    }

    private fun verifyHostKey(
        host: String,
        port: Int,
        algorithm: String,
        hostKey: ByteArray
    ): Boolean {
        // For now, accept all keys (TODO: implement full verification with user approval)
        // In a production app, this would check against known hosts and prompt the user
        return true
    }

    companion object {
        /**
         * Compute SHA-256 fingerprint of a host key.
         */
        fun computeFingerprint(hostKey: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(hostKey)
            return "SHA256:" + Base64.encodeToString(hash, Base64.NO_PADDING or Base64.NO_WRAP)
        }
    }
}
