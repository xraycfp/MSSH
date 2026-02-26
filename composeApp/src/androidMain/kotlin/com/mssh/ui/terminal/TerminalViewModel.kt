package com.mssh.ui.terminal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mssh.data.model.AuthType
import com.mssh.data.repository.HostRepository
import com.mssh.data.repository.SshKeyRepository
import com.mssh.ssh.ConnectionState
import com.mssh.ssh.SshConnectionManager
import com.mssh.ssh.SshSessionWrapper
import com.mssh.terminal.TerminalEmulator
import com.trilead.ssh2.Connection
import com.trilead.ssh2.ServerHostKeyVerifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class TerminalUiState(
    val connectionState: ConnectionState = ConnectionState.Idle,
    val title: String = "",
    val hostName: String = ""
)

class TerminalViewModel(
    private val hostId: Long,
    private val hostRepository: HostRepository,
    private val sshKeyRepository: SshKeyRepository,
    private val sshConnectionManager: SshConnectionManager
) : ViewModel() {

    companion object {
        private const val TAG = "TerminalViewModel"
    }

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState

    val emulator = TerminalEmulator(
        cols = 80,
        rows = 24,
        onOutput = { bytes ->
            viewModelScope.launch(Dispatchers.IO) {
                emulatorOutputCount++
                if (shouldLogInput(emulatorOutputCount)) {
                    Log.d(
                        TAG,
                        "emulator onOutput #$emulatorOutputCount bytes=${bytes.size} preview=${previewBytes(bytes, bytes.size)}"
                    )
                }
                try {
                    sessionWrapper?.write(bytes)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to write terminal response", e)
                }
            }
        }
    )

    private var connection: Connection? = null
    private var sessionWrapper: SshSessionWrapper? = null
    private var readJob: Job? = null
    private var stderrJob: Job? = null
    private var lastPtySize: Pair<Int, Int>? = null
    private var inputWriteCount: Long = 0
    private var inputBytesTotal: Long = 0
    private var emulatorOutputCount: Long = 0
    private var stdoutChunkCount: Long = 0
    private var stderrChunkCount: Long = 0
    private var resizeEventCount: Long = 0

    init {
        Log.d(TAG, "init hostId=$hostId")
        connect()
    }

    private fun connect() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "connect start hostId=$hostId")
                _uiState.update { it.copy(connectionState = ConnectionState.Connecting) }

                val hostConfig = hostRepository.getHostById(hostId)
                if (hostConfig == null) {
                    Log.e(TAG, "connect failed: host not found hostId=$hostId")
                    _uiState.update {
                        it.copy(connectionState = ConnectionState.Error("Host not found"))
                    }
                    return@launch
                }

                Log.d(
                    TAG,
                    "host loaded name=${hostConfig.name} user=${hostConfig.username} target=${hostConfig.host}:${hostConfig.port} auth=${hostConfig.authType}"
                )
                _uiState.update { it.copy(hostName = hostConfig.name) }

                Log.d(TAG, "ssh connect begin")
                val conn = sshConnectionManager.connect(
                    host = hostConfig.host,
                    port = hostConfig.port,
                    hostKeyVerifier = ServerHostKeyVerifier { _, _, _, _ -> true }
                )
                connection = conn
                Log.d(TAG, "ssh connect success")

                _uiState.update { it.copy(connectionState = ConnectionState.Authenticating) }

                val authenticated = when (hostConfig.authType) {
                    AuthType.PASSWORD -> {
                        sshConnectionManager.authenticateWithPassword(
                            conn,
                            hostConfig.username,
                            hostConfig.password ?: ""
                        )
                    }

                    AuthType.PUBLIC_KEY -> {
                        val keyId = hostConfig.keyId
                        if (keyId != null) {
                            val sshKey = sshKeyRepository.getKeyById(keyId)
                            if (sshKey != null) {
                                sshConnectionManager.authenticateWithPublicKey(
                                    conn,
                                    hostConfig.username,
                                    sshKey.privateKey.toCharArray(),
                                    sshKey.passphrase
                                )
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    }
                }

                if (!authenticated) {
                    Log.e(TAG, "authentication failed")
                    _uiState.update {
                        it.copy(connectionState = ConnectionState.Error("Authentication failed"))
                    }
                    return@launch
                }
                Log.d(TAG, "authentication success")

                val session = sshConnectionManager.openShellSession(
                    conn,
                    cols = emulator.currentCols,
                    rows = emulator.currentRows
                )
                sessionWrapper = session
                lastPtySize = emulator.currentCols to emulator.currentRows
                Log.d(
                    TAG,
                    "shell opened cols=${emulator.currentCols} rows=${emulator.currentRows}"
                )

                _uiState.update { it.copy(connectionState = ConnectionState.Connected) }
                Log.d(TAG, "state -> Connected")

                startReadLoop(session)
                startStderrLoop(session)
            } catch (e: CancellationException) {
                Log.d(TAG, "connect cancelled", e)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Connect failed", e)
                _uiState.update {
                    it.copy(connectionState = ConnectionState.Error(e.message ?: "Connection failed"))
                }
            }
        }
    }

    private fun startReadLoop(session: SshSessionWrapper) {
        readJob?.cancel()
        readJob = viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "stdout read loop started")
            val stdout = session.stdout
            val buffer = ByteArray(8192)

            try {
                while (isActive) {
                    val bytesRead = stdout.read(buffer)
                    if (bytesRead == -1) {
                        Log.w(TAG, "stdout EOF -> disconnected")
                        _uiState.update { it.copy(connectionState = ConnectionState.Disconnected) }
                        break
                    }

                    if (bytesRead > 0) {
                        stdoutChunkCount++
                        if (shouldLogChunk(stdoutChunkCount)) {
                            Log.d(
                                TAG,
                                "stdout chunk#$stdoutChunkCount bytes=$bytesRead preview=${previewBytes(buffer, bytesRead)}"
                            )
                        }
                        // Keep terminal mutation on Main to avoid races with Compose reads and resize.
                        withContext(Dispatchers.Main.immediate) {
                            try {
                                emulator.processBytes(buffer, 0, bytesRead)
                                if (emulator.title.isNotBlank()) {
                                    _uiState.update { it.copy(title = emulator.title) }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Terminal parse failed on stdout chunk", e)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "stdout read loop cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Stdout read loop failed", e)
                if (isActive) {
                    _uiState.update { it.copy(connectionState = ConnectionState.Disconnected) }
                }
            } finally {
                Log.d(TAG, "stdout read loop ended")
            }
        }
    }

    private fun startStderrLoop(session: SshSessionWrapper) {
        stderrJob?.cancel()
        stderrJob = viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "stderr read loop started")
            val stderr = session.stderr
            val buffer = ByteArray(4096)

            try {
                while (isActive) {
                    val bytesRead = stderr.read(buffer)
                    if (bytesRead <= 0) {
                        if (bytesRead == -1) {
                            Log.d(TAG, "stderr EOF")
                        }
                        break
                    }
                    stderrChunkCount++
                    if (shouldLogChunk(stderrChunkCount)) {
                        Log.d(
                            TAG,
                            "stderr chunk#$stderrChunkCount bytes=$bytesRead preview=${previewBytes(buffer, bytesRead)}"
                        )
                    }
                    withContext(Dispatchers.Main.immediate) {
                        try {
                            emulator.processBytes(buffer, 0, bytesRead)
                        } catch (e: Exception) {
                            Log.e(TAG, "Terminal parse failed on stderr chunk", e)
                        }
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "stderr read loop cancelled")
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Stderr read loop stopped", e)
            } finally {
                Log.d(TAG, "stderr read loop ended")
            }
        }
    }

    fun sendInput(data: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = sessionWrapper
            if (session == null) {
                Log.w(
                    TAG,
                    "sendInput dropped bytes=${data.size} preview=${previewBytes(data, data.size)} reason=noSession state=${stateName(_uiState.value.connectionState)}"
                )
                return@launch
            }

            inputWriteCount++
            inputBytesTotal += data.size
            if (shouldLogInput(inputWriteCount)) {
                Log.d(
                    TAG,
                    "sendInput #$inputWriteCount bytes=${data.size} totalBytes=$inputBytesTotal preview=${previewBytes(data, data.size)}"
                )
            }
            try {
                session.write(data)
            } catch (e: Exception) {
                Log.e(TAG, "Write failed", e)
            }
        }
    }

    fun resizeTerminal(cols: Int, rows: Int) {
        resizeEventCount++
        val size = cols.coerceAtLeast(1) to rows.coerceAtLeast(1)
        if (size == lastPtySize) {
            if (resizeEventCount <= 10 || resizeEventCount % 50L == 0L) {
                Log.d(TAG, "resizeTerminal #$resizeEventCount ignored sameSize=${size.first}x${size.second}")
            }
            return
        }
        val oldSize = lastPtySize
        lastPtySize = size

        viewModelScope.launch(Dispatchers.IO) {
            val session = sessionWrapper
            if (session == null) {
                Log.w(TAG, "resizeTerminal dropped newSize=${size.first}x${size.second} reason=noSession")
                return@launch
            }
            Log.d(
                TAG,
                "resizeTerminal #$resizeEventCount ${oldSize?.first ?: "-"}x${oldSize?.second ?: "-"} -> ${size.first}x${size.second}"
            )
            try {
                session.resizePty(size.first, size.second)
            } catch (e: Exception) {
                Log.w(TAG, "resizePty failed: ${size.first}x${size.second}", e)
            }
        }
    }

    fun reconnect() {
        Log.d(TAG, "reconnect requested")
        disconnect()
        emulator.resize(emulator.currentCols, emulator.currentRows)
        connect()
    }

    fun disconnect() {
        Log.d(TAG, "disconnect requested")
        readJob?.cancel()
        readJob = null
        stderrJob?.cancel()
        stderrJob = null
        sessionWrapper?.close()
        sessionWrapper = null
        connection?.let { sshConnectionManager.disconnect(it) }
        connection = null
        lastPtySize = null
        _uiState.update { it.copy(connectionState = ConnectionState.Disconnected) }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "onCleared")
        disconnect()
    }

    private fun shouldLogChunk(count: Long): Boolean {
        return count <= 10L || count % 100L == 0L
    }

    private fun shouldLogInput(count: Long): Boolean {
        return count <= 25L || count % 50L == 0L
    }

    private fun previewBytes(bytes: ByteArray, length: Int): String {
        val show = minOf(length, 16)
        if (show <= 0) return "<empty>"
        val hex = (0 until show).joinToString(" ") { i ->
            String.format("%02x", bytes[i].toInt() and 0xFF)
        }
        return if (length > show) "$hex ..." else hex
    }

    private fun stateName(state: ConnectionState): String = when (state) {
        is ConnectionState.Idle -> "Idle"
        is ConnectionState.Connecting -> "Connecting"
        is ConnectionState.Authenticating -> "Authenticating"
        is ConnectionState.Connected -> "Connected"
        is ConnectionState.Error -> "Error"
        is ConnectionState.Disconnected -> "Disconnected"
    }
}
