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

class PingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PingRepository()
    private val historyStore = PingHistoryStore(application)
    private val _state = MutableStateFlow(PingUiState(history = historyStore.load()))
    val state: StateFlow<PingUiState> = _state.asStateFlow()
    private var runJob: Job? = null
    private var activeConfig: PingConfig? = null
    private var activeAddress: String? = null
    private var lastRunStartedAt = 0L

    fun start(config: PingConfig) {
        if (runJob?.isActive == true) return
        val validationError = runCatching(config::validate).exceptionOrNull()?.message
        if (validationError != null) {
            _state.value = _state.value.copy(error = validationError)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastRunStartedAt < MinimumRunIntervalMillis) {
            _state.value = _state.value.copy(error = "Please wait before starting another Ping")
            return
        }
        lastRunStartedAt = now
        activeConfig = config
        activeAddress = null
        _state.value = _state.value.copy(
            running = true,
            activeTarget = config.target.trim(),
            probes = emptyList(),
            result = null,
            error = null,
        )
        runJob = viewModelScope.launch {
            try {
                val result = repository.run(
                    config = config,
                    onResolved = { address -> activeAddress = address },
                    onProbe = { probe ->
                        if (_state.value.running) {
                            _state.value = _state.value.copy(probes = _state.value.probes + probe)
                        }
                    },
                )
                val history = historyStore.add(result)
                _state.value = _state.value.copy(
                    running = false,
                    probes = result.probes,
                    result = result,
                    history = history,
                )
            } catch (cancelled: CancellationException) {
                retainStoppedResult()
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(
                    running = false,
                    error = failure.message ?: "Ping could not be completed",
                )
            } finally {
                activeConfig = null
                activeAddress = null
            }
        }
    }

    fun stop() {
        if (runJob?.isActive != true) return
        _state.value = _state.value.copy(running = false)
        runJob?.cancel()
        runJob = null
    }

    fun removeHistory(id: Long) {
        _state.value = _state.value.copy(history = historyStore.remove(id))
    }

    fun clearHistory() {
        _state.value = _state.value.copy(history = historyStore.clear())
    }

    private fun retainStoppedResult() {
        val config = activeConfig ?: return
        val probes = _state.value.probes
        if (probes.isEmpty()) {
            _state.value = _state.value.copy(running = false)
            return
        }
        val result = PingSummary(
            target = runCatching { PingRepository.normalizeTarget(config.target) }.getOrDefault(config.target.trim()),
            resolvedAddress = activeAddress ?: config.target.trim(),
            method = config.method,
            port = config.port.takeIf { config.method == PingMethod.TCP },
            probes = probes,
            stopped = true,
            completedAtMillis = System.currentTimeMillis(),
        )
        _state.value = _state.value.copy(
            running = false,
            result = result,
            history = historyStore.add(result),
        )
    }

    private companion object {
        const val MinimumRunIntervalMillis = 1_000L
    }
}
