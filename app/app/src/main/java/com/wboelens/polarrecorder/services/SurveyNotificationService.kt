package com.wboelens.polarrecorder.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.wboelens.polarrecorder.PolarRecorderApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SurveyNotificationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var notificationJob: Job? = null
    private var notificationCount = 0

    companion object {
        private const val TAG = "SurveyNotificationService"
        private const val NOTIFICATION_CHANNEL_ID = "survey_reminder_channel"
        private const val SERVICE_NOTIFICATION_ID = 2001

        // For testing: 30 seconds (commented out)
        // private const val TEST_INTERVAL_MS = 30_000L

        // For production: Random intervals between 2-4 hours (fits 3-5 probes in a ~10h day)
        private const val MIN_INTERVAL_HOURS = 2
        private const val MAX_INTERVAL_HOURS = 4
        private const val MAX_DAILY_NOTIFICATIONS = 5
        private const val RANDOM_PROBE_REQUEST_CODE = 3001

        fun start(context: Context) {
            val intent = Intent(context, SurveyNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        @Suppress("unused")
        fun stop(context: Context) {
            val intent = Intent(context, SurveyNotificationService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        startForeground(SERVICE_NOTIFICATION_ID, createServiceNotification())
        startNotificationScheduler()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startNotificationScheduler() {
        notificationJob = serviceScope.launch {
            while (isActive) {
                /* For testing: Send notification every 30 seconds (commented out)
                delay(TEST_INTERVAL_MS)
                sendSurveyNotification()
                */

                // For production: Random intervals between 2-6 hours
                // Check if we've reached the daily limit
                if (notificationCount >= MAX_DAILY_NOTIFICATIONS) {
                    Log.d(TAG, "Daily notification limit reached. Resetting at midnight.")
                    // Wait until next day (simplified - should use AlarmManager for accuracy)
                    delay(calculateDelayUntilMidnight())
                    notificationCount = 0
                }

                // Calculate random delay between MIN and MAX hours
                val randomHours = kotlin.random.Random.nextInt(MIN_INTERVAL_HOURS, MAX_INTERVAL_HOURS + 1)
                val delayMs = randomHours * 60 * 60 * 1000L

                Log.d(TAG, "Next notification in $randomHours hours")
                delay(delayMs)
                sendSurveyNotification()
            }
        }
    }

    private fun sendSurveyNotification() {
        // Don't send notification if recording is already active
        val recordingManager = PolarRecorderApplication.recordingManager
        if (recordingManager.isRecording.value) {
            Log.d(TAG, "Recording is active, skipping survey notification")
            return
        }

        notificationCount++
        Log.d(TAG, "Sending random ESM probe notification (#$notificationCount)")

        val intent = Intent(this, com.wboelens.polarrecorder.ui.screens.SurveyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", "random")
        }

        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            RANDOM_PROBE_REQUEST_CODE,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        com.wboelens.polarrecorder.utils.NotificationHelper.showRandomProbeNotification(this, pendingIntent)
    }

    private fun createServiceNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Survey Reminder")
            .setContentText("Running in background")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setSilent(true)
            .setSound(null)
            .setVibrate(null)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Survey Reminders",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Periodic reminders to fill survey"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // For production: Calculate delay until midnight
    private fun calculateDelayUntilMidnight(): Long {
        val calendar = java.util.Calendar.getInstance()
        val now = calendar.timeInMillis

        // Set to next midnight
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)

        return calendar.timeInMillis - now
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        notificationJob?.cancel()
    }
}

