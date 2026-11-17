package com.farhan.today.data.repo

import androidx.room.withTransaction
import com.farhan.today.data.db.ApplicationStatus
import com.farhan.today.data.db.ApplicationStatusChangeEntity
import com.farhan.today.data.db.EventMode
import com.farhan.today.data.db.JobApplicationEntity
import com.farhan.today.data.db.JobEventEntity
import com.farhan.today.data.db.JobEventType
import com.farhan.today.data.db.ReminderSource
import com.farhan.today.data.db.TodayDatabase
import com.farhan.today.domain.formatLongDate
import com.farhan.today.domain.formatTime
import com.farhan.today.reminder.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

data class ApplicationInput(
    val company: String,
    val role: String,
    val location: String = "",
    val url: String = "",
    val source: String = "",
    val status: ApplicationStatus = ApplicationStatus.SAVED,
    val appliedDate: LocalDate? = null,
    val deadlineDate: LocalDate? = null,
    val notes: String = "",
) {
    /** Perusahaan dan posisi wajib diisi. */
    val isValid: Boolean get() = company.isNotBlank() && role.isNotBlank()
}

data class EventInput(
    val applicationId: String,
    val type: JobEventType,
    val title: String,
    val start: LocalDateTime,
    val durationMinutes: Int,
    val mode: EventMode,
    val locationOrLink: String = "",
    val reminderMinutesBefore: Int? = null,
)

