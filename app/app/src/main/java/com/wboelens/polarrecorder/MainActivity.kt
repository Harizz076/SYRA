package com.wboelens.polarrecorder

import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.dataSavers.InitializationState
import com.wboelens.polarrecorder.managers.DeviceInfoForDataSaver
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
import com.wboelens.polarrecorder.ui.screens.RecordingScreen
import com.wboelens.polarrecorder.ui.screens.RecordingSettingsScreen
import com.wboelens.polarrecorder.ui.theme.AppTheme
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import com.wboelens.polarrecorder.viewModels.FileSystemSettingsViewModel
import com.wboelens.polarrecorder.viewModels.LogViewModel



class MainActivity : ComponentActivity() {

  // Use shared instances from Application
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
    private const val TAG = "PolarManager"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate: Initializing MainActivity")



    // Start Survey notification service
    com.wboelens.polarrecorder.services.SurveyNotificationService.start(applicationContext)

    permissionManager = PermissionManager(this)


    setContent {
      AppTheme {
        // Show main app content directly
        val navController = rememberNavController()

          // Get the snackbarHostState from the ErrorHandler
          val (snackbarHostState, currentLogType) =
              SnackbarMessageDisplayer(logViewModel = logViewModel)

          LaunchedEffect(Unit) {
            permissionManager.checkAndRequestPermissions {
              Log.d(TAG, "Necessary permissions for scanning granted")
              if (navController.currentDestination?.route == "deviceSelection") {
                polarManager.startPeriodicScanning()
              }
            }
          }


          Scaffold(
              snackbarHost = {
                LogMessageSnackbarHost(
                    snackbarHostState,
                    currentLogType
                )
              }
          ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = "deviceSelection",
                modifier = Modifier.padding(paddingValues),
            ) {
              composable("deviceSelection") {
                DeviceSelectionScreen(
                    deviceViewModel = deviceViewModel,
                    polarManager = polarManager,
                    onContinue = { navController.navigate("deviceConnection") },
                )
              }
              composable("deviceConnection") {
                DeviceConnectionScreen(
                    deviceViewModel = deviceViewModel,
                    polarManager = polarManager,
                    onBackPressed = { navController.navigateUp() },
                    onContinue = {
                      // Skip device settings screen - data types are auto-selected
                      navController.navigate("recordingSettings")
                    },
                )
              }
              composable("recordingSettings") {
                RecordingSettingsScreen(
                    deviceViewModel = deviceViewModel,
                    fileSystemSettingsViewModel = fileSystemViewModel,
                    dataSavers = dataSavers,
                    preferencesManager = preferencesManager,
                    polarManager = polarManager,
                    onBackPressed = { navController.navigateUp() },
                    onNavigateToDeviceSelection = {
                      // Navigate back to device selection and clear the back stack
                      navController.navigate("deviceSelection") {
                        popUpTo("deviceSelection") { inclusive = true }
                      }
                    },
                    onContinue = { navController.navigate("dataSaverInitialization") },
                )
              }
              composable("dataSaverInitialization") {
                DataSaverInitializationScreen(
                    dataSavers = dataSavers,
                    deviceViewModel = deviceViewModel,
                    recordingManager = recordingManager,
                    preferencesManager = preferencesManager,
                    onBackPressed = { navController.navigateUp() },
                    onContinue = { navController.navigate("recording") },
                )
              }
              composable("recording") {
                // skip data saver initialisation screen
                val backAction = {
                  if (recordingManager.isRecording.value) {
                    recordingManager.stopRecording()
                  }
                  navController.navigate("recordingSettings") {
                    popUpTo("recordingSettings") { inclusive = true }
                  }
                }

                BackHandler(onBack = backAction)
                RecordingScreen(
                    deviceViewModel = deviceViewModel,
                    recordingManager = recordingManager,
                    dataSavers = dataSavers,
                    onBackPressed = backAction,
                    onRestartRecording = { navController.navigate("dataSaverInitialization") },
                )
              }
            }
          }
        }
      }
    }

  /* 
   * autoStartRecording() was fully replaced by the flow in `setContent` that launches `SurveyActivity(music_pre)`.
   * The `SurveyActivity` is responsible for initializing the `AutoRecordViewModel` and actually 
   * starting the recording, to prevent it from bypassing the pre-session survey.
   * Keeping it removed to clean up dead code. 
   */



  override fun onDestroy() {
    super.onDestroy()
    polarManager.cleanup()
    recordingManager.cleanup()
  }
}

