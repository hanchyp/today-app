package com.farhan.today.data.backup

import android.content.ContentValues
import android.database.Cursor
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.farhan.today.data.db.CategoryEntity
import com.farhan.today.data.db.ExerciseEntity
import com.farhan.today.data.db.MoneyKind
import com.farhan.today.data.db.ReminderSource
import com.farhan.today.data.db.SeedData
import com.farhan.today.data.db.TodayDatabase
import com.farhan.today.data.prefs.UserPrefs
import com.farhan.today.reminder.ReminderScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock

/** Kelompok data yang bisa direset sendiri-sendiri dari Pengaturan. */
enum class DataFeature(val label: String, val description: String, val tables: List<String>, val reminderSource: ReminderSource?) {
    WORKOUT(
        "Latihan", "Rutinitas, jadwal, sesi, dan catatan set. Katalog gerakan bawaan disediakan lagi.",
        listOf("exercise", "routine", "routine_exercise", "routine_schedule", "workout_session", "set_log"), ReminderSource.WORKOUT,
    ),
    CAREER(
        "Karier", "Lamaran kerja, jadwal interview/coding test, dan riwayat status.",
        listOf("job_application", "job_event", "application_status_change"), ReminderSource.JOB_EVENT,
    ),
    FINANCE(
        "Keuangan", "Transaksi, anggaran, dompet, tagihan, dan pembayarannya. Kategori bawaan disediakan lagi.",
        listOf("category", "budget", "wallet", "wallet_allocation", "money_transaction", "bill", "bill_payment"), ReminderSource.BILL,
    ),
    TASKS("Tugas", "Semua proyek dan tugas.", listOf("project", "task"), ReminderSource.TASK),
    CALENDAR("Kalender", "Acara yang kamu buat di Kalender.", listOf("calendar_event"), null),
}

class BackupException(message: String) : Exception(message)

data class BackupInfo(val exportedAt: Long, val schemaVersion: Int, val rows: Int, val tables: Map<String, Int>)

/**
 * Backup, restore, dan hapus data (Sprint 5).
 *
 * Format JSON: { app: "today", schemaVersion, exportedAt, prefs: {...}, tables: { nama: [ {kolom: nilai} ] } }.
 * Ekspor dibaca langsung dari SQLite per kolom, jadi otomatis ikut skema tanpa daftar field manual.
 */
