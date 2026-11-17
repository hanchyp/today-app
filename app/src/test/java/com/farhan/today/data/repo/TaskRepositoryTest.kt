package com.farhan.today.data.repo

import android.app.Application
import com.farhan.today.TestToday
import com.farhan.today.data.db.TaskStatus
import com.farhan.today.data.db.TodayDatabase
import com.farhan.today.fixedClock
import com.farhan.today.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TaskRepositoryTest {
    private lateinit var db: TodayDatabase
    private lateinit var repo: TaskRepository

    @Before fun setUp() {
        db = inMemoryDb()
        repo = TaskRepository(db, fixedClock())
    }

    @After fun tearDown() = db.close()

    @Test fun newTasksGoToEndOfTheirProject_andReorderWorks() = runTest {
        val p = repo.saveProject("Skripsi")
        val a = repo.saveTask(TaskInput("Bab 3", p, TestToday.plusDays(1)))
        val b = repo.saveTask(TaskInput("Bab 4", p))
        val loose = repo.saveTask(TaskInput("Beli tinta"))
        assertEquals(listOf(0, 1), listOf(a, b).map { repo.task(it)!!.position })
        assertEquals(0, repo.task(loose)!!.position)

        repo.reorder(listOf(b, a))
        assertEquals(listOf(1, 0), listOf(a, b).map { repo.task(it)!!.position })
    }

    @Test fun toggleDone_setsAndClearsCompletedAt_andUpdatesProgress() = runTest {
        val p = repo.saveProject("Skripsi")
        val a = repo.saveTask(TaskInput("Bab 3", p, TestToday.plusDays(1)))
        repo.saveTask(TaskInput("Bab 4", p, TestToday.plusDays(4)))

        assertEquals(TaskStatus.DONE, repo.toggleDone(a))
        assertNotNull(repo.task(a)!!.completedAt)
        val progress = repo.projects().first().single()
        assertEquals(1, progress.done)
        assertEquals(TestToday.plusDays(4), progress.nextDue) // tugas selesai tidak dihitung sebagai deadline berikutnya

        assertEquals(TaskStatus.TODO, repo.toggleDone(a))
        assertNull(repo.task(a)!!.completedAt)
    }

    @Test fun deleteAndRestore() = runTest {
        val a = repo.saveTask(TaskInput("Bab 3", dueDate = TestToday))
        repo.deleteTask(a)
        assertEquals(0, repo.allTasks().first().size)
        repo.restoreTask(a)
        assertEquals(listOf("Bab 3"), repo.allTasks().first().map { it.task.title })
    }

    @Test fun timeWithoutDateIsDropped() = runTest {
        val a = repo.saveTask(TaskInput("Tanpa tanggal", dueTime = LocalTime.of(9, 0)))
        assertNull(repo.task(a)!!.dueTime)
    }

    @Test fun deletingProjectHidesItsTasksFromAgenda() = runTest {
        val p = repo.saveProject("Skripsi")
        repo.saveTask(TaskInput("Bab 3", p, TestToday))
        repo.deleteProject(p)
        assertEquals(0, repo.tasksDueBetween(TestToday, TestToday).first().size)
        assertEquals(0, repo.projects().first().size)
    }
}

class TaskRulesTest {
    private val today = TestToday

    @Test fun deadlineBadges() {
        assertNull(deadlineBadge(null, today, done = false))
        assertEquals(DeadlineKind.DONE, deadlineBadge(today.minusDays(3), today, done = true)?.kind)
        assertEquals("Terlambat 1 hari", deadlineBadge(today.minusDays(1), today, false)?.label)
        assertEquals("Terlambat 3 hari", deadlineBadge(today.minusDays(3), today, false)?.label)
        assertEquals(DeadlineKind.TODAY, deadlineBadge(today, today, false)?.kind)
        assertEquals("Besok", deadlineBadge(today.plusDays(1), today, false)?.label)
        assertEquals("12 Nov", deadlineBadge(today.plusDays(3), today, false)?.label)
    }

    @Test fun progress() {
        assertEquals(0f, projectProgress(0, 0))
        assertEquals(0.25f, projectProgress(1, 4))
        assertEquals(1f, projectProgress(4, 4))
    }
}
