package com.wboelens.polarrecorder.ui.screens.questionnaire

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wboelens.polarrecorder.ui.theme.SyraBlue
import com.wboelens.polarrecorder.ui.theme.SyraSurface
import com.wboelens.polarrecorder.ui.theme.SyraText
import kotlinx.coroutines.delay

/**
 * Lists all questionnaires available in `assets/questionnaires/`.
 * Tapping a row navigates to [QuestionnaireScreen] for that questionnaire.
 *
 * @param onBack       Called when the user taps the back button.
 * @param onSelected   Called with the file name of the chosen questionnaire.
 */
@Composable
fun QuestionnaireListScreen(
    onBack: () -> Unit,
    onSelected: (fileName: String) -> Unit,
) {
    val context = LocalContext.current
    val files = remember { QuestionnaireRepository.listQuestionnaires(context) }
    val questionnaires = remember {
        files.mapNotNull { fileName ->
            QuestionnaireRepository.load(context, fileName)?.let { fileName to it }
        }
    }
    var visible by remember { mutableStateOf(false) }
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
                        text = "Questionnaires",
                        style = MaterialTheme.typography.headlineSmall,
                        color = SyraText,
                    )
                }
            }

            HorizontalDivider(color = SyraSurface, thickness = 1.dp)

            if (questionnaires.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Assignment,
                            contentDescription = null,
                            tint = SyraText.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = "No questionnaires found",
                            style = MaterialTheme.typography.titleSmall,
                            color = SyraText.copy(alpha = 0.35f),
                        )
                        Text(
                            text = "Add JSON files to assets/questionnaires/",
                            style = MaterialTheme.typography.bodySmall,
                            color = SyraText.copy(alpha = 0.25f),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            text = "${questionnaires.size} AVAILABLE",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                            color = SyraBlue.copy(alpha = 0.6f),
                        )
                    }
                    items(questionnaires, key = { it.first }) { (fileName, q) ->
                        QuestionnaireListTile(
                            questionnaire = q,
                            onClick       = { onSelected(fileName) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionnaireListTile(
    questionnaire: Questionnaire,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(SyraSurface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SyraBlue.copy(alpha = 0.10f), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Assignment,
                contentDescription = null,
                tint = SyraBlue,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = questionnaire.title,
                style = MaterialTheme.typography.titleSmall,
                color = SyraText,
            )
            Text(
                text  = "~${questionnaire.estimatedMinutes} min · v${questionnaire.version}",
                style = MaterialTheme.typography.bodySmall,
                color = SyraText.copy(alpha = 0.5f),
            )
        }
    }
}
