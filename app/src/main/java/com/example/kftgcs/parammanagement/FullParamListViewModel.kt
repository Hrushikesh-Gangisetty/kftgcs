package com.example.kftgcs.parammanagement

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kftgcs.telemetry.SharedViewModel
import com.example.kftgcs.utils.LogUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────
// Data classes
// ─────────────────────────────────────────────────────────────────────

/**
 * Represents a single drone parameter with metadata from ArduPilot.
 */
data class DroneParam(
    val name: String,
    val value: Float,
    val paramIndex: Int,
    val paramCount: Int,
    val paramType: Int
)

/**
 * UI state for the Full Param List screen.
 */
data class FullParamListState(
    val params: Map<String, DroneParam> = emptyMap(),
    val isLoading: Boolean = false,
    val loadingProgress: Float = 0f,      // 0.0 – 1.0
    val receivedCount: Int = 0,
    val totalCount: Int = 0,
    val errorMessage: String? = null,
    val isDroneConnected: Boolean = false,
    // Write-param feedback
    val writingParam: String? = null,      // Name of param currently being written
    val writeSuccess: String? = null,      // Success message (param name)
    val writeError: String? = null,        // Error message
    // Metadata loading
    val isMetadataLoading: Boolean = false,
    val metadataCount: Int = 0
)

// ─────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────

