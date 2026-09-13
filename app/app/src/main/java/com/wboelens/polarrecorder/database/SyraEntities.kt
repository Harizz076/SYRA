package com.wboelens.polarrecorder.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

// ─── Research Session ─────────────────────────────────────────────────────────

@Entity(tableName = "research_sessions")
data class ResearchSession(
    @PrimaryKey val sessionUuid: String,
    @ColumnInfo(name = "participant_id") val participantId: String,
    @ColumnInfo(name = "study_mode") val studyMode: String, // TRIGGERED | TRIGGERED_RANDOM | RANDOM_ESM | PHYSIOLOGY
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "is_exported") val isExported: Boolean = false,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int = 1,
)

// ─── Survey Response ──────────────────────────────────────────────────────────

@Entity(
    tableName = "survey_responses",
    foreignKeys = [
        ForeignKey(
            entity = ResearchSession::class,
            parentColumns = ["sessionUuid"],
            childColumns = ["session_uuid"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class SurveyResponse(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_uuid", index = true) val sessionUuid: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "survey_id") val surveyId: String,       // e.g. "K10", "daily_mood"
    @ColumnInfo(name = "prompt_type") val promptType: String,   // "PRE" | "POST" | "RANDOM" | "BASELINE"
    @ColumnInfo(name = "responses_json") val responsesJson: String, // {"q1": 3, "q2": "text", ...}
)
