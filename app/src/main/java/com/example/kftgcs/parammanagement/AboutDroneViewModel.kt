package com.example.kftgcs.parammanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kftgcs.telemetry.SharedViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

// ───────────────────────────────────────────────────────────────────────────
// FRAME_CLASS lookup table (ArduPilot FRAME_CLASS parameter values)
// ───────────────────────────────────────────────────────────────────────────
val FRAME_CLASS_NAMES = mapOf(
    0 to "Undefined",
    1 to "Quad",
    2 to "Hexa",
    3 to "Octo",
    4 to "OctoQuad",
    5 to "Y6",
    6 to "Heli",
    7 to "Tri",
    8 to "SingleCopter",
    9 to "CoaxCopter",
    10 to "BiCopter",
    11 to "Heli Dual",
    12 to "DodecaHexa",
    13 to "HeliQuad",
    14 to "Deca",
    15 to "Script"
)

// ───────────────────────────────────────────────────────────────────────────
// FRAME_TYPE lookup table (ArduPilot FRAME_TYPE parameter values)
// ───────────────────────────────────────────────────────────────────────────
val FRAME_TYPE_NAMES = mapOf(
    0 to "Plus (+)",
    1 to "X",
    2 to "V",
    3 to "H",
    4 to "V-Tail",
    5 to "A-Tail",
    10 to "Y6B",
    11 to "Y6F",
    12 to "BetaFlight X",
    13 to "DJI X",
    14 to "Clockwise X",
    15 to "I",
    18 to "BetaFlight X Rev"
)

data class DroneInfoState(
    val fcId: String = "—",
    val firmwareVersion: String = "—",
    val frameClass: String = "—",
    val frameType: String = "—",
    val isLoadingParams: Boolean = false,
    val paramsError: String? = null,
    val isDroneConnected: Boolean = false
)

class AboutDroneViewModel(
    private val sharedViewModel: SharedViewModel
) : ViewModel() {

    private val _droneInfo = MutableStateFlow(DroneInfoState())
    val droneInfo: StateFlow<DroneInfoState> = _droneInfo.asStateFlow()

    init {
        // Continuously observe TelemetryState for FC ID, firmware, and connection status
        viewModelScope.launch {
            sharedViewModel.telemetryState.collect { state ->
                _droneInfo.value = _droneInfo.value.copy(
                    fcId = state.droneUid?.takeIf { it.isNotBlank() } ?: "—",
                    firmwareVersion = state.firmwareVersion?.takeIf { it.isNotBlank() } ?: "—",
                    isDroneConnected = state.connected
                )
            }
        }
    }

    /**
     * Fetch FRAME_CLASS and FRAME_TYPE from the FCU using PARAM_REQUEST_READ (MAVLink #20).
     * Results arrive via PARAM_VALUE (MAVLink #22) through the existing paramValue SharedFlow.
     */
    fun fetchFrameParams() {
        viewModelScope.launch {
            if (_droneInfo.value.isDroneConnected.not()) {
                _droneInfo.value = _droneInfo.value.copy(
                    paramsError = "Drone not connected",
                    isLoadingParams = false
                )
                return@launch
            }

            _droneInfo.value = _droneInfo.value.copy(
                isLoadingParams = true,
                paramsError = null,
                frameClass = "Fetching…",
                frameType = "Fetching…"
            )

            try {
                // Request FRAME_CLASS (timeout 3 s per param)
                Timber.d("AboutDroneVM: Requesting FRAME_CLASS")
                val frameClassRaw = sharedViewModel.readParameter("FRAME_CLASS", 3000L)
                val frameClassLabel = if (frameClassRaw != null) {
                    val key = frameClassRaw.toInt()
                    FRAME_CLASS_NAMES[key] ?: "Unknown ($key)"
                } else {
                    "Timeout / N/A"
                }

                _droneInfo.value = _droneInfo.value.copy(frameClass = frameClassLabel)

                // Request FRAME_TYPE
                Timber.d("AboutDroneVM: Requesting FRAME_TYPE")
                val frameTypeRaw = sharedViewModel.readParameter("FRAME_TYPE", 3000L)
                val frameTypeLabel = if (frameTypeRaw != null) {
                    val key = frameTypeRaw.toInt()
                    FRAME_TYPE_NAMES[key] ?: "Unknown ($key)"
                } else {
                    "Timeout / N/A"
                }

                _droneInfo.value = _droneInfo.value.copy(
                    frameType = frameTypeLabel,
                    isLoadingParams = false
                )

                Timber.d("AboutDroneVM: Done — frameClass=$frameClassLabel, frameType=$frameTypeLabel")

            } catch (e: Exception) {
                Timber.e(e, "AboutDroneVM: Error fetching frame params")
                _droneInfo.value = _droneInfo.value.copy(
                    frameClass = "Error",
                    frameType = "Error",
                    paramsError = e.message ?: "Unknown error",
                    isLoadingParams = false
                )
            }
        }
    }
}

