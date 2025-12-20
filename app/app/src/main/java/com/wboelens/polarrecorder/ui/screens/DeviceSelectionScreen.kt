package com.wboelens.polarrecorder.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.ui.components.DeviceList
import com.wboelens.polarrecorder.viewModels.ConnectionState
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectionScreen(
    deviceViewModel: DeviceViewModel,
    polarManager: PolarManager,
    onContinue: () -> Unit,
) {
  val selectedDevices by deviceViewModel.selectedDevices.observeAsState(emptyList())
  val allDevices by deviceViewModel.allDevices.observeAsState(emptyList())
  val state = rememberPullToRefreshState()
  val coroutineScope = rememberCoroutineScope()
  val isRefreshing = polarManager.isRefreshing
  val isBLEEnabled = polarManager.isBLEEnabled

  var hasAttemptedAutoConnect by remember { mutableStateOf(false) }
  var isAutoConnecting by remember { mutableStateOf(false) }

  // Auto-connect logic: When devices are found, check if saved device is present
  LaunchedEffect(allDevices) {
    if (!hasAttemptedAutoConnect && allDevices.isNotEmpty()) {
      val savedDeviceId = polarManager.getAutoConnectDeviceId()
      if (savedDeviceId.isNotEmpty()) {
        val savedDevice = allDevices.find { it.info.deviceId == savedDeviceId }
        if (savedDevice != null && savedDevice.connectionState == ConnectionState.DISCONNECTED) {
          hasAttemptedAutoConnect = true
          isAutoConnecting = true
          // Auto-select device
          deviceViewModel.selectDevices(setOf(savedDeviceId))
        }
      }
    }
  }

  // Separate effect to handle navigation after device selection
  LaunchedEffect(selectedDevices, isAutoConnecting) {
    if (isAutoConnecting && selectedDevices.isNotEmpty()) {
      // Wait a brief moment for UI to show the message, then navigate
      kotlinx.coroutines.delay(800)
      // Stop scanning and navigate to connection screen
      polarManager.stopPeriodicScanning()
      onContinue()
    }
  }

  // Simplified refresh function
  val onRefresh: () -> Unit = {
    hasAttemptedAutoConnect = false
    isAutoConnecting = false
    coroutineScope.launch { polarManager.scanForDevices() }
  }

  MaterialTheme {
    Scaffold(
        topBar = {
          TopAppBar(
              title = { Text("Select Devices") },
              actions = {
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Trigger Refresh") }
              },
          )
        }
    ) { paddingValues ->
      PullToRefreshBox(
          modifier = Modifier.fillMaxSize().padding(paddingValues),
          state = state,
          isRefreshing = isRefreshing.value && isBLEEnabled.value,
          onRefresh = onRefresh,
      ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
          // Show auto-connecting message
          if (isAutoConnecting) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
              CircularProgressIndicator(
                  modifier = Modifier.size(24.dp),
                  strokeWidth = 2.dp
              )
              Spacer(modifier = Modifier.size(12.dp))
              Text(
                  text = "Auto-connecting to saved device...",
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.primary
              )
            }
            Spacer(modifier = Modifier.size(16.dp))
          }

          DeviceList(
              deviceViewModel = deviceViewModel,
              isBLEEnabled = polarManager.isBLEEnabled.value,
          )

          Spacer(modifier = Modifier.weight(1f))

          Button(
              onClick = {
                polarManager.stopPeriodicScanning()
                onContinue()
              },
              enabled = selectedDevices.isNotEmpty(),
              modifier = Modifier.align(Alignment.End),
          ) {
            Text("Connect Devices")
          }
        }
      }
    }
  }
}
