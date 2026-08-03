package com.nexdevlab.networktools.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DnsLookupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DnsLookupRepository(application)
    private val _state = MutableStateFlow(DnsLookupUiState())
    val state: StateFlow<DnsLookupUiState> = _state.asStateFlow()
    private var queryJob: Job? = null
    private var pendingQuery: PreparedDnsQuery? = null
    private var pendingComparison = false

    fun start(config: DnsQueryConfig, compare: Boolean) {
        if (queryJob?.isActive == true || _state.value.permissionRequired) return
        val prepared = try {
            repository.prepare(config, compare)
        } catch (failure: Exception) {
            _state.value = _state.value.copy(error = failure.message ?: "DNS query is invalid")
            return
        }
        pendingQuery = prepared
        pendingComparison = compare
        _state.value = DnsLookupUiState(activeQuery = prepared)
        if (prepared.localNetworkResolver && !PortScannerRepository.hasLocalNetworkPermission(getApplication())) {
            _state.value = _state.value.copy(permissionRequired = true)
            return
        }
        runPrepared(prepared, compare)
    }

    fun continueAfterPermission(granted: Boolean) {
        val prepared = pendingQuery
        _state.value = _state.value.copy(permissionRequired = false)
        if (!granted || prepared == null || !PortScannerRepository.hasLocalNetworkPermission(getApplication())) {
            pendingQuery = null
            _state.value = _state.value.copy(error = "Local-network permission was not granted")
            return
        }
        runPrepared(prepared, pendingComparison)
    }

    fun dismissPermission() {
        pendingQuery = null
        _state.value = _state.value.copy(permissionRequired = false)
    }

    fun stop() {
        queryJob?.cancel()
    }

    private fun runPrepared(prepared: PreparedDnsQuery, compare: Boolean) {
        if (queryJob?.isActive == true) return
        queryJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                running = true,
                result = null,
                comparison = null,
                comparisonProgress = emptyList(),
                error = null,
            )
            try {
                if (compare) {
                    val progress = CopyOnWriteArrayList<DnsLookupResult>()
                    val comparison = repository.compare(prepared) { result ->
                        progress += result
                        _state.value = _state.value.copy(comparisonProgress = progress.toList())
                    }
                    _state.value = _state.value.copy(comparison = comparison)
                } else {
                    _state.value = _state.value.copy(result = repository.lookup(prepared))
                }
            } catch (cancelled: CancellationException) {
                _state.value = _state.value.copy(error = "DNS query stopped")
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(error = failure.message ?: "DNS query could not be completed")
            } finally {
                pendingQuery = null
                _state.value = _state.value.copy(running = false)
                queryJob = null
            }
        }
    }
}
