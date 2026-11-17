package com.farhan.today.domain

import com.farhan.today.data.db.SetLogEntity
import com.farhan.today.data.db.WorkoutSessionEntity
import kotlin.math.roundToLong

/** Logika latihan yang murni (tanpa DB) supaya mudah dites. */
object WorkoutMath {
    const val WEIGHT_STEP = 2.5

    /** Total volume = Σ beban × rep dari set yang selesai. */
    fun volume(sets: List<SetLogEntity>): Double = sets.filter { it.done }.sumOf { it.weightKg * it.reps }

    fun durationMinutes(session: WorkoutSessionEntity, now: Long? = null): Long {
        val start = session.startedAt ?: return 0
        val end = session.endedAt ?: now ?: return 0
        return ((end - start) / 60_000.0).roundToLong().coerceAtLeast(0)
    }

    /**
     * PR = beban terberat sesi ini untuk sebuah gerakan melebihi rekor sebelumnya.
     * Gerakan yang baru pertama kali dicatat tidak dihitung PR (belum ada pembanding).
     */
    fun personalRecords(sets: List<SetLogEntity>, previousBest: Map<String, Double>): Map<String, Double> =
        sets.filter { it.done && it.reps > 0 }
            .groupBy { it.exerciseId }
            .mapValues { (_, s) -> s.maxOf { it.weightKg } }
            .filter { (exerciseId, best) -> previousBest[exerciseId]?.let { best > it } == true }

    data class WeekStats(val done: Int, val target: Int, val minutes: Long)

    fun weekStats(doneThisWeek: List<WorkoutSessionEntity>, scheduledPerWeek: Int): WeekStats =
        WeekStats(
            done = doneThisWeek.size,
            target = maxOf(scheduledPerWeek, doneThisWeek.size),
            minutes = doneThisWeek.sumOf { durationMinutes(it) },
        )

    /** Beban dibulatkan ke kelipatan 0,5 kg dan tidak negatif. */
    fun stepWeight(current: Double, delta: Double): Double = (((current + delta) * 2).roundToLong() / 2.0).coerceAtLeast(0.0)
}

/** "42 kg", "42,5 kg" */
fun formatKg(kg: Double): String = formatNumber(kg) + " kg"

fun formatNumber(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else ("%.1f".format(java.util.Locale.ROOT, v)).replace('.', ',')

/** "42 mnt", "1j 04m" */
fun formatDuration(minutes: Long): String =
    if (minutes < 60) "$minutes mnt" else "${minutes / 60}j %02dm".format(minutes % 60)

/** "1.250 kg" untuk volume. */
fun formatVolume(kg: Double): String =
    kg.roundToLong().toString().reversed().chunked(3).joinToString(".").reversed() + " kg"
