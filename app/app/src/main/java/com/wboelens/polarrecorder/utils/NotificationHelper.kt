package com.wboelens.polarrecorder.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.wboelens.polarrecorder.ui.screens.SurveyActivity

object NotificationHelper {
    private const val CHANNEL_ID = "survey_notifications"
    private const val CHANNEL_NAME = "Survey Notifications"
    const val MUSIC_POST_NOTIFICATION_ID = 2001
    const val MUSIC_PRE_NOTIFICATION_ID = 2002
    const val SENSOR_DISCONNECTED_NOTIFICATION_ID = 2003
    const val RANDOM_PROBE_NOTIFICATION_ID = 2004

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for music listening surveys"
                enableVibration(true)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showMusicPostNotification(context: Context) {
        createNotificationChannel(context)

        // Vibrate to get user's attention
        vibrateDevice(context)

        val intent = Intent(context, SurveyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", "music_post")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Done listening?")
            .setContentText("Please fill this survey")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(120000) // 120 seconds
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(MUSIC_POST_NOTIFICATION_ID, notification)
    }

    fun showMusicPreNotification(context: Context, intent: Intent) {
        createNotificationChannel(context)

        // Vibrate to get user's attention
        vibrateDevice(context)

        val pendingIntent = PendingIntent.getActivity(
            context,
            1, // Different request code from post notification
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Music Started")
            .setContentText("Tap to fill out the pre-listening survey")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(300000) // 5 minutes timeout just in case
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(MUSIC_PRE_NOTIFICATION_ID, notification)
    }

    fun cancelMusicPostNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(MUSIC_POST_NOTIFICATION_ID)
    }

    fun showRandomProbeNotification(context: Context, pendingIntent: android.app.PendingIntent) {
        createNotificationChannel(context)
        vibrateDevice(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("How are you feeling?")
            .setContentText("Tap to do a quick 3-minute check-in.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setTimeoutAfter(15 * 60 * 1000L) // dismiss after 15 minutes if ignored
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(RANDOM_PROBE_NOTIFICATION_ID, notification)
    }

    fun showSensorDisconnectedNotification(context: Context, deviceId: String) {
        showSensorAlert(
            context,
            title = "⚠️ Sensor Disconnected",
            body = "Polar $deviceId lost connection. Please re-wear your sensor."
        )
    }

    fun showSensorNotConnectedNotification(context: Context) {
        showSensorAlert(
            context,
            title = "⚠️ Polar Sensor Not Connected",
            body = "Music detected but no sensor is worn. Please put on your Polar sensor."
        )
    }

    private fun showSensorAlert(context: Context, title: String, body: String) {
        createNotificationChannel(context)
        vibrateDevice(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setOngoing(true)   // Persistent — stays until explicitly cancelled
            .setAutoCancel(false)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(SENSOR_DISCONNECTED_NOTIFICATION_ID, notification)
    }

    fun cancelSensorDisconnectedNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(SENSOR_DISCONNECTED_NOTIFICATION_ID)
    }

    private fun vibrateDevice(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Short vibrate for 300ms
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }
    }
}

