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

import com.wboelens.polarrecorder.PolarRecorderApplication
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.PermissionManager
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.managers.RecordingManager
import com.wboelens.polarrecorder.managers.SurveyManager
import com.wboelens.polarrecorder.ui.theme.AppTheme
import com.wboelens.polarrecorder.viewModels.AutoRecordStatus
import com.wboelens.polarrecorder.viewModels.AutoRecordViewModel
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import com.wboelens.polarrecorder.viewModels.LogViewModel
import com.google.gson.Gson
import com.wboelens.polarrecorder.database.ResearchSession
import com.wboelens.polarrecorder.database.SurveyResponse
import com.wboelens.polarrecorder.ui.screens.questionnaire.QuestionnaireRepository
import com.wboelens.polarrecorder.ui.screens.questionnaire.QuestionnaireScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

class SurveyActivity : ComponentActivity() {

    private val deviceViewModel: DeviceViewModel
        get() = PolarRecorderApplication.deviceViewModel

    private val logViewModel: LogViewModel
        get() = PolarRecorderApplication.logViewModel

    private val polarManager: PolarManager
        get() = PolarRecorderApplication.polarManager

    private val recordingManager: RecordingManager
        get() = PolarRecorderApplication.recordingManager

    private val preferencesManager: PreferencesManager
        get() = PolarRecorderApplication.preferencesManager

    private val dataSavers: DataSavers
        get() = PolarRecorderApplication.dataSavers

    private val surveyManager: SurveyManager
        get() = PolarRecorderApplication.surveyManager

    private val autoRecordViewModel: AutoRecordViewModel by viewModels()

    private lateinit var permissionManager: PermissionManager

    // Track if this activity started the recording
    private var didStartRecording = false

    // Track if we're currently starting a recording to prevent double-initialization
    private var isStartingRecording = false

    // Notification type from intent (reactive state to handle onNewIntent seamlessly)
    private var notificationTypeState = androidx.compose.runtime.mutableStateOf("music_pre")

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
        notificationTypeState.value = intent.getStringExtra("notification_type") ?: "music_pre"

        // Get track info from intent (if from music notification)
        initialTrackName = intent.getStringExtra("track_name")
        initialTrackArtist = intent.getStringExtra("track_artist")
        initialTrackAlbum = intent.getStringExtra("track_album")
        initialTrackUri = intent.getStringExtra("track_uri")
        initialTrackDuration = intent.getLongExtra("track_duration", -1).takeIf { it >= 0 }
        initialTrackPosition = intent.getLongExtra("track_position", -1).takeIf { it >= 0 }

        Log.d(TAG, "onCreate: Notification type: ${notificationTypeState.value}")
        if (initialTrackName != null) {
            Log.d(TAG, "onCreate: Initial track: $initialTrackName by $initialTrackArtist (Album: $initialTrackAlbum)")
        }

        // Clear any pending survey from previous session
        surveyManager.clearPendingSurvey()

        Log.d(TAG, "onCreate: Initializing SurveyActivity with auto-record")

        // Check if recording was already active
        val wasRecordingActive = recordingManager.isRecording.value
        Log.d(TAG, "Recording was already active: $wasRecordingActive")

        // Only initialize permission manager locally
        permissionManager = PermissionManager(this)

