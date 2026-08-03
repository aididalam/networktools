package com.nexdevlab.networktools.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RouterSetupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RouterSetupRepository(application)
    private val _state = MutableStateFlow(RouterSetupUiState())
    val state: StateFlow<RouterSetupUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.refreshing) return
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = true, detectionError = null)
            val result = withContext(Dispatchers.Default) { runCatching(repository::detectRouter) }
            _state.value = result.fold(
                onSuccess = { RouterSetupUiState(detectedRouter = it) },
                onFailure = {
                    RouterSetupUiState(detectionError = it.message ?: "Router gateway could not be detected")
                },
            )
        }
    }
}
