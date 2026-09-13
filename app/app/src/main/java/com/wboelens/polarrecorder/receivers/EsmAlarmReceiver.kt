package com.wboelens.polarrecorder.receivers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wboelens.polarrecorder.PolarRecorderApplication
import com.wboelens.polarrecorder.managers.EsmScheduler
import com.wboelens.polarrecorder.ui.screens.SurveyActivity
import com.wboelens.polarrecorder.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Lightweight BroadcastReceiver that replaces the persistent SurveyNotificationService.
 *
 * Design principles (Section 8-B of the SYRA Design Document):
 *  - The receiver is woken by AlarmManager; it runs on a background coroutine, posts the
 *    survey notification, then immediately finishes — allowing the SoC to re-enter Doze.
 *  - No WakeLock is held beyond the goAsync() window (< 10 s).
 *  - The next daily probe alarm is set sequentially: each receiver firing registers the
 *    NEXT alarm, so no all-at-once scheduling; a device reboot re-triggers daily scheduling
 *    via the BOOT_COMPLETED receiver.
 *
 * Intent extras:
 *  - EXTRA_PROBE_INDEX   Int  — 0-based index of this probe within today's schedule
 *  - EXTRA_PROBE_TIMES   LongArray — today's full list of probe epoch-ms timestamps
 */
class EsmAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EsmAlarmReceiver"

        const val ACTION_ESM_PROBE      = "com.wboelens.polarrecorder.ACTION_ESM_PROBE"
        const val ACTION_SCHEDULE_DAY   = "com.wboelens.polarrecorder.ACTION_SCHEDULE_DAY"

        const val EXTRA_PROBE_INDEX = "probe_index"
        const val EXTRA_PROBE_TIMES = "probe_times"

        private const val DAILY_RESCHEDULE_REQUEST_CODE = 9001
        private const val PROBE_REQUEST_CODE_BASE       = 9010  // + probe index

        /**
         * Schedules the full optimal ESM probe sequence for today.
         *
         * Call once at app start (or boot) when the study mode requires ESM probes.
         * Cancels any existing alarms first to avoid duplicates.
         */
        suspend fun scheduleTodayProbes(context: Context, scheduler: EsmScheduler) {
            val probeTimesMs = scheduler.computeTodaySchedule()
            Log.d(TAG, "Scheduling ${probeTimesMs.size} ESM probes for today: $probeTimesMs")

            cancelAll(context)
            setProbeAlarm(context, probeTimesMs.toLongArray(), probeIndex = 0)
            scheduleDailyReschedule(context)
        }

        /**
         * Cancels all outstanding ESM alarms for today.
         */
        fun cancelAll(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            for (i in 0..3) {
                val pi = buildProbeIntent(context, LongArray(0), i)
                am.cancel(pi)
            }
            val reschedulePi = buildRescheduleIntent(context)
            am.cancel(reschedulePi)
            Log.d(TAG, "All ESM alarms cancelled")
        }

        // ── Internal helpers ────────────────────────────────────────────────────

        private fun setProbeAlarm(context: Context, probeTimes: LongArray, probeIndex: Int) {
            if (probeIndex >= probeTimes.size) {
                Log.d(TAG, "No more probes to schedule for today")
                return
            }

            val fireAtMs = probeTimes[probeIndex]
            val now = System.currentTimeMillis()

            if (fireAtMs <= now) {
                // Slot already passed; skip to the next
                Log.d(TAG, "Probe $probeIndex time $fireAtMs already passed — skipping")
                setProbeAlarm(context, probeTimes, probeIndex + 1)
                return
            }

            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = buildProbeIntent(context, probeTimes, probeIndex)

            // Use setExactAndAllowWhileIdle for reliable delivery even in Doze
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAtMs, pi)
            Log.d(TAG, "Alarm set for probe $probeIndex at $fireAtMs (in ${(fireAtMs - now) / 60_000} min)")
        }

        /** Schedules a daily 07:58 alarm to re-compute tomorrow's probe schedule. */
        private fun scheduleDailyReschedule(context: Context) {
            val cal = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
                set(java.util.Calendar.HOUR_OF_DAY, 7)
                set(java.util.Calendar.MINUTE, 58)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                buildRescheduleIntent(context)
            )
            Log.d(TAG, "Daily reschedule alarm set for tomorrow 07:58")
        }

        private fun buildProbeIntent(context: Context, probeTimes: LongArray, index: Int): PendingIntent {
            val intent = Intent(context, EsmAlarmReceiver::class.java).apply {
                action = ACTION_ESM_PROBE
                putExtra(EXTRA_PROBE_INDEX, index)
                putExtra(EXTRA_PROBE_TIMES, probeTimes)
            }
            return PendingIntent.getBroadcast(
                context,
                PROBE_REQUEST_CODE_BASE + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildRescheduleIntent(context: Context): PendingIntent {
            val intent = Intent(context, EsmAlarmReceiver::class.java).apply {
                action = ACTION_SCHEDULE_DAY
            }
            return PendingIntent.getBroadcast(
                context,
                DAILY_RESCHEDULE_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ESM_PROBE -> handleProbe(context, intent)
            ACTION_SCHEDULE_DAY -> handleDaySchedule(context)
            Intent.ACTION_BOOT_COMPLETED -> handleDaySchedule(context)
            else -> Log.w(TAG, "Unknown action: ${intent.action}")
        }
    }

    // ── Probe handling ──────────────────────────────────────────────────────────

    private fun handleProbe(context: Context, intent: Intent) {
        val probeIndex = intent.getIntExtra(EXTRA_PROBE_INDEX, 0)
        val probeTimes = intent.getLongArrayExtra(EXTRA_PROBE_TIMES) ?: LongArray(0)

        Log.d(TAG, "ESM probe fired: index=$probeIndex")

        // Skip if recording is currently active
        val recordingManager = PolarRecorderApplication.recordingManager
        if (recordingManager.isRecording.value) {
            Log.d(TAG, "Recording active — skipping ESM probe notification")
        } else {
            showEsmNotification(context)
        }

        // Schedule the next probe in the chain (sequential alarm registration)
        val nextIndex = probeIndex + 1
        if (nextIndex < probeTimes.size) {
            setProbeAlarm(context, probeTimes, nextIndex)
        } else {
            Log.d(TAG, "All probes for today delivered")
        }
    }

    private fun handleDaySchedule(context: Context) {
        Log.d(TAG, "Daily reschedule triggered — computing tomorrow's ESM schedule")

        // Verify study mode still requires ESM probes
        val prefs = PolarRecorderApplication.preferencesManager
        if (!prefs.requiresEsmScheduler) {
            Log.d(TAG, "Study mode ${prefs.studyMode} does not require ESM scheduler — skipping")
            return
        }

        // Run scheduling on IO dispatcher (DB queries); goAsync() gives up to 10s
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = PolarRecorderApplication.syraDatabase
                val scheduler = EsmScheduler(context, db)
                scheduleTodayProbes(context, scheduler)
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling ESM probes: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    // ── Notification ────────────────────────────────────────────────────────────

    private fun showEsmNotification(context: Context) {
        val surveyIntent = Intent(context, SurveyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", "random")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NotificationHelper.RANDOM_PROBE_NOTIFICATION_ID,
            surveyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        NotificationHelper.showRandomProbeNotification(context, pendingIntent)
        Log.d(TAG, "ESM probe notification posted")
    }
}
