package com.wboelens.polarrecorder.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wboelens.polarrecorder.ui.theme.SyraBlue
import com.wboelens.polarrecorder.ui.theme.SyraSurface
import com.wboelens.polarrecorder.ui.theme.SyraText
import com.wboelens.polarrecorder.ui.theme.SyraWhite
import kotlinx.coroutines.delay

/** Regex: alphanumeric + hyphens, 3–24 characters */
private val PARTICIPANT_ID_REGEX = Regex("^[A-Za-z0-9-]{3,24}$")

/**
 * Second onboarding screen — the researcher supplies the participant with a unique ID
 * which is entered here and stored locally. No network call is made.
 *
 * @param onIdConfirmed Called with the trimmed, validated participant ID string.
 */
@Composable
fun ParticipantIdScreen(
    onIdConfirmed: (participantId: String) -> Unit,
) {
    var idText by remember { mutableStateOf("") }
    var hasSubmitAttempt by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val isValid = PARTICIPANT_ID_REGEX.matches(idText.trim())
    val showError = hasSubmitAttempt && !isValid

    LaunchedEffect(Unit) {
        delay(120)
        visible = true
        delay(300)
        focusRequester.requestFocus()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 8 }),
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
                    text = "Participant\nIdentification",
                    style = MaterialTheme.typography.headlineLarge,
                    color = SyraText,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Your researcher has assigned you a unique ID. Enter it exactly as given.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SyraText.copy(alpha = 0.6f),
                )
            }

            HorizontalDivider(color = SyraSurface, thickness = 1.dp)

            // ── Body ──────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // Instruction card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SyraSurface, RoundedCornerShape(4.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "ID FORMAT",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = SyraBlue,
                    )
                    Text(
                        text = "• Between 3 and 24 characters\n" +
                            "• Letters (A–Z), numbers (0–9), and hyphens (-) only\n" +
                            "• Example: PARTICIPANT-A91 or P042",
                        style = MaterialTheme.typography.bodySmall,
                        color = SyraText.copy(alpha = 0.75f),
                    )
                }

                // Input field
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "PARTICIPANT ID",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = SyraBlue,
                    )
                    OutlinedTextField(
                        value = idText,
                        onValueChange = {
                            // Sanitise: strip spaces, uppercase
                            idText = it.replace(" ", "").uppercase()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        placeholder = {
                            Text(
                                "e.g. PARTICIPANT-A91",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SyraText.copy(alpha = 0.35f),
                            )
                        },
                        singleLine = true,
                        isError = showError,
                        supportingText = if (showError) {
                            {
                                Text(
                                    text = when {
                                        idText.isBlank() -> "ID cannot be empty."
                                        idText.length < 3 -> "ID must be at least 3 characters."
                                        idText.length > 24 -> "ID must not exceed 24 characters."
                                        else -> "Only letters, numbers, and hyphens are allowed."
                                    },
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType   = KeyboardType.Ascii,
                            imeAction      = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                hasSubmitAttempt = true
                                if (isValid) {
                                    focusManager.clearFocus()
                                    onIdConfirmed(idText.trim())
                                }
                            },
                        ),
                        shape = RoundedCornerShape(4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = SyraBlue,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor    = SyraBlue,
                            cursorColor          = SyraBlue,
                            focusedContainerColor   = SyraWhite,
                            unfocusedContainerColor = SyraWhite,
                        ),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = SyraText),
                    )
                }

                // Privacy note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SyraSurface.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(12.dp),
                ) {
                    Text(
                        text = "🔒  Your ID is stored only on this device. " +
                            "It is never transmitted over any network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SyraText.copy(alpha = 0.6f),
                    )
                }
            }

            // ── Footer ────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Button(
                    onClick = {
                        hasSubmitAttempt = true
                        if (isValid) {
                            focusManager.clearFocus()
                            onIdConfirmed(idText.trim())
                        }
                    },
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
                        "Confirm & Begin Study",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
