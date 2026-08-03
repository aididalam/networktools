package com.nexdevlab.networktools.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WhoisViewModel : ViewModel() {
    private val repository = WhoisRepository()
    private val _state = MutableStateFlow(WhoisUiState())
    val state: StateFlow<WhoisUiState> = _state.asStateFlow()
    private var lookupJob: Job? = null
    private var lastLookupStartedAt = 0L

    fun lookup(
        query: String,
        queryType: WhoisQueryType,
        serverMode: WhoisServerMode,
        manualServer: String,
    ) {
        if (lookupJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (now - lastLookupStartedAt < MinimumLookupIntervalMillis) {
            _state.value = _state.value.copy(error = "Please wait before starting another lookup")
            return
        }
        lastLookupStartedAt = now
        lookupJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val result = repository.lookup(query, queryType, serverMode, manualServer)
                _state.value = WhoisUiState(result = result)
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(loading = false)
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = failure.message ?: "The WHOIS lookup could not be completed",
                )
            }
        }
    }

    fun cancelLookup() {
        lookupJob?.cancel()
        lookupJob = null
    }

    private companion object {
        const val MinimumLookupIntervalMillis = 1_500L
    }
}
