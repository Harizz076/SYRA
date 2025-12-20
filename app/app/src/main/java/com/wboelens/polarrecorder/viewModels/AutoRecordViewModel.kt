package com.wboelens.polarrecorder.viewModels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.dataSavers.InitializationState
import com.wboelens.polarrecorder.managers.DeviceInfoForDataSaver
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.managers.RecordingManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class AutoRecordStatus {
    IDLE,
    SCANNING,
    CONNECTING,
    CONFIGURING,
    INITIALIZING_SAVERS,
    RECORDING,
    FAILED_SCANNING,
    FAILED_CONNECTING,
    FAILED_CONFIGURING,
    FAILED_INITIALIZING,
    FAILED_RECORDING
}

class AutoRecordViewModel : ViewModel() {
    private val _status = MutableStateFlow(AutoRecordStatus.IDLE)
    val status: StateFlow<AutoRecordStatus> = _status

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage

    companion object {
        private const val TAG = "AutoRecordViewModel"
        private const val SCAN_TIMEOUT = 15000L // 15 seconds
        private const val CONNECTION_TIMEOUT = 10000L // 10 seconds
    }

    fun startAutoRecordFlow(
        polarManager: PolarManager,
        deviceViewModel: DeviceViewModel,
        recordingManager: RecordingManager,
        dataSavers: DataSavers,
        preferencesManager: PreferencesManager,
        logViewModel: LogViewModel,
        surveyManager: com.wboelens.polarrecorder.managers.SurveyManager? = null,
        notificationType: String = "music_pre"
    ) {
        viewModelScope.launch {
            try {
                logViewModel.addLogMessage("Starting auto-record flow")

                // CRITICAL: Check if recording is already active
                if (recordingManager.isRecording.value) {
                    Log.d(TAG, "Recording already active, showing status only")
                    _status.value = AutoRecordStatus.RECORDING
                    _statusMessage.value = "Recording in progress"

                    // Save pending survey response if exists
                    surveyManager?.let { manager ->
                        if (manager.hasPendingSurvey()) {
                            Log.d(TAG, "Saving pending survey response to active recording directory")
                            manager.savePendingSurveyToRecording(dataSavers.fileSystem.recordingDir)
                        }
                    }

                    return@launch
                }

                // Check if device is already connected
                val connectedDevices = deviceViewModel.connectedDevices.value ?: emptyList()
                if (connectedDevices.isNotEmpty()) {
                    Log.d(TAG, "Device already connected, skipping scan/connect")
                    _status.value = AutoRecordStatus.CONFIGURING
                    _statusMessage.value = "Device already connected"
                    delay(500)

                    // Jump straight to data saver initialization
                    initializeDataSaversAndStartRecording(
                        deviceViewModel,
                        recordingManager,
                        dataSavers,
                        preferencesManager,
                        connectedDevices,
                        surveyManager,
                        notificationType
                    )
                    return@launch
                }

                // Step 1: Start scanning
                _status.value = AutoRecordStatus.SCANNING
                _statusMessage.value = "Scanning for devices..."

                val autoConnectDeviceId = polarManager.getAutoConnectDeviceId()
                if (autoConnectDeviceId.isEmpty()) {
                    _status.value = AutoRecordStatus.FAILED_SCANNING
                    _statusMessage.value = "No saved device found. Tap to connect."
                    return@launch
                }

                // Start scanning
                polarManager.startPeriodicScanning()

                // Wait for device to be found
                var deviceFound = false
                var attempts = 0
                val maxAttempts = (SCAN_TIMEOUT / 1000).toInt()

                while (!deviceFound && attempts < maxAttempts) {
                    delay(1000)
                    deviceViewModel.allDevices.value?.find {
                        it.info.deviceId == autoConnectDeviceId
                    }?.let {
                        deviceFound = true
                    }
                    attempts++
                }

                if (!deviceFound) {
                    polarManager.stopPeriodicScanning()
                    _status.value = AutoRecordStatus.FAILED_SCANNING
                    _statusMessage.value = "Device not found. Tap to connect."
                    return@launch
                }

                _statusMessage.value = "Device found!"
                delay(500)

                // Step 2: Connect to device
                _status.value = AutoRecordStatus.CONNECTING
                _statusMessage.value = "Connecting to device..."

                // Select and connect
                deviceViewModel.selectDevices(setOf(autoConnectDeviceId))
                polarManager.connectToDevice(autoConnectDeviceId)

                // Wait for connection
                var connected = false
                attempts = 0
                val connectionMaxAttempts = (CONNECTION_TIMEOUT / 1000).toInt()

                while (!connected && attempts < connectionMaxAttempts) {
                    delay(1000)
                    deviceViewModel.selectedDevices.value?.firstOrNull()?.let { device ->
                        when (device.connectionState) {
                            ConnectionState.CONNECTED -> connected = true
                            ConnectionState.FAILED -> {
                                _status.value = AutoRecordStatus.FAILED_CONNECTING
                                _statusMessage.value = "Connection failed. Tap to retry."
                                return@launch
                            }
                            ConnectionState.FETCHING_CAPABILITIES -> {
                                _status.value = AutoRecordStatus.CONFIGURING
                                _statusMessage.value = "Fetching device capabilities..."
                            }
                            ConnectionState.FETCHING_SETTINGS -> {
                                _status.value = AutoRecordStatus.CONFIGURING
                                _statusMessage.value = "Configuring device settings..."
                            }
                            else -> {}
                        }
                    }
                    attempts++
                }

                if (!connected) {
                    _status.value = AutoRecordStatus.FAILED_CONNECTING
                    _statusMessage.value = "Connection timeout. Tap to retry."
                    return@launch
                }

                polarManager.stopPeriodicScanning()

                // Step 3: Configuration (already done by connection flow)
                _status.value = AutoRecordStatus.CONFIGURING
                _statusMessage.value = "Device configured successfully"
                delay(1000)

                // Step 4: Initialize data savers and start recording
                val selectedDevices = deviceViewModel.selectedDevices.value ?: emptyList()
                if (selectedDevices.isEmpty()) {
                    _status.value = AutoRecordStatus.FAILED_CONFIGURING
                    _statusMessage.value = "No devices selected. Tap to retry."
                    return@launch
                }

                initializeDataSaversAndStartRecording(
                    deviceViewModel,
                    recordingManager,
                    dataSavers,
                    preferencesManager,
                    selectedDevices,
                    surveyManager,
                    notificationType
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error in auto-record flow", e)
                _status.value = AutoRecordStatus.FAILED_RECORDING
                _statusMessage.value = "Error: ${e.message}. Tap to retry."
            }
        }
    }

    private suspend fun initializeDataSaversAndStartRecording(
        deviceViewModel: DeviceViewModel,
        recordingManager: RecordingManager,
        dataSavers: DataSavers,
        preferencesManager: PreferencesManager,
        selectedDevices: List<DeviceViewModel.Device>,
        surveyManager: com.wboelens.polarrecorder.managers.SurveyManager? = null,
        notificationType: String = "music_pre"
    ) {
        try {
            // Step 4: Initialize data savers
            _status.value = AutoRecordStatus.INITIALIZING_SAVERS
            _statusMessage.value = "Initializing data savers..."

            // Set recording name with timestamp
            // If there's a random recording name from survey, use that instead
            recordingManager.currentRecordingName =
                surveyManager?.randomRecordingName?.value ?: run {
                    if (preferencesManager.recordingNameAppendTimestamp) {
                        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                            .format(java.util.Date())
                        // For random notifications, add "random_baseline" to folder name
                        if (notificationType == "random") {
                            "${preferencesManager.recordingName}_random_baseline_$timestamp"
                        } else {
                            "${preferencesManager.recordingName}_$timestamp"
                        }
                    } else {
                        preferencesManager.recordingName
                    }
                }

            // Create device info map
            val deviceIdsWithInfo: Map<String, DeviceInfoForDataSaver> =
                selectedDevices.associate { device ->
                    val dataTypesWithLog = deviceViewModel
                        .getDeviceDataTypes(device.info.deviceId)
                        .map { it.name }
                        .toMutableList()
                    dataTypesWithLog.add("LOG")

                    device.info.deviceId to DeviceInfoForDataSaver(
                        device.info.name,
                        dataTypesWithLog.toSet()
                    )
                }.toMutableMap().apply {
                    // Add Spotify if enabled
                    if (dataSavers.spotify.isEnabled.value) {
                        put("spotify", DeviceInfoForDataSaver("Spotify", setOf("track_info")))
                    }
                }

            // Initialize enabled savers
            val enabledSavers = dataSavers.asList().filter { it.isEnabled.value }
            if (enabledSavers.isEmpty()) {
                _status.value = AutoRecordStatus.FAILED_INITIALIZING
                _statusMessage.value = "No data savers enabled. Tap to configure."
                return
            }

            enabledSavers.forEach { saver ->
                saver.initSaving(recordingManager.currentRecordingName, deviceIdsWithInfo)
            }

            // Wait for all savers to initialize
            var allInitialized = false
            var attempts = 0
            val initMaxAttempts = 30 // 30 seconds max

            while (!allInitialized && attempts < initMaxAttempts) {
                delay(1000)
                allInitialized = enabledSavers.all {
                    it.isInitialized.value == InitializationState.SUCCESS
                }

                // Check for failures
                val anyFailed = enabledSavers.any {
                    it.isInitialized.value == InitializationState.FAILED
                }
                if (anyFailed) {
                    _status.value = AutoRecordStatus.FAILED_INITIALIZING
                    _statusMessage.value = "Data saver initialization failed. Tap to retry."
                    return
                }

                attempts++
            }

            if (!allInitialized) {
                _status.value = AutoRecordStatus.FAILED_INITIALIZING
                _statusMessage.value = "Initialization timeout. Tap to retry."
                return
            }

            // Step 5: Start recording
            _status.value = AutoRecordStatus.RECORDING
            _statusMessage.value = "Starting recording..."

            recordingManager.startRecording()

            // Verify recording started
            delay(1000)
            if (recordingManager.isRecording.value) {
                _statusMessage.value = "Recording in progress"
                Log.d(TAG, "Auto-record flow completed successfully")

                // Save pending survey response if exists
                surveyManager?.let {
                    if (it.hasPendingSurvey()) {
                        Log.d(TAG, "Saving pending survey response to recording directory")
                        it.savePendingSurveyToRecording(dataSavers.fileSystem.recordingDir)
                    }
                }
            } else {
                _status.value = AutoRecordStatus.FAILED_RECORDING
                _statusMessage.value = "Failed to start recording. Tap to retry."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in data saver initialization", e)
            _status.value = AutoRecordStatus.FAILED_RECORDING
            _statusMessage.value = "Error: ${e.message}. Tap to retry."
        }
    }

    fun cancelAutoRecordFlow(
        polarManager: PolarManager,
        recordingManager: RecordingManager,
        dataSavers: DataSavers,
        logViewModel: LogViewModel
    ) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Canceling auto-record flow")
                logViewModel.addLogMessage("Canceling recording session")

                // Stop scanning if in progress
                polarManager.stopPeriodicScanning()

                // Stop recording if started
                if (recordingManager.isRecording.value) {
                    recordingManager.stopRecording()
                }

                // Delete the recording folder if it was created
                recordingManager.deleteCurrentRecordingFolder(dataSavers)

                // Reset status
                _status.value = AutoRecordStatus.IDLE
                _statusMessage.value = "Session canceled"

                logViewModel.addLogMessage("Recording session canceled and folder deleted")
            } catch (e: Exception) {
                Log.e(TAG, "Error canceling auto-record flow", e)
                logViewModel.addLogError("Error canceling session: ${e.message}")
            }
        }
    }

    @Suppress("unused")
    fun reset() {
        _status.value = AutoRecordStatus.IDLE
        _statusMessage.value = ""
    }
}

