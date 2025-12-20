package com.wboelens.polarrecorder.ui.screens

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.wboelens.polarrecorder.MainActivity
import com.wboelens.polarrecorder.PolarRecorderApplication
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.PermissionManager
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.managers.RecordingManager
import com.wboelens.polarrecorder.managers.SpotifyManager
import com.wboelens.polarrecorder.ui.theme.AppTheme
import com.wboelens.polarrecorder.viewModels.AutoRecordViewModel
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import com.wboelens.polarrecorder.viewModels.LogViewModel

class SurveyActivity : ComponentActivity() {

    // Spotify connection
    private val clientId = "3c52f0046fa342baa89e7b66004177f8"
    private val redirectUri = "com.wboelens.polarrecorder://callback"
    private var spotifyAppRemote: SpotifyAppRemote? = null

    // Pause timeout tracking
    private var pauseTimeoutHandler: android.os.Handler? = null
    private var pauseTimeoutRunnable: Runnable? = null
    private var isPausedDuringRecording = false
    private val PAUSE_TIMEOUT_MS = 60000L // 60 seconds


    // Use shared instances from Application
    private val deviceViewModel: DeviceViewModel
        get() = PolarRecorderApplication.deviceViewModel

    private val logViewModel: LogViewModel
        get() = PolarRecorderApplication.logViewModel

    private val autoRecordViewModel: AutoRecordViewModel by viewModels()

    private val polarManager: PolarManager
        get() = PolarRecorderApplication.polarManager

    private val recordingManager: RecordingManager
        get() = PolarRecorderApplication.recordingManager

    private val preferencesManager: PreferencesManager
        get() = PolarRecorderApplication.preferencesManager

    private val dataSavers: DataSavers
        get() = PolarRecorderApplication.dataSavers

    private val spotifyManager: SpotifyManager
        get() = PolarRecorderApplication.spotifyManager

    private val surveyManager: com.wboelens.polarrecorder.managers.SurveyManager
        get() = PolarRecorderApplication.surveyManager

    private lateinit var permissionManager: PermissionManager

    // Track if this activity started the recording
    private var didStartRecording = false

    // Track if we're currently starting a recording to prevent double-initialization
    private var isStartingRecording = false

    // Notification type from intent
    private var notificationType: String = "music_pre"

    // Track info from notification (for logging the initial song)
    private var initialTrackName: String? = null
    private var initialTrackArtist: String? = null
    private var initialTrackAlbum: String? = null
    private var initialTrackUri: String? = null
    private var initialTrackDuration: Long? = null
    private var initialTrackPosition: Long? = null