class CareerRepository(
    private val db: TodayDatabase,
    private val clock: Clock,
    private val reminders: ReminderScheduler? = null,
) {
    private val dao = db.careerDao()

    fun applications() = dao.applications()
    fun applicationsWithNextEvent() = dao.applicationsWithNextEvent(clock.millis())
    fun application(id: String): Flow<JobApplicationEntity?> = dao.application(id)
    fun events(applicationId: String) = dao.eventsForApplication(applicationId)
    fun statusHistory(applicationId: String) = dao.statusHistory(applicationId)
    suspend fun deleteStatusHistory(applicationId: String, id: String) = dao.softDeleteStatusChange(applicationId, id, clock.millis())
    suspend fun event(id: String) = dao.event(id)

    /** Event dengan tanggal mulai dalam [start, end] (inklusif), memakai zona perangkat. */
    fun eventsBetween(start: LocalDate, end: LocalDate) = dao.eventsBetween(
        start.atStartOfDay(clock.zone).toInstant().toEpochMilli(),
        end.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli(),
    )

    /** Buat (id = null) atau ubah lamaran. Perubahan status dicatat di riwayat dalam transaksi yang sama. */
    suspend fun saveApplication(input: ApplicationInput, id: String? = null): String {
        require(input.isValid) { "Perusahaan dan posisi wajib diisi" }
        val now = clock.millis()
        return db.withTransaction {
            val existing = id?.let { dao.applicationOnce(it) }
            val entity = JobApplicationEntity(
                id = existing?.id ?: com.farhan.today.data.db.newId(),
                company = input.company.trim(), role = input.role.trim(),
                location = input.location.trim(), url = input.url.trim(), source = input.source.trim(),
                status = input.status, appliedDate = input.appliedDate, deadlineDate = input.deadlineDate,
                salaryNote = existing?.salaryNote.orEmpty(), notes = input.notes.trim(),
                createdAt = existing?.createdAt ?: now, updatedAt = now,
            )
            dao.upsertApplication(entity)
            if (existing?.status != input.status) {
                recordStatus(entity.id, existing?.status, input.status, now)
            }
            entity.id
        }
    }

    suspend fun changeStatus(id: String, to: ApplicationStatus) {
        val now = clock.millis()
        db.withTransaction {
            val app = dao.applicationOnce(id) ?: return@withTransaction
            if (app.status == to) return@withTransaction
            dao.upsertApplication(app.copy(status = to, updatedAt = now))
            recordStatus(id, app.status, to, now)
        }
    }

    /** Soft delete lamaran beserta event-nya, dan batalkan pengingatnya. */
    suspend fun deleteApplication(id: String) {
        val now = clock.millis()
        val events = dao.eventsForApplicationOnce(id)
        db.withTransaction {
            dao.softDeleteEventsOf(id, now)
            dao.softDeleteApplication(id, now)
        }
        events.forEach { reminders?.cancel(ReminderSource.JOB_EVENT, it.id) }
    }

    /** Buat/ubah event, lalu jadwalkan ulang pengingatnya. Mengembalikan event tersimpan. */
    suspend fun saveEvent(input: EventInput, id: String? = null): JobEventEntity {
        val now = clock.millis()
        val startAt = input.start.atZone(clock.zone).toInstant().toEpochMilli()
        val existing = id?.let { dao.event(it) }
        val event = JobEventEntity(
            id = existing?.id ?: com.farhan.today.data.db.newId(),
            applicationId = input.applicationId, type = input.type,
            title = input.title.trim().ifBlank { defaultTitle(input.type) },
            startAt = startAt, durationMinutes = input.durationMinutes, mode = input.mode,
            locationOrLink = input.locationOrLink.trim(), reminderMinutesBefore = input.reminderMinutesBefore,
            done = existing?.done ?: false, createdAt = existing?.createdAt ?: now, updatedAt = now,
        )
        db.withTransaction {
            dao.upsertEvent(event)
            dao.applicationOnce(input.applicationId)?.let { dao.upsertApplication(it.copy(updatedAt = now)) }
        }
        syncReminder(event)
        return event
    }

    suspend fun setEventDone(id: String, done: Boolean) {
        val e = dao.event(id) ?: return
        val updated = e.copy(done = done, updatedAt = clock.millis())
        dao.upsertEvent(updated)
        syncReminder(updated)
    }

    suspend fun deleteEvent(id: String) {
        dao.softDeleteEvent(id, clock.millis())
        reminders?.cancel(ReminderSource.JOB_EVENT, id)
    }

    private suspend fun syncReminder(event: JobEventEntity) {
        val r = reminders ?: return
        val minutes = event.reminderMinutesBefore
        if (minutes == null || event.done || event.deletedAt != null) {
            r.cancel(ReminderSource.JOB_EVENT, event.id)
            return
        }
        val company = dao.applicationOnce(event.applicationId)?.company.orEmpty()
        val start = Instant.ofEpochMilli(event.startAt).atZone(clock.zone)
        r.schedule(
            source = ReminderSource.JOB_EVENT, sourceId = event.id,
            triggerAt = event.startAt - minutes * 60_000L,
            title = "${event.title} · ${reminderLabel(minutes)} lagi",
            body = "$company · ${formatLongDate(start.toLocalDate())}, ${formatTime(start.toLocalTime())}" +
                if (event.locationOrLink.isNotBlank()) " · ${event.locationOrLink}" else "",
        )
    }

    private suspend fun recordStatus(id: String, from: ApplicationStatus?, to: ApplicationStatus, now: Long) {
        dao.upsertStatusChange(
            ApplicationStatusChangeEntity(applicationId = id, fromStatus = from, toStatus = to, changedAt = now, createdAt = now, updatedAt = now)
        )
    }

    companion object {
        fun defaultTitle(type: JobEventType) = when (type) {
            JobEventType.CODING_TEST -> "Coding test"
            JobEventType.INTERVIEW -> "Interview"
            JobEventType.FOLLOW_UP -> "Follow-up"
            JobEventType.OTHER -> "Agenda lamaran"
        }

        fun reminderLabel(minutes: Int) = when {
            minutes >= 1440 && minutes % 1440 == 0 -> "${minutes / 1440} hari"
            minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60} jam"
            else -> "$minutes menit"
        }

        /** Saat menjadwalkan interview, tawarkan menaikkan status bila masih di tahap sebelumnya. */
        fun shouldOfferPromotion(current: ApplicationStatus, eventType: JobEventType): ApplicationStatus? {
            val target = when (eventType) {
                JobEventType.INTERVIEW -> ApplicationStatus.INTERVIEW
                JobEventType.CODING_TEST -> ApplicationStatus.CODING_TEST
                else -> return null
            }
            val pipeline = PIPELINE
            val ci = pipeline.indexOf(current)
            return if (ci >= 0 && ci < pipeline.indexOf(target)) target else null
        }

        /** Urutan tahapan untuk stepper; REJECTED/WITHDRAWN di luar pipeline. */
        val PIPELINE = listOf(
            ApplicationStatus.SAVED, ApplicationStatus.APPLIED, ApplicationStatus.CODING_TEST,
            ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER,
        )
    }
}

fun ApplicationStatus.label(): String = when (this) {
    ApplicationStatus.SAVED -> "Tersimpan"
    ApplicationStatus.APPLIED -> "Sudah apply"
    ApplicationStatus.CODING_TEST -> "Coding test"
    ApplicationStatus.INTERVIEW -> "Interview"
    ApplicationStatus.OFFER -> "Offer"
    ApplicationStatus.REJECTED -> "Ditolak"
    ApplicationStatus.WITHDRAWN -> "Dibatalkan"
}

fun JobEventType.label(): String = CareerRepository.defaultTitle(this)