class FullParamListViewModel(
    private val sharedViewModel: SharedViewModel,
    private val application: Application
) : ViewModel() {

    companion object {
        private const val TAG = "FullParamListVM"
        /** Timeout after last received param before we consider loading done. */
        private const val RECEIVE_TIMEOUT_MS = 4000L
    }

    private val _state = MutableStateFlow(FullParamListState())
    val state: StateFlow<FullParamListState> = _state.asStateFlow()

    /** Parameter metadata (descriptions, defaults, options) loaded from ArduPilot */
    private val _paramMetadata = MutableStateFlow<Map<String, ParamMeta>>(emptyMap())
    val paramMetadata: StateFlow<Map<String, ParamMeta>> = _paramMetadata.asStateFlow()

    private var collectJob: Job? = null
    private var timeoutJob: Job? = null

    init {
        // Observe drone connection status
        viewModelScope.launch {
            sharedViewModel.telemetryState.collect { telemetry ->
                _state.update { it.copy(isDroneConnected = telemetry.connected) }
            }
        }

        // Load parameter metadata (from cache/network/fallback)
        viewModelScope.launch {
            _state.update { it.copy(isMetadataLoading = true) }
            try {
                val metadata = ArduPilotParamMetadataRepository.loadMetadata(application)
                _paramMetadata.value = metadata
                _state.update { it.copy(isMetadataLoading = false, metadataCount = metadata.size) }
                LogUtils.d(TAG, "📋 Loaded ${metadata.size} parameter metadata entries")
            } catch (e: Exception) {
                LogUtils.e(TAG, "Failed to load param metadata", e)
                _paramMetadata.value = FALLBACK_PARAM_METADATA
                _state.update { it.copy(isMetadataLoading = false, metadataCount = FALLBACK_PARAM_METADATA.size) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────

    /**
     * Force refresh metadata from ArduPilot servers.
     */
    fun refreshMetadata() {
        viewModelScope.launch {
            _state.update { it.copy(isMetadataLoading = true) }
            val success = ArduPilotParamMetadataRepository.refreshMetadata(application)
            if (success) {
                val metadata = ArduPilotParamMetadataRepository.loadMetadata(application)
                _paramMetadata.value = metadata
                _state.update { it.copy(isMetadataLoading = false, metadataCount = metadata.size) }
                LogUtils.d(TAG, "🔄 Refreshed metadata: ${metadata.size} params")
            } else {
                _state.update { it.copy(isMetadataLoading = false) }
                LogUtils.e(TAG, "❌ Failed to refresh metadata")
            }
        }
    }

    /**
     * Request all parameters from the drone (PARAM_REQUEST_LIST #21).
     * Collects incoming PARAM_VALUE messages and builds the list.
     */
    fun fetchAllParams() {
        if (_state.value.isLoading) return

        _state.update {
            it.copy(
                isLoading = true,
                loadingProgress = 0f,
                receivedCount = 0,
                totalCount = 0,
                errorMessage = null,
                params = emptyMap()
            )
        }

        // Step 1: Start collector BEFORE sending request (avoid race)
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            sharedViewModel.paramValue.collect { pv ->
                val paramName = pv.paramId.trim().replace("\u0000", "")
                if (paramName.isBlank()) return@collect

                val paramCount = pv.paramCount.toInt()
                val paramIndex = pv.paramIndex.toInt()

                val droneParam = DroneParam(
                    name = paramName,
                    value = pv.paramValue,
                    paramIndex = paramIndex,
                    paramCount = paramCount,
                    paramType = pv.paramType.value.toInt()
                )

                _state.update { current ->
                    val updatedParams = current.params.toMutableMap()
                    updatedParams[paramName] = droneParam
                    val received = updatedParams.size
                    val total = if (paramCount > 0) paramCount else current.totalCount
                    val progress = if (total > 0) received.toFloat() / total else 0f
                    current.copy(
                        params = updatedParams,
                        receivedCount = received,
                        totalCount = total,
                        loadingProgress = progress.coerceIn(0f, 1f)
                    )
                }

                // Reset the "no more params" timeout each time we get one
                resetTimeout()
            }
        }

        // Step 2: Small delay to let collector start, then send the request
        viewModelScope.launch {
            delay(100)
            try {
                sharedViewModel.requestAllParameters()
                LogUtils.d(TAG, "📤 PARAM_REQUEST_LIST sent")
            } catch (e: Exception) {
                LogUtils.e(TAG, "Failed to request all parameters", e)
                _state.update {
                    it.copy(isLoading = false, errorMessage = "Failed to request parameters: ${e.message}")
                }
                collectJob?.cancel()
            }
        }
    }

    /**
     * Write (set) a single parameter on the drone using PARAM_SET (#23).
     * Retries up to 2 times on timeout.
     */
    fun writeParam(paramName: String, newValue: Float) {
        if (!_state.value.isDroneConnected) {
            _state.update { it.copy(writeError = "Drone not connected") }
            return
        }

        _state.update { it.copy(writingParam = paramName, writeSuccess = null, writeError = null) }

        viewModelScope.launch {
            var ack: com.divpundir.mavlink.definitions.common.ParamValue? = null
            val maxRetries = 3

            for (attempt in 1..maxRetries) {
                try {
                    LogUtils.d(TAG, "📤 Writing param $paramName = $newValue (attempt $attempt/$maxRetries)")
                    ack = sharedViewModel.setParameter(paramName, newValue, timeoutMs = 5000L)
                    if (ack != null) break
                    LogUtils.e(TAG, "⏱ Timeout writing $paramName (attempt $attempt/$maxRetries)")
                } catch (e: Exception) {
                    LogUtils.e(TAG, "❌ Error writing $paramName (attempt $attempt)", e)
                }
                if (attempt < maxRetries) delay(500)
            }

            if (ack != null) {
                val confirmedValue = ack.paramValue
                LogUtils.d(TAG, "✅ Param $paramName confirmed = $confirmedValue")

                // Update local map with confirmed value
                _state.update { current ->
                    val updatedParams = current.params.toMutableMap()
                    updatedParams[paramName]?.let { existing ->
                        updatedParams[paramName] = existing.copy(value = confirmedValue)
                    }
                    current.copy(
                        params = updatedParams,
                        writingParam = null,
                        writeSuccess = paramName
                    )
                }
            } else {
                LogUtils.e(TAG, "❌ Failed to write $paramName after $maxRetries attempts")
                _state.update {
                    it.copy(writingParam = null, writeError = "Failed to write $paramName after $maxRetries attempts")
                }
            }
        }
    }

    /**
     * Refresh a single parameter (PARAM_REQUEST_READ #20).
     */
    fun refreshParam(paramName: String) {
        viewModelScope.launch {
            val value = sharedViewModel.readParameter(paramName, 3000L)
            if (value != null) {
                _state.update { current ->
                    val updatedParams = current.params.toMutableMap()
                    updatedParams[paramName]?.let { existing ->
                        updatedParams[paramName] = existing.copy(value = value)
                    }
                    current.copy(params = updatedParams)
                }
            }
        }
    }

    fun clearWriteMessages() {
        _state.update { it.copy(writeSuccess = null, writeError = null) }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    // ─────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────

    /**
     * After no PARAM_VALUE is received for [RECEIVE_TIMEOUT_MS] ms,
     * mark loading as complete.
     */
    private fun resetTimeout() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(RECEIVE_TIMEOUT_MS)
            val s = _state.value
            if (s.isLoading) {
                LogUtils.d(TAG, "✅ All params received: ${s.receivedCount}/${s.totalCount}")
                _state.update { it.copy(isLoading = false, loadingProgress = 1f) }
                collectJob?.cancel()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        collectJob?.cancel()
        timeoutJob?.cancel()
    }
}

