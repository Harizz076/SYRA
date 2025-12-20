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
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.dataSavers.InitializationState
import com.wboelens.polarrecorder.managers.DeviceInfoForDataSaver
import com.wboelens.polarrecorder.managers.PermissionManager
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.managers.RecordingManager
import com.wboelens.polarrecorder.managers.SpotifyManager
import com.wboelens.polarrecorder.receivers.SpotifyBroadcastReceiver
import com.wboelens.polarrecorder.ui.components.LogMessageSnackbarHost
import com.wboelens.polarrecorder.ui.components.SnackbarMessageDisplayer
import com.wboelens.polarrecorder.ui.screens.DataSaverInitializationScreen
import com.wboelens.polarrecorder.ui.screens.DeviceConnectionScreen
import com.wboelens.polarrecorder.ui.screens.DeviceSelectionScreen
import com.wboelens.polarrecorder.ui.screens.RecordingScreen
import com.wboelens.polarrecorder.ui.screens.RecordingSettingsScreen
import com.wboelens.polarrecorder.ui.screens.SplashScreen
import com.wboelens.polarrecorder.ui.theme.AppTheme
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import com.wboelens.polarrecorder.viewModels.FileSystemSettingsViewModel
import com.wboelens.polarrecorder.viewModels.LogViewModel

class MainActivity : ComponentActivity() {
  // Spotify App Remote SDK
  private val clientId = "3c52f0046fa342baa89e7b66004177f8"
  private val redirectUri = "com.wboelens.polarrecorder://callback"
  private var spotifyAppRemote: SpotifyAppRemote? = null
  private var isConnectingToSpotify = false
  private lateinit var spotifyManager: SpotifyManager
  private val spotifyBroadcastReceiver = SpotifyBroadcastReceiver()

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

    // Get shared spotifyManager from Application
    this.spotifyManager = PolarRecorderApplication.spotifyManager

    // TODO: Re-enable auto-recording later - commented out for now to focus on manual recording
    // spotifyManager.onPlaybackStarted = {
    //   if (!recordingManager.isRecording.value) {
    //     Log.d(TAG, "Auto-starting recording due to Spotify playback")
    //     autoStartRecording()
    //   }
    // }

    // TODO: Re-enable device connection auto-recording later
    // deviceViewModel.connectedDevices.observe(this) { devices ->
    //   if (devices.isNotEmpty() &&
    //       !recordingManager.isRecording.value &&
    //       spotifyManager != null) {
    //
    //     if (spotifyAppRemote != null) {
    //       Log.d(TAG, "Polar device connected and Spotify active - attempting auto-start")
    //       android.os.Handler(mainLooper).postDelayed({
    //         autoStartRecording()
    //       }, 2000)
    //     }
    //   }
    // }

