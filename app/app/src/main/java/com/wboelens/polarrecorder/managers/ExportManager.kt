package com.wboelens.polarrecorder.managers

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.wboelens.polarrecorder.database.SyraDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Packages all unexported SYRA research sessions into a ZIP archive in the app's
 * external Downloads directory, ready for ADB extraction.
 *
 * Output path: <external>/Download/syra_exports/syra_export_<participantId>_<timestamp>.zip
 *
 * ADB pull command:
 *   adb pull /sdcard/Android/data/com.wboelens.polarrecorder/files/Download/syra_exports/ .
 */
class ExportManager(
    private val context: Context,
    private val db: SyraDatabase,
    private val participantId: String,
) {
    companion object {
        private const val TAG = "ExportManager"
    }

    private val gson = Gson()

    /** Returns the path of the generated ZIP, or null if nothing to export. */
    suspend fun exportToZip(): File? = withContext(Dispatchers.IO) {
        val sessions = db.sessionDao().getUnexported()
        if (sessions.isEmpty()) {
            Log.d(TAG, "No unexported sessions — nothing to do")
            return@withContext null
        }

        val timestamp = System.currentTimeMillis()
        val exportDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "syra_exports",
        ).also { it.mkdirs() }

        val zipFile = File(exportDir, "syra_export_${participantId}_$timestamp.zip")
        Log.d(TAG, "Creating ZIP: ${zipFile.absolutePath}")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zip ->
            // ── metadata.json ─────────────────────────────────────────────────
            val meta = mapOf(
                "participant_id" to participantId,
                "export_timestamp_ms" to timestamp,
                "session_count" to sessions.size,
                "schema_version" to 1,
            )
            zip.addEntry("metadata.json", gson.toJson(meta))

            // ── Per-session data ──────────────────────────────────────────────
            for (session in sessions) {
                val responses = db.surveyResponseDao().getForSession(session.sessionUuid)

                // sessions/<uuid>.json
                val sessionPayload = mapOf(
                    "session_uuid" to session.sessionUuid,
                    "participant_id" to session.participantId,
                    "study_mode" to session.studyMode,
                    "start_time_ms" to session.startTime,
                    "end_time_ms" to session.endTime,
                    "response_count" to responses.size,
                )
                zip.addEntry("sessions/${session.sessionUuid}.json", gson.toJson(sessionPayload))

                // sessions/<uuid>_responses.json
                val responsesPayload = responses.map { r ->
                    mapOf(
                        "id" to r.id,
                        "survey_id" to r.surveyId,
                        "prompt_type" to r.promptType,
                        "timestamp_ms" to r.timestamp,
                        "responses" to r.responsesJson,
                    )
                }
                zip.addEntry(
                    "sessions/${session.sessionUuid}_responses.json",
                    gson.toJson(responsesPayload),
                )
            }
        }

        // Mark all exported sessions
        sessions.forEach { db.sessionDao().markExported(it.sessionUuid) }
        Log.d(TAG, "Export complete: ${sessions.size} sessions → ${zipFile.name}")
        zipFile
    }

    private fun ZipOutputStream.addEntry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
