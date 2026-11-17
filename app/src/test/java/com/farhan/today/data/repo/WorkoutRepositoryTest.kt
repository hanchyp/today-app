package com.farhan.today.data.repo

import android.app.Application
import com.farhan.today.Jakarta
import com.farhan.today.TestToday
import com.farhan.today.data.db.Equipment
import com.farhan.today.data.db.ExerciseEntity
import com.farhan.today.data.db.MuscleGroup
import com.farhan.today.data.db.ReminderSource
import com.farhan.today.data.db.RoutineSetTarget
import com.farhan.today.data.db.SessionStatus
import com.farhan.today.data.db.SetLogEntity
import com.farhan.today.data.db.TodayDatabase
import com.farhan.today.data.db.WorkoutSessionEntity
import com.farhan.today.domain.WorkoutMath
import com.farhan.today.domain.formatDuration
import com.farhan.today.domain.formatKg
import com.farhan.today.domain.formatVolume
import com.farhan.today.inMemoryDb
import com.farhan.today.millisAt
import com.farhan.today.reminder.FakeAlarmGateway
import com.farhan.today.reminder.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Jam yang bisa dimajukan, untuk mensimulasikan durasi latihan. */
class MutableClock(var millis: Long, private val zone: ZoneId = Jakarta) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(millis, zone)
    override fun instant(): Instant = Instant.ofEpochMilli(millis)
    fun advanceMinutes(m: Long) { millis += m * 60_000 }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutRepositoryTest {
    private lateinit var db: TodayDatabase
    private val clock = MutableClock(millisAt(TestToday, 9, 41)) // Senin
    private val gateway = FakeAlarmGateway()
    private var remindersOn = false
    private lateinit var repo: WorkoutRepository
    private lateinit var bench: String
    private lateinit var ohp: String

    @Before fun setUp() = runTest {
        db = inMemoryDb()
        repo = WorkoutRepository(db, clock, ReminderScheduler(db.reminderDao(), gateway, clock)) { remindersOn }
        bench = repo.createExercise("Bench press", MuscleGroup.CHEST, Equipment.BARBELL)
        ohp = repo.createExercise("Overhead press", MuscleGroup.SHOULDERS, Equipment.BARBELL)
    }

    @After fun tearDown() = db.close()

    private suspend fun pushDay(days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)) = repo.saveRoutine(
        RoutineInput(
            "Push Day", "Dada, bahu, trisep",
            listOf(RoutineExerciseInput(bench, sets = 3, reps = 8, weightKg = 40.0, restSeconds = 120), RoutineExerciseInput(ohp, sets = 2, reps = 10)),
            days, LocalTime.of(17, 0),
        )
    )

    private suspend fun planned() = db.workoutDao().allPlannedFrom(TestToday)

    @Test fun saveRoutine_plansSessionsForTheNextSevenDays() = runTest {
        pushDay()
        assertEquals(listOf(TestToday, TestToday.plusDays(3)), planned().map { it.plannedDate }.sorted())
        assertTrue(planned().all { it.plannedTime == LocalTime.of(17, 0) && it.name == "Push Day" })
    }

    @Test fun workoutHome_showsOnlyTodaysScheduleAndResumableSession() = runTest {
        pushDay()
        val today = planned().first { it.plannedDate == TestToday }
        val tomorrow = millisAt(TestToday.plusDays(1), 9)

        assertEquals(listOf(today.id), repo.homeSessions().first().map { it.session.id })
        clock.millis = tomorrow
        assertTrue(repo.homeSessions().first().isEmpty())

        clock.millis = millisAt(TestToday, 17)
        repo.startSession(today.id)
        clock.millis = tomorrow
        val running = repo.homeSessions().first().single().session
        assertEquals(today.id, running.id)
        assertEquals(SessionStatus.IN_PROGRESS, running.status)
    }

    @Test fun planSessions_isIdempotent_andRespectsSkipped() = runTest {
        pushDay()
        assertEquals(0, repo.planSessions())
        assertEquals(2, planned().size)

        val today = planned().first { it.plannedDate == TestToday }
        repo.skipSession(today.id)
        assertEquals(0, repo.planSessions()) // tanggal yang dilewati tidak dibuat ulang
        assertEquals(listOf(TestToday.plusDays(3)), planned().map { it.plannedDate })

        // Jumat: jendela Jum–Kam memuat Senin & Kamis minggu depan (Kamis ini sudah ada)
        clock.millis = millisAt(TestToday.plusDays(4), 8)
        assertEquals(2, repo.planSessions())
        assertEquals(0, repo.planSessions())
    }

    @Test fun changingScheduleReplansFuturePlannedSessions() = runTest {
        val id = pushDay()
        repo.saveRoutine(RoutineInput("Push Day", "", listOf(RoutineExerciseInput(bench)), setOf(DayOfWeek.WEDNESDAY), LocalTime.of(6, 30)), id)
        assertEquals(listOf(TestToday.plusDays(2)), planned().map { it.plannedDate })
        assertEquals(LocalTime.of(6, 30), planned().single().plannedTime)
    }

    @Test fun startSession_snapshotsRoutine_andUsesLastWeights() = runTest {
        pushDay()
        val first = planned().first { it.plannedDate == TestToday }
        repo.startSession(first.id)
        val sets = db.workoutDao().setsOnce(first.id)
        assertEquals(5, sets.size) // 3 bench + 2 ohp
        assertEquals(40.0, sets.first { it.exerciseId == bench }.weightKg, 0.0)
        assertEquals(0.0, sets.first { it.exerciseId == ohp }.weightKg, 0.0)

        // Catat bench 42,5 kg lalu selesai
        sets.filter { it.exerciseId == bench }.forEach { repo.updateSet(it.id, 42.5, 8); repo.toggleSetDone(it.id) }
        clock.advanceMinutes(45)
        repo.finishSession(first.id)

        assertEquals(listOf(bench), repo.recentExercises().map { it.id })

        // Sesi berikutnya memakai beban terakhir, bukan target
        val next = planned().first()
        repo.startSession(next.id)
        assertTrue(db.workoutDao().setsOnce(next.id).filter { it.exerciseId == bench }.all { it.weightKg == 42.5 })
    }

    @Test fun startSession_usesIndividualRoutineSetTargets() = runTest {
        val targets = listOf(RoutineSetTarget(12, 20.0), RoutineSetTarget(8, 40.5), RoutineSetTarget(6, null))
        val routine = repo.saveRoutine(RoutineInput(
            "Upper",
            exercises = listOf(RoutineExerciseInput(bench, setTargets = targets, restSeconds = 120)),
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        ))
        assertEquals(targets, repo.routineExercises(routine).single().item.setTargets)
        val first = planned().first()
        repo.startSession(first.id)
        val firstSets = db.workoutDao().setsOnce(first.id)
        assertEquals(listOf(12, 8, 6), firstSets.map { it.reps })
        assertEquals(listOf(20.0, 40.5, 0.0), firstSets.map { it.weightKg })
        firstSets.forEach { set ->
            repo.updateSet(set.id, 30.0, set.reps)
            assertEquals(120, repo.toggleSetDone(set.id))
        }
        clock.advanceMinutes(45)
        repo.finishSession(first.id)

        val next = planned().first()
        repo.startSession(next.id)
        assertEquals(listOf(20.0, 40.5, 30.0), db.workoutDao().setsOnce(next.id).map { it.weightKg })
    }

    @Test fun onlyOneSessionRunsAtATime() = runTest {
        pushDay()
        val (a, b) = planned().sortedBy { it.plannedDate }
        assertEquals(a.id, repo.startSession(a.id))
        assertEquals(a.id, repo.startSession(b.id))
        assertEquals(a.id, repo.inProgress().first()?.id)
    }

    @Test fun toggleSetDone_returnsRestSecondsFromRoutine() = runTest {
        pushDay()
        val s = planned().first { it.plannedDate == TestToday }
        repo.startSession(s.id)
        val benchSet = db.workoutDao().setsOnce(s.id).first { it.exerciseId == bench }
        assertEquals(120, repo.toggleSetDone(benchSet.id))
        assertEquals(null, repo.toggleSetDone(benchSet.id)) // dibatalkan → tanpa timer
    }

    @Test fun summary_volumeDurationAndPersonalRecord() = runTest {
        pushDay()
        val s1 = planned().first { it.plannedDate == TestToday }
        repo.startSession(s1.id)
        db.workoutDao().setsOnce(s1.id).filter { it.exerciseId == bench }.forEach { repo.updateSet(it.id, 40.0, 8); repo.toggleSetDone(it.id) }
        clock.advanceMinutes(42)
        val first = repo.finishSession(s1.id)
        assertEquals(42, first.minutes)
        assertEquals(3 * 40.0 * 8, first.volumeKg, 0.0)
        assertEquals(3, first.setsDone)
        assertEquals(5, first.setsTotal)
        assertTrue(first.records.isEmpty()) // pertama kali: belum ada pembanding

        clock.advanceMinutes(60 * 24 * 3)
        val s2 = repo.createSessionNow(repo.routines().first().first().id)
        repo.startSession(s2)
        val sets2 = db.workoutDao().setsOnce(s2).filter { it.exerciseId == bench }
        repo.updateSet(sets2[0].id, 45.0, 5); repo.toggleSetDone(sets2[0].id)
        clock.advanceMinutes(30)
        val second = repo.finishSession(s2)
        assertEquals(listOf("Bench press" to 45.0), second.records.map { it.exerciseName to it.weightKg })
        assertEquals(40.0, second.records.single().previousKg, 0.0)
    }

    @Test fun editingRoutine_doesNotChangeHistory() = runTest {
        val id = pushDay()
        val s = planned().first { it.plannedDate == TestToday }
        repo.startSession(s.id)
        db.workoutDao().setsOnce(s.id).forEach { repo.toggleSetDone(it.id) }
        clock.advanceMinutes(40)
        repo.finishSession(s.id)

        repo.saveRoutine(RoutineInput("Upper Body", "", listOf(RoutineExerciseInput(ohp, sets = 5)), setOf(DayOfWeek.FRIDAY)), id)

        val hist = repo.history().first().single()
        assertEquals("Push Day", hist.session.name)
        assertEquals(5, hist.setsDone)
        assertEquals(2, hist.exerciseCount)
    }

    @Test fun weekStats_countDoneSessionsAgainstSchedule() = runTest {
        pushDay()
        val s = planned().first { it.plannedDate == TestToday }
        repo.startSession(s.id)
        clock.advanceMinutes(50)
        repo.finishSession(s.id)
        val stats = repo.weekStats().first()
        assertEquals(1, stats.done)
        assertEquals(2, stats.target)
        assertEquals(50, stats.minutes)
    }

    @Test fun reminders_followSetting() = runTest {
        pushDay()
        assertTrue(gateway.active.isEmpty())
        remindersOn = true
        repo.syncReminders()
        assertEquals(setOf(millisAt(TestToday, 16, 30), millisAt(TestToday.plusDays(3), 16, 30)), gateway.active.values.toSet())

        // Mulai sesi → pengingatnya dibatalkan
        repo.startSession(planned().first { it.plannedDate == TestToday }.id)
        assertEquals(setOf(millisAt(TestToday.plusDays(3), 16, 30)), gateway.active.values.toSet())

        remindersOn = false
        repo.syncReminders()
        assertTrue(gateway.active.isEmpty())
        assertTrue(db.reminderDao().scheduled().none { it.sourceType == ReminderSource.WORKOUT })
    }

    @Test fun discardSession_returnsToPlanned() = runTest {
        pushDay()
        val s = planned().first { it.plannedDate == TestToday }
        repo.startSession(s.id)
        repo.discardSession(s.id)
        assertEquals(SessionStatus.PLANNED, db.workoutDao().sessionOnce(s.id)?.status)
        assertTrue(db.workoutDao().setsOnce(s.id).isEmpty())
    }
}

