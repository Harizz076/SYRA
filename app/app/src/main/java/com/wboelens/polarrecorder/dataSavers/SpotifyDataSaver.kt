package com.wboelens.polarrecorder.dataSavers

import android.content.Context
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.viewModels.LogViewModel
import com.wboelens.polarrecorder.managers.DeviceInfoForDataSaver

data class SpotifyTrackData(
    val trackId: String,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationMs: Long,
    val isPaused: Boolean,
    val playbackPosition: Long,
    val playbackSpeed: Float,
    val timestamp: Long
)

class SpotifyDataSaver(
    context: Context,
    logViewModel: LogViewModel,
    preferencesManager: PreferencesManager
) : DataSaver(logViewModel, preferencesManager) {

    companion object {
        private const val DEVICE_ID = "spotify"
        private const val DATA_TYPE = "track_info"
    }

    override val isConfigured: Boolean
        get() = true // Always configured as it doesn't require external setup

    override fun enable() {
        _isEnabled.value = true
        preferencesManager.spotifyEnabled = true
        logViewModel.addLogMessage("Spotify tracking enabled")
    }

    override fun disable() {
        _isEnabled.value = false
        preferencesManager.spotifyEnabled = false
        logViewModel.addLogMessage("Spotify tracking disabled")
    }

    override fun saveData(
        phoneTimestamp: Long,
        deviceId: String,
        recordingName: String,
        dataType: String,
        data: Any
    ) {
        // This will be called by other savers when they receive Spotify data
        // The data should already be in the correct format
    }

    fun logTrackData(recordingName: String, trackData: SpotifyTrackData) {
        if (!_isEnabled.value) return

        val key = "$DEVICE_ID/$DATA_TYPE"

        // Mark first message as saved if not already done
        if (firstMessageSaved[key] == false) {
            firstMessageSaved[key] = true
            if (firstMessageSaved.values.all { it }) {
                _isInitialized.value = InitializationState.SUCCESS
                logViewModel.addLogMessage("Spotify data saver initialized successfully")
            }
        }

        // Log to user
        logViewModel.addLogMessage(
            "Spotify: ${trackData.trackName} by ${trackData.artistName} - " +
            "${if (trackData.isPaused) "Paused" else "Playing"}"
        )
    }

    override fun initSaving(
        recordingName: String,
        deviceIdsWithInfo: Map<String, DeviceInfoForDataSaver>
    ) {
        super.initSaving(recordingName, deviceIdsWithInfo)

        // Add Spotify as a "device" with track_info as data type
        val key = "$DEVICE_ID/$DATA_TYPE"
        firstMessageSaved[key] = false

        // Spotify is always immediately ready - no async initialization needed
        _isInitialized.value = InitializationState.SUCCESS

        logViewModel.addLogMessage("Spotify data saver initialized for recording: $recordingName")
    }

    override fun stopSaving() {
        super.stopSaving()
        logViewModel.addLogMessage("Spotify data saver stopped")
    }

    fun getDeviceInfo(): Pair<String, DeviceInfoForDataSaver> {
        return DEVICE_ID to DeviceInfoForDataSaver(
            deviceName = "Spotify",
            dataTypes = setOf(DATA_TYPE)
        )
    }
}

