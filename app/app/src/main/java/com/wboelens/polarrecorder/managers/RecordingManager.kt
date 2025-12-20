package com.wboelens.polarrecorder.managers

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Observer
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.EcgSample
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarMagnetometerData
import com.polar.sdk.api.model.PolarPpgData
import com.polar.sdk.api.model.PolarPpiData
import com.polar.sdk.api.model.PolarTemperatureData
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.dataSavers.InitializationState
import com.wboelens.polarrecorder.services.RecordingService
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import com.wboelens.polarrecorder.viewModels.LogViewModel
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DeviceInfoForDataSaver(val deviceName: String, val dataTypes: Set<String>)

fun getDataFragment(dataType: PolarBleApi.PolarDeviceDataType, data: Any): Float? {
  return when (dataType) {
    PolarBleApi.PolarDeviceDataType.HR -> (data as PolarHrData).samples.lastOrNull()?.hr?.toFloat()
    PolarBleApi.PolarDeviceDataType.PPI ->
        (data as PolarPpiData).samples.lastOrNull()?.ppi?.toFloat()
    PolarBleApi.PolarDeviceDataType.ACC ->
        (data as PolarAccelerometerData).samples.lastOrNull()?.x?.toFloat()
    PolarBleApi.PolarDeviceDataType.PPG ->
        (data as PolarPpgData).samples.lastOrNull()?.channelSamples?.lastOrNull()?.toFloat()
    PolarBleApi.PolarDeviceDataType.ECG -> {
      val ecgData = data as PolarEcgData
      when (val ecgSample = ecgData.samples.lastOrNull()) {
        is EcgSample -> ecgSample.voltage.toFloat()
        else -> null
      }
    }
    PolarBleApi.PolarDeviceDataType.GYRO -> (data as PolarGyroData).samples.lastOrNull()?.x
    PolarBleApi.PolarDeviceDataType.TEMPERATURE ->
        (data as PolarTemperatureData).samples.lastOrNull()?.temperature
    PolarBleApi.PolarDeviceDataType.MAGNETOMETER ->
        (data as PolarMagnetometerData).samples.lastOrNull()?.x
    else -> throw IllegalArgumentException("Unsupported data type: $dataType")
  }
}

