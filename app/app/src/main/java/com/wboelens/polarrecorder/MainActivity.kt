package com.wboelens.polarrecorder

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.gson.Gson
import com.wboelens.polarrecorder.database.ResearchSession
import com.wboelens.polarrecorder.database.SurveyResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.MediaPlaybackManager
import com.wboelens.polarrecorder.managers.PermissionManager
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.managers.RecordingManager

import com.wboelens.polarrecorder.ui.components.LogMessageSnackbarHost
import com.wboelens.polarrecorder.ui.components.SnackbarMessageDisplayer
import com.wboelens.polarrecorder.ui.screens.DataSaverInitializationScreen
import com.wboelens.polarrecorder.ui.screens.DeviceConnectionScreen
import com.wboelens.polarrecorder.ui.screens.DeviceSelectionScreen
import com.wboelens.polarrecorder.ui.screens.HomeScreen
import com.wboelens.polarrecorder.ui.screens.RecordingScreen
import com.wboelens.polarrecorder.ui.screens.RecordingSettingsScreen
import com.wboelens.polarrecorder.ui.screens.SetupScreen
import com.wboelens.polarrecorder.ui.screens.onboarding.CONSENT_VERSION
import com.wboelens.polarrecorder.ui.screens.onboarding.EthicalConsentScreen
import com.wboelens.polarrecorder.ui.screens.onboarding.ParticipantIdScreen
import com.wboelens.polarrecorder.ui.screens.questionnaire.QuestionnaireListScreen
import com.wboelens.polarrecorder.ui.screens.questionnaire.QuestionnaireRepository
import com.wboelens.polarrecorder.ui.screens.questionnaire.QuestionnaireScreen
import com.wboelens.polarrecorder.ui.theme.AppTheme
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import com.wboelens.polarrecorder.viewModels.FileSystemSettingsViewModel
import com.wboelens.polarrecorder.viewModels.LogViewModel

// ─── Route constants ──────────────────────────────────────────────────────────
private object Route {
    const val CONSENT       = "consent"
    const val PARTICIPANT_ID= "participantId"
    const val HOME          = "home"
    const val SETUP         = "setup"
    const val DEVICE_SELECT = "deviceSelection"
    const val DEVICE_CONNECT= "deviceConnection"
    const val REC_SETTINGS  = "recordingSettings"
    const val DATASAVER_INIT= "dataSaverInitialization"
    const val RECORDING     = "recording"
    const val QUESTIONNAIRES= "questionnaires"
    const val QUESTIONNAIRE = "questionnaire/{fileName}"
    fun questionnaireDetail(fileName: String) = "questionnaire/$fileName"
}

class MainActivity : ComponentActivity() {

    // ── Shared instances from Application ─────────────────────────────────────
    private val deviceViewModel: DeviceViewModel
        get() = PolarRecorderApplication.deviceViewModel

    private val logViewModel: LogViewModel
        get() = PolarRecorderApplication.logViewModel

    private val fileSystemViewModel: FileSystemSettingsViewModel by viewModels()

    private val polarManager: PolarManager
        get() = PolarRecorderApplication.polarManager

    private val recordingManager: RecordingManager
        get() = PolarRecorderApplication.recordingManager

    private val preferencesManager: PreferencesManager
        get() = PolarRecorderApplication.preferencesManager

    private val dataSavers: DataSavers
        get() = PolarRecorderApplication.dataSavers

    private val mediaPlaybackManager: MediaPlaybackManager
        get() = PolarRecorderApplication.mediaPlaybackManager

    private lateinit var permissionManager: PermissionManager

    private val directoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            fileSystemViewModel.handleDirectoryResult(this, result.data?.data)
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Initializing MainActivity")

        permissionManager = PermissionManager(this)

