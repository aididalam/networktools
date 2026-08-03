package com.nexdevlab.networktools.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IpInformationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = IpInformationRepository(application)
    private val _state = MutableStateFlow(IpInformationUiState())
    val state: StateFlow<IpInformationUiState> = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var includeWifiDetails = false

    fun refresh(wifiDetailsAllowed: Boolean = includeWifiDetails) {
        includeWifiDetails = wifiDetailsAllowed
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                _state.value = IpInformationUiState(snapshot = repository.load(includeWifiDetails))
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(loading = false)
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Network information could not be refreshed",
                )
            }
        }
    }

    fun cancelRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }
}
