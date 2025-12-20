package com.wboelens.polarrecorder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.services.SpotifyMonitorService

class SpotifyBroadcastReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "SpotifyBroadcastReceiver"
        const val SPOTIFY_ACTIVE_ACTION = "com.spotify.music.active"

        // Callback to notify when Spotify becomes active
        var onSpotifyActive: (() -> Unit)? = null
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            SPOTIFY_ACTIVE_ACTION -> {
                Log.d(TAG, "Spotify is now active - new track in queue")
                context?.let {
                    // Check if auto-recording is enabled before starting the service
                    val preferencesManager = PreferencesManager(it)
                    if (preferencesManager.autoRecordingEnabled) {
                        // Start the monitoring service
                        SpotifyMonitorService.start(it)
                    } else {
                        Log.d(TAG, "Auto-recording is disabled, not starting Spotify monitor service")
                    }
                }
                onSpotifyActive?.invoke()
            }
        }
    }
}