        setContent {
            AppTheme {
                SurveyScreen(
                    notificationType = notificationTypeState.value,
                    autoRecordViewModel = autoRecordViewModel,
                    surveyManager = surveyManager,
                    dataSavers = dataSavers,
                    onClose = { handleClose() },
                    onCancelRecording = { handleCancelRecording() },
                    onNavigateToConnect = {
                        // Navigate to MainActivity to connect devices
                        val intent = Intent(this, com.wboelens.polarrecorder.MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }

        // For music_post, don't start auto-record (recording already active)
        if (notificationTypeState.value == "music_post") {
            Log.d(TAG, "Music post notification - skipping auto-record (recording already active)")
            return
        }

        // Start auto-record flow immediately in background while user fills survey
        permissionManager.checkAndRequestPermissions {
            Log.d(TAG, "Permissions granted, starting auto-record flow")

            // Check if recording was already active
            val currentWasRecordingActive = recordingManager.isRecording.value

            // If recording was not already active, mark that we started it
            if (!currentWasRecordingActive) {
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
                notificationTypeState.value
            )

            // Start random notification timeout if this is a random notification
            if (notificationTypeState.value == "random") {
                recordingManager.startRandomTimeout()
            }

            // Clear the flag after a delay to allow the flow to complete
            android.os.Handler(mainLooper).postDelayed({
                isStartingRecording = false
            }, 5000) // 500ms delay to show "Submitted ✓" feedback
        }
    }

    private fun handleClose() {
        if (notificationTypeState.value == "random") {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        notificationTypeState.value = intent.getStringExtra("notification_type") ?: "music_pre"
        
        initialTrackName = intent.getStringExtra("track_name")
        initialTrackArtist = intent.getStringExtra("track_artist")
        initialTrackAlbum = intent.getStringExtra("track_album")
        initialTrackUri = intent.getStringExtra("track_uri")
        initialTrackDuration = intent.getLongExtra("track_duration", -1).takeIf { it >= 0 }
        initialTrackPosition = intent.getLongExtra("track_position", -1).takeIf { it >= 0 }
        
        Log.d(TAG, "onNewIntent: Reused existing activity. Notification type updated to: ${notificationTypeState.value}")
    }
}

@Composable
fun SurveyScreen(
    notificationType: String,
    autoRecordViewModel: AutoRecordViewModel,
    surveyManager: SurveyManager,
    dataSavers: DataSavers,
    onClose: () -> Unit,
    onCancelRecording: () -> Unit,
    onNavigateToConnect: () -> Unit
) {
    val status by autoRecordViewModel.status.collectAsState()
    val statusMessage by autoRecordViewModel.statusMessage.collectAsState()
    val context = LocalContext.current

    val questionnaireFileName = when (notificationType) {
        "music_pre" -> "music_pre.json"
        "music_post" -> "music_post.json"
        "random" -> "daily_mood.json"
        else -> "music_pre.json"
    }

    val questionnaire = remember(questionnaireFileName) {
        QuestionnaireRepository.load(context, questionnaireFileName)
    }

    // Mark music_post survey as opened (cancel notification timeout)
    LaunchedEffect(notificationType) {
        if (notificationType == "music_post") {
            PolarRecorderApplication.recordingManager.markMusicPostSurveyOpened()
            com.wboelens.polarrecorder.utils.NotificationHelper.cancelMusicPostNotification(
                PolarRecorderApplication.instance
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Status Bar at the Top (if recording)
        if (status != AutoRecordStatus.IDLE) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 24.dp) // Offset for status bar
            ) {
                AutoRecordStatusBar(
                    status = status,
                    statusMessage = statusMessage,
                    onNavigateToConnect = onNavigateToConnect
                )
            }
        }

        // Questionnaire below
        if (questionnaire != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (status != AutoRecordStatus.IDLE) 96.dp else 0.dp)
            ) {
                QuestionnaireScreen(
                    questionnaire = questionnaire,
                    onBack = onCancelRecording, // Mapping back to cancel recording for ambulatory
                    onSubmitted = { answers ->
                        // Persist to encrypted Room database
                        val db = PolarRecorderApplication.syraDatabase
                        val gson = Gson()
                        val preferencesManager = PolarRecorderApplication.preferencesManager
                        val sessionUuid = preferencesManager.participantId + "_" + System.currentTimeMillis()

                        kotlinx.coroutines.MainScope().launch {
                            withContext(Dispatchers.IO) {
                                // Insert a dummy session to map the ambulatory response
                                val session = ResearchSession(
                                    sessionUuid = sessionUuid,
                                    participantId = preferencesManager.participantId,
                                    studyMode = preferencesManager.studyMode,
                                    startTime = System.currentTimeMillis(),
                                    endTime = System.currentTimeMillis()
                                )
                                db.sessionDao().insert(session)

                                val promptTypeStr = when (notificationType) {
                                    "music_pre" -> "PRE"
                                    "music_post" -> "POST"
                                    "random" -> "RANDOM"
                                    else -> "BASELINE"
                                }

                                db.surveyResponseDao().insert(
                                    SurveyResponse(
                                        sessionUuid = sessionUuid,
                                        timestamp = System.currentTimeMillis(),
                                        surveyId = questionnaire.questionnaireId,
                                        promptType = promptTypeStr,
                                        responsesJson = gson.toJson(answers)
                                    )
                                )
                            }
                        }

                        // Mark RecordingManager states
                        when (notificationType) {
                            "music_post" -> {
                                PolarRecorderApplication.recordingManager.markMusicPostSurveySubmitted()
                                if (PolarRecorderApplication.recordingManager.isRecording.value) {
                                    PolarRecorderApplication.recordingManager.stopMusicPostRecording()
                                }
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ onClose() }, 500)
                            }
                            "random" -> {
                                PolarRecorderApplication.recordingManager.markRandomSurveySubmitted()
                                onClose()
                            }
                            else -> {
                                onClose()
                            }
                        }
                    }
                )
            }
        }
    }
}
@Composable
fun AutoRecordStatusBar(
    status: AutoRecordStatus,
    statusMessage: String,
    onNavigateToConnect: () -> Unit
) {
    val isFailureState = status.name.startsWith("FAILED")
    val backgroundColor = when {
        status == AutoRecordStatus.RECORDING ->
            MaterialTheme.colorScheme.primaryContainer
        isFailureState ->
            MaterialTheme.colorScheme.errorContainer
        status == AutoRecordStatus.IDLE ->
            MaterialTheme.colorScheme.surfaceVariant
        else ->
            MaterialTheme.colorScheme.secondaryContainer
    }

    val contentColor = when {
        status == AutoRecordStatus.RECORDING ->
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

                if (status != AutoRecordStatus.IDLE &&
                    status != AutoRecordStatus.RECORDING) {
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
private fun getStatusTitle(status: AutoRecordStatus): String {
    return when (status) {
        AutoRecordStatus.IDLE -> "Auto-Record Ready"
        AutoRecordStatus.SCANNING -> "Scanning for Devices"
        AutoRecordStatus.CONNECTING -> "Connecting to Device"
        AutoRecordStatus.CONFIGURING -> "Configuring Device"
        AutoRecordStatus.INITIALIZING_SAVERS -> "Initializing Storage"
        AutoRecordStatus.RECORDING -> "● Recording Active"
        AutoRecordStatus.FAILED_SCANNING -> "Connection Failed"
        AutoRecordStatus.FAILED_CONNECTING -> "Connection Failed"
        AutoRecordStatus.FAILED_CONFIGURING -> "Configuration Failed"
        AutoRecordStatus.FAILED_INITIALIZING -> "Initialization Failed"
        AutoRecordStatus.FAILED_RECORDING -> "Recording Failed"
    }
}
