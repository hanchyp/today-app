package com.farhan.today.data.repo

import androidx.room.withTransaction
import com.farhan.today.data.db.Equipment
import com.farhan.today.data.db.ExerciseEntity
import com.farhan.today.data.db.MuscleGroup
import com.farhan.today.data.db.ReminderSource
import com.farhan.today.data.db.RoutineEntity
import com.farhan.today.data.db.RoutineExerciseEntity
import com.farhan.today.data.db.RoutineScheduleEntity
import com.farhan.today.data.db.RoutineSetTarget
import com.farhan.today.data.db.SeedData
import com.farhan.today.data.db.SessionStatus
import com.farhan.today.data.db.SetLogEntity
import com.farhan.today.data.db.TodayDatabase
import com.farhan.today.data.db.WorkoutSessionEntity
import com.farhan.today.data.db.newId
import com.farhan.today.domain.WorkoutMath
import com.farhan.today.domain.formatTime
import com.farhan.today.reminder.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

data class RoutineExerciseInput(
    val exerciseId: String,
    val sets: Int = 3,
    val reps: Int = 8,
    val weightKg: Double? = null,
    val restSeconds: Int = 90,
    val setTargets: List<RoutineSetTarget>? = null,
)

data class RoutineInput(
    val name: String,
    val focus: String = "",
    val exercises: List<RoutineExerciseInput> = emptyList(),
    val days: Set<DayOfWeek> = emptySet(),
    val time: LocalTime = LocalTime.of(17, 0),
) {
    val isValid: Boolean get() = name.isNotBlank() && exercises.isNotEmpty()
}

data class PersonalRecord(val exerciseId: String, val exerciseName: String, val weightKg: Double, val previousKg: Double)

data class SessionSummary(
    val session: WorkoutSessionEntity,
    val minutes: Long,
    val volumeKg: Double,
    val setsDone: Int,
    val setsTotal: Int,
    val records: List<PersonalRecord>,
)