class RecordingManager(
    private val context: Context,
    private val polarManager: PolarManager,
    private val logViewModel: LogViewModel,
    private val deviceViewModel: DeviceViewModel,
    private val preferencesManager: PreferencesManager,
    private val dataSavers: DataSavers,
    private val spotifyManager: SpotifyManager? = null,
) {
  companion object {
    private const val RETRY_COUNT = 3L
  }

  private val _isRecording = MutableStateFlow(false)
  val isRecording: StateFlow<Boolean> = _isRecording

  var currentRecordingName: String = ""

  // Random notification timeout tracking
  private var randomTimeoutHandler: Handler? = null
  private var randomTimeoutRunnable: Runnable? = null
  private val RANDOM_RECORDING_TIMEOUT_MS = 120000L // 120 seconds
  private var randomRecordingStartTime: Long = 0
  var isRandomRecording: Boolean = false // Track if current recording is from random notification
  var hasRandomSurveySubmitted: Boolean = false // Track if survey was submitted for random recording

  // Music post notification timeout tracking (for 60s pause + 120s notification)
  private var musicPostTimeoutHandler: Handler? = null
  private var musicPostTimeoutRunnable: Runnable? = null
  private val MUSIC_POST_SURVEY_TIMEOUT_MS = 120000L // 120 seconds for notification
  private val MUSIC_POST_MAX_RECORDING_TIMEOUT_MS = 120000L // 2 minutes max after survey submission
  private var musicPostNotificationSentTime: Long = 0
  var isMusicPostRecording: Boolean = false // Track if waiting for music_post survey
  var hasMusicPostSurveySubmitted: Boolean = false // Track if music_post survey was submitted
  var musicPostSurveySubmitTime: Long = 0 // Track when survey was submitted

  private val connectedDevicesObserver =
      Observer<List<DeviceViewModel.Device>> { devices ->
        if (!_isRecording.value) {
          return@Observer
        }

        if (devices.isEmpty() && preferencesManager.recordingStopOnDisconnect) {
          logViewModel.addLogError("No devices connected, stopping recording")
          stopRecording()
        } else {
          val selectedDevices = deviceViewModel.selectedDevices.value ?: emptyList()
          val connectedDeviceIds = devices.map { it.info.deviceId }

          // Process devices that were selected but are no longer connected
          selectedDevices.forEach { selectedDevice ->
            if (!connectedDeviceIds.contains(selectedDevice.info.deviceId)) {
              // Clean up by disposing all active streams for this device
              disposables[selectedDevice.info.deviceId]?.forEach { (_, disposable) ->
                disposable.dispose()
              }
              // Remove the device from our tracking map to prevent memory leaks
              disposables.remove(selectedDevice.info.deviceId)
            }
          }

          // Handle devices that have reconnected
          devices.forEach { device ->
            // Check if this device has no active streams (it was disconnected previously)
            // Note: isEmpty() != false checks for null, empty, or non-existent map
            if (disposables[device.info.deviceId]?.isEmpty() != false) {
              // Restart data streams for this device
              startStreamsForDevice(device)
            }
          }
        }
      }

  private val logMessagesObserver =
      Observer<List<LogViewModel.LogEntry>> { messages ->
        if (messages.isNotEmpty() && messages.size > lastSavedLogSize) {
          saveUnsavedLogMessages(messages)
        }
      }

  private val disposables = mutableMapOf<String, MutableMap<String, Disposable>>()
  private val messagesLock = Any()

  // Track how many log messages we've processed
  private var lastSavedLogSize = 0

  private val _lastData =
      MutableStateFlow<Map<String, Map<PolarBleApi.PolarDeviceDataType, Float?>>>(emptyMap())
  val lastData: StateFlow<Map<String, Map<PolarBleApi.PolarDeviceDataType, Float?>>> = _lastData

  private val _lastDataTimestamps = MutableStateFlow<Map<String, Long>>(emptyMap())
  val lastDataTimestamps: StateFlow<Map<String, Long>> = _lastDataTimestamps

  init {
    deviceViewModel.connectedDevices.observeForever(connectedDevicesObserver)
    logViewModel.logMessages.observeForever(logMessagesObserver)
  }

  private fun saveUnsavedLogMessages(messages: List<LogViewModel.LogEntry>) {
    val enabledDataSavers = dataSavers.asList().filter { it.isEnabled.value }
    val selectedDevices = deviceViewModel.selectedDevices.value

    if (!_isRecording.value || selectedDevices.isNullOrEmpty() || enabledDataSavers.isEmpty()) {
      // Recording is not in progress, or no devices or data savers are enabled
      // So we can't save the messages
      return
    }

    synchronized(messagesLock) {
      for (i in lastSavedLogSize until messages.size) {
        val entry = messages[i]
        val data = listOf(mapOf("type" to entry.type.name, "message" to entry.message))

        selectedDevices.forEach { device ->
          enabledDataSavers.forEach { saver ->
            saver.saveData(
                entry.timestamp,
                device.info.deviceId,
                currentRecordingName,
                "LOG",
                data,
            )
          }
        }
      }
      lastSavedLogSize = messages.size
    }
  }

  fun startRecording() {
    if (preferencesManager.recordingName === "") {
      logViewModel.addLogError("Recording name cannot be the empty string")
      return
    }

    if (_isRecording.value) {
      logViewModel.addLogError("Recording already in progress")
      return
    }

    val selectedDevices = deviceViewModel.selectedDevices.value
    if (selectedDevices.isNullOrEmpty()) {
      logViewModel.addLogError("Cannot start recording: No Polar devices selected")
      return
    }

    val connectedDevices = deviceViewModel.connectedDevices.value ?: emptyList()
    val connectedDeviceIds = connectedDevices.map { it.info.deviceId }
    val disconnectedDevices =
        selectedDevices.filter { !connectedDeviceIds.contains(it.info.deviceId) }
    if (disconnectedDevices.isNotEmpty()) {
      val disconnectedNames = disconnectedDevices.joinToString(", ") { it.info.name }
      logViewModel.addLogError(
          "Cannot start recording: Some selected devices are not connected: $disconnectedNames"
      )
      return
    }

    // Check if datasavers are initialized
    val enabledDataSavers = dataSavers.asList().filter { it.isEnabled.value }
    if (enabledDataSavers.isEmpty()) {
      logViewModel.addLogError("Cannot start recording: No data savers are enabled")
      return
    }

    val uninitializedSavers =
        enabledDataSavers.filter { it.isInitialized.value != InitializationState.SUCCESS }
    if (uninitializedSavers.isNotEmpty()) {
      logViewModel.addLogError(
          "Cannot start recording: Data savers are not initialized. Please go through the initialization process first."
      )
      return
    }

    // Clear last data and last data timestamps when starting new recording
    _lastData.value =
        selectedDevices.associate { device ->
          device.info.deviceId to device.dataTypes.associateWith { null }
        }
    _lastDataTimestamps.value = emptyMap()

    // Log app version information
    logDeviceAndAppInfo()

    logViewModel.addLogSuccess(
        "Recording $currentRecordingName started, saving to ${dataSavers.enabledCount} data saver(s)",
    )

    // Start Spotify tracking if enabled
    if (preferencesManager.spotifyEnabled) {
      if (spotifyManager != null) {
        spotifyManager.startTracking(currentRecordingName)
        logViewModel.addLogMessage("Spotify tracking enabled for this recording")
      } else {
        logViewModel.addLogError("Spotify tracking is enabled but Spotify is not connected/active")
      }
    }

    // Start the foreground service
    val serviceIntent = Intent(context, RecordingService::class.java)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      context.startForegroundService(serviceIntent)
    } else {
      context.startService(serviceIntent)
    }

    _isRecording.value = true
    selectedDevices.forEach { device -> startStreamsForDevice(device) }
  }

  private fun startStreamsForDevice(device: DeviceViewModel.Device) {
    val deviceId = device.info.deviceId

    disposables[deviceId] = mutableMapOf()
    disposables[deviceId]?.let { deviceDisposables ->
      val selectedDataTypes = deviceViewModel.getDeviceDataTypes(deviceId)
      selectedDataTypes.forEach { dataType ->
        deviceDisposables[dataType.name.lowercase()] = startStreamForDevice(deviceId, dataType)
      }
    }
  }

  private fun startStreamForDevice(
      deviceId: String,
      dataType: PolarBleApi.PolarDeviceDataType,
  ): Disposable {
    val selectedSensorSettings =
        deviceViewModel.getDeviceSensorSettingsForDataType(deviceId, dataType)

    return polarManager
        .startStreaming(deviceId, dataType, selectedSensorSettings)
        .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
        .observeOn(io.reactivex.rxjava3.schedulers.Schedulers.computation())
        .retry(RETRY_COUNT)
        .doOnSubscribe { logViewModel.addLogMessage("Starting $dataType stream for $deviceId") }
        .doOnError { error ->
          logViewModel.addLogError("Stream error for $deviceId - $dataType: ${error.message}")
        }
        .doOnComplete {
          logViewModel.addLogError("Stream completed unexpectedly for $deviceId - $dataType")
        }
        .subscribe(
            { data ->
              val phoneTimestamp = System.currentTimeMillis()

              // Update last data timestamp for this device
              _lastDataTimestamps.value += (deviceId to phoneTimestamp)

              // Update last data for this device
              _lastData.value =
                  _lastData.value.toMutableMap().apply {
                    val deviceData = this[deviceId]?.toMutableMap() ?: mutableMapOf()
                    deviceData[dataType] = getDataFragment(dataType, data)
                    this[deviceId] = deviceData
                  }

              val batchData =
                  when (dataType) {
                    PolarBleApi.PolarDeviceDataType.HR -> (data as PolarHrData).samples
                    PolarBleApi.PolarDeviceDataType.PPI -> (data as PolarPpiData).samples
                    PolarBleApi.PolarDeviceDataType.ACC -> (data as PolarAccelerometerData).samples
                    PolarBleApi.PolarDeviceDataType.PPG -> (data as PolarPpgData).samples
                    PolarBleApi.PolarDeviceDataType.ECG -> (data as PolarEcgData).samples
                    PolarBleApi.PolarDeviceDataType.GYRO -> (data as PolarGyroData).samples
                    PolarBleApi.PolarDeviceDataType.TEMPERATURE ->
                        (data as PolarTemperatureData).samples

                    PolarBleApi.PolarDeviceDataType.MAGNETOMETER ->
                        (data as PolarMagnetometerData).samples

                    else -> throw IllegalArgumentException("Unsupported data type: $dataType")
                  }

              dataSavers
                  .asList()
                  .filter { it.isEnabled.value }
                  .forEach { saver ->
                    saver.saveData(
                        phoneTimestamp,
                        deviceId,
                        currentRecordingName,
                        dataType.name,
                        batchData,
                    )
                  }
            },
            { error ->
              logViewModel.addLogError(
                  "${dataType.name} recording failed for device $deviceId: ${error.message}",
              )
            },
        )
  }

  private fun logDeviceAndAppInfo() {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName
    val versionCode =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
          packageInfo.longVersionCode
        } else {
          @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
        }
    logViewModel.addLogMessage("App version: $versionName (code: $versionCode)")

    // Add Polar SDK version information
    val polarSdkVersion = polarManager.getSdkVersion()
    logViewModel.addLogMessage("Polar SDK version: $polarSdkVersion")

    // Add Android version information
    val androidVersion =
        "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
    logViewModel.addLogMessage("OS version: $androidVersion")

    // Add device information
    val deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
    logViewModel.addLogMessage("Phone: $deviceInfo")
  }

  fun stopRecording() {
    if (!_isRecording.value) {
      logViewModel.addLogError("Trying to stop recording while no recording in progress")
      return
    }

    logViewModel.addLogMessage("Recording stopped")
    // Force save the final log message (pt. 1)
    logViewModel.requestFlushQueue()

    // Wait for the log to be flushed before continuing by posting to the main thread, just like
    // requestFlushQueue does.
    Handler(Looper.getMainLooper()).post {
      // Force save the final log message (pt. 2)
      saveUnsavedLogMessages(logViewModel.logMessages.value?.toList() ?: emptyList())

      // Stop the foreground service
      context.stopService(Intent(context, RecordingService::class.java))

      // Dispose all streams
      disposables.forEach { (_, deviceDisposables) ->
        deviceDisposables.forEach { (_, disposable) -> disposable.dispose() }
      }
      disposables.clear()

      // tell dataSavers to stop saving
      dataSavers.asList().filter { it.isEnabled.value }.forEach { saver -> saver.stopSaving() }

      // Stop Spotify tracking
      spotifyManager?.stopTracking()

      _isRecording.value = false

      // Clear timestamps when stopping recording
      _lastDataTimestamps.value = emptyMap()
    }
  }

  fun startRandomTimeout() {
    // Cancel any existing timeout
    cancelRandomTimeout()

    // Record start time
    randomRecordingStartTime = System.currentTimeMillis()
    isRandomRecording = true
    hasRandomSurveySubmitted = false // Reset submission flag

    // Create timeout handler if not exists
    if (randomTimeoutHandler == null) {
      randomTimeoutHandler = Handler(Looper.getMainLooper())
    }

    // Create new timeout runnable
    randomTimeoutRunnable = Runnable {
      // Check if survey was submitted
      if (hasRandomSurveySubmitted) {
        // Survey submitted and 120 seconds passed - stop recording
        logViewModel.addLogMessage("Random notification recording completed (120 seconds + survey submitted)")

        if (_isRecording.value) {
          stopRecording()
          logViewModel.addLogMessage("Auto-recording stopped after 120 seconds")
        }

        // Reset flags
        isRandomRecording = false
        hasRandomSurveySubmitted = false
      } else {
        // 120 seconds passed but survey not submitted yet - keep recording
        logViewModel.addLogMessage("120 seconds elapsed but survey not submitted - recording continues")
        logViewModel.addLogMessage("Recording will stop when survey is submitted")
        // Don't reset isRandomRecording - keep it true so we know to stop when survey comes
      }
    }

    // Schedule timeout for 120 seconds
    randomTimeoutHandler?.postDelayed(randomTimeoutRunnable!!, RANDOM_RECORDING_TIMEOUT_MS)
    logViewModel.addLogMessage("Started 120-second recording timer for random notification")
  }

  fun cancelRandomTimeout() {
    randomTimeoutRunnable?.let {
      randomTimeoutHandler?.removeCallbacks(it)
      randomTimeoutRunnable = null
    }
    isRandomRecording = false
    hasRandomSurveySubmitted = false
  }

  fun getRemainingRandomTime(): Long {
    if (!isRandomRecording) return 0
    val elapsed = System.currentTimeMillis() - randomRecordingStartTime
    return (RANDOM_RECORDING_TIMEOUT_MS - elapsed).coerceAtLeast(0)
  }

  fun markRandomSurveySubmitted() {
    if (!isRandomRecording) return

    hasRandomSurveySubmitted = true
    val remainingTime = getRemainingRandomTime()

    if (remainingTime <= 0) {
      // 120 seconds already passed, stop recording immediately
      logViewModel.addLogMessage("Survey submitted after 120 seconds - stopping recording")
      if (_isRecording.value) {
        stopRecording()
      }
      cancelRandomTimeout()
      isRandomRecording = false
    } else {
      // Less than 120 seconds, recording will continue
      logViewModel.addLogMessage("Survey submitted - recording will continue for ${remainingTime / 1000} more seconds")
    }
  }

  // Music post notification timeout methods
  fun startMusicPostNotificationTimeout(onTimeout: () -> Unit) {
    // Cancel any existing timeout
    cancelMusicPostTimeout()

    // Mark start time
    musicPostNotificationSentTime = System.currentTimeMillis()
    isMusicPostRecording = true
    hasMusicPostSurveySubmitted = false

    // Create timeout handler if not exists
    if (musicPostTimeoutHandler == null) {
      musicPostTimeoutHandler = Handler(Looper.getMainLooper())
    }

    // Create new timeout runnable
    musicPostTimeoutRunnable = Runnable {
      // 120 seconds passed without survey click - stop recording
      logViewModel.addLogMessage("Music post notification expired (120 seconds) - stopping recording")

      // Cancel the notification
      com.wboelens.polarrecorder.utils.NotificationHelper.cancelMusicPostNotification(context)

      if (_isRecording.value) {
        stopRecording()
      }

      // Reset flags
      isMusicPostRecording = false
      hasMusicPostSurveySubmitted = false

      // Call the timeout callback
      onTimeout()
    }

    // Schedule timeout for 120 seconds
    musicPostTimeoutHandler?.postDelayed(musicPostTimeoutRunnable!!, MUSIC_POST_SURVEY_TIMEOUT_MS)
    logViewModel.addLogMessage("Music post notification sent - will auto-stop in 120 seconds if not clicked")
  }

  fun cancelMusicPostTimeout() {
    musicPostTimeoutRunnable?.let {
      musicPostTimeoutHandler?.removeCallbacks(it)
      musicPostTimeoutRunnable = null
    }
  }

  fun markMusicPostSurveyOpened() {
    // User clicked notification - cancel the 120s notification timeout
    cancelMusicPostTimeout()
    logViewModel.addLogMessage("Music post survey opened - notification timeout cancelled")
  }

  fun markMusicPostSurveySubmitted() {
    if (!isMusicPostRecording) return

    hasMusicPostSurveySubmitted = true
    musicPostSurveySubmitTime = System.currentTimeMillis()

    // Start 2-minute max recording timeout after survey submission
    startMusicPostRecordingTimeout()
  }

  private fun startMusicPostRecordingTimeout() {
    // Create timeout handler if not exists
    if (musicPostTimeoutHandler == null) {
      musicPostTimeoutHandler = Handler(Looper.getMainLooper())
    }

    // Cancel any existing runnable
    musicPostTimeoutRunnable?.let {
      musicPostTimeoutHandler?.removeCallbacks(it)
    }

    // Create new timeout runnable for max 2 minutes after submission
    musicPostTimeoutRunnable = Runnable {
      logViewModel.addLogMessage("Max recording time reached (2 minutes after survey) - stopping recording")

      if (_isRecording.value) {
        stopRecording()
      }

      // Reset flags
      isMusicPostRecording = false
      hasMusicPostSurveySubmitted = false
    }

    // Schedule timeout for 2 minutes
    musicPostTimeoutHandler?.postDelayed(musicPostTimeoutRunnable!!, MUSIC_POST_MAX_RECORDING_TIMEOUT_MS)
    logViewModel.addLogMessage("Music post survey submitted - recording will stop in 2 minutes max")
  }

  fun stopMusicPostRecording() {
    if (!isMusicPostRecording || !hasMusicPostSurveySubmitted) {
      logViewModel.addLogError("Cannot stop music post recording - invalid state")
      return
    }

    // Cancel timeout and stop recording
    cancelMusicPostTimeout()

    if (_isRecording.value) {
      stopRecording()
    }

    // Reset flags
    isMusicPostRecording = false
    hasMusicPostSurveySubmitted = false

    logViewModel.addLogMessage("Music post recording stopped after survey submission")
  }

  fun deleteCurrentRecordingFolder(dataSavers: DataSavers) {
    try {
      val recordingDir = dataSavers.fileSystem.recordingDir
      if (recordingDir != null) {
        val folderName = recordingDir.name ?: "unknown"
        val deleted = recordingDir.delete()
        if (deleted) {
          logViewModel.addLogMessage("Deleted recording folder: $folderName")
          // Clear the reference
          dataSavers.fileSystem.recordingDir = null
        } else {
          logViewModel.addLogError("Failed to delete recording folder: $folderName")
        }
      } else {
        logViewModel.addLogMessage("No recording folder to delete")
      }
    } catch (e: Exception) {
      logViewModel.addLogError("Error deleting recording folder: ${e.message}")
    }
  }

  fun cleanup() {
    // Dispose any active streams
    disposables.forEach { (_, deviceDisposables) ->
      deviceDisposables.forEach { (_, disposable) -> disposable.dispose() }
    }
    disposables.clear()

    // cleanup dataSavers
    dataSavers.asList().forEach { saver -> saver.cleanup() }

    deviceViewModel.connectedDevices.removeObserver(connectedDevicesObserver)
    logViewModel.logMessages.removeObserver(logMessagesObserver)
  }
}
