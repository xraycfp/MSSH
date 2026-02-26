package com.mssh.ui.keys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mssh.data.model.KeyType
import com.mssh.data.model.SshKey
import com.mssh.data.repository.SshKeyRepository
import com.mssh.ssh.SshKeyGenerator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class KeyManagerUiState(
    val keys: List<SshKey> = emptyList(),
    val isGenerating: Boolean = false,
    val showGenerateDialog: Boolean = false,
    val showKeyDetail: SshKey? = null,
    val error: String? = null
)

class KeyManagerViewModel(
    private val sshKeyRepository: SshKeyRepository,
    private val keyGenerator: SshKeyGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(KeyManagerUiState())
    val uiState: StateFlow<KeyManagerUiState> = _uiState

    init {
        viewModelScope.launch {
            sshKeyRepository.getAllKeys().collect { keys ->
                _uiState.update { it.copy(keys = keys) }
            }
        }
    }

    fun showGenerateDialog() = _uiState.update { it.copy(showGenerateDialog = true) }
    fun hideGenerateDialog() = _uiState.update { it.copy(showGenerateDialog = false) }
    fun showKeyDetail(key: SshKey) = _uiState.update { it.copy(showKeyDetail = key) }
    fun hideKeyDetail() = _uiState.update { it.copy(showKeyDetail = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    fun generateKey(name: String, type: KeyType, passphrase: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, showGenerateDialog = false) }
            try {
                val key = keyGenerator.generateKey(name, type, passphrase)
                sshKeyRepository.insertKey(key)
                _uiState.update { it.copy(isGenerating = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        error = "Failed to generate key: ${e.message}"
                    )
                }
            }
        }
    }

    fun deleteKey(id: Long) {
        viewModelScope.launch {
            sshKeyRepository.deleteKey(id)
        }
    }
}
