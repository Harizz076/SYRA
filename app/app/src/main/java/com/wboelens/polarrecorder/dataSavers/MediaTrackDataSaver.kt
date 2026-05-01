package com.wboelens.polarrecorder.dataSavers

import android.content.Context
import android.util.Log
import com.wboelens.polarrecorder.managers.DeviceInfoForDataSaver
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.viewModels.LogViewModel

/**
 * DataSaver for media track metadata.
 *
 * This acts as a marker/pass-through data saver that aligns with the SYRA
 * multi-saver architecture. Actual track event logging (play/pause, metadata)
 * is handled by MediaPlaybackManager writing to track_info.jsonl via the
 * FileSystemDataSaver.
 */
class MediaTrackDataSaver(
    private val context: Context,
    logViewModel: LogViewModel,
    preferencesManager: PreferencesManager,
) : DataSaver(logViewModel, preferencesManager) {
    companion object {
        private const val TAG = "MediaTrackSaver"
    }

    override val isConfigured: Boolean
        get() = true // No external account or config needed

    override fun enable() {
        _isEnabled.value = true
        preferencesManager.mediaTrackEnabled = true
        Log.d(TAG, "Media track metadata tracking enabled")
    }

    override fun disable() {
        _isEnabled.value = false
        preferencesManager.mediaTrackEnabled = false
        Log.d(TAG, "Media track metadata tracking disabled")
    }

    override fun initSaving(
        recordingName: String,
        deviceIdsWithInfo: Map<String, DeviceInfoForDataSaver>
    ) {
        super.initSaving(recordingName, deviceIdsWithInfo)
        if (!_isEnabled.value) {
            _isInitialized.value = InitializationState.SUCCESS
            return
        }
        Log.d(TAG, "Initialized media track tracking for recording: $recordingName")
        _isInitialized.value = InitializationState.SUCCESS
    }

    override fun stopSaving() {
        if (!_isEnabled.value) return
        Log.d(TAG, "Stopped media track tracking")
        super.stopSaving()
    }

    override fun saveData(
        phoneTimestamp: Long,
        deviceId: String,
        recordingName: String,
        dataType: String,
        data: Any
    ) {
        // Track event data is written directly by MediaPlaybackManager
        // via FileSystemDataSaver. This is a marker saver for architecture compliance.
    }
}