class WorkoutRepository(
    private val db: TodayDatabase,
    private val clock: Clock,
    private val reminders: ReminderScheduler? = null,
    private val catalogIds: () -> Set<String> = { emptySet() },
    /** Pengingat latihan 30 menit sebelum jadwal aktif? (dari DataStore) */
    private val remindersEnabled: suspend () -> Boolean = { false },
) {
    private val dao = db.workoutDao()

    // ------------------------------------------------------------ baca

    fun sessionsBetween(start: LocalDate, end: LocalDate) = dao.sessionsBetween(start, end)
    fun schedules() = dao.schedules()
    fun routines() = dao.routines()
    fun exercises() = dao.exercises()
    fun catalog() = exercises().map { exercises ->
        val ids = catalogIds()
        exercises.filter { it.id in ids || Triple(it.name, it.muscleGroup, it.equipment) !in SeedData.exerciseCatalog }
    }
    fun allRoutineExercises() = dao.allRoutineExercises()
    fun session(id: String) = dao.session(id)
    fun sets(sessionId: String) = dao.sets(sessionId)
    fun inProgress() = dao.inProgress()
    @OptIn(ExperimentalCoroutinesApi::class)
    fun homeSessions() = flow {
        while (true) {
            val date = LocalDate.now(clock)
            emit(date)
            val nextDay = date.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
            delay((nextDay - clock.millis()).coerceAtLeast(1L))
        }
    }.distinctUntilChanged().flatMapLatest(dao::homeSessions)
    fun history(limit: Int = 20) = dao.history(limit)
    fun weightHistory(exerciseId: String) = dao.weightHistory(exerciseId)
    suspend fun routineExercises(routineId: String) = dao.routineExercisesOnce(routineId)
    suspend fun routine(id: String) = dao.routineOnce(id)
    suspend fun schedulesFor(routineId: String) = dao.schedulesForRoutineOnce(routineId)
    suspend fun exercise(id: String) = dao.exerciseOnce(id)
    suspend fun lastSets(exerciseId: String, excludeSessionId: String) = dao.lastSets(exerciseId, excludeSessionId)
    suspend fun recentExercises() = dao.recentExercises(limit = 8)

    /** Statistik minggu berjalan (Senin–Minggu). */
    fun weekStats(): Flow<WorkoutMath.WeekStats> {
        val today = LocalDate.now(clock)
        val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return combine(dao.doneBetween(start, start.plusDays(6)), dao.schedules().map { it.size }) { done, perWeek ->
            WorkoutMath.weekStats(done, perWeek)
        }
    }

    // ------------------------------------------------------------ rutinitas & gerakan

    suspend fun createExercise(name: String, group: MuscleGroup, equipment: Equipment = Equipment.OTHER): String {
        require(name.isNotBlank()) { "Nama gerakan wajib diisi" }
        val now = clock.millis()
        val e = ExerciseEntity(name = name.trim(), muscleGroup = group, equipment = equipment, createdAt = now, updatedAt = now)
        dao.upsertExercise(e)
        return e.id
    }

    suspend fun renameExercise(id: String, name: String, group: MuscleGroup) {
        require(name.isNotBlank()) { "Nama gerakan wajib diisi" }
        val e = dao.exerciseOnce(id) ?: return
        dao.upsertExercise(e.copy(name = name.trim(), muscleGroup = group, updatedAt = clock.millis()))
    }

    /**
     * Buat/ubah rutinitas. Gerakan & jadwal lama diganti seluruhnya; sesi PLANNED ke depan dibuat ulang
     * agar nama/jam/hari baru langsung terpakai. Riwayat (DONE/IN_PROGRESS) tidak disentuh.
     */
    suspend fun saveRoutine(input: RoutineInput, id: String? = null): String {
        require(input.isValid) { "Nama dan minimal satu gerakan wajib diisi" }
        val now = clock.millis()
        val today = LocalDate.now(clock)
        val routineId = db.withTransaction {
            val existing = id?.let { dao.routineOnce(it) }
            val routine = RoutineEntity(
                id = existing?.id ?: newId(), name = input.name.trim(), focus = input.focus.trim(),
                archived = false, createdAt = existing?.createdAt ?: now, updatedAt = now,
            )
            dao.upsertRoutine(routine)
            dao.softDeleteRoutineExercises(routine.id, now)
            dao.softDeleteSchedules(routine.id, now)
            dao.upsertRoutineExercises(input.exercises.mapIndexed { i, e ->
                val targets = e.setTargets?.take(20)?.takeIf { it.isNotEmpty() }?.map {
                    RoutineSetTarget(it.reps.coerceIn(1, 100), it.weightKg?.takeIf { kg -> kg.isFinite() && kg >= 0 })
                }
                RoutineExerciseEntity(
                    routineId = routine.id, exerciseId = e.exerciseId, position = i,
                    targetSets = targets?.size ?: e.sets.coerceIn(1, 20),
                    targetReps = targets?.first()?.reps ?: e.reps.coerceIn(1, 100),
                    targetWeightKg = if (targets == null) e.weightKg else targets.first().weightKg,
                    restSeconds = e.restSeconds.coerceIn(0, 600),
                    createdAt = now, updatedAt = now, setTargets = targets,
                )
            })
            dao.upsertSchedules(input.days.sortedBy { it.value }.map {
                RoutineScheduleEntity(routineId = routine.id, dayOfWeek = it.value, time = input.time, createdAt = now, updatedAt = now)
            })
            routine.id
        }
        dao.plannedFrom(routineId, today).forEach { removePlanned(it, now) }
        planSessions()
        return routineId
    }

    suspend fun deleteRoutine(id: String) {
        val now = clock.millis()
        dao.plannedFrom(id, LocalDate.now(clock)).forEach { removePlanned(it, now) }
        dao.softDeleteRoutine(id, now)
    }

    private suspend fun removePlanned(s: WorkoutSessionEntity, now: Long) {
        dao.softDeleteSession(s.id, now)
        reminders?.cancel(ReminderSource.WORKOUT, s.id)
    }

    // ------------------------------------------------------------ perencana

    /**
     * Membuat sesi PLANNED untuk [days] hari ke depan dari jadwal mingguan.
     * Idempoten: tanggal yang sudah punya sesi apa pun (termasuk SKIPPED) dilewati.
     * Juga menyelaraskan pengingat 30 menit sebelum jadwal.
     */
    suspend fun planSessions(days: Int = 7): Int {
        val now = clock.millis()
        val today = LocalDate.now(clock)
        val schedules = dao.activeSchedulesOnce()
        var created = 0
        db.withTransaction {
            for (offset in 0 until days) {
                val date = today.plusDays(offset.toLong())
                schedules.filter { it.dayOfWeek == date.dayOfWeek.value }.forEach { sc ->
                    if (dao.countSessionsOn(sc.routineId, date) == 0) {
                        val routine = dao.routineOnce(sc.routineId) ?: return@forEach
                        dao.upsertSession(
                            WorkoutSessionEntity(
                                routineId = routine.id, name = routine.name, plannedDate = date, plannedTime = sc.time,
                                status = SessionStatus.PLANNED, createdAt = now, updatedAt = now,
                            )
                        )
                        created++
                    }
                }
            }
        }
        syncReminders()
        return created
    }

    /** Pasang/batalkan pengingat untuk semua sesi PLANNED ke depan sesuai pengaturan. */
    suspend fun syncReminders() {
        val r = reminders ?: return
        val enabled = remindersEnabled()
        dao.allPlannedFrom(LocalDate.now(clock)).forEach { s ->
            val time = s.plannedTime
            if (!enabled || time == null) {
                r.cancel(ReminderSource.WORKOUT, s.id)
            } else {
                val at = s.plannedDate.atTime(time).atZone(clock.zone).toInstant().toEpochMilli()
                r.schedule(ReminderSource.WORKOUT, s.id, at - 30 * 60_000L, "${s.name} 30 menit lagi", "Latihan jam ${formatTime(time)}. Siapkan botol minum!")
            }
        }
    }

    // ------------------------------------------------------------ sesi

    /** Sesi dadakan hari ini dari rutinitas (tanpa menunggu jadwal). */
    suspend fun createSessionNow(routineId: String): String {
        val routine = dao.routineOnce(routineId) ?: error("Rutinitas tidak ditemukan")
        val now = clock.millis()
        val s = WorkoutSessionEntity(
            routineId = routine.id, name = routine.name, plannedDate = LocalDate.now(clock),
            plannedTime = LocalTime.now(clock).withSecond(0).withNano(0),
            status = SessionStatus.PLANNED, createdAt = now, updatedAt = now,
        )
        dao.upsertSession(s)
        return s.id
    }

    /**
     * Mulai sesi: salin gerakan rutinitas menjadi baris set (snapshot), sehingga mengubah rutinitas
     * nanti tidak mengubah sesi ini. Beban awal = beban terakhir, lalu target, lalu 0.
     * Hanya satu sesi boleh berjalan; jika ada, sesi itu yang dikembalikan.
     */
    suspend fun startSession(sessionId: String): String {
        dao.inProgressOnce()?.let { if (it.id != sessionId) return it.id }
        val s = dao.sessionOnce(sessionId) ?: error("Sesi tidak ditemukan")
        if (s.status == SessionStatus.IN_PROGRESS) return s.id
        val now = clock.millis()
        val plan = s.routineId?.let { dao.routineExercisesOnce(it) }.orEmpty()
        val sets = plan.flatMapIndexed { order, row ->
            val last = dao.lastSets(row.item.exerciseId, s.id)
            (0 until row.item.targetSets).map { i ->
                val target = row.item.setTargets?.getOrNull(i)
                SetLogEntity(
                    sessionId = s.id, exerciseId = row.item.exerciseId, exerciseOrder = order, setIndex = i,
                    weightKg = target?.weightKg ?: last.getOrNull(i)?.weightKg ?: last.lastOrNull()?.weightKg
                        ?: row.item.targetWeightKg.takeIf { target == null } ?: 0.0,
                    reps = target?.reps ?: row.item.targetReps,
                    done = false, completedAt = null, createdAt = now, updatedAt = now,
                )
            }
        }
        db.withTransaction {
            dao.softDeleteSetsOf(s.id, now)
            dao.upsertSets(sets)
            dao.upsertSession(s.copy(status = SessionStatus.IN_PROGRESS, startedAt = now, endedAt = null, updatedAt = now))
        }
        reminders?.cancel(ReminderSource.WORKOUT, s.id)
        return s.id
    }

    suspend fun updateSet(setId: String, weightKg: Double, reps: Int) {
        val set = dao.setOnce(setId) ?: return
        dao.upsertSets(listOf(set.copy(weightKg = weightKg.coerceAtLeast(0.0), reps = reps.coerceIn(0, 999), updatedAt = clock.millis())))
    }

    /** Membalik status selesai sebuah set; mengembalikan detik istirahat bila set baru saja diselesaikan. */
    suspend fun toggleSetDone(setId: String): Int? {
        val set = dao.setOnce(setId) ?: return null
        val now = clock.millis()
        val done = !set.done
        dao.upsertSets(listOf(set.copy(done = done, completedAt = if (done) now else null, updatedAt = now)))
        if (!done) return null
        val routineId = dao.sessionOnce(set.sessionId)?.routineId
        return routineId?.let { id -> dao.routineExercisesOnce(id).firstOrNull { it.item.exerciseId == set.exerciseId }?.item?.restSeconds } ?: 90
    }

    suspend fun addSet(sessionId: String, exerciseOrder: Int) {
        val sets = dao.setsOnce(sessionId).filter { it.exerciseOrder == exerciseOrder }
        val last = sets.maxByOrNull { it.setIndex } ?: return
        val now = clock.millis()
        dao.upsertSets(listOf(last.copy(id = newId(), setIndex = last.setIndex + 1, done = false, completedAt = null, createdAt = now, updatedAt = now)))
    }

    suspend fun deleteSet(setId: String) = dao.softDeleteSet(setId, clock.millis())

    /** Tambah gerakan di tengah sesi (di akhir urutan). */
    suspend fun addExerciseToSession(sessionId: String, exerciseId: String, sets: Int = 3, reps: Int = 8) {
        val existing = dao.setsOnce(sessionId)
        val order = (existing.maxOfOrNull { it.exerciseOrder } ?: -1) + 1
        val last = dao.lastSets(exerciseId, sessionId)
        val now = clock.millis()
        dao.upsertSets((0 until sets).map { i ->
            SetLogEntity(
                sessionId = sessionId, exerciseId = exerciseId, exerciseOrder = order, setIndex = i,
                weightKg = last.getOrNull(i)?.weightKg ?: last.lastOrNull()?.weightKg ?: 0.0, reps = reps,
                done = false, completedAt = null, createdAt = now, updatedAt = now,
            )
        })
    }

    suspend fun finishSession(sessionId: String): SessionSummary {
        val s = dao.sessionOnce(sessionId) ?: error("Sesi tidak ditemukan")
        val now = clock.millis()
        dao.upsertSession(s.copy(status = SessionStatus.DONE, endedAt = now, startedAt = s.startedAt ?: now, updatedAt = now))
        return summary(sessionId)
    }

    /** Batalkan sesi berjalan: set dihapus, sesi kembali PLANNED. */
    suspend fun deleteHistory(sessionId: String) = db.withTransaction {
        val session = dao.sessionOnce(sessionId) ?: return@withTransaction
        if (session.status != SessionStatus.DONE) return@withTransaction
        val now = clock.millis()
        dao.softDeleteSetsOf(sessionId, now)
        dao.softDeleteSession(sessionId, now)
    }

    suspend fun discardSession(sessionId: String) {
        val s = dao.sessionOnce(sessionId) ?: return
        val now = clock.millis()
        db.withTransaction {
            dao.softDeleteSetsOf(sessionId, now)
            dao.upsertSession(s.copy(status = SessionStatus.PLANNED, startedAt = null, endedAt = null, updatedAt = now))
        }
    }

    suspend fun skipSession(sessionId: String) {
        val s = dao.sessionOnce(sessionId) ?: return
        dao.upsertSession(s.copy(status = SessionStatus.SKIPPED, updatedAt = clock.millis()))
        reminders?.cancel(ReminderSource.WORKOUT, sessionId)
    }

    /** Ringkasan sesi (dipakai layar "Selesai latihan" dan detail riwayat). */
    suspend fun summary(sessionId: String): SessionSummary {
        val s = dao.sessionOnce(sessionId) ?: error("Sesi tidak ditemukan")
        val sets = dao.setsOnce(sessionId)
        val before = dao.bestsBefore(s.endedAt ?: clock.millis(), sessionId).associate { it.exerciseId to it.bestKg }
        val prs = WorkoutMath.personalRecords(sets, before).map { (exId, kg) ->
            PersonalRecord(exId, dao.exerciseOnce(exId)?.name ?: "Gerakan", kg, before.getValue(exId))
        }
        return SessionSummary(
            session = s,
            minutes = WorkoutMath.durationMinutes(s, clock.millis()),
            volumeKg = WorkoutMath.volume(sets),
            setsDone = sets.count { it.done },
            setsTotal = sets.size,
            records = prs,
        )
    }
}
