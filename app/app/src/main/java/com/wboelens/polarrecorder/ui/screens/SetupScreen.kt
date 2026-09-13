package com.wboelens.polarrecorder.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wboelens.polarrecorder.managers.PreferencesManager
import com.wboelens.polarrecorder.ui.theme.SyraBlue
import com.wboelens.polarrecorder.ui.theme.SyraSurface
import com.wboelens.polarrecorder.ui.theme.SyraText
import com.wboelens.polarrecorder.viewModels.DeviceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Researcher-facing Setup screen — accessible from the HomeScreen.
 * Groups all configurable settings behind a single entry point so the
 * participant never sees setup noise on first launch.
 *
 * Sections:
 *  1. Sensor Pairing   — BLE device selection & connection
 *  2. Recording        — Recording name, split size, auto-record toggle
 *  3. Data Export      — ADB/USB export instructions + export action
 *  4. Participant Info — Displays the locked-in participant ID and consent version
 */
@Composable
fun SetupScreen(
    preferencesManager: PreferencesManager,
    deviceViewModel: DeviceViewModel,
    onBack: () -> Unit,
    onNavigateToSensorPairing: () -> Unit,
    onNavigateToRecordingSettings: () -> Unit,
    onExportData: suspend () -> Unit,
) {
    val connectedDevices by deviceViewModel.connectedDevices.observeAsState()
    val isConnected = connectedDevices?.isNotEmpty() == true
    var visible by remember { mutableStateOf(false) }
    var studyMode by remember { mutableStateOf(preferencesManager.studyMode) }
    val isPhysiologyMode = studyMode == "PHYSIOLOGY"
    var exportStatus by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = SyraBlue,
                    )
                }
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        text = "SYRA",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = SyraBlue.copy(alpha = 0.55f),
                            letterSpacing = 4.sp,
                        ),
                    )
                    Text(
                        text = "Setup",
                        style = MaterialTheme.typography.headlineSmall,
                        color = SyraText,
                    )
                }
            }

            HorizontalDivider(color = SyraSurface, thickness = 1.dp)

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Section: Study Design ─────────────────────────────────────────
                item { SectionLabel("STUDY DESIGN") }
                item {
                    StudyModeSelector(
                        currentMode = studyMode,
                        onModeSelected = {
                            studyMode = it
                            preferencesManager.studyMode = it
                        },
                    )
                }

                // ── Section: Sensor (PHYSIOLOGY mode only) ────────────────────
                if (isPhysiologyMode) {
                    item { Spacer(Modifier.height(8.dp)) }
                    item { SectionLabel("SENSOR PAIRING") }
                    item {
                        SetupRow(
                            icon        = Icons.Outlined.Bluetooth,
                            title       = "Bluetooth Sensor",
                            description = if (isConnected)
                                "Connected: ${connectedDevices?.firstOrNull()?.info?.deviceId ?: "Polar Device"}"
                            else
                                "No sensor connected — tap to scan",
                            onClick = onNavigateToSensorPairing,
                            statusDot   = if (isConnected) SetupDot.GREEN else SetupDot.RED,
                        )
                    }
                }

                // ── Section: Recording ────────────────────────────────────────────
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionLabel("RECORDING") }
                item {
                    SetupRow(
                        icon        = Icons.Outlined.Tune,
                        title       = "Recording Settings",
                        description = "Session name, file split size, auto-record behaviour",
                        onClick     = onNavigateToRecordingSettings,
                    )
                }

                // ── Section: Data Export ─────────────────────────────────────────
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionLabel("DATA EXPORT") }
                item { ExportInstructionCard() }
                item {
                    Button(
                        onClick = {
                            if (!isExporting) {
                                isExporting = true
                                exportStatus = "Packaging data…"
                                scope.launch {
                                    try {
                                        onExportData()
                                        exportStatus = "Export complete ✔"
                                    } catch (e: Exception) {
                                        exportStatus = "Export failed: ${e.message}"
                                    } finally {
                                        isExporting = false
                                    }
                                }
                            }
                        },
                        enabled = !isExporting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SyraBlue,
                            contentColor   = androidx.compose.ui.graphics.Color.White,
                        ),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.IosShare,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (isExporting) "Exporting…" else "Export Data to ZIP")
                    }
                }
                if (exportStatus.isNotBlank()) {
                    item {
                        Text(
                            text = exportStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = SyraText.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }

                // ── Section: Participant Info ────────────────────────────────────
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionLabel("PARTICIPANT INFO") }
                item {
                    InfoRow(
                        icon  = Icons.Outlined.Person,
                        label = "Participant ID",
                        value = preferencesManager.participantId.ifBlank { "—" },
                    )
                }
                item {
                    InfoRow(
                        icon  = Icons.Outlined.FolderOpen,
                        label = "Consent Version",
                        value = preferencesManager.consentVersion.ifBlank { "—" },
                    )
                }
            }
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