        setContent {
            AppTheme {
                val navController = rememberNavController()

                // ── Determine start destination ────────────────────────────
                val startDestination = if (preferencesManager.onboardingComplete) {
                    Route.HOME
                } else {
                    Route.CONSENT
                }

                val (snackbarHostState, currentLogType) =
                    SnackbarMessageDisplayer(logViewModel = logViewModel)

                Scaffold(
                    snackbarHost = {
                        LogMessageSnackbarHost(snackbarHostState, currentLogType)
                    },
                ) { paddingValues ->
                    NavHost(
                        navController    = navController,
                        startDestination = startDestination,
                        modifier         = Modifier.padding(paddingValues),
                    ) {

                        // ── Onboarding: Ethical Consent ────────────────────
                        composable(Route.CONSENT) {
                            EthicalConsentScreen(
                                onConsentAccepted = {
                                    navController.navigate(Route.PARTICIPANT_ID)
                                },
                            )
                        }

                        // ── Onboarding: Participant ID entry ───────────────
                        composable(Route.PARTICIPANT_ID) {
                            ParticipantIdScreen(
                                onIdConfirmed = { participantId ->
                                    // Atomically write both values + mark onboarding done
                                    preferencesManager.completeOnboarding(
                                        participantId  = participantId,
                                        consentVersion = CONSENT_VERSION,
                                    )
                                    Log.d(TAG, "Onboarding complete — participantId=$participantId")
                                    navController.navigate(Route.HOME) {
                                        popUpTo(Route.CONSENT) { inclusive = true }
                                    }
                                },
                            )
                        }

                        // ── Home ──────────────────────────────────────────────────
                        composable(Route.HOME) {
                            androidx.compose.runtime.LaunchedEffect(Unit) {
                                permissionManager.checkAndRequestPermissions {
                                    Log.d(TAG, "Permissions checked/granted on Home screen")
                                }
                            }
                            HomeScreen(
                                participantId              = preferencesManager.participantId,
                                studyMode                  = preferencesManager.studyMode,
                                notificationAccessGranted  = mediaPlaybackManager.isNotificationAccessGranted(),
                                deviceViewModel            = deviceViewModel,
                                onNavigateToSetup          = { navController.navigate(Route.SETUP) },
                                onNavigateToQuestionnaires = { navController.navigate(Route.QUESTIONNAIRES) },
                                onNavigateToRecording      = {
                                    navController.navigate(Route.REC_SETTINGS)
                                },
                            )
                        }

                        // ── Setup ────────────────────────────────────────────────
                        composable(Route.SETUP) {
                            SetupScreen(
                                preferencesManager            = preferencesManager,
                                deviceViewModel               = deviceViewModel,
                                onBack                        = { navController.navigateUp() },
                                onNavigateToSensorPairing     = { navController.navigate(Route.DEVICE_SELECT) },
                                onNavigateToRecordingSettings = { navController.navigate(Route.REC_SETTINGS) },
                                onExportData                  = {
                                    PolarRecorderApplication.exportManager.exportToZip()
                                },
                            )
                        }

                        // ── Sensor: Device selection ───────────────────────
                        composable(Route.DEVICE_SELECT) {
                            permissionManager.checkAndRequestPermissions {
                                Log.d(TAG, "BLE permissions granted — starting scan")
                                polarManager.startPeriodicScanning()
                            }
                            DeviceSelectionScreen(
                                deviceViewModel = deviceViewModel,
                                polarManager    = polarManager,
                                onContinue      = { navController.navigate(Route.DEVICE_CONNECT) },
                            )
                        }

                        // ── Sensor: Device connection ──────────────────────
                        composable(Route.DEVICE_CONNECT) {
                            DeviceConnectionScreen(
                                deviceViewModel = deviceViewModel,
                                polarManager    = polarManager,
                                onBackPressed   = { navController.navigateUp() },
                                onContinue      = { navController.navigate(Route.REC_SETTINGS) },
                            )
                        }

                        // ── Recording settings ─────────────────────────────
                        composable(Route.REC_SETTINGS) {
                            RecordingSettingsScreen(
                                deviceViewModel            = deviceViewModel,
                                fileSystemSettingsViewModel= fileSystemViewModel,
                                dataSavers                 = dataSavers,
                                preferencesManager         = preferencesManager,
                                polarManager               = polarManager,
                                onBackPressed              = { navController.navigateUp() },
                                onNavigateToDeviceSelection = {
                                    navController.navigate(Route.DEVICE_SELECT) {
                                        popUpTo(Route.DEVICE_SELECT) { inclusive = true }
                                    }
                                },
                                onContinue = { navController.navigate(Route.DATASAVER_INIT) },
                            )
                        }

                        // ── Data saver initialization ──────────────────────
                        composable(Route.DATASAVER_INIT) {
                            DataSaverInitializationScreen(
                                dataSavers        = dataSavers,
                                deviceViewModel   = deviceViewModel,
                                recordingManager  = recordingManager,
                                preferencesManager= preferencesManager,
                                onBackPressed     = { navController.navigateUp() },
                                onContinue        = { navController.navigate(Route.RECORDING) },
                            )
                        }

                        // ── Recording ──────────────────────────────────────
                        composable(Route.RECORDING) {
                            val backAction = {
                                if (recordingManager.isRecording.value) {
                                    recordingManager.stopRecording()
                                }
                                navController.navigate(Route.HOME) {
                                    popUpTo(Route.HOME) { inclusive = true }
                                }
                            }
                            BackHandler(onBack = backAction)
                            RecordingScreen(
                                deviceViewModel   = deviceViewModel,
                                recordingManager  = recordingManager,
                                dataSavers        = dataSavers,
                                onBackPressed     = backAction,
                                onRestartRecording= { navController.navigate(Route.DATASAVER_INIT) },
                            )
                        }

                        // ── Questionnaire list ─────────────────────────────
                        composable(Route.QUESTIONNAIRES) {
                            QuestionnaireListScreen(
                                onBack     = { navController.navigateUp() },
                                onSelected = { fileName ->
                                    navController.navigate(Route.questionnaireDetail(fileName))
                                },
                            )
                        }

                        // ── Questionnaire detail ─────────────────────────────────
                        composable(Route.QUESTIONNAIRE) { backStackEntry ->
                            val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
                            val questionnaire = remember(fileName) {
                                QuestionnaireRepository.load(applicationContext, fileName)
                            }
                            if (questionnaire != null) {
                                QuestionnaireScreen(
                                    questionnaire = questionnaire,
                                    onBack        = { navController.navigateUp() },
                                    onSubmitted   = { answers ->
                                        Log.d(TAG, "Survey submitted: $fileName, ${answers.size} answers")
                                        // Persist to encrypted Room database
                                        val db = PolarRecorderApplication.syraDatabase
                                        val gson = Gson()
                                        val sessionUuid = preferencesManager.participantId +
                                            "_" + System.currentTimeMillis()
                                        kotlinx.coroutines.MainScope().launch {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                val session = ResearchSession(
                                                    sessionUuid   = sessionUuid,
                                                    participantId = preferencesManager.participantId,
                                                    studyMode     = preferencesManager.studyMode,
                                                    startTime     = System.currentTimeMillis(),
                                                    endTime       = System.currentTimeMillis(),
                                                )
                                                db.sessionDao().insert(session)
                                                db.surveyResponseDao().insert(
                                                    SurveyResponse(
                                                        sessionUuid   = sessionUuid,
                                                        timestamp     = System.currentTimeMillis(),
                                                        surveyId      = questionnaire.questionnaireId,
                                                        promptType    = "BASELINE",
                                                        responsesJson = gson.toJson(answers),
                                                    )
                                                )
                                            }
                                        }
                                        navController.navigateUp()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        polarManager.cleanup()
        recordingManager.cleanup()
    }
}