    companion object {
        private const val TAG = "SurveyActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get notification type from intent
        notificationType = intent.getStringExtra("notification_type") ?: "music_pre"

        // Get track info from intent (if from music notification)
        initialTrackName = intent.getStringExtra("track_name")
        initialTrackArtist = intent.getStringExtra("track_artist")
        initialTrackAlbum = intent.getStringExtra("track_album")
        initialTrackUri = intent.getStringExtra("track_uri")
        initialTrackDuration = intent.getLongExtra("track_duration", -1).takeIf { it >= 0 }
        initialTrackPosition = intent.getLongExtra("track_position", -1).takeIf { it >= 0 }

        Log.d(TAG, "onCreate: Notification type: $notificationType")
        if (initialTrackName != null) {
            Log.d(TAG, "onCreate: Initial track: $initialTrackName by $initialTrackArtist (Album: $initialTrackAlbum)")
        }

        // Clear any pending survey from previous session
        surveyManager.clearPendingSurvey()

        Log.d(TAG, "onCreate: Initializing SurveyActivity with auto-record")

        // Check if recording was already active
        val wasRecordingActive = recordingManager.isRecording.value
        Log.d(TAG, "Recording was already active: $wasRecordingActive")

        // Initialize pause timeout handler
        pauseTimeoutHandler = android.os.Handler(mainLooper)


        // Set up callback for when music starts playing after recording stopped
        setupSpotifyPlaybackCallback()

        // Connect to Spotify for tracking
        connectToSpotify()

        // Only initialize permission manager locally
        permissionManager = PermissionManager(this)

        setContent {
            AppTheme {
                SurveyScreen(
                    notificationType = notificationType,
                    autoRecordViewModel = autoRecordViewModel,
                    surveyManager = surveyManager,
                    dataSavers = dataSavers,
                    onClose = { handleClose() },
                    onCancelRecording = { handleCancelRecording() },
                    onNavigateToConnect = {
                        // Navigate to MainActivity to connect devices
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }

        // For music_post, don't start auto-record (recording already active)
        if (notificationType == "music_post") {
            Log.d(TAG, "Music post notification - skipping auto-record (recording already active)")
            return
        }

        // Start auto-record flow immediately in background while user fills survey
        permissionManager.checkAndRequestPermissions {
            Log.d(TAG, "Permissions granted, starting auto-record flow")

            // Check if recording was already active
            val wasRecordingActive = recordingManager.isRecording.value

            // If recording was not already active, mark that we started it
            if (!wasRecordingActive) {
                didStartRecording = true
                isStartingRecording = true // Prevent callback from triggering duplicate flow
            }

            autoRecordViewModel.startAutoRecordFlow(
                polarManager,
                deviceViewModel,
                recordingManager,
                dataSavers,
                preferencesManager,
                logViewModel,
                surveyManager,
                notificationType
            )

            // Start random notification timeout if this is a random notification
            if (notificationType == "random") {
                recordingManager.startRandomTimeout()
            }

            // Clear the flag after a delay to allow the flow to complete
            android.os.Handler(mainLooper).postDelayed({
                isStartingRecording = false
            }, 5000) // 5 seconds should be enough for initialization

            // Log initial track info if available
            logInitialTrackInfo()
        }
    }

    private fun logInitialTrackInfo() {
        // Wait a bit for recording to start and Spotify tracking to be enabled
        android.os.Handler(mainLooper).postDelayed({
            if (initialTrackName != null && initialTrackArtist != null && recordingManager.isRecording.value) {
                Log.d(TAG, "Logging initial track that triggered notification")

                // Manually save initial track info
                spotifyManager.logInitialTrack(
                    trackName = initialTrackName!!,
                    artistName = initialTrackArtist!!,
                    albumName = initialTrackAlbum ?: "Unknown",
                    trackUri = initialTrackUri ?: "",
                    duration = initialTrackDuration ?: 0L,
                    position = initialTrackPosition ?: 0L,
                    isPaused = false
                )

                logViewModel.addLogMessage("Logged initial track: $initialTrackName by $initialTrackArtist")
            }
        }, 2000) // Wait 2 seconds for recording to initialize
    }

    override fun onDestroy() {
        super.onDestroy()

        // Cancel any pending pause timeout
        cancelPauseTimeout()


        // DON'T disconnect from Spotify if recording is active
        // Keep the connection for pause monitoring
        if (recordingManager.isRecording.value) {
            Log.d(TAG, "Recording is active, keeping Spotify connected for monitoring")
            // Just remove our local reference but don't disconnect
            spotifyAppRemote = null
        } else {
            // Only disconnect if not recording
            spotifyAppRemote?.let {
                SpotifyAppRemote.disconnect(it)
                spotifyAppRemote = null
                spotifyManager.setSpotifyAppRemote(null)
                Log.d(TAG, "Disconnected from Spotify")
            }
        }

        // NEVER stop recording from SurveyActivity
        // The recording should continue even after survey is closed
        // Only stop when user explicitly stops from MainActivity or pause timeout
        Log.d(TAG, "SurveyActivity destroyed, keeping recording active")
    }

    private fun connectToSpotify() {
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(false)
            .build()

        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                spotifyManager.setSpotifyAppRemote(appRemote)
                Log.d(TAG, "Connected to Spotify in SurveyActivity")
                logViewModel.addLogMessage("Connected to Spotify")

                // Subscribe to player state for pause timeout monitoring
                subscribeToPlayerState()
            }

            override fun onFailure(throwable: Throwable) {
                Log.e(TAG, "Failed to connect to Spotify", throwable)
                logViewModel.addLogError("Failed to connect to Spotify: ${throwable.message}", false)
            }
        })
    }

