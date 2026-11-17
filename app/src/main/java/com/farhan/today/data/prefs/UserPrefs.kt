package com.farhan.today.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/** Modul yang bisa disembunyikan dari navigasi bawah. */
enum class AppModule(val label: String) { WORKOUT("Latihan"), CAREER("Karier"), FINANCE("Keuangan"), TASKS("Tugas") }

data class Settings(
    val userName: String,
    val modules: Set<AppModule>,
    val dailySummary: Boolean,
    val dailySummaryTime: LocalTime,
    /** Menit sebelum interview/coding test; null = tanpa pengingat. */
    val interviewReminder: Int?,
    val billReminderDays: Int,
    val workoutReminders: Boolean,
    /** Notifikasi timer istirahat dibuat bergetar (mulai, 10 detik terakhir, selesai) agar diteruskan ke smartband. */
    val bandRestAlerts: Boolean,
    val weekStartsSunday: Boolean,
    val backupReminder: Boolean,
    val lastBackupAt: Long?,
)

class UserPrefs(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.dataStore)

    private object Keys {
        val NAME = stringPreferencesKey("user_name")
        val SEEDED = booleanPreferencesKey("seeded_v1")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val MODULES = stringSetPreferencesKey("modules")
        val WORKOUT_REMINDERS = booleanPreferencesKey("workout_reminders")
        val REST_ENDS_AT = longPreferencesKey("rest_ends_at")
        val REST_TOTAL = intPreferencesKey("rest_total_seconds")
        val REST_SESSION = stringPreferencesKey("rest_session_id")
        val REST_EXERCISE = stringPreferencesKey("rest_exercise_name")
        val BAND_REST = booleanPreferencesKey("band_rest_alerts")
        val DAILY_SUMMARY = booleanPreferencesKey("daily_summary")
        val DAILY_SUMMARY_MIN = intPreferencesKey("daily_summary_minutes")
        val INTERVIEW_REMINDER = intPreferencesKey("interview_reminder") // -1 = tidak ada
        val BILL_REMINDER_DAYS = intPreferencesKey("bill_reminder_days")
        val WEEK_SUNDAY = booleanPreferencesKey("week_starts_sunday")
        val BACKUP_REMINDER = booleanPreferencesKey("backup_reminder")
        val LAST_BACKUP = longPreferencesKey("last_backup_at")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            userName = p[Keys.NAME] ?: "Farhan",
            modules = p[Keys.MODULES]?.mapNotNull { runCatching { AppModule.valueOf(it) }.getOrNull() }?.toSet() ?: AppModule.entries.toSet(),
            dailySummary = p[Keys.DAILY_SUMMARY] ?: false,
            dailySummaryTime = (p[Keys.DAILY_SUMMARY_MIN] ?: (6 * 60 + 30)).let { LocalTime.of(it / 60, it % 60) },
            interviewReminder = (p[Keys.INTERVIEW_REMINDER] ?: 30).takeIf { it >= 0 },
            billReminderDays = p[Keys.BILL_REMINDER_DAYS] ?: 1,
            workoutReminders = p[Keys.WORKOUT_REMINDERS] ?: false,
            bandRestAlerts = p[Keys.BAND_REST] ?: false,
            weekStartsSunday = p[Keys.WEEK_SUNDAY] ?: false,
            backupReminder = p[Keys.BACKUP_REMINDER] ?: false,
            lastBackupAt = p[Keys.LAST_BACKUP],
        )
    }
    suspend fun settingsOnce(): Settings = settings.first()

    val userName: Flow<String> = settings.map { it.userName }
    val weekStartsSunday: Flow<Boolean> = settings.map { it.weekStartsSunday }
    val onboarded: Flow<Boolean> = store.data.map { it[Keys.ONBOARDED] ?: false }

    suspend fun setUserName(name: String) { store.edit { it[Keys.NAME] = name.trim() } }
    suspend fun setOnboarded(done: Boolean = true) { store.edit { it[Keys.ONBOARDED] = done } }
    suspend fun setModules(modules: Set<AppModule>) { store.edit { it[Keys.MODULES] = modules.map(AppModule::name).toSet() } }
    suspend fun setDailySummary(on: Boolean, time: LocalTime? = null) {
        store.edit { p ->
            p[Keys.DAILY_SUMMARY] = on
            time?.let { p[Keys.DAILY_SUMMARY_MIN] = it.hour * 60 + it.minute }
        }
    }
    suspend fun setInterviewReminder(minutes: Int?) { store.edit { it[Keys.INTERVIEW_REMINDER] = minutes ?: -1 } }
    suspend fun setBillReminderDays(days: Int) { store.edit { it[Keys.BILL_REMINDER_DAYS] = days } }
    suspend fun setWeekStartsSunday(on: Boolean) { store.edit { it[Keys.WEEK_SUNDAY] = on } }
    suspend fun setBackupReminder(on: Boolean) { store.edit { it[Keys.BACKUP_REMINDER] = on } }
    suspend fun markBackedUp(at: Long) { store.edit { it[Keys.LAST_BACKUP] = at } }

    suspend fun isSeeded(): Boolean = store.data.first()[Keys.SEEDED] == true
    suspend fun markSeeded() { store.edit { it[Keys.SEEDED] = true } }

    // ---------------- Latihan (Sprint 3)

    /** Pengingat 30 menit sebelum latihan terjadwal (default mati). */
    val workoutReminders: Flow<Boolean> = settings.map { it.workoutReminders }
    suspend fun workoutRemindersOnce(): Boolean = settingsOnce().workoutReminders
    suspend fun setWorkoutReminders(on: Boolean) { store.edit { it[Keys.WORKOUT_REMINDERS] = on } }
    suspend fun setBandRestAlerts(on: Boolean) { store.edit { it[Keys.BAND_REST] = on } }

    /** Timer istirahat disimpan sebagai waktu selesai, jadi tetap benar walau aplikasi ditutup. */
    val restTimer: Flow<Pair<Long, Int>?> = store.data.map { p ->
        val end = p[Keys.REST_ENDS_AT] ?: return@map null
        end to (p[Keys.REST_TOTAL] ?: 0)
    }
    suspend fun setRestTimer(endsAt: Long?, totalSeconds: Int = 0, sessionId: String? = null, exerciseName: String? = null) {
        store.edit {
            if (endsAt == null) {
                it.remove(Keys.REST_ENDS_AT); it.remove(Keys.REST_TOTAL); it.remove(Keys.REST_SESSION); it.remove(Keys.REST_EXERCISE)
            } else {
                it[Keys.REST_ENDS_AT] = endsAt; it[Keys.REST_TOTAL] = totalSeconds
                sessionId?.let { id -> it[Keys.REST_SESSION] = id }
                exerciseName?.let { n -> it[Keys.REST_EXERCISE] = n }
            }
        }
    }

    /** Timer istirahat lengkap (untuk aksi dari notifikasi/alarm yang tidak punya konteks UI). */
    suspend fun restTimerDetailOnce(): RestTimerState? {
        val p = store.data.first()
        val end = p[Keys.REST_ENDS_AT] ?: return null
        return RestTimerState(end, p[Keys.REST_TOTAL] ?: 0, p[Keys.REST_SESSION], p[Keys.REST_EXERCISE] ?: "Latihan")
    }
}

data class RestTimerState(val endsAt: Long, val totalSeconds: Int, val sessionId: String?, val exerciseName: String)
