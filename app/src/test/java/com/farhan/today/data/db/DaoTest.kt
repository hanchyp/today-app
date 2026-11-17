package com.farhan.today.data.db

import android.app.Application
import com.farhan.today.TestToday
import com.farhan.today.inMemoryDb
import com.farhan.today.millisAt
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
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DaoTest {
    private lateinit var db: TodayDatabase
    private val now = millisAt(TestToday, 9, 41)

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() = db.close()

    // ------------------------------------------------------------ Latihan

    @Test fun workout_sessionWithExerciseCount_andSoftDelete() = runTest {
        val dao = db.workoutDao()
        val bench = ExerciseEntity(name = "Bench press", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BARBELL, createdAt = now, updatedAt = now)
        val ohp = ExerciseEntity(name = "Overhead press", muscleGroup = MuscleGroup.SHOULDERS, equipment = Equipment.BARBELL, createdAt = now, updatedAt = now)
        dao.upsertExercises(listOf(bench, ohp))
        val push = RoutineEntity(name = "Push Day", focus = "Dada", createdAt = now, updatedAt = now)
        dao.upsertRoutine(push)
        dao.upsertRoutineExercises(listOf(bench, ohp).mapIndexed { i, e ->
            RoutineExerciseEntity(routineId = push.id, exerciseId = e.id, position = i, targetSets = 3, targetReps = 8, targetWeightKg = 40.0, restSeconds = 90, createdAt = now, updatedAt = now)
        })
        val session = WorkoutSessionEntity(routineId = push.id, name = "Push Day", plannedDate = TestToday, plannedTime = LocalTime.of(17, 0), status = SessionStatus.PLANNED, createdAt = now, updatedAt = now)
        dao.upsertSession(session)

        val rows = dao.sessionsBetween(TestToday, TestToday).first()
        assertEquals(1, rows.size)
        assertEquals(2, rows[0].exerciseCount)
        assertEquals(LocalTime.of(17, 0), rows[0].session.plannedTime)

        dao.softDeleteSession(session.id, now)
        assertTrue(dao.sessionsBetween(TestToday, TestToday).first().isEmpty())
    }

    @Test fun workout_schedulesHideDeletedRoutine() = runTest {
        val dao = db.workoutDao()
        val r = RoutineEntity(name = "Leg Day", focus = "Kaki", createdAt = now, updatedAt = now)
        dao.upsertRoutine(r)
        dao.upsertSchedules(listOf(RoutineScheduleEntity(routineId = r.id, dayOfWeek = 6, time = LocalTime.of(7, 30), createdAt = now, updatedAt = now)))
        assertEquals("Leg Day", dao.schedules().first().single().routineName)

        dao.softDeleteRoutine(r.id, now)
        assertTrue(dao.schedules().first().isEmpty())
    }

    // ------------------------------------------------------------ Karier

    @Test fun career_eventsBetweenJoinCompany_andHideDeletedApplication() = runTest {
        val dao = db.careerDao()
        val app = JobApplicationEntity(company = "GDP Labs", role = "Intern", status = ApplicationStatus.CODING_TEST, createdAt = now, updatedAt = now)
        dao.upsertApplication(app)
        dao.upsertEvent(JobEventEntity(applicationId = app.id, type = JobEventType.CODING_TEST, title = "Coding test", startAt = millisAt(TestToday, 20), durationMinutes = 90, mode = EventMode.ONLINE, createdAt = now, updatedAt = now))
        dao.upsertEvent(JobEventEntity(applicationId = app.id, type = JobEventType.INTERVIEW, title = "Interview", startAt = millisAt(TestToday.plusDays(3), 10), durationMinutes = 60, mode = EventMode.ONSITE, createdAt = now, updatedAt = now))

        val today = dao.eventsBetween(millisAt(TestToday, 0), millisAt(TestToday.plusDays(1), 0)).first()
        assertEquals(listOf("Coding test"), today.map { it.event.title })
        assertEquals("GDP Labs", today.single().company)

        dao.softDeleteApplication(app.id, now)
        assertTrue(dao.eventsBetween(0, Long.MAX_VALUE).first().isEmpty())
        assertTrue(dao.applications().first().isEmpty())
    }

    // ------------------------------------------------------------ Keuangan

    @Test fun finance_expenseTotalsRespectRangeKindCategoryAndSoftDelete() = runTest {
        val dao = db.financeDao()
        val food = CategoryEntity(name = "Makan", kind = MoneyKind.EXPENSE, iconKey = "food", colorKey = "finance", position = 0, createdAt = now, updatedAt = now)
        val salary = CategoryEntity(name = "Gaji", kind = MoneyKind.INCOME, iconKey = "trend", colorKey = "income", position = 0, createdAt = now, updatedAt = now)
        dao.upsertCategories(listOf(food, salary))
        val lunch = MoneyTransactionEntity(kind = MoneyKind.EXPENSE, amount = 24_000, categoryId = food.id, occurredAt = millisAt(TestToday, 12), createdAt = now, updatedAt = now)
        dao.upsertTransactions(
            listOf(
                lunch,
                MoneyTransactionEntity(kind = MoneyKind.EXPENSE, amount = 8_000, categoryId = null, occurredAt = millisAt(TestToday, 8), createdAt = now, updatedAt = now),
                MoneyTransactionEntity(kind = MoneyKind.INCOME, amount = 500_000, categoryId = salary.id, occurredAt = millisAt(TestToday, 9), createdAt = now, updatedAt = now),
                // minggu lalu — di luar rentang
                MoneyTransactionEntity(kind = MoneyKind.EXPENSE, amount = 99_000, categoryId = food.id, occurredAt = millisAt(TestToday.minusDays(7), 12), createdAt = now, updatedAt = now),
            )
        )
        val from = millisAt(TestToday, 0)
        val to = millisAt(TestToday.plusDays(1), 0)
        assertEquals(32_000L, dao.expenseTotal(from, to).first())
        assertEquals(24_000L, dao.expenseTotalForCategory(food.id, from, to).first())

        dao.softDeleteTransaction(lunch.id, now)
        assertEquals(8_000L, dao.expenseTotal(from, to).first())
        assertEquals(0L, dao.expenseTotalForCategory(food.id, from, to).first())
    }

    @Test fun finance_activeBillsExcludeInactiveAndDeleted() = runTest {
        val dao = db.financeDao()
        val gym = BillEntity(name = "Gym", amount = 150_000, categoryId = null, repeat = BillRepeat.MONTHLY, nextDueDate = TestToday.plusDays(3), createdAt = now, updatedAt = now)
        dao.upsertBill(gym)
        dao.upsertBill(gym.copy(id = newId(), name = "Lama", active = false))
        val wifi = gym.copy(id = newId(), name = "Wifi")
        dao.upsertBill(wifi)
        dao.softDeleteBill(wifi.id, now)
        assertEquals(listOf("Gym"), dao.activeBills().first().map { it.name })
    }

    // ------------------------------------------------------------ Tugas

    @Test fun tasks_projectProgress_dueRange_andSoftDelete() = runTest {
        val dao = db.taskDao()
        val p = ProjectEntity(name = "Skripsi", createdAt = now, updatedAt = now)
        dao.upsertProject(p)
        val revisi = TaskEntity(projectId = p.id, title = "Revisi Bab 3", dueDate = TestToday.plusDays(1), createdAt = now, updatedAt = now)
        dao.upsertTasks(
            listOf(
                revisi,
                TaskEntity(projectId = p.id, title = "Olah data", dueDate = TestToday.plusDays(4), createdAt = now, updatedAt = now),
                TaskEntity(projectId = p.id, title = "Referensi", status = TaskStatus.DONE, dueDate = TestToday.minusDays(2), createdAt = now, updatedAt = now),
            )
        )
        val progress = dao.projects().first().single()
        assertEquals(3, progress.total)
        assertEquals(1, progress.done)
        assertEquals(TestToday.plusDays(1), progress.nextDue)

        val due = dao.tasksDueBetween(TestToday, TestToday.plusDays(2)).first()
        assertEquals(listOf("Revisi Bab 3"), due.map { it.task.title })
        assertEquals("Skripsi", due.single().projectName)

        // Daftar semua tugas: aktif dulu, selesai di akhir
        assertEquals("Referensi", dao.allTasks().first().last().task.title)

        dao.softDeleteTask(revisi.id, now)
        assertTrue(dao.tasksDueBetween(TestToday, TestToday.plusDays(2)).first().isEmpty())
        assertEquals(2, dao.projects().first().single().total)
    }
}
