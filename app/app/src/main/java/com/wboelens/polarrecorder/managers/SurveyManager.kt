package com.wboelens.polarrecorder.managers

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.wboelens.polarrecorder.viewModels.LogViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SurveyResponse(
    val pleasantness: Int,
    val energy: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val notificationType: String, // "music_pre", "music_post", or "random"
    val musicLiking: Int? = null, // Only for music_post
    val musicFamiliarity: Int? = null // Only for music_post
)

class SurveyManager(
    private val context: Context,
    private val logViewModel: LogViewModel
) {
    companion object {
        private const val TAG = "SurveyManager"
    }

    // Pending survey response for music notifications (to be saved when recording starts)
    private val _pendingSurveyResponse = MutableStateFlow<SurveyResponse?>(null)
    val pendingSurveyResponse: StateFlow<SurveyResponse?> = _pendingSurveyResponse

    // Current recording directory name for random notifications
    private val _randomRecordingName = MutableStateFlow<String?>(null)
    val randomRecordingName: StateFlow<String?> = _randomRecordingName

    // Current recording directory for random notifications
    private var currentRecordingDir: DocumentFile? = null

    /**
     * Store a survey response based on notification type
     * For music_pre notifications: stores temporarily until recording starts
     * For music_post notifications: stores temporarily until recording stops
     * For random notifications: stores with a new folder name
     */
    fun storeSurveyResponse(
        response: SurveyResponse,
        baseDir: DocumentFile?
    ) {
        when (response.notificationType) {
            "music_pre" -> {
                // Store temporarily - will be saved when recording starts
                _pendingSurveyResponse.value = response
                logViewModel.addLogMessage("Survey response stored (pending recording start)")
                Log.d(TAG, "Stored pending survey response: pleasantness=${response.pleasantness}, energy=${response.energy}")
            }
            "music_post" -> {
                // Store temporarily - will be saved when recording stops
                _pendingSurveyResponse.value = response
                logViewModel.addLogMessage("Post-music survey response stored (pending recording stop)")
                Log.d(TAG, "Stored post-music survey response: pleasantness=${response.pleasantness}, energy=${response.energy}, liking=${response.musicLiking}, familiarity=${response.musicFamiliarity}")
            }
            "random" -> {
                // Create new folder name and store temporarily
                // The actual folder will be created during recording initialization
                val folderName = "random_${System.currentTimeMillis()}"
                _randomRecordingName.value = folderName
                _pendingSurveyResponse.value = response

                logViewModel.addLogMessage("Survey response stored (will create folder: $folderName)")
                Log.d(TAG, "Stored random survey response, folder name: $folderName")
            }
        }
    }

    /**
     * Save pending music survey response to the recording directory
     * Called when auto-recording starts
     */
    fun savePendingSurveyToRecording(recordingDir: DocumentFile?) {
        val response = _pendingSurveyResponse.value

        if (response == null) {
            Log.d(TAG, "No pending survey response to save")
            return
        }

        if (recordingDir == null) {
            logViewModel.addLogError("Cannot save pending survey: recording directory is null")
            return
        }

        // Check if already saved to this directory
        val existingFile = recordingDir.findFile("pre.jsonl")
        if (existingFile != null && existingFile.length() > 0) {
            Log.d(TAG, "Survey already saved to this recording directory, skipping duplicate save")
            // Still clear the pending response
            _pendingSurveyResponse.value = null
            _randomRecordingName.value = null
            return
        }

        saveSurveyToFile(response, recordingDir, "pre.jsonl")

        // Clear pending response and random recording name after saving
        _pendingSurveyResponse.value = null
        _randomRecordingName.value = null

        logViewModel.addLogMessage("Pending survey saved to recording folder")
        Log.d(TAG, "Saved pending survey to recording directory")
    }

    /**
     * Save post-music survey response to the recording directory
     * Called when recording stops after music_post notification
     */
    fun savePostMusicSurveyToRecording(recordingDir: DocumentFile?) {
        val response = _pendingSurveyResponse.value

        if (response == null || response.notificationType != "music_post") {
            Log.d(TAG, "No pending post-music survey response to save")
            return
        }

        if (recordingDir == null) {
            logViewModel.addLogError("Cannot save post-music survey: recording directory is null")
            return
        }

        saveSurveyToFile(response, recordingDir, "post.jsonl")

        // Clear pending response after saving
        _pendingSurveyResponse.value = null

        logViewModel.addLogMessage("Post-music survey saved to recording folder")
        Log.d(TAG, "Saved post-music survey to recording directory")
    }

    /**
     * Get the current recording directory for random notifications
     * This is used by RecordingManager to know where to save data
     */
    fun getCurrentRecordingDir(): DocumentFile? = currentRecordingDir

    /**
     * Clear the current recording directory after recording stops
     */
    fun clearCurrentRecordingDir() {
        currentRecordingDir = null
        _randomRecordingName.value = null
    }

    /**
     * Save survey response to a JSONL file
     */
    private fun saveSurveyToFile(
        response: SurveyResponse,
        directory: DocumentFile,
        filename: String
    ) {
        try {
            val file = directory.findFile(filename)
                ?: directory.createFile("application/jsonl", filename)

            if (file == null) {
                logViewModel.addLogError("Failed to create survey file: $filename")
                return
            }

            val jsonObject = JSONObject().apply {
                put("timestamp", response.timestamp)
                put("pleasantness", response.pleasantness)
                put("energy", response.energy)
                put("notification_type", response.notificationType)
                // Add music_post specific fields if present
                response.musicLiking?.let { put("music_liking", it) }
                response.musicFamiliarity?.let { put("music_familiarity", it) }
                put("formatted_time", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(Date(response.timestamp)))
            }

            val jsonLine = jsonObject.toString() + "\n"

            context.contentResolver.openOutputStream(file.uri, "wa")?.use { outputStream ->
                outputStream.write(jsonLine.toByteArray())
                Log.d(TAG, "Survey data written to $filename: $jsonLine")
            } ?: run {
                logViewModel.addLogError("Failed to open output stream for $filename")
            }

        } catch (e: Exception) {
            logViewModel.addLogError("Error saving survey: ${e.message}")
            Log.e(TAG, "Error saving survey", e)
        }
    }

    /**
     * Check if there's a pending survey response
     */
    fun hasPendingSurvey(): Boolean = _pendingSurveyResponse.value != null

    /**
     * Clear pending survey response (called when new notification is opened)
     */
    fun clearPendingSurvey() {
        _pendingSurveyResponse.value = null
        _randomRecordingName.value = null
        Log.d(TAG, "Cleared pending survey for new session")
    }
}

