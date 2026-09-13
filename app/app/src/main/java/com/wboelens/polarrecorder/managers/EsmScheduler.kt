package com.wboelens.polarrecorder.managers

import android.content.Context
import android.util.Log
import com.wboelens.polarrecorder.database.SyraDatabase
import java.util.Calendar
import kotlin.random.Random

/**
 * Usage-Informed Optimal ESM Prompting Algorithm.
 *
 * Implements Section 9 of the SYRA Research Design Document. All processing is local-only;
 * no data ever leaves the device.
 *
 * The waking day (08:00 – 22:00) is divided into 30-minute slots t ∈ [1..28].
 * For each slot a weight is computed:
 *   W(t) = α · P(Response|t) + β · P(Music|t) + γ · P(Active|t)
 *
 * N probe times are selected by weighted roulette-wheel sampling, with a minimum
 * spacing guard (D_min = 90 minutes) to prevent prompt fatigue.
 */
class EsmScheduler(
    private val context: Context,
    private val db: SyraDatabase
) {
    companion object {
        private const val TAG = "EsmScheduler"

        // Waking window
        private const val DAY_START_HOUR = 8   // 08:00
        private const val DAY_END_HOUR = 22    // 22:00

        // Slot duration in minutes
        private const val SLOT_MINUTES = 30

        // Total number of 30-min slots in the waking window  (08:00–22:00 = 14h = 28 slots)
        private const val TOTAL_SLOTS = ((DAY_END_HOUR - DAY_START_HOUR) * 60) / SLOT_MINUTES

        // Number of probes to schedule each day
        private const val PROBES_PER_DAY = 4

        // Minimum gap between consecutive probes (in minutes)
        private const val MIN_GAP_MINUTES = 90

        // How many days of history to aggregate
        private const val HISTORY_DAYS = 7

        // Linear combination weights (must sum to 1.0)
        private const val ALPHA = 0.5f  // historical response rate
        private const val BETA  = 0.3f  // music-listening presence
        private const val GAMMA = 0.2f  // general phone/screen activity
    }

    /**
     * Computes N optimal probe times for today using weighted slot sampling.
     *
     * @return List of epoch-millisecond timestamps (today's dates) for each probe,
     *         sorted ascending. Returns uniform fallback times if history is insufficient.
     */
    suspend fun computeTodaySchedule(): List<Long> {
        Log.d(TAG, "Computing today's ESM schedule")

        val weights = computeSlotWeights()
        Log.d(TAG, "Slot weights computed: ${weights.map { "%.2f".format(it) }}")

        val chosenSlots = selectSlots(weights, PROBES_PER_DAY)
        Log.d(TAG, "Chosen slots (0-indexed): $chosenSlots")

        return chosenSlots.map { slotToEpochMs(it) }.sorted()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Weight computation
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Queries the local DB and builds a weight array of size [TOTAL_SLOTS].
     * Falls back to uniform weights (1/TOTAL_SLOTS) when no history exists.
     */
    private suspend fun computeSlotWeights(): FloatArray {
        val cutoff = System.currentTimeMillis() - HISTORY_DAYS * 24 * 60 * 60 * 1000L

        // Fetch all survey responses within the history window
        val responses = try {
            db.surveyResponseDao().getResponsesSince(cutoff)
        } catch (e: Exception) {
            Log.w(TAG, "Could not query survey history — using uniform weights: ${e.message}")
            return FloatArray(TOTAL_SLOTS) { 1f / TOTAL_SLOTS }
        }

        // Fetch all music-session records (start events) within the history window
        val musicEvents = try {
            db.sessionDao().getSessionsSince(cutoff)
        } catch (e: Exception) {
            Log.w(TAG, "Could not query session history: ${e.message}")
            emptyList()
        }

        // --- P(Response | slot) -------------------------------------------------
        // Count total delivered and total completed per slot
        val totalDelivered = IntArray(TOTAL_SLOTS) { 0 }
        val totalCompleted = IntArray(TOTAL_SLOTS) { 0 }

        for (resp in responses) {
            val slot = epochMsToSlot(resp.timestamp) ?: continue
            totalDelivered[slot]++
            if (resp.responsesJson.isNotBlank()) totalCompleted[slot]++
        }

        val pResponse = FloatArray(TOTAL_SLOTS) { i ->
            if (totalDelivered[i] == 0) 0f
            else totalCompleted[i].toFloat() / totalDelivered[i].toFloat()
        }

        // --- P(Music | slot) ----------------------------------------------------
        // Count sessions that started in each slot
        val musicCount = IntArray(TOTAL_SLOTS) { 0 }
        val musicTotal = IntArray(TOTAL_SLOTS) { 0 }

        for (session in musicEvents) {
            val slot = epochMsToSlot(session.startTime) ?: continue
            musicTotal[slot]++
            // A session is "music active" — count toward the slot
            musicCount[slot]++
        }

        // Normalise by the maximum observed count (avoids division if all zero)
        val maxMusic = musicCount.max().takeIf { it > 0 } ?: 1
        val pMusic = FloatArray(TOTAL_SLOTS) { i -> musicCount[i].toFloat() / maxMusic }

        // --- P(Active | slot) ---------------------------------------------------
        // We don't have direct screen-on data, so we proxy it with any database
        // activity (responses + sessions combined) as a general activity indicator.
        val activityCount = IntArray(TOTAL_SLOTS) { i -> totalDelivered[i] + musicTotal[i] }
        val maxActivity = activityCount.max().takeIf { it > 0 } ?: 1
        val pActive = FloatArray(TOTAL_SLOTS) { i -> activityCount[i].toFloat() / maxActivity }

        // --- Combined weight W(t) -----------------------------------------------
        val weights = FloatArray(TOTAL_SLOTS) { i ->
            ALPHA * pResponse[i] + BETA * pMusic[i] + GAMMA * pActive[i]
        }

        // If all weights are zero (no history), fall back to uniform distribution
        val weightSum = weights.sum()
        if (weightSum == 0f) {
            Log.d(TAG, "No usable history — using uniform slot weights")
            return FloatArray(TOTAL_SLOTS) { 1f / TOTAL_SLOTS }
        }

        // Normalise to sum = 1.0
        return FloatArray(TOTAL_SLOTS) { i -> weights[i] / weightSum }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Weighted roulette-wheel selection with interval guard
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Selects [n] slot indices by weighted roulette-wheel sampling, enforcing
     * a minimum gap of [MIN_GAP_MINUTES] between any two chosen slots.
     *
     * If valid slots are exhausted before [n] probes are chosen, returns what
     * it has found (could be fewer than [n]).
     */
    private fun selectSlots(weights: FloatArray, n: Int): List<Int> {
        val chosen = mutableListOf<Int>()
        val remaining = weights.copyOf()   // mutable working copy

        repeat(n) {
            val candidate = rouletteWheel(remaining) ?: return@repeat

            // Interval guard: check if this slot is far enough from all already-chosen slots
            val minGapSlots = MIN_GAP_MINUTES / SLOT_MINUTES   // e.g. 90 / 30 = 3 slots
            val tooClose = chosen.any { kotlin.math.abs(it - candidate) < minGapSlots }

            if (!tooClose) {
                chosen.add(candidate)
                // Zero out the chosen slot and its neighbours so it isn't re-selected
                for (blocked in maxOf(0, candidate - minGapSlots + 1)
                        ..minOf(TOTAL_SLOTS - 1, candidate + minGapSlots - 1)) {
                    remaining[blocked] = 0f
                }
            } else {
                // Slot too close — zero it out and try to pick again in the same iteration
                remaining[candidate] = 0f
            }
        }

        return chosen.sorted()
    }

    /**
     * One spin of the roulette wheel: picks an index proportionally to its weight.
     * Returns null if all weights are zero.
     */
    private fun rouletteWheel(weights: FloatArray): Int? {
        val total = weights.sum()
        if (total <= 0f) return null

        val r = Random.nextFloat() * total
        var cumulative = 0f
        for (i in weights.indices) {
            cumulative += weights[i]
            if (r <= cumulative) return i
        }
        // Fallback (floating-point edge case): return the last non-zero index
        return weights.indexOfLast { it > 0f }.takeIf { it >= 0 }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Time helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Converts a slot index (0-based) to an absolute epoch-millisecond timestamp
     * representing today's corresponding 30-minute window start time.
     */
    private fun slotToEpochMs(slot: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, DAY_START_HOUR + (slot * SLOT_MINUTES) / 60)
            set(Calendar.MINUTE, (slot * SLOT_MINUTES) % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Add a small random jitter within the slot so probes don't always fire
        // exactly on the half-hour boundary (adds naturalness).
        val jitterMs = Random.nextLong(0L, SLOT_MINUTES * 60 * 1000L)
        return cal.timeInMillis + jitterMs
    }

    /**
     * Maps an epoch-millisecond timestamp to a slot index (0-based) relative to
     * today's waking window. Returns null if the timestamp is outside the window.
     */
    private fun epochMsToSlot(epochMs: Long): Int? {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        if (hour < DAY_START_HOUR || hour >= DAY_END_HOUR) return null

        val minutesFromStart = (hour - DAY_START_HOUR) * 60 + minute
        return minutesFromStart / SLOT_MINUTES
    }
}
