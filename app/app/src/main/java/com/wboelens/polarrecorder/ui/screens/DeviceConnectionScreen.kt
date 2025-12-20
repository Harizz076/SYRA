package com.wboelens.polarrecorder.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wboelens.polarrecorder.managers.PolarManager
import com.wboelens.polarrecorder.viewModels.ConnectionState
import com.wboelens.polarrecorder.viewModels.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConnectionScreen(
    deviceViewModel: DeviceViewModel,
    polarManager: PolarManager,
    onBackPressed: () -> Unit,
    onContinue: () -> Unit,
) {
  val selectedDevices by deviceViewModel.selectedDevices.observeAsState(emptyList())
  val allConnected = selectedDevices.all { it.connectionState == ConnectionState.CONNECTED }

  // Save auto-connect device when first device connects successfully
  LaunchedEffect(selectedDevices) {
    selectedDevices.firstOrNull { it.connectionState == ConnectionState.CONNECTED }?.let { device ->
      polarManager.saveAutoConnectDevice(device.info.deviceId)
    }
  }

  // Auto-select all available data types for each connected device
  // ONLY after sensor settings have been fetched
  LaunchedEffect(selectedDevices) {
    selectedDevices.forEach { device ->
      if (device.connectionState == ConnectionState.CONNECTED &&
          device.dataTypes.isEmpty() &&
          device.sensorSettings.isNotEmpty()) {
        // Auto-select ONLY data types that have sensor settings available
        // (this ensures the device actually supports them)
        val availableDataTypes = device.sensorSettings.keys
        deviceViewModel.updateDeviceDataTypes(device.info.deviceId, availableDataTypes)
      }
    }
  }

  // Effect to auto-continue when all devices are connected and configured
  LaunchedEffect(allConnected, selectedDevices) {
    if (allConnected && selectedDevices.isNotEmpty()) {
      // Check if all devices have their data types configured
      val allConfigured = selectedDevices.all {
        it.dataTypes.isNotEmpty() && it.sensorSettings.isNotEmpty()
      }
      if (allConfigured) {
        onContinue()
      }
    }
  }

  // Initiate connections
  LaunchedEffect(selectedDevices) {
    selectedDevices.forEach { device ->
      if (device.connectionState == ConnectionState.DISCONNECTED) {
        polarManager.connectToDevice(device.info.deviceId)
      }
    }
  }

  MaterialTheme {
    Scaffold(
        topBar = {
          TopAppBar(
              title = { Text("Connecting Devices") },
              navigationIcon = {
                IconButton(onClick = onBackPressed) { Icon(Icons.Default.ArrowBack, "Back") }
              },
          )
        }
    ) { paddingValues ->
      Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
        selectedDevices.forEach { device ->
          DeviceConnectionItem(device = device)
          Spacer(modifier = Modifier.height(8.dp))
        }
      }
    }
  }
}

@Composable
private fun DeviceConnectionItem(device: DeviceViewModel.Device) {
  Row(
      modifier = Modifier.fillMaxWidth().padding(8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = device.info.name, style = MaterialTheme.typography.bodyLarge)
      Text(
          text =
              when (device.connectionState) {
                ConnectionState.DISCONNECTING -> "Disconnecting..." // should never be displayed
                ConnectionState.DISCONNECTED -> "Waiting..."
                ConnectionState.CONNECTING -> "Connecting..."
                ConnectionState.FETCHING_CAPABILITIES -> "Fetching capabilities..."
                ConnectionState.FETCHING_SETTINGS -> "Fetching settings..."
                ConnectionState.CONNECTED -> "Connected"
                ConnectionState.FAILED -> "Failed"
                ConnectionState.NOT_CONNECTABLE -> "Not connectable"
              },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Box(modifier = Modifier.size(24.dp).padding(4.dp), contentAlignment = Alignment.Center) {
      when (device.connectionState) {
        ConnectionState.CONNECTING,
        ConnectionState.FETCHING_CAPABILITIES -> {
          CircularProgressIndicator(modifier = Modifier.fillMaxSize(), strokeWidth = 2.dp)
        }
        ConnectionState.CONNECTED -> {
          Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = "Connected",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.fillMaxSize(),
          )
        }
        ConnectionState.FAILED -> {
          Icon(
              imageVector = Icons.Default.Error,
              contentDescription = "Failed",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.fillMaxSize(),
          )
        }
        else -> {
          /* No indicator for other states */
        }
      }
    }
  }
}
