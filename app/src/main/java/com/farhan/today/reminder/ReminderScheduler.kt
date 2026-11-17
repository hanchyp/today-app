package com.farhan.today.reminder

import com.farhan.today.data.db.ReminderDao
import com.farhan.today.data.db.ReminderEntity
import com.farhan.today.data.db.ReminderSource
import com.farhan.today.data.db.ReminderState
import java.time.Clock

/** Abstraksi AlarmManager agar penjadwalan bisa dites di JVM. */
interface AlarmGateway {
    fun set(reminderId: String, triggerAt: Long)
    fun cancel(reminderId: String)
}

/**
 * Mesin pengingat bersama (Sprint 2, dipakai ulang S3–S5).
 * Tabel `reminder` adalah daftar alarm aktif; dipakai untuk membatalkan saat sumber berubah
 * dan menjadwal ulang setelah reboot/update aplikasi.
 */
class ReminderScheduler(
    private val dao: ReminderDao,
    private val gateway: AlarmGateway,
    private val clock: Clock,
) {
    /** Menggantikan pengingat lama milik sumber yang sama. Waktu yang sudah lewat diabaikan. */
    suspend fun schedule(source: ReminderSource, sourceId: String, triggerAt: Long, title: String, body: String): ReminderEntity? {
        cancel(source, sourceId)
        val now = clock.millis()
        if (triggerAt <= now) return null
        val reminder = ReminderEntity(
            sourceType = source, sourceId = sourceId, triggerAt = triggerAt,
            title = title, body = body, state = ReminderState.SCHEDULED,
            createdAt = now, updatedAt = now,
        )
        dao.upsert(reminder)
        gateway.set(reminder.id, triggerAt)
        return reminder
    }

    suspend fun cancel(source: ReminderSource, sourceId: String) {
        val now = clock.millis()
        dao.scheduledFor(source, sourceId).forEach {
            gateway.cancel(it.id)
            dao.setState(it.id, ReminderState.CANCELLED, now)
        }
    }

    /** Setelah reboot/update: alarm hilang dari sistem, pasang lagi yang masih di masa depan. */
    suspend fun rescheduleAll(): Int {
        val now = clock.millis()
        var count = 0
        dao.scheduled().forEach {
            if (it.triggerAt > now) {
                gateway.set(it.id, it.triggerAt)
                count++
            } else {
                // Terlewat saat perangkat mati: tandai agar tidak dipasang lagi.
                dao.setState(it.id, ReminderState.FIRED, now)
            }
        }
        return count
    }

    suspend fun markFired(reminderId: String) = dao.setState(reminderId, ReminderState.FIRED, clock.millis())
}
