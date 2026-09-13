package com.wboelens.polarrecorder.ui.screens.questionnaire

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

// ─── Data models ─────────────────────────────────────────────────────────────

data class Questionnaire(
    val questionnaireId: String,
    val title: String,
    val description: String,
    val version: String,
    val estimatedMinutes: Int = 3,
    val questions: List<Question>,
)

data class Question(
    val id: String,
    val text: String,
    val type: QuestionType,
    val options: List<String>? = null,
    val validation: Map<String, Any>? = null,
    val isRequired: Boolean = true,
    val conditional: Conditional? = null,
)

enum class QuestionType {
    @SerializedName("SCALE") SCALE,
    @SerializedName("SINGLE_CHOICE") SINGLE_CHOICE,
    @SerializedName("FREE_TEXT") FREE_TEXT,
    @SerializedName("AFFECT_GRID") AFFECT_GRID,
}

data class Conditional(
    val dependsOn: String,
    val equals: String,
)

// ─── Repository ───────────────────────────────────────────────────────────────

/**
 * Loads questionnaire JSON files from `assets/questionnaires/`.
 * All operations are local; no network calls are made.
 */
object QuestionnaireRepository {

    private val gson = Gson()

    /**
     * Returns the list of filenames available in `assets/questionnaires/`.
     * Filters to `.json` files only.
     */
    fun listQuestionnaires(context: Context): List<String> =
        context.assets.list("questionnaires")
            ?.filter { it.endsWith(".json") }
            .orEmpty()

    /**
     * Parses and returns a [Questionnaire] from `assets/questionnaires/[fileName]`.
     * Returns null if the file cannot be read or parsed.
     */
    fun load(context: Context, fileName: String): Questionnaire? = runCatching {
        context.assets.open("questionnaires/$fileName").bufferedReader().use { reader ->
            gson.fromJson(reader, Questionnaire::class.java)
        }
    }.getOrNull()
}
