package com.wboelens.polarrecorder.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wboelens.polarrecorder.ui.theme.SyraBlue
import com.wboelens.polarrecorder.ui.theme.SyraSurface
import com.wboelens.polarrecorder.ui.theme.SyraText
import com.wboelens.polarrecorder.ui.theme.SyraWhite
import kotlinx.coroutines.delay

/** Current version of the ethical statement. Bump this when the statement text changes. */
const val CONSENT_VERSION = "ETH-2026-V2"

private val ETHICAL_STATEMENT_SECTIONS = listOf(
    EthSection(
        title = "Study Purpose",
        body = "You are being invited to participate in a research study conducted by the " +
            "Cognitive Science laboratory. This study aims to understand physiological and " +
            "psychological responses during everyday activities. Your participation is entirely " +
            "voluntary and you may withdraw at any time without consequence.",
    ),
    EthSection(
        title = "Data Collection",
        body = "During this study, the following data will be collected:\n\n" +
            "• Heart rate (HR) and heart rate variability (HRV) via a Polar chest strap sensor\n" +
            "• Electrocardiogram (ECG) signals\n" +
            "• Accelerometer (movement) data\n" +
            "• Responses to periodic Experience Sampling Method (ESM) questionnaires\n\n" +
            "All data is recorded directly to this device and is never transmitted to any " +
            "external server, cloud service, or third party.",
    ),
    EthSection(
        title = "Data Storage & Privacy",
        body = "All collected data is stored exclusively on this research device using " +
            "encrypted local storage. Your data is identified only by the anonymised " +
            "Participant ID assigned to you by the researcher. No personally identifiable " +
            "information (name, email, phone number) is stored.\n\n" +
            "Data will be transferred to the researcher's secure workstation via a direct " +
            "USB cable connection. No wireless transmission of personal data occurs.",
    ),
    EthSection(
        title = "Risks & Benefits",
        body = "The risks associated with this study are minimal. Wearing a chest strap sensor " +
            "may cause mild discomfort for some individuals. If you experience any discomfort, " +
            "you may remove the sensor and withdraw from the study at any time.\n\n" +
            "There are no direct benefits to you personally; however, your participation " +
            "contributes to scientific knowledge in cognitive science and human physiology.",
    ),
    EthSection(
        title = "Your Rights",
        body = "Your participation is voluntary. You have the right to:\n\n" +
            "• Withdraw consent at any time without penalty\n" +
            "• Request deletion of your data by notifying the researcher\n" +
            "• Ask questions about the study at any time\n\n" +
            "This study has been approved by the institutional ethics committee. If you have " +
            "concerns about your rights as a participant, please contact the researcher.",
    ),
    EthSection(
        title = "Contact",
        body = "For questions about the research or your rights as a participant, please " +
            "contact the principal investigator. All queries will be addressed within 48 hours.",
    ),
)

private data class EthSection(val title: String, val body: String)

/**
 * Full-screen ethical consent screen shown on first launch.
 * The participant must scroll to the bottom and tick the checkbox before proceeding.
 *
 * @param onConsentAccepted Called when the participant clicks "I Agree & Continue".
 */
@Composable
fun EthicalConsentScreen(
    onConsentAccepted: () -> Unit,
) {
    var hasReachedBottom by remember { mutableStateOf(false) }
    var isChecked by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(120)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 8 }),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding(),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
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
                    text = "Participant\nConsent",
                    style = MaterialTheme.typography.headlineLarge,
                    color = SyraText,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Version $CONSENT_VERSION · Please read carefully before proceeding.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SyraText.copy(alpha = 0.5f),
                )
            }

            HorizontalDivider(color = SyraSurface, thickness = 1.dp)

            // ── Scrollable statement body ─────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(ETHICAL_STATEMENT_SECTIONS.size) { index ->
                    val section = ETHICAL_STATEMENT_SECTIONS[index]
                    ConsentSection(title = section.title, body = section.body)
                }

                // Sentinel item — when this is visible the user has reached the bottom
                item {
                    LaunchedEffect(Unit) { hasReachedBottom = true }
                    Spacer(Modifier.height(8.dp))
                }
            }

            // ── Footer: checkbox + action buttons ────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .border(width = 1.dp, color = SyraSurface, shape = RoundedCornerShape(0.dp))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Must scroll to the bottom before checkbox is active
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { if (hasReachedBottom) isChecked = it },
                        enabled = hasReachedBottom,
                        colors = CheckboxDefaults.colors(
                            checkedColor = SyraBlue,
                            uncheckedColor = if (hasReachedBottom) SyraBlue else SyraText.copy(alpha = 0.3f),
                            checkmarkColor = SyraWhite,
                        ),
                    )
                    Text(
                        text = if (hasReachedBottom)
                            "I have read and understood the information above. I voluntarily agree to participate."
                        else
                            "Scroll to the bottom to enable this checkbox.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasReachedBottom) SyraText else SyraText.copy(alpha = 0.45f),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }

                Button(
                    onClick = onConsentAccepted,
                    enabled = isChecked,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SyraBlue,
                        contentColor   = SyraWhite,
                        disabledContainerColor = SyraSurface,
                        disabledContentColor   = SyraText.copy(alpha = 0.38f),
                    ),
                ) {
                    Text(
                        "I Agree & Continue",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsentSection(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(SyraSurface)
            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(4.dp))
            .padding(16.dp),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
            color = SyraBlue,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = SyraText,
        )
    }
}
