package com.mssh.ssh

import android.util.Log
import com.trilead.ssh2.Connection
import com.trilead.ssh2.ServerHostKeyVerifier
import com.trilead.ssh2.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/**
 * Manages SSH connections using ConnectBot sshlib.
 */
class SshConnectionManager {
    companion object {
        private const val TAG = "SshConnectionManager"
    }

    /**
     * Connect to an SSH server.
     *
     * @param host Remote host address
     * @param port Remote port (default 22)
     * @param hostKeyVerifier Callback to verify the server's host key
     * @param connectTimeoutMs Connection timeout in milliseconds
     * @return An established Connection
     */
    suspend fun connect(
        host: String,
        port: Int = 22,
        hostKeyVerifier: ServerHostKeyVerifier,
        connectTimeoutMs: Int = 10_000
    ): Connection = withContext(Dispatchers.IO) {
        Log.d(TAG, "connect begin target=$host:$port timeoutMs=$connectTimeoutMs")
        val connection = Connection(host, port)
        connection.connect(hostKeyVerifier, connectTimeoutMs, connectTimeoutMs)
        Log.d(TAG, "connect success target=$host:$port")
        connection
    }

    /**
     * Authenticate with a password.
     */
    suspend fun authenticateWithPassword(
        connection: Connection,
        username: String,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "auth password begin user=$username")
        val ok = connection.authenticateWithPassword(username, password)
        Log.d(TAG, "auth password result user=$username ok=$ok")
        ok
    }

    /**
     * Authenticate with a public key (PEM-encoded private key).
     */
    suspend fun authenticateWithPublicKey(
        connection: Connection,
        username: String,
        privateKeyPem: CharArray,
        passphrase: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "auth pubkey begin user=$username hasPassphrase=${!passphrase.isNullOrEmpty()}")
        val ok = connection.authenticateWithPublicKey(username, privateKeyPem, passphrase)
        Log.d(TAG, "auth pubkey result user=$username ok=$ok")
        ok
    }

    /**
     * Open a shell session with PTY.
     *
     * @param connection An authenticated Connection
     * @param cols Terminal columns
     * @param rows Terminal rows
     * @param termType Terminal type (default "xterm-256color")
     * @return SshSessionWrapper wrapping the session
     */
    suspend fun openShellSession(
        connection: Connection,
        cols: Int = 80,
        rows: Int = 24,
        termType: String = "xterm-256color"
    ): SshSessionWrapper = withContext(Dispatchers.IO) {
        Log.d(TAG, "open shell begin term=$termType size=${cols}x$rows")
        val session = connection.openSession()
        session.requestPTY(termType, cols, rows, 0, 0, null)
        session.startShell()
        Log.d(TAG, "open shell success")
        SshSessionWrapper(session)
    }

    /**
     * Disconnect a connection.
     */
    fun disconnect(connection: Connection) {
        Log.d(TAG, "disconnect begin")
        try {
            connection.close()
        } catch (_: Exception) {
            // Ignore close errors
        }
        Log.d(TAG, "disconnect end")
    }
}

/**
 * Wrapper around an SSH Session providing convenient access to I/O streams.
 */
class SshSessionWrapper(
    private val session: Session
) {
    companion object {
        private const val TAG = "SshSessionWrapper"
    }

    val stdout: InputStream get() = session.stdout
    val stderr: InputStream get() = session.stderr
    val stdin: OutputStream get() = session.stdin

    /**
     * Resize the PTY.
     */
    fun resizePty(cols: Int, rows: Int) {
        try {
            Log.d(TAG, "resizePty size=${cols}x$rows")
            session.resizePTY(cols, rows, 0, 0)
        } catch (_: Exception) {
            // May fail if session is closed
            Log.w(TAG, "resizePty failed size=${cols}x$rows")
        }
    }

    /**
     * Write data to the remote shell.
     */
    suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        stdin.write(data)
        stdin.flush()
    }

    /**
     * Write a string to the remote shell.
     */
    suspend fun write(text: String) = write(text.toByteArray(Charsets.UTF_8))

    /**
     * Close the session.
     */
    fun close() {
        Log.d(TAG, "close begin")
        try {
            session.close()
        } catch (_: Exception) {
            // Ignore close errors
        }
        Log.d(TAG, "close end")
    }
}
