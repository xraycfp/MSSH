package com.mssh.ssh

import android.util.Base64
import com.mssh.data.model.KeyType
import com.mssh.data.model.SshKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.StringWriter
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Generates SSH key pairs.
 */
class SshKeyGenerator {

    /**
     * Generate a new SSH key pair.
     *
     * @param name Display name for the key
     * @param type Key type to generate
     * @param passphrase Optional passphrase to encrypt the private key
     * @return SshKey with PEM-encoded public and private keys
     */
    suspend fun generateKey(
        name: String,
        type: KeyType,
        passphrase: String? = null
    ): SshKey = withContext(Dispatchers.IO) {
        val keyPair = when (type) {
            KeyType.RSA_4096 -> generateRsaKeyPair(4096)
            KeyType.ED25519 -> generateEd25519KeyPair()
            KeyType.ECDSA_256 -> generateEcdsaKeyPair("secp256r1")
            KeyType.ECDSA_384 -> generateEcdsaKeyPair("secp384r1")
        }

        val publicKeyString = encodePublicKey(keyPair, type)
        val privateKeyString = encodePemPrivateKey(keyPair)
        val fingerprint = computeFingerprint(keyPair.public.encoded)

        SshKey(
            name = name,
            type = type,
            publicKey = publicKeyString,
            privateKey = privateKeyString,
            passphrase = passphrase,
            fingerprint = fingerprint,
            createdAt = System.currentTimeMillis()
        )
    }

    private fun generateRsaKeyPair(keySize: Int): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(keySize)
        return generator.generateKeyPair()
    }

    private fun generateEd25519KeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("Ed25519")
        return generator.generateKeyPair()
    }

    private fun generateEcdsaKeyPair(curveName: String): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(curveName))
        return generator.generateKeyPair()
    }

    private fun encodePublicKey(keyPair: KeyPair, type: KeyType): String {
        val prefix = when (type) {
            KeyType.RSA_4096 -> "ssh-rsa"
            KeyType.ED25519 -> "ssh-ed25519"
            KeyType.ECDSA_256 -> "ecdsa-sha2-nistp256"
            KeyType.ECDSA_384 -> "ecdsa-sha2-nistp384"
        }
        val encoded = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        return "$prefix $encoded"
    }

    private fun encodePemPrivateKey(keyPair: KeyPair): String {
        val encoded = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        val lines = encoded.chunked(64)
        return buildString {
            appendLine("-----BEGIN PRIVATE KEY-----")
            lines.forEach { appendLine(it) }
            appendLine("-----END PRIVATE KEY-----")
        }
    }

    companion object {
        fun computeFingerprint(publicKeyBytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(publicKeyBytes)
            return "SHA256:" + Base64.encodeToString(hash, Base64.NO_PADDING or Base64.NO_WRAP)
        }
    }
}
