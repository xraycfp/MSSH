package com.mssh.ui.hosts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mssh.data.model.AuthType
import com.mssh.data.model.HostConfig
import com.mssh.data.model.SshKey
import com.mssh.data.repository.HostRepository
import com.mssh.data.repository.SshKeyRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HostEditUiState(
    val name: String = "",
    val host: String = "",
    val port: String = "22",
    val username: String = "",
    val authType: AuthType = AuthType.PASSWORD,
    val password: String = "",
    val selectedKeyId: Long? = null,
    val availableKeys: List<SshKey> = emptyList(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val error: String? = null
)

class HostEditViewModel(
    private val hostRepository: HostRepository,
    private val sshKeyRepository: SshKeyRepository,
    private val hostId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(HostEditUiState())
    val uiState: StateFlow<HostEditUiState> = _uiState

    init {
        viewModelScope.launch {
            // Load available keys
            sshKeyRepository.getAllKeys().collect { keys ->
                _uiState.update { it.copy(availableKeys = keys) }
            }
        }

        if (hostId > 0) {
            loadHost(hostId)
        }
    }

    private fun loadHost(id: Long) {
        viewModelScope.launch {
            val host = hostRepository.getHostById(id)
            if (host != null) {
                _uiState.update {
                    it.copy(
                        name = host.name,
                        host = host.host,
                        port = host.port.toString(),
                        username = host.username,
                        authType = host.authType,
                        password = host.password ?: "",
                        selectedKeyId = host.keyId,
                        isEditing = true
                    )
                }
            }
        }
    }

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }
    fun updateHost(host: String) = _uiState.update { it.copy(host = host) }
    fun updatePort(port: String) = _uiState.update { it.copy(port = port) }
    fun updateUsername(username: String) = _uiState.update { it.copy(username = username) }
    fun updateAuthType(authType: AuthType) = _uiState.update { it.copy(authType = authType) }
    fun updatePassword(password: String) = _uiState.update { it.copy(password = password) }
    fun updateSelectedKeyId(keyId: Long?) = _uiState.update { it.copy(selectedKeyId = keyId) }

    fun save() {
        val state = _uiState.value

        // Validation
        if (state.host.isBlank()) {
            _uiState.update { it.copy(error = "Host address is required") }
            return
        }
        if (state.username.isBlank()) {
            _uiState.update { it.copy(error = "Username is required") }
            return
        }

        val port = state.port.toIntOrNull() ?: 22
        val name = state.name.ifBlank { "${state.username}@${state.host}" }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val config = HostConfig(
                    id = if (state.isEditing) hostId else 0,
                    name = name,
                    host = state.host,
                    port = port,
                    username = state.username,
                    authType = state.authType,
                    password = if (state.authType == AuthType.PASSWORD) state.password else null,
                    keyId = if (state.authType == AuthType.PUBLIC_KEY) state.selectedKeyId else null
                )

                if (state.isEditing) {
                    hostRepository.updateHost(config)
                } else {
                    hostRepository.insertHost(config)
                }

                _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Save failed") }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
