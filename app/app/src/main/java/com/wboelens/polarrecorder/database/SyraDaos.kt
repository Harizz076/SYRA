package com.wboelens.polarrecorder.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

// ─── ResearchSession DAO ──────────────────────────────────────────────────────

@Dao
interface ResearchSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ResearchSession)

    @Update
    suspend fun update(session: ResearchSession)

    @Query("SELECT * FROM research_sessions WHERE sessionUuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): ResearchSession?

    @Query("SELECT * FROM research_sessions WHERE is_exported = 0 ORDER BY start_time DESC")
    suspend fun getUnexported(): List<ResearchSession>

    @Query("SELECT * FROM research_sessions ORDER BY start_time DESC")
    suspend fun getAll(): List<ResearchSession>

    @Query("SELECT * FROM research_sessions WHERE start_time >= :sinceEpochMs ORDER BY start_time ASC")
    suspend fun getSessionsSince(sinceEpochMs: Long): List<ResearchSession>

    @Query("UPDATE research_sessions SET is_exported = 1 WHERE sessionUuid = :uuid")
    suspend fun markExported(uuid: String)
}

// ─── SurveyResponse DAO ───────────────────────────────────────────────────────

@Dao
interface SurveyResponseDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(response: SurveyResponse)

    @Query("SELECT * FROM survey_responses WHERE session_uuid = :sessionUuid ORDER BY timestamp ASC")
    suspend fun getForSession(sessionUuid: String): List<SurveyResponse>

    @Query("SELECT * FROM survey_responses ORDER BY timestamp DESC")
    suspend fun getAll(): List<SurveyResponse>

    @Query("SELECT * FROM survey_responses WHERE timestamp >= :sinceEpochMs ORDER BY timestamp ASC")
    suspend fun getResponsesSince(sinceEpochMs: Long): List<SurveyResponse>
}
