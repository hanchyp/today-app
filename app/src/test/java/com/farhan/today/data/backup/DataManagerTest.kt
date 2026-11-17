package com.farhan.today.data.backup

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.farhan.today.TestToday
import com.farhan.today.data.db.Equipment
import com.farhan.today.data.db.ExerciseEntity
import com.farhan.today.data.db.MuscleGroup
import com.farhan.today.data.db.ReminderSource
import com.farhan.today.data.db.SeedData
import com.farhan.today.data.db.TodayDatabase
import com.farhan.today.data.prefs.UserPrefs
import com.farhan.today.data.repo.CareerRepository
import com.farhan.today.data.repo.EventInput
import com.farhan.today.data.repo.FinanceRepository
import com.farhan.today.data.repo.TaskInput
import com.farhan.today.data.repo.TaskRepository
import com.farhan.today.data.db.EventMode
import com.farhan.today.data.db.JobEventType
import com.farhan.today.fixedClock
import com.farhan.today.inMemoryDb
import com.farhan.today.reminder.FakeAlarmGateway
import com.farhan.today.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DataManagerTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: TodayDatabase
    private lateinit var prefs: UserPrefs
    private val clock = fixedClock()
    private val gateway = FakeAlarmGateway()
    private lateinit var reminders: ReminderScheduler
    private lateinit var manager: DataManager

    @Before fun setUp() {
        db = inMemoryDb()
        prefs = UserPrefs(PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) { tmp.newFile("prefs.preferences_pb") })
        reminders = ReminderScheduler(db.reminderDao(), gateway, clock)
        manager = DataManager(db, prefs, reminders, clock) { now -> catalog.map { it.copy(createdAt = now, updatedAt = now) } }
    }

    /** Pengganti katalog aset (exercise-catalog.json) yang di aplikasi dimuat oleh AppContainer. */
    private val catalog = listOf(
        ExerciseEntity(id = "cat-1", name = "Push-up", muscleGroup = MuscleGroup.CHEST, equipment = Equipment.BODYWEIGHT, createdAt = 0, updatedAt = 0),
        ExerciseEntity(id = "cat-2", name = "Squat", muscleGroup = MuscleGroup.LEGS, equipment = Equipment.BARBELL, createdAt = 0, updatedAt = 0),
        ExerciseEntity(id = "cat-3", name = "Plank", muscleGroup = MuscleGroup.CORE, equipment = Equipment.BODYWEIGHT, createdAt = 0, updatedAt = 0),
    )

    @After fun tearDown() = db.close()

    private suspend fun counts(): Map<String, Int> = DataManager.TABLES.associateWith { t ->
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$t`").use { it.moveToFirst(); it.getInt(0) }
    }

    private suspend fun dump(table: String): String =
        db.openHelper.readableDatabase.query("SELECT * FROM `$table` ORDER BY id").use { c ->
            buildString { while (c.moveToNext()) { for (i in 0 until c.columnCount) append(c.getString(i)).append('|'); append('\n') } }
        }

    @Test fun backupWipeRestore_roundTripIsExact() = runTest {
        SeedData(db, clock).seed()
        // Seperti AppContainer.start(): katalog aset selalu sudah ada sebelum backup
        db.workoutDao().insertCatalogExercises(catalog)
        // Data dengan pengingat aktif dan nilai NULL/desimal
        val career = CareerRepository(db, clock, reminders)
        val app = career.applications().first().first()
        career.saveEvent(EventInput(app.id, JobEventType.INTERVIEW, "Interview HR", LocalDateTime.of(TestToday.plusDays(2), LocalTime.of(14, 0)), 60, EventMode.ONSITE, "Jl. Sudirman", 60))
        TaskRepository(db, clock).saveTask(TaskInput("Tanpa deadline"))
        prefs.setUserName("Farhan")

        val before = counts()
        val snapshots = DataManager.TABLES.associateWith { dump(it) }
        val json = manager.export()

        manager.wipe()
        assertTrue(counts().getValue("money_transaction") == 0)
        assertTrue(gateway.active.isEmpty())
        prefs.setUserName("Orang lain")

        val info = manager.import(json)
        assertEquals(before, counts())
        DataManager.TABLES.forEach { assertEquals("Tabel $it", snapshots.getValue(it), dump(it)) }
        assertEquals(before.values.sum(), info.rows)
        assertEquals("Farhan", prefs.settingsOnce().userName)
        // Pengingat masa depan dipasang ulang setelah restore
        assertTrue(gateway.active.isNotEmpty())
        assertTrue(db.reminderDao().scheduled().any { it.sourceType == ReminderSource.JOB_EVENT })
    }

    @Test fun backupFileHasSchemaVersionAndAllTables() = runTest {
        SeedData(db, clock).seed()
        val root = JSONObject(manager.export())
        assertEquals("today", root.getString("app"))
        assertEquals(DataManager.SCHEMA_VERSION, root.getInt("schemaVersion"))
        assertEquals(DataManager.TABLES.toSet(), root.getJSONObject("tables").keys().asSequence().toSet())
        assertEquals("today-backup-20261109.json", DataManager.fileName(TestToday))
    }

    @Test fun inspectRejectsForeignNewerAndBrokenFiles() {
        fun fails(json: String, contains: String) {
            val e = runCatching { manager.inspect(json) }.exceptionOrNull()
            assertTrue("Harus gagal: $json", e is BackupException)
            assertTrue(e!!.message!!, e.message!!.contains(contains))
        }
        fails("bukan json", "valid")
        fails("""{"app":"lain","schemaVersion":1,"exportedAt":0,"tables":{}}""", "bukan backup")
        fails("""{"app":"today","schemaVersion":99,"exportedAt":0,"tables":{}}""", "lebih baru")
        fails("""{"app":"today","schemaVersion":1,"exportedAt":0}""", "tidak berisi")
    }

    @Test fun failedImportChangesNothing() = runTest {
        SeedData(db, clock).seed()
        val before = counts()
        // Baris rusak (kolom wajib hilang) → seluruh impor dibatalkan
        val broken = """{"app":"today","schemaVersion":1,"exportedAt":0,"tables":{"category":[{"id":"x"}]}}"""
        assertTrue(runCatching { manager.import(broken) }.isFailure)
        assertEquals(before, counts())
    }

    @Test fun wipeKeepsCatalogAndCategories() = runTest {
        SeedData(db, clock).seed()
        manager.wipe()
        val c = counts()
        assertEquals(catalog.size, c.getValue("exercise"))
        assertEquals(SeedData.expenseCategories.size + SeedData.incomeCategories.size, c.getValue("category"))
        listOf("routine", "workout_session", "job_application", "budget", "money_transaction", "bill", "project", "task", "reminder")
            .forEach { assertEquals(it, 0, c.getValue(it)) }
        assertTrue(FinanceRepository(db, clock).categories().first().isNotEmpty())
    }
}
