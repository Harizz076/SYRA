package com.wboelens.polarrecorder.managers

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import com.wboelens.polarrecorder.services.MediaPlaybackListenerService
import com.wboelens.polarrecorder.viewModels.LogViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages real-time media playback detection using Android's MediaSessionManager.
 *
 * Listens to ALL active media sessions on the device (Spotify, YouTube Music,
 * Apple Music, etc.) and fires callbacks when playback starts, pauses, or
 * track metadata changes.
 *
 * Requires the user to grant Notification Access permission.
 */
class MediaPlaybackManager(
    private val context: Context,
    private val logViewModel: LogViewModel
) {
    companion object {
        private const val TAG = "MediaPlaybackMgr"

        /**
         * Allowlist of known dedicated music streaming app package names.
         * Video-first apps (YouTube, browser, Reddit, etc.) are deliberately excluded.
         * Add new packages here as needed for participant devices.
         */
        val MUSIC_APP_ALLOWLIST = setOf(
            "com.spotify.music",               // Spotify
            "com.apple.android.music",          // Apple Music
            "com.google.android.apps.youtube.music", // YouTube Music
            "com.amazon.mp3",                   // Amazon Music
            "com.soundcloud.android",           // SoundCloud
            "deezer.android.app",               // Deezer
            "com.tidal.wave",                   // Tidal
            "com.pandora.android",              // Pandora
            "com.gaana",                        // Gaana
            "com.jio.media.jiobeats",           // JioSaavn
            "com.wynk.music",                   // Wynk Music
            "com.hungama.myplay.activity",      // Hungama Music
            "com.anghami",                      // Anghami
            "tunein.player",                    // TuneIn Radio
            "com.shazam.android",               // Shazam
            "com.bandcamp.android",             // Bandcamp
            "com.pocketcasts.podcast.player",   // Pocket Casts (podcasts)
            "au.com.shiftyjelly.pocketcasts",   // Pocket Casts alt id
            "com.google.android.music",         // Google Play Music (legacy)
            "com.sec.android.app.music",        // Samsung Music
            "com.miui.player",                  // MIUI Music Player
            "com.oneplus.music",                // OnePlus Music
            "com.musicapp.lyra"                 // Lyra - Music, Radio & Podcasts
        )
    }

    private var mediaSessionManager: MediaSessionManager? = null
    private val activeControllers = mutableMapOf<String, MediaController>()
    private val controllerCallbacks = mutableMapOf<String, MediaController.Callback>()

    // Current playback state — exposed as a StateFlow for reactive observation
    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow

    var isPlaying = false
        private set
    var currentTrackTitle: String? = null
        private set
    var currentArtist: String? = null
        private set
    var currentAlbum: String? = null
        private set
    var currentAppPackage: String? = null
        private set
    var currentDurationMs: Long? = null
        private set

    // Callbacks for external consumers (e.g., auto-recording trigger)
    var onPlaybackStarted: ((appPackage: String, trackTitle: String?, artist: String?) -> Unit)? = null
    var onPlaybackPaused: ((appPackage: String) -> Unit)? = null
    var onTrackChanged: ((appPackage: String, trackTitle: String?, artist: String?, album: String?) -> Unit)? = null

    /** Returns true if the given package belongs to a dedicated music app. */
    private fun isMusicApp(packageName: String): Boolean = packageName in MUSIC_APP_ALLOWLIST

    /**
     * Start listening for active media sessions.
     * Returns false if Notification Access is not granted.
     */
    fun startListening(): Boolean {
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        if (msm == null) {
            Log.e(TAG, "MediaSessionManager not available")
            return false
        }
        mediaSessionManager = msm

        val componentName = ComponentName(context, MediaPlaybackListenerService::class.java)

        return try {
            // Register for session changes
            msm.addOnActiveSessionsChangedListener(sessionListener, componentName)

            // Process currently active sessions
            val activeSessions = msm.getActiveSessions(componentName)
            Log.d(TAG, "Found ${activeSessions.size} active media sessions")
            handleActiveSessionsChanged(activeSessions)

            logViewModel.addLogMessage("Media playback monitoring started")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification Access not granted", e)
            logViewModel.addLogError("Please grant Notification Access permission in Settings")
            false
        }
    }

    fun stopListening() {
        mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionListener)

        // Unregister all controller callbacks
        activeControllers.forEach { (token, controller) ->
            controllerCallbacks[token]?.let { controller.unregisterCallback(it) }
        }
        activeControllers.clear()
        controllerCallbacks.clear()

        isPlaying = false
        _isPlayingFlow.value = false
        Log.d(TAG, "Media playback monitoring stopped")
    }

    /**
     * Check if Notification Access permission is granted for our app.
     */
    fun isNotificationAccessGranted(): Boolean {
        val componentName = ComponentName(context, MediaPlaybackListenerService::class.java)
        val enabledListeners = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(componentName.flattenToString())
    }

    // --- Session change listener ---

    private val sessionListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        handleActiveSessionsChanged(controllers ?: emptyList())
    }

    private fun handleActiveSessionsChanged(controllers: List<MediaController>) {
        Log.d(TAG, "Active sessions changed: ${controllers.size} sessions")

        // Unregister callbacks for sessions that are no longer active
        val activeTokens = controllers.map { it.sessionToken.toString() }.toSet()
        val removedTokens = activeControllers.keys - activeTokens
        removedTokens.forEach { token ->
            activeControllers[token]?.let { controller ->
                controllerCallbacks[token]?.let { controller.unregisterCallback(it) }
            }
            activeControllers.remove(token)
            controllerCallbacks.remove(token)
        }

        // Register callbacks for new sessions
        controllers.forEach { controller ->
            val token = controller.sessionToken.toString()
            if (!activeControllers.containsKey(token)) {
                val callback = createControllerCallback(controller)
                controller.registerCallback(callback)
                activeControllers[token] = controller
                controllerCallbacks[token] = callback

                val appPackage = controller.packageName
                Log.d(TAG, "Registered callback for media session: $appPackage")

                // Check if this session is already playing
                val state = controller.playbackState
                if (state?.state == PlaybackState.STATE_PLAYING) {
                    handlePlaybackStateChange(controller, state)
                }
            }
        }
    }

    private fun createControllerCallback(controller: MediaController): MediaController.Callback {
        return object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                state?.let { handlePlaybackStateChange(controller, it) }
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                metadata?.let { handleMetadataChange(controller, it) }
            }
        }
    }

    // --- Playback state handling ---

    private fun handlePlaybackStateChange(controller: MediaController, state: PlaybackState) {
        val appPackage = controller.packageName

        // Ignore non-music apps (YouTube, Reddit, browsers, etc.)
        if (!isMusicApp(appPackage)) {
            Log.d(TAG, "Ignoring playback from non-music app: $appPackage")
            return
        }

        val appName = getAppName(appPackage)

        when (state.state) {
            PlaybackState.STATE_PLAYING -> {
                val wasPlaying = isPlaying
                isPlaying = true
                _isPlayingFlow.value = true
                currentAppPackage = appPackage

                // Update metadata from the controller
                controller.metadata?.let { updateCurrentMetadata(it) }

                if (!wasPlaying) {
                    Log.d(TAG, "▶ PLAYING: $appName — $currentTrackTitle by $currentArtist")
                    logViewModel.addLogMessage("▶ Playing: $currentTrackTitle by $currentArtist ($appName)")
                    onPlaybackStarted?.invoke(appPackage, currentTrackTitle, currentArtist)
                }
            }
            PlaybackState.STATE_PAUSED,
            PlaybackState.STATE_STOPPED,
            PlaybackState.STATE_NONE -> {
                if (isPlaying && currentAppPackage == appPackage) {
                    isPlaying = false
                    _isPlayingFlow.value = false
                    Log.d(TAG, "⏸ PAUSED/STOPPED: $appName")
                    logViewModel.addLogMessage("⏸ Paused: $currentTrackTitle ($appName)")
                    onPlaybackPaused?.invoke(appPackage)
                }
            }
            else -> {
                // STATE_BUFFERING, STATE_ERROR, STATE_FAST_FORWARDING, etc. — ignore
            }
        }
    }

    private fun handleMetadataChange(controller: MediaController, metadata: MediaMetadata) {
        val appPackage = controller.packageName

        // Ignore non-music apps
        if (!isMusicApp(appPackage)) return

        val newTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        val newArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val newAlbum = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)

        // Only fire track changed if the track actually changed
        if (newTitle != currentTrackTitle || newArtist != currentArtist) {
            updateCurrentMetadata(metadata)
            val appName = getAppName(appPackage)
            Log.d(TAG, "🎵 Track changed: $currentTrackTitle by $currentArtist ($appName)")
            logViewModel.addLogMessage("🎵 Track: $currentTrackTitle by $currentArtist")
            onTrackChanged?.invoke(appPackage, newTitle, newArtist, newAlbum)
        }
    }

    private fun updateCurrentMetadata(metadata: MediaMetadata) {
        currentTrackTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
        currentArtist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
        currentAlbum = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)
        currentDurationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }
}
