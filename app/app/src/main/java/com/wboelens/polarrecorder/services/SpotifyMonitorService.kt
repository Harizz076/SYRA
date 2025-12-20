package com.wboelens.polarrecorder.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import com.wboelens.polarrecorder.PolarRecorderApplication
import com.wboelens.polarrecorder.managers.PreferencesManager

class SpotifyMonitorService : Service() {

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var isPlayingMusic = false
    private var hasShownNotification = false
    private var currentPlayerState: PlayerState? = null
    private lateinit var preferencesManager: PreferencesManager

    companion object {
        private const val TAG = "SpotifyMonitorService"
        private const val SERVICE_CHANNEL_ID = "spotify_monitor_service_channel"
        private const val SURVEY_CHANNEL_ID = "spotify_survey_channel"
        private const val NOTIFICATION_ID = 1001
        private const val CLIENT_ID = "3c52f0046fa342baa89e7b66004177f8"
        private const val REDIRECT_URI = "com.wboelens.polarrecorder://callback"

        fun start(context: Context) {
            val intent = Intent(context, SpotifyMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SpotifyMonitorService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        preferencesManager = PreferencesManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createServiceNotification())
        connectToSpotify()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connectToSpotify() {
        val connectionParams = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(false)
            .build()

        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                Log.d(TAG, "Connected to Spotify App Remote")
                subscribeToPlayerState()
            }

            override fun onFailure(throwable: Throwable) {
                Log.e(TAG, "Failed to connect to Spotify: ${throwable.message}")
                // Retry connection after a delay
                android.os.Handler(mainLooper).postDelayed({
                    connectToSpotify()
                }, 5000)
            }
        })
    }

    private fun subscribeToPlayerState() {
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
            handlePlayerStateChange(playerState)
        }
    }

    private fun handlePlayerStateChange(playerState: PlayerState) {
        // Check if auto-recording is enabled
        if (!preferencesManager.autoRecordingEnabled) {
            Log.d(TAG, "Auto-recording is disabled, skipping player state change handling")
            return
        }

        val isPlaying = !playerState.isPaused

        // Store current player state
        currentPlayerState = playerState

        Log.d(TAG, "Player state changed - isPaused: ${playerState.isPaused}, track: ${playerState.track.name}")

        if (isPlaying && !isPlayingMusic) {
            // Music just started playing
            isPlayingMusic = true
            showSurveyNotification(playerState)
        } else if (!isPlaying && isPlayingMusic) {
            // Music stopped
            isPlayingMusic = false
            hasShownNotification = false
            // Keep the service running but update notification
            updateServiceNotification()
        }
    }

    private fun showSurveyNotification(playerState: PlayerState) {
        if (hasShownNotification) {
            // Don't spam notifications during the same session
            return
        }

        // Don't show notification if recording is already active
        val recordingManager = PolarRecorderApplication.recordingManager
        if (recordingManager.isRecording.value) {
            Log.d(TAG, "Recording is active, skipping 'listening to music' notification")
            return
        }

        hasShownNotification = true

        // Vibrate to get user's attention
        vibrateDevice()

        val intent = Intent().apply {
            component = android.content.ComponentName(
                "com.wboelens.polarrecorder",
                "com.wboelens.polarrecorder.ui.screens.SurveyActivity"
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", "music_pre")
            // Pass current track info
            putExtra("track_name", playerState.track.name)
            putExtra("track_artist", playerState.track.artist.name)
            putExtra("track_album", playerState.track.album.name)
            putExtra("track_uri", playerState.track.uri)
            putExtra("track_duration", playerState.track.duration)
            putExtra("track_position", playerState.playbackPosition)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, SURVEY_CHANNEL_ID)
            .setContentTitle("Listening to music?")
            .setContentText("Please take a minute to fill in this survey")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(false)
            .setOngoing(true) // Make it sticky
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun vibrateDevice() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Vibrate for 500ms
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    private fun createServiceNotification(): Notification {
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle("Spotify Monitor")
            .setContentText("Monitoring Spotify playback")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .setSound(null)
            .setVibrate(null)
            .build()
    }

    private fun updateServiceNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createServiceNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Silent channel for service notification
            val serviceChannel = NotificationChannel(
                SERVICE_CHANNEL_ID,
                "Spotify Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background Spotify monitoring service"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
            }

            // Loud channel for survey notifications
            val surveyChannel = NotificationChannel(
                SURVEY_CHANNEL_ID,
                "Music Survey Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Survey prompts when listening to music"
                enableVibration(true)
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(surveyChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
        }
    }
}

