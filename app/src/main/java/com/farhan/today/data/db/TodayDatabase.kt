package com.farhan.today.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    version = 3,
    exportSchema = true,
    entities = [
        ExerciseEntity::class, RoutineEntity::class, RoutineExerciseEntity::class,
        RoutineScheduleEntity::class, WorkoutSessionEntity::class, SetLogEntity::class,
        JobApplicationEntity::class, JobEventEntity::class, ApplicationStatusChangeEntity::class,
        CategoryEntity::class, BudgetEntity::class, MoneyTransactionEntity::class,
        BillEntity::class, BillPaymentEntity::class,
        ProjectEntity::class, TaskEntity::class,
        ReminderEntity::class, CalendarEvent::class, Wallet::class, WalletAllocation::class,
    ],
)
@TypeConverters(Converters::class)
abstract class TodayDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun careerDao(): CareerDao
    abstract fun financeDao(): FinanceDao
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao

    abstract fun walletDao(): WalletDao
    abstract fun calendarDao(): CalendarDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE money_transaction ADD COLUMN walletId TEXT")
                db.execSQL("ALTER TABLE money_transaction ADD COLUMN walletFunding INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS wallet (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS wallet_allocation (id TEXT NOT NULL PRIMARY KEY, walletId TEXT NOT NULL, amount INTEGER NOT NULL, weekStart INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS calendar_event (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, notes TEXT NOT NULL, date INTEGER NOT NULL, startTime INTEGER, endTime INTEGER, weeklyUntil INTEGER)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE routine_exercise ADD COLUMN setTargets TEXT")
            }
        }
        /**
         * Tidak memakai fallbackToDestructiveMigration: data pengguna tidak boleh hilang.
         * Perubahan skema setelah v1 wajib lewat Migration + tes migrasi.
         */
        fun create(context: Context): TodayDatabase =
            Room.databaseBuilder(context, TodayDatabase::class.java, "today.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