    private fun subscribeToPlayerState() {
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
            handlePlayerStateForTimeout(playerState.isPaused)
        }
    }

    private fun handlePlayerStateForTimeout(isPaused: Boolean) {
        // Only monitor pause timeout if recording is active
        // Reset state if recording is not active
        if (!recordingManager.isRecording.value) {
            if (isPausedDuringRecording) {
                isPausedDuringRecording = false
                cancelPauseTimeout()
            }
            return
        }

        if (isPaused) {
            // Music paused - start timeout countdown
            if (!isPausedDuringRecording) {
                isPausedDuringRecording = true
                startPauseTimeout()
                Log.d(TAG, "Music paused during recording - starting 60s timeout")
                logViewModel.addLogMessage("Music paused - will send survey if paused for >60 seconds")
            }
        } else {
            // Music resumed - cancel timeout
            if (isPausedDuringRecording) {
                isPausedDuringRecording = false
                cancelPauseTimeout()
                Log.d(TAG, "Music resumed - canceling pause timeout")
                logViewModel.addLogMessage("Music resumed - continuing recording")
            }
        }
    }

    private fun startPauseTimeout() {
        // Cancel any existing timeout
        cancelPauseTimeout()

        // Create new timeout runnable
        pauseTimeoutRunnable = Runnable {
            Log.d(TAG, "Pause timeout reached (60s) - sending music_post notification")
            logViewModel.addLogMessage("Music paused for >60 seconds - sending survey notification")

            if (recordingManager.isRecording.value) {
                // Send notification instead of stopping
                com.wboelens.polarrecorder.utils.NotificationHelper.showMusicPostNotification(this)

                // Start 120-second notification timeout
                recordingManager.startMusicPostNotificationTimeout {
                    // Callback if notification expires without being clicked
                    Log.d(TAG, "Music post notification expired - recording stopped")
                }

                // Reset pause flag
                isPausedDuringRecording = false

                logViewModel.addLogMessage("Survey notification sent - will auto-stop in 120 seconds if not clicked")
            }
        }

        // Schedule timeout for 60 seconds
        pauseTimeoutHandler?.postDelayed(pauseTimeoutRunnable!!, PAUSE_TIMEOUT_MS)
    }

    private fun cancelPauseTimeout() {
        pauseTimeoutRunnable?.let {
            pauseTimeoutHandler?.removeCallbacks(it)
            pauseTimeoutRunnable = null
        }
    }


    private fun setupSpotifyPlaybackCallback() {
        // Set callback to restart recording when music plays after it was stopped
        spotifyManager.onPlaybackStarted = {
            Log.d(TAG, "Music started playing - checking if should restart recording")

            // Don't restart if we're already in the process of starting a recording
            if (!isStartingRecording && !recordingManager.isRecording.value) {
                // Only restart if we're in SurveyActivity and not already recording
                Log.d(TAG, "Restarting auto-record flow due to music playback")
                logViewModel.addLogMessage("Music playing - restarting auto-recording")

                // Mark that we're starting a new recording
                didStartRecording = true
                isPausedDuringRecording = false
                isStartingRecording = true

                // Request permissions and start auto-record flow
                permissionManager.checkAndRequestPermissions {
                    autoRecordViewModel.startAutoRecordFlow(
                        polarManager,
                        deviceViewModel,
                        recordingManager,
                        dataSavers,
                        preferencesManager,
                        logViewModel,
                        surveyManager
                    )

                    // Clear the flag after a delay
                    android.os.Handler(mainLooper).postDelayed({
                        isStartingRecording = false
                    }, 5000)
                }
            } else {
                if (isStartingRecording) {
                    Log.d(TAG, "Already starting a recording, ignoring playback callback")
                } else {
                    Log.d(TAG, "Recording already active, not restarting")
                }
            }
        }
    }

    private fun handleClose() {
        if (notificationType == "random") {
            // For random notifications, check if 120 seconds have elapsed
            val remainingTime = recordingManager.getRemainingRandomTime()

            if (remainingTime <= 0) {
                // 120 seconds already passed, stop recording immediately (survey should already be saved)
                Log.d(TAG, "120 seconds elapsed for random notification - stopping recording on close")
                if (recordingManager.isRecording.value) {
                    recordingManager.stopRecording()
                }
                recordingManager.cancelRandomTimeout()
                finish()
            } else {
                // Less than 120 seconds, keep recording in background until timeout
                Log.d(TAG, "Random notification: keeping recording in background for ${remainingTime}ms more")
                logViewModel.addLogMessage("Recording will continue in background for ${remainingTime / 1000} more seconds")
                // Activity will finish but recording continues via the timeout in RecordingManager
                finish()
            }
        } else {
            // For music_pre and music_post notifications, just close (recording continues in background)
            // User returns to whatever app they were using before
            finish()
        }
    }

    private fun handleCancelRecording() {
        Log.d(TAG, "User canceled recording session")
        logViewModel.addLogMessage("User chose not to record this session")

        // Cancel any ongoing timeouts
        recordingManager.cancelRandomTimeout()
        recordingManager.cancelMusicPostTimeout()

        // Cancel auto-record flow, stop recording, and delete folder
        autoRecordViewModel.cancelAutoRecordFlow(
            polarManager,
            recordingManager,
            dataSavers,
            logViewModel
        )

        // Clear pending survey
        surveyManager.clearPendingSurvey()

        // Close the activity
        finish()
    }
}

