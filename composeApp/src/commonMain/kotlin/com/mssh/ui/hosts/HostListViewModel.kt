package com.mssh.ui.hosts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mssh.data.model.HostConfig
import com.mssh.data.repository.HostRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HostListViewModel(
    private val hostRepository: HostRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val hosts: StateFlow<List<HostConfig>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                hostRepository.getAllHosts()
            } else {
                hostRepository.searchHosts(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteHost(id: Long) {
        viewModelScope.launch {
            hostRepository.deleteHost(id)
        }
    }
}
