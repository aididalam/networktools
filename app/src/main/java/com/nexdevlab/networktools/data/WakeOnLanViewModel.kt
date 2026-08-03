package com.nexdevlab.networktools.data

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WakeOnLanViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WakeOnLanRepository(application)
    private val store = WakeOnLanDeviceStore(application)
    private val _state = MutableStateFlow(WakeOnLanUiState(devices = store.load()))
    val state: StateFlow<WakeOnLanUiState> = _state.asStateFlow()
    private var sendJob: Job? = null
    private var pendingDevice: WakeOnLanDevice? = null
    private var lastSendStartedAt = Long.MIN_VALUE

    init {
        refreshLocalNetwork()
    }

    fun refreshLocalNetwork() {
        viewModelScope.launch {
            val result = runCatching(repository::detectLocalNetwork)
            _state.value = _state.value.copy(
                localNetwork = result.getOrNull(),
                localNetworkError = result.exceptionOrNull()?.message,
            )
        }
    }

    fun save(draft: WakeOnLanDeviceDraft, existing: WakeOnLanDevice?): WakeOnLanDevice? {
        return try {
            val saved = draft.validate(existing?.id, existing?.createdAtMillis ?: System.currentTimeMillis())
            _state.value = _state.value.copy(devices = store.upsert(saved), error = null)
            saved
        } catch (failure: Exception) {
            _state.value = _state.value.copy(error = failure.message ?: "Device could not be saved")
            null
        }
    }

    fun delete(device: WakeOnLanDevice) {
        _state.value = _state.value.copy(devices = store.remove(device.id), error = null)
    }

    fun send(device: WakeOnLanDevice, authorized: Boolean) {
        if (sendJob?.isActive == true || _state.value.permissionRequired) return
        if (!authorized) {
            _state.value = _state.value.copy(error = "Confirm that you are authorized to wake this device")
            return
        }
        val elapsed = SystemClock.elapsedRealtime()
        if (lastSendStartedAt != Long.MIN_VALUE && elapsed - lastSendStartedAt < WakeOnLanRepository.MinimumSendIntervalMillis) {
            _state.value = _state.value.copy(error = "Wait before sending another magic packet")
            return
        }
        pendingDevice = device
        _state.value = _state.value.copy(activeDevice = device, result = null, error = null)
        if (device.mode == WakeOnLanMode.LOCAL && !WakeOnLanRepository.hasLocalNetworkPermission(getApplication())) {
            _state.value = _state.value.copy(permissionRequired = true)
            return
        }
        runSend(device)
    }

    fun continueAfterPermission(granted: Boolean) {
        val device = pendingDevice
        _state.value = _state.value.copy(permissionRequired = false)
        if (!granted || device == null || !WakeOnLanRepository.hasLocalNetworkPermission(getApplication())) {
            pendingDevice = null
            _state.value = _state.value.copy(activeDevice = null, error = "Local-network permission was not granted")
            return
        }
        runSend(device)
    }

    fun dismissPermission() {
        pendingDevice = null
        _state.value = _state.value.copy(permissionRequired = false, activeDevice = null)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(result = null, error = null)
    }

    private fun runSend(device: WakeOnLanDevice) {
        if (sendJob?.isActive == true) return
        lastSendStartedAt = SystemClock.elapsedRealtime()
        sendJob = viewModelScope.launch {
            _state.value = _state.value.copy(sending = true, activeDevice = device, result = null, error = null)
            try {
                val prepared = repository.prepare(device)
                _state.value = _state.value.copy(result = repository.send(prepared))
            } catch (failure: Exception) {
                _state.value = _state.value.copy(error = failure.message ?: "Magic packet could not be sent")
            } finally {
                pendingDevice = null
                _state.value = _state.value.copy(sending = false, activeDevice = null)
                sendJob = null
            }
        }
    }
}