class DataManager(
    private val db: TodayDatabase,
    private val prefs: UserPrefs,
    private val reminders: ReminderScheduler?,
    private val clock: Clock,
    private val exerciseCatalog: (Long) -> List<ExerciseEntity> = { emptyList() },
) {
    private val sql: SupportSQLiteDatabase get() = db.openHelper.writableDatabase

    suspend fun export(): String {
        val tables = JSONObject()
        db.withTransaction {
            TABLES.forEach { t -> tables.put(t, readTable(t)) }
        }
        val s = prefs.settingsOnce()
        return JSONObject()
            .put("app", APP_ID)
            .put("schemaVersion", SCHEMA_VERSION)
            .put("exportedAt", clock.millis())
            .put("prefs", JSONObject().put("userName", s.userName).put("modules", JSONArray(s.modules.map { it.name })))
            .put("tables", tables)
            .toString()
    }

    /** Baca ringkasan file tanpa mengubah apa pun (untuk konfirmasi sebelum menimpa). */
    fun inspect(json: String): BackupInfo {
        val root = parse(json)
        val tables = root.getJSONObject("tables")
        val counts = TABLES.associateWith { tables.optJSONArray(it)?.length() ?: 0 }
        return BackupInfo(root.getLong("exportedAt"), root.getInt("schemaVersion"), counts.values.sum(), counts)
    }

    /** Timpa SEMUA data dengan isi backup, dalam satu transaksi (gagal = tidak ada yang berubah). */
    suspend fun import(json: String): BackupInfo {
        val root = parse(json)
        val info = inspect(json)
        val tables = root.getJSONObject("tables")
        cancelAllReminders()
        db.withTransaction {
            TABLES.reversed().forEach { sql.execSQL("DELETE FROM `$it`") }
            TABLES.forEach { t ->
                val rows = tables.optJSONArray(t) ?: return@forEach
                for (i in 0 until rows.length()) {
                    sql.insert(t, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, rows.getJSONObject(i).toContentValues())
                }
            }
            db.workoutDao().insertCatalogExercises(exerciseCatalog(clock.millis()))
        }
        root.optJSONObject("prefs")?.optString("userName")?.takeIf { it.isNotBlank() }?.let { prefs.setUserName(it) }
        prefs.markSeeded()
        reminders?.rescheduleAll()
        return info
    }

    /**
     * Hapus semua data pribadi. Katalog gerakan & kategori keuangan bawaan dibuat ulang
     * supaya aplikasi langsung bisa dipakai ("mulai kosong").
     */
    suspend fun wipe() {
        cancelAllReminders()
        val now = clock.millis()
        db.withTransaction {
            TABLES.reversed().forEach { sql.execSQL("DELETE FROM `$it`") }
            db.workoutDao().insertCatalogExercises(exerciseCatalog(now))
            insertDefaultCategories(now)
        }
        prefs.markSeeded()
    }

    /** Jumlah baris per fitur (untuk ditampilkan sebelum reset). */
    suspend fun count(feature: DataFeature): Int = db.withTransaction {
        feature.tables.sumOf { t -> sql.query("SELECT COUNT(*) FROM `$t`").use { c -> if (c.moveToFirst()) c.getInt(0) else 0 } }
    }

    /**
     * Reset satu fitur saja: tabel milik fitur lain tidak disentuh. Pengingat milik fitur ini
     * dibatalkan dan dihapus; data bawaan (katalog gerakan / kategori) dibuat ulang.
     */
    suspend fun reset(feature: DataFeature) {
        val now = clock.millis()
        feature.reminderSource?.let { src ->
            reminders?.let { r -> db.reminderDao().scheduled().filter { it.sourceType == src }.forEach { r.cancel(it.sourceType, it.sourceId) } }
        }
        db.withTransaction {
            feature.tables.reversed().forEach { sql.execSQL("DELETE FROM `$it`") }
            feature.reminderSource?.let { sql.execSQL("DELETE FROM `reminder` WHERE sourceType = ?", arrayOf(it.name)) }
            when (feature) {
                DataFeature.WORKOUT -> db.workoutDao().insertCatalogExercises(exerciseCatalog(now))
                DataFeature.FINANCE -> insertDefaultCategories(now)
                else -> Unit
            }
        }
    }

    private suspend fun insertDefaultCategories(now: Long) {
        db.financeDao().upsertCategories(
            SeedData.expenseCategories.mapIndexed { i, (n, icon) ->
                CategoryEntity(name = n, kind = MoneyKind.EXPENSE, iconKey = icon, colorKey = "finance", position = i, createdAt = now, updatedAt = now)
            } + SeedData.incomeCategories.mapIndexed { i, (n, icon) ->
                CategoryEntity(name = n, kind = MoneyKind.INCOME, iconKey = icon, colorKey = "income", position = i, createdAt = now, updatedAt = now)
            }
        )
    }

    private suspend fun cancelAllReminders() {
        val r = reminders ?: return
        db.reminderDao().scheduled().forEach { r.cancel(it.sourceType, it.sourceId) }
    }

    private fun readTable(table: String): JSONArray {
        val arr = JSONArray()
        sql.query("SELECT * FROM `$table`").use { c ->
            while (c.moveToNext()) {
                val o = JSONObject()
                for (i in 0 until c.columnCount) {
                    val name = c.getColumnName(i)
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> o.put(name, JSONObject.NULL)
                        Cursor.FIELD_TYPE_INTEGER -> o.put(name, c.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> o.put(name, c.getDouble(i))
                        else -> o.put(name, c.getString(i))
                    }
                }
                arr.put(o)
            }
        }
        return arr
    }

    private fun JSONObject.toContentValues(): ContentValues {
        val cv = ContentValues()
        keys().forEach { k ->
            when (val v = get(k)) {
                JSONObject.NULL -> cv.putNull(k)
                is Int -> cv.put(k, v.toLong())
                is Long -> cv.put(k, v)
                is Double -> cv.put(k, v)
                is Boolean -> cv.put(k, if (v) 1L else 0L)
                else -> cv.put(k, v.toString())
            }
        }
        return cv
    }

    private fun parse(json: String): JSONObject {
        val root = runCatching { JSONObject(json) }.getOrElse { throw BackupException("File bukan backup Today yang valid.") }
        if (root.optString("app") != APP_ID) throw BackupException("File ini bukan backup dari aplikasi Today.")
        val v = root.optInt("schemaVersion", -1)
        if (v > SCHEMA_VERSION) throw BackupException("Backup dibuat oleh versi aplikasi yang lebih baru (skema $v). Perbarui aplikasi dulu.")
        if (v < 1) throw BackupException("Versi skema backup tidak dikenali.")
        // v < SCHEMA_VERSION: tempat migrasi format backup bila skema berubah di masa depan.
        if (!root.has("tables")) throw BackupException("Backup tidak berisi data.")
        return root
    }

    companion object {
        const val APP_ID = "today"
        const val SCHEMA_VERSION = 3

        /** Urutan induk → anak (untuk insert); dibalik untuk delete. */
        val TABLES = listOf(
            "exercise", "routine", "routine_exercise", "routine_schedule", "workout_session", "set_log",
            "job_application", "job_event", "application_status_change",
            "category", "budget", "wallet", "wallet_allocation", "money_transaction", "bill", "bill_payment",
            "project", "task", "reminder", "calendar_event",
        )

        fun fileName(date: java.time.LocalDate) = "today-backup-%04d%02d%02d.json".format(date.year, date.monthValue, date.dayOfMonth)
    }
}
