package com.farhan.today.data.db

import android.app.Application
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pola tes migrasi (Sprint 5). Saat skema naik ke v2:
 * 1. tambah `Migration(1, 2)` di TodayDatabase.MIGRATIONS,
 * 2. tambah tes: createDatabase(v1) → isi data → runMigrationsAndValidate(v2, true, MIGRATION_1_2) → cek data.
 * Untuk v1, tes ini memastikan skema JSON yang diekspor cocok dengan entity (identity hash).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TodayDatabase::class.java)

    @Test fun migrateV1ToCurrent_keepsData() = runTest {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO project (id, name, description, colorKey, deadlineDate, archived, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('p1', 'Skripsi', '', 'tasks', NULL, 0, 0, 0, NULL)"
            )
            close()
        }
        // Room memvalidasi identity hash skema saat dibuka; gagal bila entity & skema JSON tidak cocok.
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), TodayDatabase::class.java, dbName)
            .addMigrations(TodayDatabase.MIGRATION_1_2, TodayDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        assertEquals(listOf("Skripsi"), db.taskDao().projects().first().map { it.project.name })
        db.close()
    }

    @Test fun migrateV2ToV3_keepsLegacyRoutineTargets() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL("INSERT INTO exercise (id, name, muscleGroup, equipment, createdAt, updatedAt) VALUES ('e1', 'Bench press', 'CHEST', 'BARBELL', 0, 0)")
            db.execSQL("INSERT INTO routine (id, name, focus, archived, createdAt, updatedAt) VALUES ('r1', 'Upper', '', 0, 0, 0)")
            db.execSQL(
                "INSERT INTO routine_exercise (id, routineId, exerciseId, position, targetSets, targetReps, targetWeightKg, restSeconds, createdAt, updatedAt) " +
                    "VALUES ('re1', 'r1', 'e1', 0, 3, 8, 40, 90, 0, 0)"
            )
        }
        helper.runMigrationsAndValidate(dbName, 3, true, TodayDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT targetSets, targetReps, targetWeightKg, restSeconds, setTargets FROM routine_exercise").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals(3, row.getInt(0))
                assertEquals(8, row.getInt(1))
                assertEquals(40.0, row.getDouble(2), 0.0)
                assertEquals(90, row.getInt(3))
                assertTrue(row.isNull(4))
            }
        }
    }
}