class WorkoutMathTest {
    private fun set(ex: String, kg: Double, reps: Int, done: Boolean = true) = SetLogEntity(
        sessionId = "s", exerciseId = ex, exerciseOrder = 0, setIndex = 0, weightKg = kg, reps = reps,
        done = done, completedAt = null, createdAt = 0, updatedAt = 0,
    )

    @Test fun volumeCountsOnlyDoneSets() {
        assertEquals(40.0 * 8 + 42.5 * 6, WorkoutMath.volume(listOf(set("b", 40.0, 8), set("b", 42.5, 6), set("b", 50.0, 5, done = false))), 0.0)
    }

    @Test fun personalRecordsNeedAPreviousBest() {
        val sets = listOf(set("bench", 45.0, 5), set("ohp", 30.0, 8), set("squat", 60.0, 5))
        val prs = WorkoutMath.personalRecords(sets, mapOf("bench" to 42.5, "ohp" to 30.0))
        assertEquals(mapOf("bench" to 45.0), prs) // ohp menyamai (bukan PR), squat belum ada pembanding
    }

    @Test fun weekStatsTargetNeverBelowDone() {
        val s = WorkoutSessionEntity(routineId = null, name = "x", plannedDate = TestToday, plannedTime = null, startedAt = 0, endedAt = 30 * 60_000, status = SessionStatus.DONE, createdAt = 0, updatedAt = 0)
        assertEquals(WorkoutMath.WeekStats(3, 3, 90), WorkoutMath.weekStats(listOf(s, s, s), scheduledPerWeek = 2))
    }

    @Test fun stepWeightAndFormatting() {
        assertEquals(42.5, WorkoutMath.stepWeight(40.0, 2.5), 0.0)
        assertEquals(0.0, WorkoutMath.stepWeight(1.0, -2.5), 0.0)
        assertEquals("42,5 kg", formatKg(42.5))
        assertEquals("40 kg", formatKg(40.0))
        assertEquals("42 mnt", formatDuration(42))
        assertEquals("1j 04m", formatDuration(64))
        assertEquals("1.250 kg", formatVolume(1250.0))
    }

    @Suppress("unused") private val keep = ExerciseEntity(name = "", muscleGroup = MuscleGroup.CORE, equipment = Equipment.OTHER, createdAt = 0, updatedAt = 0)
}
