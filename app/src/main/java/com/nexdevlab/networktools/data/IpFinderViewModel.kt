package com.nexdevlab.networktools.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class IpFinderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = IpFinderRepository(application)
    private val _state = MutableStateFlow(IpFinderUiState())
    val state: StateFlow<IpFinderUiState> = _state.asStateFlow()
    private var lookupJob: Job? = null
    private var pendingLookup: PreparedIpFinderLookup? = null

    fun start(config: IpFinderConfig) {
        if (lookupJob?.isActive == true || _state.value.permissionRequired) return
        val prepared = try {
            repository.prepare(config)
        } catch (failure: Exception) {
            _state.value = _state.value.copy(error = failure.message ?: "IP Finder input is invalid")
            return
        }
        pendingLookup = prepared
        _state.value = IpFinderUiState(activeLookup = prepared)
        if (prepared.localNetworkResolver && !PortScannerRepository.hasLocalNetworkPermission(getApplication())) {
            _state.value = _state.value.copy(permissionRequired = true)
            return
        }
        runPrepared(prepared)
    }

    fun continueAfterPermission(granted: Boolean) {
        val prepared = pendingLookup
        _state.value = _state.value.copy(permissionRequired = false)
        if (!granted || prepared == null || !PortScannerRepository.hasLocalNetworkPermission(getApplication())) {
            pendingLookup = null
            _state.value = _state.value.copy(error = "Local-network permission was not granted")
            return
        }
        runPrepared(prepared)
    }

    fun dismissPermission() {
        pendingLookup = null
        _state.value = _state.value.copy(permissionRequired = false)
    }

    fun stop() {
        lookupJob?.cancel()
    }

    fun clearResult() {
        if (!_state.value.running && !_state.value.permissionRequired) _state.value = IpFinderUiState()
    }

    private fun runPrepared(prepared: PreparedIpFinderLookup) {
        if (lookupJob?.isActive == true) return
        lookupJob = viewModelScope.launch {
            _state.value = _state.value.copy(running = true, result = null, error = null)
            try {
                _state.value = _state.value.copy(result = repository.lookup(prepared))
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(error = "IP lookup stopped")
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(error = failure.message ?: "IP lookup could not be completed")
            } finally {
                pendingLookup = null
                _state.value = _state.value.copy(running = false)
                lookupJob = null
            }
        }
    }
}
