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

class WifiAnalyzerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WifiAnalyzerRepository(application)
    private val savedStore = WifiSavedNetworkStore(application)
    private val _state = MutableStateFlow(WifiAnalyzerUiState(savedNetworks = savedStore.load()))
    val state: StateFlow<WifiAnalyzerUiState> = _state.asStateFlow()
    private var scanJob: Job? = null
    private var lastScanStartedAt = 0L

    fun scan() {
        if (scanJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (now - lastScanStartedAt < MinimumScanIntervalMillis) {
            val seconds = ((MinimumScanIntervalMillis - (now - lastScanStartedAt) + 999) / 1_000)
            _state.value = _state.value.copy(error = "Android limits Wi-Fi scans; try again in $seconds seconds")
            return
        }
        lastScanStartedAt = now
        scanJob = viewModelScope.launch {
            _state.value = _state.value.copy(scanning = true, error = null)
            try {
                val snapshot = repository.scan(_state.value.savedNetworks.map { it.bssid }.toSet())
                val saved = savedStore.updateSeen(snapshot.accessPoints)
                val currentBssids = snapshot.accessPoints.map { it.bssid }.toSet()
                _state.value = _state.value.copy(
                    scanning = false,
                    snapshot = snapshot.copy(
                        accessPoints = snapshot.accessPoints.map { accessPoint ->
                            accessPoint.copy(saved = saved.any { it.bssid == accessPoint.bssid })
                        },
                    ),
                    savedNetworks = saved,
                    comparisonBssids = _state.value.comparisonBssids.intersect(currentBssids),
                )
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(scanning = false)
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(
                    scanning = false,
                    error = failure.message ?: "Wi-Fi scan could not be completed",
                )
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun toggleSaved(accessPoint: WifiAccessPoint) {
        val saved = savedStore.toggle(accessPoint)
        _state.value = _state.value.copy(
            savedNetworks = saved,
            snapshot = _state.value.snapshot?.copy(
                accessPoints = _state.value.snapshot?.accessPoints.orEmpty().map {
                    if (it.bssid == accessPoint.bssid) it.copy(saved = saved.any { saved -> saved.bssid == it.bssid }) else it
                },
            ),
        )
    }

    fun removeSaved(bssid: String) {
        val saved = savedStore.remove(bssid)
        _state.value = _state.value.copy(
            savedNetworks = saved,
            snapshot = _state.value.snapshot?.copy(
                accessPoints = _state.value.snapshot?.accessPoints.orEmpty().map {
                    if (it.bssid == bssid) it.copy(saved = false) else it
                },
            ),
        )
    }

    fun clearSaved() {
        savedStore.clear()
        _state.value = _state.value.copy(
            savedNetworks = emptyList(),
            snapshot = _state.value.snapshot?.copy(
                accessPoints = _state.value.snapshot?.accessPoints.orEmpty().map { it.copy(saved = false) },
            ),
        )
    }

    fun toggleComparison(bssid: String) {
        val selected = _state.value.comparisonBssids.toMutableSet()
        if (!selected.remove(bssid)) {
            if (selected.size >= MaximumComparisonAccessPoints) return
            selected += bssid
        }
        _state.value = _state.value.copy(comparisonBssids = selected)
    }

    private companion object {
        const val MinimumScanIntervalMillis = 30_000L
        const val MaximumComparisonAccessPoints = 2
    }
}