    // Register Spotify broadcast receiver
    val filter = IntentFilter(SpotifyBroadcastReceiver.SPOTIFY_ACTIVE_ACTION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(spotifyBroadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
    } else {
      @Suppress("UnspecifiedRegisterReceiverFlag")
      registerReceiver(spotifyBroadcastReceiver, filter)
    }

    // Set callback for when Spotify becomes active
    SpotifyBroadcastReceiver.onSpotifyActive = {
      Log.d(TAG, "Spotify broadcast received - attempting reconnection if needed")
      logViewModel.addLogMessage("Spotify activity detected")
    }

    // Start Spotify monitoring service only if auto-recording is enabled
    if (preferencesManager.autoRecordingEnabled) {
      com.wboelens.polarrecorder.services.SpotifyMonitorService.start(applicationContext)
    }

    // Start Survey notification service
    com.wboelens.polarrecorder.services.SurveyNotificationService.start(applicationContext)

    permissionManager = PermissionManager(this)


    setContent {
      AppTheme {
        // State to control splash screen visibility
        var showSplash by remember { mutableStateOf(true) }

        if (showSplash) {
          // Show splash screen with video
          SplashScreen(
              onVideoComplete = {
                showSplash = false
              }
          )
        } else {
          // Show main app content after splash
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

          // Check if recording is active on startup and navigate to recording screen
          val isRecording by recordingManager.isRecording.collectAsState(initial = false)

          LaunchedEffect(isRecording) {
            // Navigate to recording screen if recording becomes active and we're not already there
            if (isRecording && navController.currentDestination?.route !in listOf(
                  "recording",
                  "dataSaverInitialization"
              )) {
              navController.navigate("recording") {
                popUpTo("deviceSelection") { inclusive = false }
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
  }
  override fun onStart() {
    super.onStart()
    Log.d(TAG, "========== onStart CALLED ==========")
    Log.d(TAG, "onStart - Activity started")
    Log.d(TAG, "onStart - spotifyAppRemote: ${if (spotifyAppRemote != null) "CONNECTED" else "NULL"}")
    Log.d(TAG, "onStart - isConnectingToSpotify: $isConnectingToSpotify")

    // Only connect to Spotify if not already connected or connecting
    if (spotifyAppRemote == null && !isConnectingToSpotify) {
      Log.d(TAG, "onStart - Conditions met, calling connectToSpotify()")
      connectToSpotify()
    } else {
      Log.d(TAG, "onStart - Skipping Spotify connection (already connected or connecting)")
    }
    Log.d(TAG, "====================================")
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)

    Log.d(TAG, "========== onNewIntent CALLED ==========")
    Log.d(TAG, "onNewIntent - Intent action: ${intent.action}")
    Log.d(TAG, "onNewIntent - Intent data: ${intent.data}")
    Log.d(TAG, "onNewIntent - Scheme: ${intent.data?.scheme}, Host: ${intent.data?.host}")
    Log.d(TAG, "onNewIntent - isConnectingToSpotify: $isConnectingToSpotify")
    Log.d(TAG, "onNewIntent - spotifyAppRemote: ${if (spotifyAppRemote != null) "CONNECTED" else "NULL"}")

    // Handle the redirect from Spotify authorization
    if (intent.data?.scheme == "com.wboelens.polarrecorder" && intent.data?.host == "callback") {
      Log.d(TAG, "✓ Received authorization callback from Spotify")
      Log.d(TAG, "→ Resetting isConnectingToSpotify = false")

      // Reset the flag
      isConnectingToSpotify = false
      logViewModel.addLogMessage("Authorization callback received, connecting...")

      // Wait a moment then retry connection - THIS CAUSES THE LOOP BUT AT LEAST IT WORKS
      Log.d(TAG, "→ Scheduling retry connection in 500ms")
      android.os.Handler(mainLooper).postDelayed({
        Log.d(TAG, "→ Retry: Calling connectToSpotify() after auth callback")
        connectToSpotify()
      }, 500)
    } else {
      Log.d(TAG, "✗ Intent does NOT match Spotify callback pattern")
    }
    Log.d(TAG, "========================================")
  }

  private fun connectToSpotify() {
    Log.d(TAG, "========== connectToSpotify CALLED ==========")

    // First check if Spotify is installed
    if (!isSpotifyInstalled()) {
      Log.w(TAG, "✗ Spotify app is not installed")
      logViewModel.addLogMessage("Spotify app is not installed. Please install Spotify to use this feature.")
      Log.d(TAG, "=============================================")
      return
    }

    Log.d(TAG, "✓ Spotify app is installed")
    Log.d(TAG, "Setting isConnectingToSpotify = true")
    isConnectingToSpotify = true

    // Only show auth view if not previously authorized
    val showAuth = !preferencesManager.spotifyAuthorized
    Log.d(TAG, "showAuthView = $showAuth (spotifyAuthorized = ${preferencesManager.spotifyAuthorized})")

    // Set the connection parameters
    val connectionParams = ConnectionParams.Builder(clientId)
        .setRedirectUri(redirectUri)
        .showAuthView(showAuth)
        .build()

    Log.d(TAG, "Calling SpotifyAppRemote.connect() with showAuthView=$showAuth...")

    // Connect to Spotify App Remote
    SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
        override fun onConnected(appRemote: SpotifyAppRemote) {
            Log.d(TAG, "========== onConnected CALLBACK ==========")
            spotifyAppRemote = appRemote
            isConnectingToSpotify = false

            // Mark as authorized on successful connection
            preferencesManager.spotifyAuthorized = true

            Log.d(TAG, "✓ Connected to Spotify!")
            Log.d(TAG, "Setting isConnectingToSpotify = false")
            Log.d(TAG, "Setting spotifyAuthorized = true")
            logViewModel.addLogMessage("Connected to Spotify!")
            connected()
            Log.d(TAG, "==========================================")
        }

        override fun onFailure(throwable: Throwable) {
            Log.d(TAG, "========== onFailure CALLBACK ==========")
            Log.e(TAG, "✗ Spotify connection failed: ${throwable.message}", throwable)

            // Check if it's an authorization error
            when {
                throwable.message?.contains("authorization", ignoreCase = true) == true ||
                throwable.message?.contains("UserNotAuthorizedException", ignoreCase = true) == true -> {
                    Log.d(TAG, "→ Authorization required - auth flow should have opened")
                    Log.d(TAG, "→ Keeping isConnectingToSpotify = true during auth")

                    // Reset authorization flag since it failed
                    preferencesManager.spotifyAuthorized = false

                    // If we weren't showing auth view, retry with auth view
                    if (!showAuth) {
                        Log.d(TAG, "→ Retrying with showAuthView=true")
                        isConnectingToSpotify = false
                        preferencesManager.spotifyAuthorized = false
                        android.os.Handler(mainLooper).postDelayed({
                            connectToSpotify()
                        }, 500)
                    } else {
                        logViewModel.addLogMessage("Please authorize app in browser...")
                        // Keep isConnectingToSpotify = true - auth flow is in progress
                    }
                }
                throwable.message?.contains("not installed", ignoreCase = true) == true -> {
                    Log.d(TAG, "→ Spotify not installed")
                    isConnectingToSpotify = false
                    logViewModel.addLogMessage("Spotify app not found. Please install Spotify.")
                }
                else -> {
                    Log.d(TAG, "→ Other error")
                    isConnectingToSpotify = false
                    logViewModel.addLogMessage("Spotify connection failed: ${throwable.message}")
                }
            }
            Log.d(TAG, "========================================")
        }
    })
    Log.d(TAG, "=============================================")
  }

  private fun isSpotifyInstalled(): Boolean {
    return try {
      packageManager.getPackageInfo("com.spotify.music", 0)
      true
    } catch (e: PackageManager.NameNotFoundException) {
      false
    }
  }

  private fun connected() {
    Log.d(TAG, "connected: Spotify connected")

    // Set the SpotifyAppRemote in SpotifyManager
    spotifyManager.setSpotifyAppRemote(spotifyAppRemote)
  }

  private fun autoStartRecording() {
    Log.d(TAG, "========== autoStartRecording CALLED ==========")

    // Get connected Polar devices FIRST
    val connectedDevices = deviceViewModel.connectedDevices.value ?: emptyList()
    Log.d(TAG, "Connected Polar devices: ${connectedDevices.size}")

    // CRITICAL: Must have at least one Polar device connected
    if (connectedDevices.isEmpty()) {
      Log.w(TAG, "Cannot auto-start recording: No Polar devices connected yet")
      logViewModel.addLogMessage("Waiting for Polar devices to connect before starting recording...")
      return
    }

    // Check if file system is configured
    if (!dataSavers.fileSystem.isConfigured) {
      Log.w(TAG, "Cannot auto-start recording: File system not configured")
      logViewModel.addLogMessage("Cannot auto-start recording: Please configure file system storage first")
      return
    }

    // Enable file system saver if not already enabled
    if (!dataSavers.fileSystem.isEnabled.value) {
      dataSavers.fileSystem.enable()
    }

    // Check if already recording
    if (recordingManager.isRecording.value) {
      Log.d(TAG, "Already recording, skipping auto-start")
      return
    }

    // Get or create recording name with timestamp
    val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
        .format(java.util.Date())
    val recordingName = if (preferencesManager.recordingNameAppendTimestamp) {
      "${preferencesManager.recordingName}_$timestamp"
    } else {
      preferencesManager.recordingName
    }

    recordingManager.currentRecordingName = recordingName

    // Build device info map including all connected Polar devices + Spotify
    val deviceIdsWithInfo = mutableMapOf<String, DeviceInfoForDataSaver>()

    // Add all connected Polar devices
    connectedDevices.forEach { device ->
      val dataTypesWithLog = deviceViewModel.getDeviceDataTypes(device.info.deviceId)
          .map { it.name }
          .toMutableList()
      dataTypesWithLog.add("LOG")

      Log.d(TAG, "Device ${device.info.deviceId} data types: $dataTypesWithLog")

      deviceIdsWithInfo[device.info.deviceId] = DeviceInfoForDataSaver(
          deviceName = device.info.name,
          dataTypes = dataTypesWithLog.toSet()
      )
    }

    // Add Spotify
    if (preferencesManager.spotifyEnabled) {
      deviceIdsWithInfo["spotify"] = DeviceInfoForDataSaver(
          deviceName = "Spotify",
          dataTypes = setOf("track_info")
      )
    }

    Log.d(TAG, "Initializing data savers for ${deviceIdsWithInfo.size} devices")

    // Initialize data savers
    dataSavers.asList().filter { it.isEnabled.value }.forEach { saver ->
      saver.initSaving(recordingName, deviceIdsWithInfo)
    }

    // Wait for initialization to complete, then start recording
    android.os.Handler(mainLooper).postDelayed({
      val allInitialized = dataSavers.asList()
          .filter { it.isEnabled.value }
          .all { it.isInitialized.value == InitializationState.SUCCESS }

      if (allInitialized) {
        // Ensure selected devices match connected devices
        if (connectedDevices.isNotEmpty()) {
          deviceViewModel.selectDevices(connectedDevices.map { it.info.deviceId }.toSet())
        }

        recordingManager.startRecording()
        Log.d(TAG, "Auto-recording started successfully with ${connectedDevices.size} Polar devices + Spotify")
        logViewModel.addLogMessage("Auto-recording started: $recordingName (${connectedDevices.size} sensors + Spotify)")
      } else {
        Log.e(TAG, "Failed to initialize data savers for auto-recording")
        logViewModel.addLogError("Failed to auto-start recording: initialization failed")
      }
    }, 1000)

    Log.d(TAG, "===============================================")
  }

  override fun onStop() {
    super.onStop()
    Log.d(TAG, "========== onStop CALLED ==========")
    Log.d(TAG, "onStop - Activity stopped")
    Log.d(TAG, "onStop - isConnectingToSpotify: $isConnectingToSpotify")
    Log.d(TAG, "onStop - spotifyAppRemote: ${if (spotifyAppRemote != null) "CONNECTED" else "NULL"}")

    // Check if recording is in progress
    val isRecording = recordingManager.isRecording.value
    Log.d(TAG, "onStop - isRecording: $isRecording")

    // Only disconnect if we're not in the middle of connecting (authorization flow)
    // AND we're not currently recording (need to keep Spotify connection for tracking)
    if (!isConnectingToSpotify && !isRecording) {
      spotifyAppRemote?.let {
        Log.d(TAG, "onStop - Disconnecting from Spotify (not recording)")
        SpotifyAppRemote.disconnect(it)
        spotifyAppRemote = null
        spotifyManager.setSpotifyAppRemote(null)
        Log.d(TAG, "onStop - Disconnected from Spotify, set spotifyAppRemote = null")
      }
    } else {
      if (isConnectingToSpotify) {
        Log.d(TAG, "onStop - Skipping disconnect (authorization in progress)")
      }
      if (isRecording) {
        Log.d(TAG, "onStop - Skipping disconnect (recording in progress, need to track Spotify events)")
      }
    }
    Log.d(TAG, "===================================")
  }

  override fun onDestroy() {
    super.onDestroy()
    polarManager.cleanup()
    recordingManager.cleanup()
    spotifyManager.cleanup()

    // Unregister the broadcast receiver
    try {
      unregisterReceiver(spotifyBroadcastReceiver)
    } catch (e: IllegalArgumentException) {
      Log.w(TAG, "Broadcast receiver was not registered")
    }
  }
}