@Composable
fun SurveyScreen(
    notificationType: String,
    autoRecordViewModel: AutoRecordViewModel,
    surveyManager: com.wboelens.polarrecorder.managers.SurveyManager,
    dataSavers: DataSavers,
    onClose: () -> Unit,
    onCancelRecording: () -> Unit,
    onNavigateToConnect: () -> Unit
) {
    val status by autoRecordViewModel.status.collectAsState()
    val statusMessage by autoRecordViewModel.statusMessage.collectAsState()

    // State for slider values (1-7 scale) - start at middle value (4)
    // Use key with timestamp to force recreation on new survey
    val surveyKey = remember { System.currentTimeMillis() }
    var pleasantnessValue by remember(surveyKey) { mutableFloatStateOf(4f) }
    var energyValue by remember(surveyKey) { mutableFloatStateOf(4f) }
    var musicLikingValue by remember(surveyKey) { mutableFloatStateOf(4f) }
    var musicFamiliarityValue by remember(surveyKey) { mutableFloatStateOf(4f) }
    var hasSubmitted by remember(surveyKey) { mutableStateOf(false) }

    // Mark music_post survey as opened (cancel notification timeout)
    LaunchedEffect(notificationType) {
        if (notificationType == "music_post") {
            PolarRecorderApplication.recordingManager.markMusicPostSurveyOpened()
            // Cancel the notification
            com.wboelens.polarrecorder.utils.NotificationHelper.cancelMusicPostNotification(
                PolarRecorderApplication.instance
            )
        }
    }

    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val screenWidth = maxWidth
            val screenHeight = maxHeight
            val isCompactScreen = screenHeight < 700.dp
            val isWideScreen = screenWidth > 600.dp

            // Responsive spacing based on screen size and notification type
            val verticalPadding = when {
                isCompactScreen && notificationType == "music_post" -> 12.dp
                isCompactScreen -> 16.dp
                notificationType == "music_post" -> 16.dp
                else -> 24.dp
            }

            val horizontalPadding = when {
                screenWidth < 360.dp -> 16.dp
                isWideScreen -> 32.dp
                else -> 20.dp
            }

            val questionSpacing = when {
                isCompactScreen && notificationType == "music_post" -> 16.dp
                isCompactScreen -> 20.dp
                notificationType == "music_post" -> 20.dp
                else -> 24.dp
            }

            val sectionSpacing = when {
                isCompactScreen -> 16.dp
                else -> 24.dp
            }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Main survey content - scrollable
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Title
                    Text(
                        text = if (notificationType == "music_post") "Post-Music Survey" else "Quick Survey",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (notificationType == "music_post")
                            "Please answer these four questions"
                        else
                            "Please answer these two questions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // Add cancel button for non-music_post notifications
                    if (notificationType != "music_post" && !hasSubmitted) {
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = onCancelRecording,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isWideScreen) 48.dp else 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                text = "I do not want to record this session",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(sectionSpacing))

                    // Question 1: Pleasantness
                    SurveyQuestion(
                        question = "How pleasant do you feel right now?",
                        value = pleasantnessValue,
                        onValueChange = { pleasantnessValue = it }
                    )

                    Spacer(modifier = Modifier.height(questionSpacing))

                    // Question 2: Energy
                    SurveyQuestion(
                        question = "How energized do you feel right now?",
                        value = energyValue,
                        onValueChange = { energyValue = it }
                    )

                    // Additional questions for music_post
                    if (notificationType == "music_post") {
                        Spacer(modifier = Modifier.height(questionSpacing))

                        // Question 3: Music Liking
                        SurveyQuestion(
                            question = "How much did you like the music you just heard?",
                            value = musicLikingValue,
                            onValueChange = { musicLikingValue = it }
                        )

                        Spacer(modifier = Modifier.height(questionSpacing))

                        // Question 4: Music Familiarity
                        SurveyQuestion(
                            question = "How familiar was the music you just heard?",
                            leftLabel = "Never heard it",
                            rightLabel = "Know it very well",
                            value = musicFamiliarityValue,
                            onValueChange = { musicFamiliarityValue = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(sectionSpacing))

                    // Submit button
                    Button(
                    onClick = {
                        if (!hasSubmitted) {
                            val response = com.wboelens.polarrecorder.managers.SurveyResponse(
                                pleasantness = pleasantnessValue.toInt(),
                                energy = energyValue.toInt(),
                                notificationType = notificationType,
                                musicLiking = if (notificationType == "music_post") musicLikingValue.toInt() else null,
                                musicFamiliarity = if (notificationType == "music_post") musicFamiliarityValue.toInt() else null
                            )

                            // Store the survey response
                            val baseDir = dataSavers.fileSystem.recordingDir?.parentFile
                            surveyManager.storeSurveyResponse(response, baseDir)

                            // Handle different notification types
                            when (notificationType) {
                                "music_post" -> {
                                    // Mark survey as submitted
                                    PolarRecorderApplication.recordingManager.markMusicPostSurveySubmitted()

                                    // Save survey to recording immediately
                                    val recordingDir = dataSavers.fileSystem.recordingDir
                                    if (recordingDir != null) {
                                        surveyManager.savePostMusicSurveyToRecording(recordingDir)
                                    }

                                    // Stop recording immediately after survey submission
                                    if (PolarRecorderApplication.recordingManager.isRecording.value) {
                                        PolarRecorderApplication.recordingManager.stopMusicPostRecording()
                                    }

                                    // Move app to background after a short delay (only for music_post)
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        onClose()
                                    }, 500) // 500ms delay to show "Submitted ✓" feedback
                                }
                                "random" -> {
                                    // For random notifications: mark survey as submitted
                                    val recordingDir = dataSavers.fileSystem.recordingDir
                                    if (recordingDir != null && PolarRecorderApplication.recordingManager.isRecording.value) {
                                        surveyManager.savePendingSurveyToRecording(recordingDir)
                                        PolarRecorderApplication.recordingManager.markRandomSurveySubmitted()
                                    }
                                }
                                else -> {
                                    // For music_pre: save if recording already active
                                    val recordingDir = dataSavers.fileSystem.recordingDir
                                    if (recordingDir != null && PolarRecorderApplication.recordingManager.isRecording.value) {
                                        surveyManager.savePendingSurveyToRecording(recordingDir)
                                    }
                                }
                            }

                            hasSubmitted = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isWideScreen) 48.dp else 0.dp)
                        .heightIn(min = 48.dp),
                    enabled = !hasSubmitted
                ) {
                    Text(
                        text = if (hasSubmitted) "Submitted ✓" else "Submit",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                    if (hasSubmitted) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Thank you for your response!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = onClose) {
                            Text("Close")
                        }
                    }

                    // Add bottom padding for scrolling
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Status bar at the bottom
                AutoRecordStatusBar(
                    status = status,
                    statusMessage = statusMessage,
                    onNavigateToConnect = onNavigateToConnect
                )
            }
        }
    }
}