private enum class SetupDot { GREEN, RED, NONE }

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
        color = SyraBlue.copy(alpha = 0.6f),
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun SetupRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    statusDot: SetupDot = SetupDot.NONE,
) {
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
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(40.dp)
                .background(SyraBlue.copy(alpha = 0.10f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SyraBlue,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text  = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = SyraText,
                )
                if (statusDot != SetupDot.NONE) {
                    val dotColor = if (statusDot == SetupDot.GREEN)
                        androidx.compose.ui.graphics.Color(0xFF2E6E4E)
                    else
                        MaterialTheme.colorScheme.error
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(dotColor, androidx.compose.foundation.shape.CircleShape),
                    )
                }
            }
            Text(
                text  = description,
                style = MaterialTheme.typography.bodySmall,
                color = SyraText.copy(alpha = 0.55f),
            )
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
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
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SyraBlue.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                text  = label,
                style = MaterialTheme.typography.labelMedium,
                color = SyraText.copy(alpha = 0.5f),
            )
            Text(
                text  = value,
                style = MaterialTheme.typography.bodyMedium,
                color = SyraText,
            )
        }
    }
}

@Composable
private fun ExportInstructionCard() {
    Column(
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.IosShare,
                contentDescription = null,
                tint = SyraBlue,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text  = "ADB / USB Export",
                style = MaterialTheme.typography.titleSmall,
                color = SyraText,
            )
        }
        Text(
            text  = "Connect the device to the researcher's computer via USB, then run:",
            style = MaterialTheme.typography.bodySmall,
            color = SyraText.copy(alpha = 0.6f),
        )
        // Command block
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    SyraText.copy(alpha = 0.06f),
                    RoundedCornerShape(4.dp),
                )
                .padding(12.dp),
        ) {
            Text(
                text  = "adb pull /sdcard/Android/data/\n  com.wboelens.polarrecorder/\n  files/Download/syra_exports/ .",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    letterSpacing = 0.sp,
                ),
                color = SyraText.copy(alpha = 0.85f),
            )
        }
        Text(
            text  = "Data remains encrypted on-device and is never transmitted wirelessly.",
            style = MaterialTheme.typography.bodySmall,
            color = SyraText.copy(alpha = 0.45f),
        )
    }
}

// ─── Study Mode Selector ───────────────────────────────────────────────────────

private data class StudyModeOption(
    val key: String,
    val label: String,
    val description: String,
)

private val STUDY_MODES = listOf(
    StudyModeOption(
        key = "TRIGGERED",
        label = "Triggered",
        description = "Pre/post self-reports triggered by music playback. No sensor required.",
    ),
    StudyModeOption(
        key = "TRIGGERED_RANDOM",
        label = "Triggered + Random",
        description = "Playback-triggered reports plus usage-optimal random ESM probes.",
    ),
    StudyModeOption(
        key = "RANDOM_ESM",
        label = "Random ESM Only",
        description = "Usage-informed random ESM probes only. No music triggers, no sensor. Maximum battery efficiency.",
    ),
    StudyModeOption(
        key = "PHYSIOLOGY",
        label = "Physiology",
        description = "All of the above plus continuous ECG/ACC logging via Polar H10. Sensor required.",
    ),
)

@Composable
private fun StudyModeSelector(
    currentMode: String,
    onModeSelected: (String) -> Unit,
) {
    Column(
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        STUDY_MODES.forEach { mode ->
            val selected = currentMode == mode.key
            FilterChip(
                selected = selected,
                onClick  = { onModeSelected(mode.key) },
                label    = {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text  = mode.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) SyraBlue else SyraText,
                        )
                        Text(
                            text  = mode.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) SyraBlue.copy(alpha = 0.7f)
                                    else SyraText.copy(alpha = 0.5f),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SyraBlue.copy(alpha = 0.10f),
                    selectedLabelColor     = SyraBlue,
                ),
            )
        }
    }
}
