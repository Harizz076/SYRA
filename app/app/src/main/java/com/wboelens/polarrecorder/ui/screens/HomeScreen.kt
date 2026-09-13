package com.wboelens.polarrecorder.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wboelens.polarrecorder.ui.theme.SyraBlue
import com.wboelens.polarrecorder.ui.theme.SyraSurface
import com.wboelens.polarrecorder.ui.theme.SyraText
import com.wboelens.polarrecorder.ui.theme.SyraWhite
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import kotlinx.coroutines.delay

/**
 * Main application hub displayed after onboarding is complete.
 * Gives the participant a clear, minimal interface to:
 *  - See current sensor connection status at a glance
 *  - Navigate to the questionnaire section
 *  - Access the researcher-facing Setup screen
 */
@Composable
fun HomeScreen(
    participantId: String,
    studyMode: String,
    notificationAccessGranted: Boolean,
    deviceViewModel: DeviceViewModel,
    onNavigateToSetup: () -> Unit,
    onNavigateToQuestionnaires: () -> Unit,
    onNavigateToRecording: () -> Unit,
) {
    val connectedDevices by deviceViewModel.connectedDevices.observeAsState()
    val isConnected = connectedDevices?.isNotEmpty() == true
    val isPhysiologyMode = studyMode == "PHYSIOLOGY"
    val requiresMediaListener = studyMode != "RANDOM_ESM"
    // Recording is blocked only in physiology mode when no sensor is connected
    val recordingBlocked = isPhysiologyMode && !isConnected
    var visible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val powerManager = remember { context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager }
    var isIgnoringBattery by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        delay(80)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 10 }),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 20.dp),
            ) {
                Text(
                    text = "SYRA",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = SyraBlue.copy(alpha = 0.55f),
                        letterSpacing = 4.sp,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Research\nPlatform",
                    style = MaterialTheme.typography.headlineLarge,
                    color = SyraText,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "ID: $participantId",
                    style = MaterialTheme.typography.labelMedium,
                    color = SyraText.copy(alpha = 0.45f),
                )
            }

            HorizontalDivider(color = SyraSurface, thickness = 1.dp)

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 24.dp,
                    vertical = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── Notification access warning (TRIGGERED / TRIGGERED_RANDOM modes) ──
                if (requiresMediaListener && !notificationAccessGranted) {
                    item {
                        NotificationAccessCard(
                            onOpenSettings = {
                                val intent = android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            }
                        )
                    }
                }

                // ── Battery optimization warning ──
                if (requiresMediaListener && !isIgnoringBattery) {
                    item {
                        BatteryOptimizationCard(
                            onOpenSettings = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.util.Log.e("HomeScreen", "Failed to open battery settings", e)
                                }
                            }
                        )
                    }
                }

                // ── Sensor status card (PHYSIOLOGY mode only) ─────────────────
                if (isPhysiologyMode) {
                    item {
                        SensorStatusCard(
                            isConnected = isConnected,
                            deviceName  = connectedDevices?.firstOrNull()?.info?.deviceId ?: "",
                        )
                    }
                }

                // ── Navigation tiles ─────────────────────────────────────────
                item {
                    Text(
                        text = "ACTIONS",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = SyraBlue.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                item {
                    NavTile(
                        icon  = Icons.Outlined.Assignment,
                        title = "Questionnaires",
                        sub   = "Complete your scheduled surveys",
                        onClick = onNavigateToQuestionnaires,
                    )
                }

                item {
                    NavTile(
                        icon  = Icons.Filled.FavoriteBorder,
                        title = "Start Recording",
                        sub   = when {
                            isPhysiologyMode && !isConnected -> "Connect a sensor first in Setup"
                            isPhysiologyMode -> "Sensor ready — begin a session"
                            else -> "Begin a music listening session"
                        },
                        onClick = { if (!recordingBlocked) onNavigateToRecording() },
                        dimmed = recordingBlocked,
                    )
                }

                item {
                    NavTile(
                        icon  = Icons.Outlined.Settings,
                        title = "Setup",
                        sub   = "Sensor pairing, recording & export settings",
                        onClick = onNavigateToSetup,
                    )
                }
            }
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun SensorStatusCard(isConnected: Boolean, deviceName: String) {
    val dotColor = if (isConnected) androidx.compose.ui.graphics.Color(0xFF2E6E4E)
                   else MaterialTheme.colorScheme.error
    val label    = if (isConnected) "Sensor Connected" else "No Sensor Connected"
    val sublabel = if (isConnected) (if (deviceName.isNotBlank()) deviceName else "Polar Device")
                   else "Open Setup to pair a Polar device"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(SyraSurface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(4.dp),
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape),
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = SyraText,
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = SyraText.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun NotificationAccessCard(onOpenSettings: () -> Unit) {
    androidx.compose.material3.Card(
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = SyraBlue.copy(alpha = 0.08f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = SyraBlue.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notification Access Required",
                    style = MaterialTheme.typography.titleSmall,
                    color = SyraText,
                )
                Text(
                    text = "Music detection is inactive. Tap to open Settings → Notification Access and enable SYRA.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SyraText.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun BatteryOptimizationCard(onOpenSettings: () -> Unit) {
    androidx.compose.material3.Card(
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Battery Optimization Active",
                    style = MaterialTheme.typography.titleSmall,
                    color = SyraText,
                )
                Text(
                    text = "To prevent background music detection from being frozen by the system, tap here to turn off battery optimization for SYRA.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SyraText.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun NavTile(
    icon: ImageVector,
    title: String,
    sub: String,
    onClick: () -> Unit,
    dimmed: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (dimmed) SyraSurface.copy(alpha = 0.5f) else SyraSurface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(enabled = !dimmed, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = if (dimmed) SyraText.copy(alpha = 0.06f) else SyraBlue.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(4.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (dimmed) SyraText.copy(alpha = 0.3f) else SyraBlue,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (dimmed) SyraText.copy(alpha = 0.38f) else SyraText,
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = if (dimmed) SyraText.copy(alpha = 0.28f) else SyraText.copy(alpha = 0.55f),
            )
        }
    }
}
