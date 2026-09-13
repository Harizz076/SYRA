package com.wboelens.polarrecorder

import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.util.Log
import com.wboelens.polarrecorder.database.DatabaseManager
import com.wboelens.polarrecorder.database.SyraDatabase
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.EsmScheduler
import com.wboelens.polarrecorder.managers.ExportManager
import com.wboelens.polarrecorder.managers.MediaPlaybackManager
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.managers.RecordingManager
import com.wboelens.polarrecorder.managers.SurveyManager
import com.wboelens.polarrecorder.receivers.EsmAlarmReceiver
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

        @SuppressLint("StaticFieldLeak")
        lateinit var databaseManager: DatabaseManager
            private set

        @SuppressLint("StaticFieldLeak")
        lateinit var syraDatabase: SyraDatabase
            private set

        @SuppressLint("StaticFieldLeak")
        lateinit var exportManager: ExportManager
            private set
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pauseJob: Job? = null

    // Holds the background recording task so it isn't garbage collected
    private var activeAutoRecordViewModel: com.wboelens.polarrecorder.viewModels.AutoRecordViewModel? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        // ── One-time cleanup: kill legacy SurveyNotificationService ──────────────────────────────
        // SurveyNotificationService was replaced by EsmAlarmReceiver in a prior build.
        // Because it used START_STICKY, the OS may resurrect it after an APK update even
        // though we no longer call SurveyNotificationService.start() anywhere.
        // Calling stopService() here is always safe — it is a no-op if the service isn't running.
        try {
            val legacyIntent = Intent().apply {
                setClassName("com.wboelens.polarrecorder", "com.wboelens.polarrecorder.services.SurveyNotificationService")
            }
            stopService(legacyIntent)
            Log.d(TAG, "Legacy SurveyNotificationService stopped (cleanup)")
        } catch (e: Exception) {
            Log.w(TAG, "Could not stop legacy service (may not have been running): ${e.message}")
        }

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

        // ── Cold-start session cleanup (BUG-04) ─────────────────────────────────────────────────
        // ponytail: RecordingManager is newly constructed so _isRecording = false already.
        // But isMusicPostRecording / isRandomRecording are plain vars that survive nothing —
        // they're also freshly false. This block exists as a safety net in case logic changes.
        recordingManager.isMusicPostRecording = false
        recordingManager.hasMusicPostSurveySubmitted = false
        recordingManager.isRandomRecording = false
        recordingManager.hasRandomSurveySubmitted = false
        Log.d(TAG, "Cold-start: dangling session flags reset")

        // ── Encrypted local database (SQLCipher via Android Keystore) ────────────────
        databaseManager = DatabaseManager(applicationContext)
        syraDatabase = databaseManager.getDatabase()
        exportManager = ExportManager(
            context = applicationContext,
            db = syraDatabase,
            participantId = preferencesManager.participantId,
        )
        Log.d(TAG, "Encrypted database initialised")

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

        // Auto-start media listening only for modes that use music triggers.
        // RANDOM_ESM disables all media playback listeners to save battery.
        if (preferencesManager.requiresMediaListener &&
            preferencesManager.autoRecordingEnabled &&
            mediaPlaybackManager.isNotificationAccessGranted()
        ) {
            mediaPlaybackManager.startListening()
            Log.d(TAG, "Media playback monitoring auto-started")
        }

        // Only observe music events for modes that trigger surveys from media playback
        if (preferencesManager.requiresMediaListener) {
            startMusicObserver()
        }

        // Start the optimal ESM probe scheduler for modes that require it.
        // The AlarmManager-based receiver replaces the old persistent foreground service,
        // allowing the SoC to enter Doze between probes (Section 8-B of the design doc).
        // Runs on IO dispatcher because scheduleTodayProbes() queries the Room database.
        if (preferencesManager.autoRecordingEnabled && preferencesManager.requiresEsmScheduler) {
            applicationScope.launch(Dispatchers.IO) {
                try {
                    val esmScheduler = EsmScheduler(applicationContext, syraDatabase)
                    EsmAlarmReceiver.scheduleTodayProbes(applicationContext, esmScheduler)
                    Log.d(TAG, "ESM probe scheduler (AlarmManager) started for mode: ${preferencesManager.studyMode}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to schedule ESM probes on startup: ${e.message}", e)
                }
            }
        }

        Log.d(TAG, "Shared managers initialized successfully")

        // Register lifecycle observer to re-initialize the media listener if the user
        // grants Notification Access after the app first launched.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) { ensureMediaListenerRunning() }
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityPaused(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
    }

    /**
     * Idempotent: starts media playback monitoring if:
     *   - The study mode requires it (not RANDOM_ESM)
     *   - Notification Access permission is now granted
     *   - The listener hasn't been started yet (isPlaying == false and no active sessions registered)
     *
     * Called on every Activity resume so that permission granted in the system settings
     * takes effect the next time the user returns to the app.
     */
    fun ensureMediaListenerRunning() {
        if (!preferencesManager.requiresMediaListener) return
        if (!preferencesManager.autoRecordingEnabled) return
        if (!mediaPlaybackManager.isNotificationAccessGranted()) return

        // startListening() internally checks if already registered; calling it again when
        // already active re-scans current sessions which is harmless and desirable.
        val started = mediaPlaybackManager.startListening()
        if (started) {
            Log.d(TAG, "ensureMediaListenerRunning: media listener (re)started after permission check")
        }
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
        var lastStateWasPlaying: Boolean? = null
        applicationScope.launch {
            mediaPlaybackManager.isPlayingFlow.collect { isPlaying ->
                val previousState = lastStateWasPlaying
                lastStateWasPlaying = isPlaying

                if (previousState == null) {
                    Log.d(TAG, "startMusicObserver: Initial flow collection ignored (isPlaying = $isPlaying)")
                    return@collect
                }

                if (previousState == isPlaying) {
                    return@collect
                }

                if (preferencesManager.autoRecordingEnabled) {
                    if (isPlaying) {
                        // Music started or resumed
                        pauseJob?.cancel()
                        pauseJob = null

                        Log.d(TAG, "Music started/resumed")
                        
                        if (!recordingManager.isRecording.value && !recordingManager.isMusicPostRecording) {
                            // Check if any Polar sensor is connected before starting a recording
                            val connectedDevices = deviceViewModel.connectedDevices.value ?: emptyList()
                            // Only enforce sensor requirement in PHYSIOLOGY mode
                            if (connectedDevices.isEmpty() && preferencesManager.requiresSensor) {
                                Log.w(TAG, "Music started but no Polar sensor connected (PHYSIOLOGY mode) — notifying user")
                                NotificationHelper.showSensorNotConnectedNotification(applicationContext)
                                // Don't start auto-record: physiology mode needs a sensor
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
