package com.nexdevlab.networktools.data

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexdevlab.networktools.ui.PrivacyPolicyUrl
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SpeedTestViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SpeedTestRepository(application)
    private val historyStore = SpeedTestHistoryStore(application)
    private val policyStore = SpeedTestPolicyStore(application)
    private val isDebuggable = application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<SpeedTestUiState> = _state.asStateFlow()
    private var testJob: Job? = null
    private var serverDiscoveryJob: Job? = null

    init {
        resolveLocalEdge()
    }

    fun selectServer(choice: SpeedServerChoice) {
        if (_state.value.running) return
        serverDiscoveryJob?.cancel()
        val blocked = isReleaseBlocked()
        _state.value = _state.value.copy(
            selectedServer = choice,
            selectedEndpoint = SpeedEndpointChoice.AUTOMATIC,
            serverOptions = listOf(SpeedEndpointChoice.AUTOMATIC),
            serversLoading = false,
            serverOptionsError = null,
            regionalServerFallback = false,
            releaseBlocked = blocked,
            error = null,
        )
        if (choice.mode != SpeedServerMode.LOCAL && !blocked) loadServerOptions(choice)
    }

    fun selectEndpoint(choice: SpeedEndpointChoice) {
        if (_state.value.running || choice !in _state.value.serverOptions) return
        _state.value = _state.value.copy(selectedEndpoint = choice, error = null)
    }

    fun requestStart() {
        if (testJob?.isActive == true) return
        val network = repository.networkState()
        _state.value = _state.value.copy(networkType = network.label, metered = network.metered, error = null)
        when {
            _state.value.releaseBlocked -> _state.value = _state.value.copy(
                error = "Internet Speed Meter is disabled until the app privacy-policy URL is configured.",
            )
            !network.connected -> _state.value = _state.value.copy(
                error = "Connect to a validated internet connection before starting the test.",
            )
            policyStore.remainingTests() <= 0 -> _state.value = _state.value.copy(
                remainingTestsToday = 0,
                error = "The daily speed-test limit has been reached. Try again after 24 hours.",
            )
            !policyStore.hasCurrentConsent() -> _state.value = _state.value.copy(consentRequired = true)
            network.metered -> _state.value = _state.value.copy(meteredConfirmationRequired = true)
            else -> start()
        }
    }

    fun acceptConsentAndContinue() {
        policyStore.acceptConsent()
        _state.value = _state.value.copy(consentRequired = false)
        requestStart()
    }

    fun dismissConsent() {
        _state.value = _state.value.copy(consentRequired = false)
    }

    fun confirmMeteredAndStart() {
        _state.value = _state.value.copy(meteredConfirmationRequired = false)
        start()
    }

    fun dismissMeteredConfirmation() {
        _state.value = _state.value.copy(meteredConfirmationRequired = false)
    }

    fun stop() {
        if (testJob?.isActive != true) return
        _state.value = _state.value.copy(running = false, phase = SpeedTestPhase.STOPPED, liveMbps = 0.0)
        testJob?.cancel()
        testJob = null
    }

    fun refreshNetwork() {
        if (_state.value.running) return
        val network = repository.networkState()
        _state.value = _state.value.copy(networkType = network.label, metered = network.metered)
        resolveLocalEdge()
        val choice = _state.value.selectedServer
        if (choice.mode != SpeedServerMode.LOCAL && !_state.value.releaseBlocked) loadServerOptions(choice)
    }

    fun removeHistory(completedAtMillis: Long) {
        _state.value = _state.value.copy(history = historyStore.remove(completedAtMillis))
    }

    fun clearHistory() {
        _state.value = _state.value.copy(history = historyStore.clear())
    }

    fun onScreenClosed() {
        stop()
        serverDiscoveryJob?.cancel()
    }

    private fun start() {
        if (testJob?.isActive == true || _state.value.releaseBlocked) return
        if (!policyStore.recordStart()) {
            _state.value = _state.value.copy(remainingTestsToday = 0, error = "The daily speed-test limit has been reached.")
            return
        }
        val choice = _state.value.selectedServer
        val preferredMachine = _state.value.selectedEndpoint.machine
        serverDiscoveryJob?.cancel()
        _state.value = _state.value.copy(
            running = true,
            phase = SpeedTestPhase.DISCOVERING,
            activeServer = null,
            liveMbps = 0.0,
            downloadMbps = null,
            uploadMbps = null,
            latencyMillis = null,
            jitterMillis = null,
            elapsedMillis = 0,
            result = null,
            error = null,
            serversLoading = false,
            meteredConfirmationRequired = false,
            remainingTestsToday = policyStore.remainingTests(),
        )
        testJob = viewModelScope.launch {
            var latestDownload: Double? = null
            try {
                val result = repository.run(
                    choice = choice,
                    preferredMachine = preferredMachine,
                    onServer = { server ->
                        _state.value = _state.value.copy(
                            activeServer = server,
                            localCountryCode = if (choice.mode == SpeedServerMode.LOCAL) {
                                server.countryCode
                            } else _state.value.localCountryCode,
                            localEdgeName = if (choice.mode == SpeedServerMode.LOCAL) {
                                server.city
                            } else _state.value.localEdgeName,
                        )
                    },
                    onProgress = { progress ->
                        if (progress.phase == SpeedTestPhase.UPLOAD && _state.value.phase == SpeedTestPhase.DOWNLOAD) {
                            latestDownload = _state.value.liveMbps
                        }
                        _state.value = _state.value.copy(
                            phase = progress.phase,
                            liveMbps = progress.megabitsPerSecond,
                            downloadMbps = if (progress.phase == SpeedTestPhase.DOWNLOAD) {
                                progress.megabitsPerSecond
                            } else latestDownload ?: _state.value.downloadMbps,
                            uploadMbps = if (progress.phase == SpeedTestPhase.UPLOAD) progress.megabitsPerSecond else null,
                            elapsedMillis = progress.elapsedMicros / 1_000L,
                            latencyMillis = progress.rttMillis ?: _state.value.latencyMillis,
                        )
                    },
                )
                _state.value = _state.value.copy(
                    running = false,
                    phase = SpeedTestPhase.COMPLETE,
                    liveMbps = result.downloadMbps,
                    downloadMbps = result.downloadMbps,
                    uploadMbps = result.uploadMbps,
                    latencyMillis = result.latencyMillis,
                    jitterMillis = result.jitterMillis,
                    result = result,
                    history = historyStore.add(result),
                )
            } catch (cancelled: CancellationException) {
                if (_state.value.running) {
                    _state.value = _state.value.copy(running = false, phase = SpeedTestPhase.STOPPED, liveMbps = 0.0)
                }
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(
                    running = false,
                    phase = SpeedTestPhase.IDLE,
                    liveMbps = 0.0,
                    downloadMbps = null,
                    uploadMbps = null,
                    latencyMillis = null,
                    jitterMillis = null,
                    result = null,
                    error = safeError(failure),
                )
            } finally {
                testJob = null
            }
        }
    }

    private fun initialState(): SpeedTestUiState {
        val network = repository.networkState()
        return SpeedTestUiState(
            history = historyStore.load(),
            networkType = network.label,
            metered = network.metered,
            releaseBlocked = isReleaseBlocked(),
            remainingTestsToday = policyStore.remainingTests(),
        )
    }

    private fun resolveLocalEdge() {
        viewModelScope.launch {
            val edge = repository.resolveLocalEdge() ?: return@launch
            _state.value = _state.value.copy(
                localCountryCode = edge.countryCode,
                localEdgeName = edge.edgeName,
            )
        }
    }

    private fun loadServerOptions(choice: SpeedServerChoice) {
        serverDiscoveryJob?.cancel()
        _state.value = _state.value.copy(
            selectedEndpoint = SpeedEndpointChoice.AUTOMATIC,
            serverOptions = listOf(SpeedEndpointChoice.AUTOMATIC),
            serversLoading = true,
            serverOptionsError = null,
            regionalServerFallback = false,
        )
        serverDiscoveryJob = viewModelScope.launch {
            try {
                val result = repository.discoverServerOptions(choice)
                if (_state.value.selectedServer == choice) {
                    _state.value = _state.value.copy(
                        serverOptions = result.options,
                        serversLoading = false,
                        serverOptionsError = null,
                        regionalServerFallback = result.regionalFallback,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (_state.value.selectedServer == choice) {
                    _state.value = _state.value.copy(
                        serversLoading = false,
                        serverOptionsError = safeError(failure),
                        regionalServerFallback = false,
                    )
                }
            }
        }
    }

    private fun isReleaseBlocked(): Boolean = !isDebuggable && PrivacyPolicyUrl.isBlank()

    private fun safeError(failure: Exception): String = when (failure) {
        is IOException -> failure.message ?: "The speed test could not connect to the selected server"
        else -> "The speed test could not be completed"
    }.take(180)
}
