package com.wboelens.polarrecorder

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.util.Log
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.MediaPlaybackManager
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.managers.RecordingManager
import com.wboelens.polarrecorder.managers.SurveyManager
import com.wboelens.polarrecorder.utils.NotificationHelper
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import com.wboelens.polarrecorder.viewModels.LogViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PolarRecorderApplication : Application() {
    companion object {
        private const val TAG = "PolarRecorderApp"

        // Shared instances
        lateinit var instance: PolarRecorderApplication
            private set

        lateinit var deviceViewModel: DeviceViewModel
            private set

        lateinit var logViewModel: LogViewModel
            private set

        lateinit var preferencesManager: PreferencesManager
            private set

        lateinit var dataSavers: DataSavers
            private set

        @SuppressLint("StaticFieldLeak") // Using Application context, not Activity context
        lateinit var polarManager: PolarManager
            private set

        @SuppressLint("StaticFieldLeak") // Using Application context, not Activity context
        lateinit var surveyManager: SurveyManager
            private set

        @SuppressLint("StaticFieldLeak") // Using Application context, not Activity context
        lateinit var recordingManager: RecordingManager
            private set

        @SuppressLint("StaticFieldLeak")
        lateinit var mediaPlaybackManager: MediaPlaybackManager
            private set
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pauseJob: Job? = null

    // Holds the background recording task so it isn't garbage collected
    private var activeAutoRecordViewModel: com.wboelens.polarrecorder.viewModels.AutoRecordViewModel? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "Application onCreate - initializing shared managers")

        // Initialize ViewModels
        deviceViewModel = DeviceViewModel()
        logViewModel = LogViewModel()

        // Initialize managers
        preferencesManager = PreferencesManager(applicationContext)
        dataSavers = DataSavers(applicationContext, logViewModel, preferencesManager)
        surveyManager = SurveyManager(applicationContext, logViewModel)
        polarManager = PolarManager(applicationContext, deviceViewModel, logViewModel, preferencesManager)
        recordingManager = RecordingManager(
            applicationContext,
            polarManager,
            logViewModel,
            deviceViewModel,
            preferencesManager,
            dataSavers
        )
        // Inject recordingManager back into polarManager so it can check recording state on disconnect
        polarManager.setRecordingManager(recordingManager)

        // Initialize media playback manager for real-time music detection
        mediaPlaybackManager = MediaPlaybackManager(applicationContext, logViewModel)
        
        // Wire up media playback data savings
        mediaPlaybackManager.onPlaybackStarted = { appPackage, trackTitle, artist ->
            saveMediaEventToDisk("PLAYING", appPackage, trackTitle, artist, null)
        }
        
        mediaPlaybackManager.onPlaybackPaused = { appPackage ->
            saveMediaEventToDisk("PAUSED", appPackage, mediaPlaybackManager.currentTrackTitle, mediaPlaybackManager.currentArtist, mediaPlaybackManager.currentAlbum)
        }
        
        mediaPlaybackManager.onTrackChanged = { appPackage, trackTitle, artist, album ->
            saveMediaEventToDisk("TRACK_CHANGED", appPackage, trackTitle, artist, album)
        }

        // Auto-start media listening if permission is granted and auto-recording is enabled
        if (preferencesManager.autoRecordingEnabled && mediaPlaybackManager.isNotificationAccessGranted()) {
            mediaPlaybackManager.startListening()
            Log.d(TAG, "Media playback monitoring auto-started")
        }

        startMusicObserver()

        // Start random ESM probe scheduler if auto-recording is enabled
        if (preferencesManager.autoRecordingEnabled) {
            com.wboelens.polarrecorder.services.SurveyNotificationService.start(applicationContext)
            Log.d(TAG, "ESM probe scheduler started")
        }

        Log.d(TAG, "Shared managers initialized successfully")
    }

    private fun saveMediaEventToDisk(event: String, appPackage: String, trackTitle: String?, artist: String?, album: String?) {
        if (!recordingManager.isRecording.value) return
        
        val data = mutableMapOf<String, Any>(
            "event" to event,
            "app_package" to appPackage
        )
        trackTitle?.let { data["track_title"] = it }
        artist?.let { data["artist"] = it }
        album?.let { data["album"] = it }
        mediaPlaybackManager.currentDurationMs?.let { data["duration_ms"] = it }
        
        try {
            dataSavers.fileSystem.saveData(
                System.currentTimeMillis(),
                "mediaTrack",
                recordingManager.currentRecordingName,
                "track_info",
                data
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save media track data: ${e.message}")
        }
    }

    private fun startMusicObserver() {
        applicationScope.launch {
            mediaPlaybackManager.isPlayingFlow.collect { isPlaying ->
                if (preferencesManager.autoRecordingEnabled) {
                    if (isPlaying) {
                        // Music started or resumed
                        pauseJob?.cancel()
                        pauseJob = null

                        Log.d(TAG, "Music started/resumed")
                        
                        if (!recordingManager.isRecording.value && !recordingManager.isMusicPostRecording) {
                            // Check if any Polar sensor is connected before starting a recording
                            val connectedDevices = deviceViewModel.connectedDevices.value ?: emptyList()
                            if (connectedDevices.isEmpty()) {
                                Log.w(TAG, "Music started but no Polar sensor connected — notifying user")
                                NotificationHelper.showSensorNotConnectedNotification(applicationContext)
                                // Don't start auto-record: there's no sensor to record from
                                return@collect
                            }

                            // Sensor is connected — dismiss any lingering warning
                            NotificationHelper.cancelSensorDisconnectedNotification(applicationContext)

                            Log.d(TAG, "Starting new session: showing music_pre notification and launching background recording")
                            val intent = Intent(applicationContext, com.wboelens.polarrecorder.ui.screens.SurveyActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra("notification_type", "music_pre")
                                mediaPlaybackManager.currentTrackTitle?.let { putExtra("track_name", it) }
                                mediaPlaybackManager.currentArtist?.let { putExtra("track_artist", it) }
                                mediaPlaybackManager.currentAlbum?.let { putExtra("track_album", it) }
                                mediaPlaybackManager.currentDurationMs?.let { putExtra("track_duration", it) }
                            }
                            
                            // Send heads up notification to bypass Android 10+ background app start blocks
                            com.wboelens.polarrecorder.utils.NotificationHelper.showMusicPreNotification(applicationContext, intent)
                            
                            // Start recording in the background natively without waiting for user to click the survey
                            activeAutoRecordViewModel = com.wboelens.polarrecorder.viewModels.AutoRecordViewModel()
                            activeAutoRecordViewModel?.startAutoRecordFlow(
                                polarManager,
                                deviceViewModel,
                                recordingManager,
                                dataSavers,
                                preferencesManager,
                                logViewModel,
                                surveyManager,
                                "music_pre"
                            )
                        } else {
                            Log.d(TAG, "Resuming existing active session")
                        }
                    } else {
                        // Music stopped or paused
                        if (recordingManager.isRecording.value && !recordingManager.isMusicPostRecording) {
                            Log.d(TAG, "Music paused — starting 60s grace period")
                            logViewModel.addLogMessage("Music paused. Post-survey will arrive in 60s if not resumed.")
                            
                            pauseJob?.cancel()

                            pauseJob = launch {
                                delay(60_000L) // 60 seconds grace period
                                
                                Log.d(TAG, "60s grace period ended — triggering post-survey notification")
                                logViewModel.addLogMessage("60s pause timeout reached - sending post-survey notification")
                                
                                NotificationHelper.showMusicPostNotification(applicationContext)
                                recordingManager.startMusicPostNotificationTimeout {
                                     Log.d(TAG, "User missed 120s post-survey notification window")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
