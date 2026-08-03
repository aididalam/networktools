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

class PortScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PortScannerRepository(application)
    private val historyStore = PortScanHistoryStore(application)
    private val _state = MutableStateFlow(PortScannerUiState(history = historyStore.load()))
    val state: StateFlow<PortScannerUiState> = _state.asStateFlow()
    private var scanJob: Job? = null
    private var preparedScan: PreparedPortScan? = null

    fun localTarget(): String = repository.localTarget()

    fun start(config: PortScanConfig) {
        if (scanJob?.isActive == true || _state.value.permissionRequired) return
        scanJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                preparing = true,
                running = false,
                activeConfig = config,
                normalizedTarget = null,
                resolvedAddress = null,
                entries = emptyList(),
                totalPorts = 0,
                result = null,
                error = null,
            )
            try {
                val prepared = repository.prepare(config)
                preparedScan = prepared
                _state.value = _state.value.copy(
                    preparing = false,
                    normalizedTarget = prepared.normalizedTarget,
                    resolvedAddress = prepared.resolvedAddress,
                    totalPorts = prepared.ports.size,
                )
                if (prepared.localNetworkTarget && !PortScannerRepository.hasLocalNetworkPermission(getApplication())) {
                    scanJob = null
                    _state.value = _state.value.copy(permissionRequired = true)
                    return@launch
                }
                runPrepared(prepared)
            } catch (cancelled: CancellationException) {
                saveStoppedResult()
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(
                    preparing = false,
                    running = false,
                    error = failure.message ?: "Port scan could not be completed",
                )
            } finally {
                scanJob = null
            }
        }
    }

    fun continueAfterPermission(granted: Boolean) {
        val prepared = preparedScan
        _state.value = _state.value.copy(permissionRequired = false)
        if (!granted || prepared == null || !PortScannerRepository.hasLocalNetworkPermission(getApplication())) {
            preparedScan = null
            _state.value = _state.value.copy(error = "Local-network permission was not granted")
            return
        }
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            try {
                runPrepared(prepared)
            } catch (cancelled: CancellationException) {
                saveStoppedResult()
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(
                    preparing = false,
                    running = false,
                    error = failure.message ?: "Port scan could not be completed",
                )
            } finally {
                scanJob = null
            }
        }
    }

    fun dismissPermission() {
        preparedScan = null
        _state.value = _state.value.copy(permissionRequired = false)
    }

    fun stop() {
        scanJob?.cancel()
    }

    fun removeHistory(id: Long) {
        _state.value = _state.value.copy(history = historyStore.remove(id))
    }

    fun clearHistory() {
        _state.value = _state.value.copy(history = historyStore.clear())
    }

    private suspend fun runPrepared(prepared: PreparedPortScan) {
        _state.value = _state.value.copy(preparing = false, running = true, error = null)
        val result = repository.scan(prepared) { entries ->
            _state.value = _state.value.copy(entries = entries)
        }
        preparedScan = null
        val history = historyStore.add(result)
        _state.value = _state.value.copy(
            running = false,
            entries = result.entries,
            result = result,
            history = history,
        )
    }

    private fun saveStoppedResult() {
        val prepared = preparedScan
        val current = _state.value
        val result = if (prepared != null && current.entries.isNotEmpty()) {
            PortScanResult(
                target = prepared.normalizedTarget,
                resolvedAddress = prepared.resolvedAddress,
                entries = current.entries,
                timeoutMillis = prepared.config.timeoutMillis,
                stopped = true,
                completedAtMillis = System.currentTimeMillis(),
            )
        } else null
        preparedScan = null
        val history = result?.let(historyStore::add) ?: current.history
        _state.value = current.copy(
            preparing = false,
            running = false,
            result = result,
            history = history,
        )
    }
}
