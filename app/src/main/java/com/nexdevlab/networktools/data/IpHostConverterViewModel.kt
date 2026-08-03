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

class IpHostConverterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = IpHostConverterRepository(application)
    private val _state = MutableStateFlow(IpHostConverterUiState())
    val state: StateFlow<IpHostConverterUiState> = _state.asStateFlow()
    private var conversionJob: Job? = null
    private var pendingConversion: PreparedIpHostConversion? = null

    fun start(config: IpHostConversionConfig) {
        if (conversionJob?.isActive == true || _state.value.permissionRequired) return
        val prepared = try {
            repository.prepare(config)
        } catch (failure: Exception) {
            _state.value = _state.value.copy(error = failure.message ?: "Conversion input is invalid")
            return
        }
        pendingConversion = prepared
        _state.value = IpHostConverterUiState(activeConversion = prepared)
        if (prepared.localNetworkResolver && !PortScannerRepository.hasLocalNetworkPermission(getApplication())) {
            _state.value = _state.value.copy(permissionRequired = true)
            return
        }
        runPrepared(prepared)
    }

    fun continueAfterPermission(granted: Boolean) {
        val prepared = pendingConversion
        _state.value = _state.value.copy(permissionRequired = false)
        if (!granted || prepared == null || !PortScannerRepository.hasLocalNetworkPermission(getApplication())) {
            pendingConversion = null
            _state.value = _state.value.copy(error = "Local-network permission was not granted")
            return
        }
        runPrepared(prepared)
    }

    fun dismissPermission() {
        pendingConversion = null
        _state.value = _state.value.copy(permissionRequired = false)
    }

    fun stop() {
        conversionJob?.cancel()
    }

    fun clearResult() {
        if (!_state.value.running && !_state.value.permissionRequired) {
            _state.value = IpHostConverterUiState()
        }
    }

    private fun runPrepared(prepared: PreparedIpHostConversion) {
        if (conversionJob?.isActive == true) return
        conversionJob = viewModelScope.launch {
            _state.value = _state.value.copy(running = true, result = null, error = null)
            try {
                _state.value = _state.value.copy(result = repository.convert(prepared))
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(error = "Conversion stopped")
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(error = failure.message ?: "Conversion could not be completed")
            } finally {
                pendingConversion = null
                _state.value = _state.value.copy(running = false)
                conversionJob = null
            }
        }
    }
}
