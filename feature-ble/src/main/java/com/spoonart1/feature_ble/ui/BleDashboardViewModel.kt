package com.spoonart1.feature_ble.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spoonart1.blespoonart.core.logger.LiveLog
import com.spoonart1.blespoonart.core.logger.Status
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class BleUiState(
    val selectedModeIndex: Int = 1,
    val isAdvertiseChecked: Boolean = true,
    val isScanChecked: Boolean = true,
    val isWakeLockChecked: Boolean = false,
    val statusText: String = "Idle",
    val logText: String = "",
)

@HiltViewModel
class BleDashboardViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(BleUiState())
    val uiState: StateFlow<BleUiState> = _uiState.asStateFlow()

    init {
        startLogPoller()
    }

    private fun startLogPoller() {
        viewModelScope.launch {
            while (isActive) {
                val s = LiveLog.status
                val statusString = when (s) {
                    is Status.Idle -> "Idle"
                    is Status.BluetoothOff -> "Bluetooth unavailable or off"
                    is Status.Custom -> s.message
                }
                val snapshot = LiveLog.snapshot()

                _uiState.update { current ->
                    current.copy(
                        statusText = statusString,
                        logText = snapshot,
                    )
                }
                delay(1000)
            }
        }
    }

    fun setModeIndex(index: Int) {
        _uiState.update { it.copy(selectedModeIndex = index) }
    }

    fun setAdvertiseChecked(checked: Boolean) {
        _uiState.update { it.copy(isAdvertiseChecked = checked) }
    }

    fun setScanChecked(checked: Boolean) {
        _uiState.update { it.copy(isScanChecked = checked) }
    }

    fun setWakeLockChecked(checked: Boolean) {
        _uiState.update { it.copy(isWakeLockChecked = checked) }
    }

    fun updateStatusMessage(message: String) {
        LiveLog.status = Status.Custom(message)
        _uiState.update { it.copy(statusText = message) }
    }
}