package com.wboelens.polarrecorder.ui.screens.questionnaire

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wboelens.polarrecorder.ui.theme.SyraBlue
import com.wboelens.polarrecorder.ui.theme.SyraSurface
import com.wboelens.polarrecorder.ui.theme.SyraText
import com.wboelens.polarrecorder.ui.theme.SyraWhite
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * Full-screen dynamic questionnaire renderer.
 *
 * Supports: SCALE, SINGLE_CHOICE, FREE_TEXT question types.
 * Conditional questions (skip patterns) are resolved in real-time via [derivedStateOf].
 *
 * @param questionnaire  The loaded [Questionnaire] model.
 * @param onBack         Called when the user taps the back button.
 * @param onSubmitted    Called with a map of question-id → answer when the form is valid and submitted.
 */
@Composable
fun QuestionnaireScreen(
    questionnaire: Questionnaire,
    onBack: () -> Unit,
    onSubmitted: (answers: Map<String, Any>) -> Unit,
) {
    val answers = remember { mutableStateMapOf<String, Any>() }

    // Resolve which questions are currently visible based on conditional logic
    val visibleQuestions by remember {
        derivedStateOf {
            questionnaire.questions.filter { question ->
                val conditional = question.conditional ?: return@filter true
                val parentAnswer = answers[conditional.dependsOn]
                parentAnswer == conditional.equals
            }
        }
    }

    // Are all required visible questions answered?
    val isFormValid by remember {
        derivedStateOf {
            visibleQuestions
                .filter { it.isRequired }
                .all { answers.containsKey(it.id) }
        }
    }

    val answeredRequired by remember {
        derivedStateOf {
            visibleQuestions.count { it.isRequired && answers.containsKey(it.id) }
        }
    }
    val totalRequired by remember {
        derivedStateOf { visibleQuestions.count { it.isRequired } }
    }
    val progress = if (totalRequired == 0) 1f else answeredRequired.toFloat() / totalRequired

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(80)
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
                        text = questionnaire.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = SyraText,
                    )
                }
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = SyraBlue,
                trackColor = SyraSurface,
            )

            HorizontalDivider(color = SyraSurface, thickness = 1.dp)

            // ── Question list ─────────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Description card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(SyraSurface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${questionnaire.estimatedMinutes} MIN · ${visibleQuestions.size} QUESTIONS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = SyraBlue.copy(alpha = 0.6f),
                        )
                        Text(
                            text = questionnaire.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = SyraText.copy(alpha = 0.65f),
                        )
                    }
                }

                items(visibleQuestions, key = { it.id }) { question ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically(),
                        exit  = fadeOut() + shrinkVertically(),
                    ) {
                        QuestionCard(
                            question       = question,
                            currentAnswer  = answers[question.id],
                            onAnswerChanged = { answers[question.id] = it },
                        )
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }

            // ── Footer submit ─────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!isFormValid) {
                    Text(
                        text = "$answeredRequired / $totalRequired required questions answered",
                        style = MaterialTheme.typography.bodySmall,
                        color = SyraText.copy(alpha = 0.45f),
                    )
                }
                Button(
                    onClick = { if (isFormValid) onSubmitted(answers.toMap()) },
                    enabled = isFormValid,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor         = SyraBlue,
                        contentColor           = SyraWhite,
                        disabledContainerColor = SyraSurface,
                        disabledContentColor   = SyraText.copy(alpha = 0.38f),
                    ),
                ) {
                    Text("Submit Questionnaire", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ─── Question card ────────────────────────────────────────────────────────────

@Composable
private fun QuestionCard(
    question: Question,
    currentAnswer: Any?,
    onAnswerChanged: (Any) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(4.dp))
            .background(SyraSurface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Question label row
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Required dot
            if (question.isRequired) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(6.dp)
                        .background(SyraBlue, CircleShape),
                )
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Text(
                text = question.text,
                style = MaterialTheme.typography.titleSmall,
                color = SyraText,
                modifier = Modifier.weight(1f),
            )
        }

        // Input widget based on type
        when (question.type) {
            QuestionType.SCALE -> {
                val min = (question.validation?.get("min") as? Double)?.toInt() ?: 1
                val max = (question.validation?.get("max") as? Double)?.toInt() ?: 5
                @Suppress("UNCHECKED_CAST")
                val labels = question.validation?.get("labels") as? Map<String, String>
                ScaleSelector(
                    min      = min,
                    max      = max,
                    labels   = labels,
                    selected = (currentAnswer as? Double)?.toInt(),
                    onSelected = { onAnswerChanged(it.toDouble()) },
                )
            }
            QuestionType.SINGLE_CHOICE -> {
                SingleChoiceSelector(
                    options        = question.options ?: emptyList(),
                    selectedOption = currentAnswer as? String,
                    onSelected     = onAnswerChanged,
                )
            }
            QuestionType.FREE_TEXT -> {
                val maxLen = (question.validation?.get("maxLength") as? Double)?.toInt() ?: 250
                FreeTextInput(
                    maxLen   = maxLen,
                    value    = currentAnswer as? String ?: "",
                    onChange = onAnswerChanged,
                )
            }
            QuestionType.AFFECT_GRID -> {
                @Suppress("UNCHECKED_CAST")
                val answerMap = currentAnswer as? Map<String, Int>
                AffectGridSelector(
                    selectedX = answerMap?.get("x"),
                    selectedY = answerMap?.get("y"),
                    onSelected = { x, y ->
                        onAnswerChanged(mapOf("x" to x, "y" to y))
                    }
                )
            }
        }
    }
}