@Composable
fun SurveyQuestion(
    question: String,
    leftLabel: String = "Not at all",
    rightLabel: String = "Extremely",
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = question,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .padding(bottom = 8.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = leftLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
                textAlign = TextAlign.Start
            )
            Text(
                text = rightLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
                textAlign = TextAlign.End
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 1f..7f,
            steps = 5, // 7 values total (1-7), so 5 steps between
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )

        Text(
            text = "Current: ${value.toInt()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 4.dp)
        )
    }
}

@Composable
fun AutoRecordStatusBar(
    status: com.wboelens.polarrecorder.viewModels.AutoRecordStatus,
    statusMessage: String,
    onNavigateToConnect: () -> Unit
) {
    val isFailureState = status.name.startsWith("FAILED")
    val backgroundColor = when {
        status == com.wboelens.polarrecorder.viewModels.AutoRecordStatus.RECORDING ->
            MaterialTheme.colorScheme.primaryContainer
        isFailureState ->
            MaterialTheme.colorScheme.errorContainer
        status == com.wboelens.polarrecorder.viewModels.AutoRecordStatus.IDLE ->
            MaterialTheme.colorScheme.surfaceVariant
        else ->
            MaterialTheme.colorScheme.secondaryContainer
    }

    val contentColor = when {
        status == com.wboelens.polarrecorder.viewModels.AutoRecordStatus.RECORDING ->
            MaterialTheme.colorScheme.onPrimaryContainer
        isFailureState ->
            MaterialTheme.colorScheme.onErrorContainer
        else ->
            MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        color = backgroundColor,
        tonalElevation = 3.dp
    ) {
        if (isFailureState) {
            // Clickable button for failed states
            Button(
                onClick = onNavigateToConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = getStatusTitle(status),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    if (statusMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Non-clickable status display
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp)
                ) {
                    Text(
                        text = getStatusTitle(status),
                        style = MaterialTheme.typography.titleMedium,
                        color = contentColor,
                        maxLines = 2
                    )
                    if (statusMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor,
                            maxLines = 2
                        )
                    }
                }

                if (status != com.wboelens.polarrecorder.viewModels.AutoRecordStatus.IDLE &&
                    status != com.wboelens.polarrecorder.viewModels.AutoRecordStatus.RECORDING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = contentColor,
                        strokeWidth = 3.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun getStatusTitle(status: com.wboelens.polarrecorder.viewModels.AutoRecordStatus): String {
    return when (status) {
        com.wboelens.polarrecorder.viewModels.AutoRecordStatus.IDLE -> "Auto-Record Ready"
        com.wboelens.polarrecorder.viewModels.AutoRecordStatus.SCANNING -> "Scanning for Devices"
        com.wboelens.polarrecorder.viewModels.AutoRecordStatus.CONNECTING -> "Connecting to Device"
        com.wboelens.polarrecorder.viewModels.AutoRecordStatus.CONFIGURING -> "Configuring Device"
        com.wboelens.polarrecorder.viewModels.AutoRecordStatus.INITIALIZING_SAVERS -> "Initializing Storage"
        com.wboelens.polarrecorder.viewModels.AutoRecordStatus.RECORDING -> "● Recording Active"
        com.wboelens.polarrecorder.viewModels.AutoRecordStatus.FAILED_SCANNING -> "Connection Failed"
        com.wboelens.polarrecorder.viewModels.AutoRecordStatus.FAILED_CONNECTING -> "Connection Failed"
        com.wboelens.polarrecorder.viewModels.AutoRecordStatus.FAILED_CONFIGURING -> "Configuration Failed"
        com.wboelens.polarrecorder.viewModels.AutoRecordStatus.FAILED_INITIALIZING -> "Initialization Failed"
        com.wboelens.polarrecorder.viewModels.AutoRecordStatus.FAILED_RECORDING -> "Recording Failed"
    }
}
