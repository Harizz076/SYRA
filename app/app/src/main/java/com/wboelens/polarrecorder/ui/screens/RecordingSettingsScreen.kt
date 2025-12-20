package com.wboelens.polarrecorder.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.dataSavers.DataSavers
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.ui.components.CheckboxWithLabel
import com.wboelens.polarrecorder.ui.components.SaveToOptions
import com.wboelens.polarrecorder.utils.ZipUtils
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import com.wboelens.polarrecorder.viewModels.FileSystemSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingSettingsScreen(
    deviceViewModel: DeviceViewModel,
    fileSystemSettingsViewModel: FileSystemSettingsViewModel,
    dataSavers: DataSavers,
    preferencesManager: PreferencesManager,
    polarManager: com.wboelens.polarrecorder.managers.PolarManager,
    onBackPressed: () -> Unit,
    onNavigateToDeviceSelection: () -> Unit = onBackPressed,
    onContinue: () -> Unit,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val connectedDevices = deviceViewModel.connectedDevices.observeAsState(emptySet()).value
  var recordingName by remember { mutableStateOf(preferencesManager.recordingName) }
  var appendTimestamp by remember {
    mutableStateOf(preferencesManager.recordingNameAppendTimestamp)
  }
  var recordingStopOnDisconnect by remember {
    mutableStateOf(preferencesManager.recordingStopOnDisconnect)
  }
  var autoRecordingEnabled by remember {
    mutableStateOf(preferencesManager.autoRecordingEnabled)
  }

  // Collect enabled state for all datasavers
  val datasaversList = dataSavers.asList()
  val enabledStates = datasaversList.map { it.isEnabled.collectAsState() }
  val isAnyDataSaverEnabled by
      remember(enabledStates) { derivedStateOf { enabledStates.any { it.value } } }

  MaterialTheme {
    Scaffold(
        topBar = {
          TopAppBar(
              title = { Text("Recording Settings") },
              // No back button - user must use the app flow
          )
        },
    ) { paddingValues ->
      Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
        SaveToOptions(
            dataSavers = dataSavers,
            preferencesManager = preferencesManager,
            fileSystemSettingsViewModel = fileSystemSettingsViewModel,
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = recordingName,
            onValueChange = {
              recordingName = it
              preferencesManager.recordingName = it
            },
            label = { Text("Recording Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = false, // Make read-only - display only
            isError = recordingName.isEmpty(),
            supportingText = {
              if (recordingName.isEmpty()) {
                Text("Recording name is required", color = MaterialTheme.colorScheme.error)
              }
            },
        )

        CheckboxWithLabel(
            label = "Add timestamp to recording name",
            checked = appendTimestamp,
            fullWidth = true,
            onCheckedChange = {
              appendTimestamp = it
              preferencesManager.recordingNameAppendTimestamp = it
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        CheckboxWithLabel(
            label = "Stop recording when no devices are connected",
            checked = recordingStopOnDisconnect,
            fullWidth = true,
            onCheckedChange = {
              recordingStopOnDisconnect = it
              preferencesManager.recordingStopOnDisconnect = it
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        CheckboxWithLabel(
            label = "Enable auto-recording (monitors Spotify activity)",
            checked = autoRecordingEnabled,
            fullWidth = true,
            onCheckedChange = {
              autoRecordingEnabled = it
              preferencesManager.autoRecordingEnabled = it
              // Start or stop the Spotify monitor service based on the setting
              if (it) {
                com.wboelens.polarrecorder.services.SpotifyMonitorService.start(context)
              } else {
                com.wboelens.polarrecorder.services.SpotifyMonitorService.stop(context)
              }
            },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onContinue() },
            modifier = Modifier.fillMaxWidth(),
            enabled =
                connectedDevices.isNotEmpty() &&
                    recordingName.isNotEmpty() &&
                    isAnyDataSaverEnabled,
        ) {
          Text("Start Recording")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Show different button based on connection state
        if (connectedDevices.isNotEmpty()) {
          // Show "Disconnect and Clear Auto-Connect" when devices are connected
          Button(
              onClick = {
                // Disconnect all devices
                connectedDevices.forEach { device ->
                  polarManager.disconnectDevice(device.info.deviceId)
                }
                // Clear auto-connect device
                polarManager.clearAutoConnectDevice()
                Toast.makeText(context, "Disconnected and cleared auto-connect", Toast.LENGTH_SHORT).show()
              },
              modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Disconnect and Clear Auto-Connect")
          }
        } else {
          // Show "Connect New Device" when no devices are connected
          Button(
              onClick = {
                // Navigate back to device selection screen
                onNavigateToDeviceSelection()
              },
              modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Connect New Device")
          }
        }

        // Share Logs button - only show if file system is enabled and has base directory
        val isFileSystemEnabled by dataSavers.fileSystem.isEnabled.collectAsState()
        if (isFileSystemEnabled) {
          dataSavers.fileSystem.getBaseDirectory()?.let { baseDir ->
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                  coroutineScope.launch {
                    Toast.makeText(context, "Creating zip file...", Toast.LENGTH_SHORT).show()

                    try {
                      val zipFile = withContext(Dispatchers.IO) {
                        ZipUtils.zipAllSyraFolders(context, baseDir)
                      }

                      if (zipFile != null) {
                        val shareIntent = ZipUtils.createShareIntent(context, zipFile)
                        try {
                          context.startActivity(Intent.createChooser(shareIntent, "Share Logs"))
                        } catch (_: ActivityNotFoundException) {
                          Toast.makeText(context, "No app found to share files", Toast.LENGTH_SHORT).show()
                        }
                      } else {
                        Toast.makeText(context, "No syra folders found to zip", Toast.LENGTH_LONG).show()
                      }
                    } catch (e: Exception) {
                      Toast.makeText(context, "Failed to create zip: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                  }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
              Text("Share Logs")
            }
          }
        }
      }
    }
  }
}
