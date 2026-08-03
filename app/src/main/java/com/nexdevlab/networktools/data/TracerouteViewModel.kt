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

class TracerouteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TracerouteRepository()
    private val historyStore = TracerouteHistoryStore(application)
    private val _state = MutableStateFlow(TracerouteUiState(history = historyStore.load()))
    val state: StateFlow<TracerouteUiState> = _state.asStateFlow()
    private var traceJob: Job? = null
    private var activeConfig: TracerouteConfig? = null
    private var lastTraceStartedAt = 0L

    fun start(config: TracerouteConfig) {
        if (traceJob?.isActive == true) return
        val validationError = runCatching(config::validate).exceptionOrNull()?.message
        if (validationError != null) {
            _state.value = _state.value.copy(error = validationError)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastTraceStartedAt < MinimumTraceIntervalMillis) {
            _state.value = _state.value.copy(error = "Please wait before starting another Traceroute")
            return
        }
        lastTraceStartedAt = now
        activeConfig = config
        _state.value = _state.value.copy(
            running = true,
            phase = TraceroutePhase.RESOLVING,
            currentHop = 0,
            maximumHops = config.maxHops,
            target = config.target.trim(),
            resolvedAddress = null,
            hops = emptyList(),
            result = null,
            error = null,
        )
        traceJob = viewModelScope.launch {
            try {
                val result = repository.trace(
                    config = config,
                    onPhase = { phase ->
                        if (_state.value.running) _state.value = _state.value.copy(phase = phase)
                    },
                    onResolved = { address ->
                        if (_state.value.running) _state.value = _state.value.copy(resolvedAddress = address)
                    },
                    onHop = { hop ->
                        if (_state.value.running) {
                            val hops = _state.value.hops.toMutableList()
                            val existing = hops.indexOfFirst { it.number == hop.number }
                            if (existing >= 0) hops[existing] = hop else hops += hop
                            _state.value = _state.value.copy(
                                currentHop = maxOf(_state.value.currentHop, hop.number),
                                hops = hops.sortedBy(TracerouteHop::number),
                            )
                        }
                    },
                )
                _state.value = _state.value.copy(
                    running = false,
                    phase = TraceroutePhase.IDLE,
                    hops = result.hops,
                    result = result,
                    history = historyStore.add(result),
                )
            } catch (cancelled: CancellationException) {
                retainStoppedResult()
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(
                    running = false,
                    phase = TraceroutePhase.IDLE,
                    error = failure.message ?: "Traceroute could not be completed",
                )
            } finally {
                activeConfig = null
            }
        }
    }

    fun stop() {
        if (traceJob?.isActive != true) return
        _state.value = _state.value.copy(running = false, phase = TraceroutePhase.IDLE)
        traceJob?.cancel()
        traceJob = null
    }

    fun removeHistory(id: Long) {
        _state.value = _state.value.copy(history = historyStore.remove(id))
    }

    fun clearHistory() {
        _state.value = _state.value.copy(history = historyStore.clear())
    }

    private fun retainStoppedResult() {
        val config = activeConfig ?: return
        val hops = _state.value.hops
        if (hops.isEmpty()) return
        val target = runCatching { PingRepository.normalizeTarget(config.target) }.getOrDefault(config.target.trim())
        val result = TracerouteResult(
            target = target,
            resolvedAddress = _state.value.resolvedAddress ?: target,
            hops = hops,
            reachedDestination = hops.any(TracerouteHop::reachedDestination),
            stopped = true,
            completedAtMillis = System.currentTimeMillis(),
        )
        _state.value = _state.value.copy(
            running = false,
            phase = TraceroutePhase.IDLE,
            result = result,
            history = historyStore.add(result),
        )
    }

    private companion object {
        const val MinimumTraceIntervalMillis = 1_500L
    }
}
