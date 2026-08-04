package com.nexdevlab.networktools.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectionsLogViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ConnectionLogStore(application)
    private val monitor = ConnectionMonitor(application)
    private val _state = MutableStateFlow(ConnectionLogUiState(entries = store.load()))
    val state: StateFlow<ConnectionLogUiState> = _state.asStateFlow()

    fun startMonitoring() {
        if (_state.value.monitoring) return
        runCatching {
            val baseline = monitor.snapshot()
            _state.value = _state.value.copy(monitoring = true, current = baseline, error = null)
            monitor.start(baseline) { entry, current ->
                val entries = entry?.let(store::add) ?: _state.value.entries
                _state.value = _state.value.copy(current = current, entries = entries, error = null)
            }
        }.onFailure {
            _state.value = _state.value.copy(monitoring = false, error = "Connection monitoring is unavailable")
        }
    }

    fun stopMonitoring() {
        monitor.stop()
        _state.value = _state.value.copy(monitoring = false)
    }

    fun clear() {
        _state.value = _state.value.copy(entries = store.clear(), error = null)
    }

    override fun onCleared() {
        monitor.stop()
    }
}
