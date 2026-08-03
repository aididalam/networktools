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

class LanScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LanScannerRepository(application)
    private val historyStore = LanScanHistoryStore(application)
    private val _state = MutableStateFlow(LanScannerUiState(history = historyStore.load()))
    val state: StateFlow<LanScannerUiState> = _state.asStateFlow()
    private var scanJob: Job? = null

    init {
        detectSubnet()
    }

    fun detectSubnet() {
        if (scanJob?.isActive == true) return
        _state.value = runCatching { repository.detectNetwork().subnet.display }
            .fold(
                onSuccess = { _state.value.copy(detectedSubnet = it, error = null) },
                onFailure = { _state.value.copy(detectedSubnet = null, error = it.message ?: "Subnet detection failed") },
            )
    }

    fun scan(config: LanScanConfig) {
        if (scanJob?.isActive == true) return
        runCatching(config::validate).onFailure { failure ->
            _state.value = _state.value.copy(error = failure.message ?: "Check the scan settings")
            return
        }
        scanJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                scanning = true,
                phase = "Preparing scan",
                scannedHosts = 0,
                totalHosts = config.subnet.hostCount,
                devices = emptyList(),
                result = null,
                error = null,
            )
            try {
                val result = repository.scan(
                    config = config,
                    onPhase = { phase -> _state.value = _state.value.copy(phase = phase) },
                    onProgress = { scanned, total ->
                        _state.value = _state.value.copy(scannedHosts = scanned, totalHosts = total)
                    },
                    onDevices = { devices -> _state.value = _state.value.copy(devices = devices) },
                )
                val history = historyStore.add(result)
                _state.value = _state.value.copy(
                    scanning = false,
                    phase = "Completed",
                    devices = result.devices,
                    result = result,
                    history = history,
                )
            } catch (cancelled: CancellationException) {
                val current = _state.value
                val stoppedResult = current.devices.takeIf { current.scannedHosts > 0 || it.isNotEmpty() }?.let { devices ->
                    LanScanResult(
                        subnet = config.subnet.display,
                        mode = config.mode,
                        devices = devices,
                        scannedHosts = current.scannedHosts,
                        stopped = true,
                        completedAtMillis = System.currentTimeMillis(),
                    )
                }
                val history = stoppedResult?.let(historyStore::add) ?: current.history
                _state.value = current.copy(
                    scanning = false,
                    phase = "Stopped",
                    result = stoppedResult,
                    history = history,
                )
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(
                    scanning = false,
                    phase = "Could not complete scan",
                    error = failure.message ?: "LAN scan could not be completed",
                )
            } finally {
                scanJob = null
            }
        }
    }

    fun stop() {
        scanJob?.cancel()
    }

    fun showHistory(entry: LanScanHistoryEntry) {
        _state.value = _state.value.copy(
            devices = entry.devices,
            result = LanScanResult(
                subnet = entry.subnet,
                mode = entry.mode,
                devices = entry.devices,
                scannedHosts = entry.scannedHosts,
                stopped = entry.stopped,
                completedAtMillis = entry.completedAtMillis,
            ),
            phase = "History result",
            error = null,
        )
    }

    fun removeHistory(id: Long) {
        _state.value = _state.value.copy(history = historyStore.remove(id))
    }

    fun clearHistory() {
        _state.value = _state.value.copy(history = historyStore.clear())
    }
}
