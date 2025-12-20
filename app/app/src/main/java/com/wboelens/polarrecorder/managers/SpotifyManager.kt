package com.wboelens.polarrecorder.managers

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.dataSavers.SpotifyTrackData

class SpotifyManager(
    private val context: Context,
    private val dataSavers: DataSavers,
) {
    companion object {
        private const val TAG = "SpotifyManager"
        private const val DEVICE_ID = "spotify"
        private const val DATA_TYPE = "track_info"
    }

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var lastLoggedTrackId: String? = null
    private var lastLoggedIsPaused: Boolean? = null
    private var currentRecordingName: String? = null
    private var isSubscribed = false

    // Callback for when playback starts (for auto-starting recording)
    var onPlaybackStarted: (() -> Unit)? = null

    fun setSpotifyAppRemote(appRemote: SpotifyAppRemote?) {
        spotifyAppRemote = appRemote
        if (appRemote != null && !isSubscribed) {
            subscribeToPlayerState()
        }
    }

    fun startTracking(recordingName: String) {
        currentRecordingName = recordingName
        Log.d(TAG, "Started tracking Spotify for recording: $recordingName")

        // Subscribe to player state if connected
        spotifyAppRemote?.let {
            subscribeToPlayerState()
        }
    }

    fun stopTracking() {
        currentRecordingName = null
        lastLoggedTrackId = null
        lastLoggedIsPaused = null
        Log.d(TAG, "Stopped tracking Spotify")
    }

    private fun subscribeToPlayerState() {
        if (isSubscribed) return

        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState: PlayerState ->
            handlePlayerStateChange(playerState)
        }?.setErrorCallback { throwable ->
            Log.e(TAG, "Failed to subscribe to player state: ${throwable.message}", throwable)
        }

        isSubscribed = true
        Log.d(TAG, "Subscribed to Spotify player state")
    }

    private fun handlePlayerStateChange(playerState: PlayerState) {
        val track: Track = playerState.track
        val trackId = track.uri
        val isPaused = playerState.isPaused
        val playbackPosition = playerState.playbackPosition

        // Check if music just started playing (was paused or different track, now playing)
        val justStartedPlaying = !isPaused && (lastLoggedIsPaused != false || trackId != lastLoggedTrackId)

        // Trigger auto-recording start if music just started playing and not currently recording
        if (justStartedPlaying && currentRecordingName == null) {
            Log.d(TAG, "Music started playing - triggering auto-recording start")
            onPlaybackStarted?.invoke()
        }

        // Log every state change (track change or play/pause change)
        val shouldLog = trackId != lastLoggedTrackId || isPaused != lastLoggedIsPaused

        if (shouldLog) {
            lastLoggedTrackId = trackId
            lastLoggedIsPaused = isPaused

            val trackData = SpotifyTrackData(
                trackId = trackId,
                trackName = track.name,
                artistName = track.artist.name,
                albumName = track.album.name,
                durationMs = track.duration,
                isPaused = isPaused,
                playbackPosition = playbackPosition,
                playbackSpeed = playerState.playbackSpeed,
                timestamp = System.currentTimeMillis()
            )

            Log.d(TAG, "Track: ${track.name} by ${track.artist.name}, " +
                    "isPaused: $isPaused, position: $playbackPosition, " +
                    "trackId: $trackId")

            // Log to all enabled data savers
            val recordingName = currentRecordingName ?: "no_recording"
            logTrackData(recordingName, trackData)
        }
    }

    private fun logTrackData(recordingName: String, trackData: SpotifyTrackData) {
        val timestamp = System.currentTimeMillis()

        // Log to Spotify data saver (for display purposes)
        dataSavers.spotify.logTrackData(recordingName, trackData)

        // Only save to file/MQTT if we're actively recording (recordingName is set)
        if (currentRecordingName != null) {
            // Save to all enabled data savers (FileSystem, MQTT, etc.)
            // Include BOTH playing and paused states - we want to track everything during recording
            dataSavers.asList().forEach { saver ->
                if (saver.isEnabled.value) {
                    saver.saveData(
                        phoneTimestamp = timestamp,
                        deviceId = DEVICE_ID,
                        recordingName = recordingName,
                        dataType = DATA_TYPE,
                        data = trackData
                    )
                }
            }
        }
    }
    
    /**
     * Manually log track info (used for logging the initial track that triggered notification)
     */
    fun logInitialTrack(
        trackName: String,
        artistName: String,
        albumName: String,
        trackUri: String,
        duration: Long,
        position: Long,
        isPaused: Boolean = false
    ) {
        if (currentRecordingName == null) {
            Log.w(TAG, "Cannot log initial track - no active recording")
            return
        }
        
        val trackData = SpotifyTrackData(
            trackId = trackUri,
            trackName = trackName,
            artistName = artistName,
            albumName = albumName,
            durationMs = duration,
            isPaused = isPaused,
            playbackPosition = position,
            playbackSpeed = 1.0f,
            timestamp = System.currentTimeMillis()
        )
        
        Log.d(TAG, "Manually logging initial track: $trackName by $artistName (Album: $albumName)")
        logTrackData(currentRecordingName!!, trackData)
        
        // Update last logged state to prevent duplicate logging
        lastLoggedTrackId = trackUri
        lastLoggedIsPaused = isPaused
    }

    fun cleanup() {
        isSubscribed = false
        spotifyAppRemote = null
        currentRecordingName = null
        lastLoggedTrackId = null
        lastLoggedIsPaused = null
        Log.d(TAG, "SpotifyManager cleaned up")
    }
}