// ─── Input widgets ────────────────────────────────────────────────────────────

@Composable
private fun ScaleSelector(
    min: Int,
    max: Int,
    labels: Map<String, String>?,
    selected: Int?,
    onSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Number row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            (min..max).forEach { value ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = if (isSelected) SyraBlue else SyraWhite,
                            shape = CircleShape,
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (isSelected) SyraBlue else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .clickable { onSelected(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = value.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) SyraWhite else SyraText,
                    )
                }
            }
        }
        // Label row (only min + max)
        if (!labels.isNullOrEmpty()) {
            val minLabel = labels[min.toString()]
            val maxLabel = labels[max.toString()]
            if (minLabel != null || maxLabel != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = minLabel ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = SyraText.copy(alpha = 0.5f),
                    )
                    Text(
                        text  = maxLabel ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = SyraText.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleChoiceSelector(
    options: List<String>,
    selectedOption: String?,
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selectedOption
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) SyraBlue.copy(alpha = 0.08f) else SyraWhite)
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) SyraBlue else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(4.dp),
                    )
                    .clickable { onSelected(option) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(SyraWhite, CircleShape)
                        .border(
                            width = if (isSelected) 5.dp else 1.5.dp,
                            color = if (isSelected) SyraBlue else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        ),
                )
                Text(
                    text  = option,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) SyraBlue else SyraText,
                )
            }
        }
    }
}

@Composable
private fun FreeTextInput(
    maxLen: Int,
    value: String,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= maxLen) onChange(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Type your response here…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SyraText.copy(alpha = 0.35f),
                )
            },
            minLines = 3,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction    = ImeAction.Default,
            ),
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor    = SyraBlue,
                unfocusedBorderColor  = MaterialTheme.colorScheme.outlineVariant,
                cursorColor           = SyraBlue,
                focusedContainerColor = SyraWhite,
                unfocusedContainerColor = SyraWhite,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = SyraText),
        )
        Text(
            text  = "${value.length} / $maxLen",
            style = MaterialTheme.typography.labelSmall,
            color = SyraText.copy(alpha = 0.4f),
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun AffectGridSelector(
    selectedX: Int?,
    selectedY: Int?,
    onSelected: (Int, Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Labels for Y-axis (Energy/Arousal)
        Text(
            text = "High Energy",
            style = MaterialTheme.typography.labelSmall,
            color = SyraText.copy(alpha = 0.6f)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Label for X-axis (Pleasantness) Left
            Text(
                text = "Unpleasant",
                style = MaterialTheme.typography.labelSmall,
                color = SyraText.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )

            // The Grid
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(SyraWhite)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            // Map tap coordinates to a 99x99 grid (1 to 99)
                            val gridX = ((offset.x / size.width) * 98 + 1).roundToInt().coerceIn(1, 99)
                            // Y is inverted (0 is top, we want 99 at top)
                            val gridY = ((1f - (offset.y / size.height)) * 98 + 1).roundToInt().coerceIn(1, 99)
                            onSelected(gridX, gridY)
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // Draw grid lines
                    val stepCount = 10
                    for (i in 1 until stepCount) {
                        val x = canvasWidth * (i.toFloat() / stepCount)
                        val y = canvasHeight * (i.toFloat() / stepCount)
                        // Vertical lines
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.5f),
                            start = Offset(x, 0f),
                            end = Offset(x, canvasHeight),
                            strokeWidth = 1f
                        )
                        // Horizontal lines
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.5f),
                            start = Offset(0f, y),
                            end = Offset(canvasWidth, y),
                            strokeWidth = 1f
                        )
                    }

                    // Draw center crosshairs
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.8f),
                        start = Offset(canvasWidth / 2f, 0f),
                        end = Offset(canvasWidth / 2f, canvasHeight),
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.8f),
                        start = Offset(0f, canvasHeight / 2f),
                        end = Offset(canvasWidth, canvasHeight / 2f),
                        strokeWidth = 2f
                    )

                    // Draw selected point
                    if (selectedX != null && selectedY != null) {
                        val px = canvasWidth * ((selectedX - 1) / 98f)
                        val py = canvasHeight * (1f - ((selectedY - 1) / 98f))
                        drawCircle(
                            color = SyraBlue,
                            radius = 6.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // Label for X-axis (Pleasantness) Right
            Text(
                text = "Pleasant",
                style = MaterialTheme.typography.labelSmall,
                color = SyraText.copy(alpha = 0.6f),
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start
            )
        }

        // Labels for Y-axis (Energy/Arousal) Bottom
        Text(
            text = "Low Energy",
            style = MaterialTheme.typography.labelSmall,
            color = SyraText.copy(alpha = 0.6f)
        )
        
        if (selectedX != null && selectedY != null) {
            Text(
                text = "Selected: ($selectedX, $selectedY)",
                style = MaterialTheme.typography.bodySmall,
                color = SyraBlue,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
