package com.farhan.today.data.repo

import com.farhan.today.data.db.BillEntity
import com.farhan.today.data.db.CalendarDao
import kotlinx.coroutines.flow.flowOf
import com.farhan.today.data.db.BillRepeat
import com.farhan.today.data.db.JobEventRow
import com.farhan.today.data.db.JobEventType
import com.farhan.today.data.db.ScheduleRow
import com.farhan.today.data.db.SessionRow
import com.farhan.today.data.db.SessionStatus
import com.farhan.today.data.db.TaskRow
import com.farhan.today.data.db.TaskStatus
import com.farhan.today.domain.formatRupiah
import com.farhan.today.domain.model.AgendaItem
import com.farhan.today.domain.model.AgendaSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Menggabungkan tanggal dari semua modul menjadi satu daftar agenda (docs/SPRINTS.md 0.5).
 * Urutan per hari: item berjam (naik), lalu item seharian.
 */
class AgendaRepository(
    private val workout: WorkoutRepository,
    private val career: CareerRepository,
    private val finance: FinanceRepository,
    private val tasks: TaskRepository,
    private val clock: Clock,
    private val calendar: CalendarDao? = null,
) {
    fun agendaBetween(start: LocalDate, end: LocalDate): Flow<List<AgendaItem>> = combine(
        workout.sessionsBetween(start, end),
        workout.schedules(),
        career.eventsBetween(start, end),
        finance.activeBills(),
        tasks.tasksDueBetween(start, end),
    ) { sessions, schedules, events, bills, dueTasks ->
        buildAgenda(start, end, LocalDate.now(clock), sessions, schedules, events, bills, dueTasks)
    }.combine(calendar?.between(start, end) ?: flowOf(emptyList())) { items, events ->
        (items + events.flatMap { it.occurrences(start, end) }).sortedWith(agendaOrder)
    }

    internal fun buildAgenda(
        start: LocalDate,
        end: LocalDate,
        today: LocalDate,
        sessions: List<SessionRow>,
        schedules: List<ScheduleRow>,
        events: List<JobEventRow>,
        bills: List<BillEntity>,
        dueTasks: List<TaskRow>,
    ): List<AgendaItem> {
        val items = mutableListOf<AgendaItem>()

        // Latihan: sesi nyata
        sessions.filter { it.session.status != SessionStatus.SKIPPED }.forEach { row ->
            val s = row.session
            items += AgendaItem(
                id = "session:${s.id}", source = AgendaSource.WORKOUT, sourceId = s.id,
                label = "Latihan", title = s.name, subtitle = workoutSubtitle(row.exerciseCount, row.focus),
                date = s.plannedDate, time = s.plannedTime, done = s.status == SessionStatus.DONE,
            )
        }
        // Latihan: jadwal mingguan yang belum dibuatkan sesi (hanya hari ini dan ke depan)
        val sessionKeys = sessions.map { it.session.routineId to it.session.plannedDate }.toSet()
        var day = maxOf(start, today)
        while (!day.isAfter(end)) {
            schedules.filter { it.schedule.dayOfWeek == day.dayOfWeek.value }.forEach { row ->
                if ((row.schedule.routineId to day) !in sessionKeys) {
                    items += AgendaItem(
                        id = "schedule:${row.schedule.id}:$day", source = AgendaSource.WORKOUT,
                        sourceId = row.schedule.routineId, label = "Latihan", title = row.routineName,
                        subtitle = workoutSubtitle(row.exerciseCount, row.focus),
                        date = day, time = row.schedule.time, done = false,
                    )
                }
            }
            day = day.plusDays(1)
        }

        // Karier
        events.forEach { row ->
            val e = row.event
            val at = Instant.ofEpochMilli(e.startAt).atZone(clock.zone)
            items += AgendaItem(
                id = "event:${e.id}", source = AgendaSource.CAREER, sourceId = e.applicationId,
                label = jobEventLabel(e.type), title = e.title, subtitle = row.company,
                date = at.toLocalDate(), time = at.toLocalTime().withSecond(0).withNano(0), done = e.done,
            )
        }

        // Keuangan: jatuh tempo tagihan, termasuk perulangan dalam rentang
        bills.forEach { bill ->
            dueDatesInRange(bill, start, end).forEach { due ->
                items += AgendaItem(
                    id = "bill:${bill.id}:$due", source = AgendaSource.FINANCE, sourceId = bill.id,
                    label = "Tagihan", title = bill.name, subtitle = formatRupiah(bill.amount),
                    date = due, time = null, done = false,
                )
            }
        }

        // Tugas
        dueTasks.forEach { row ->
            val t = row.task
            val due = t.dueDate ?: return@forEach
            items += AgendaItem(
                id = "task:${t.id}", source = AgendaSource.TASK, sourceId = t.id,
                label = "Tugas", title = t.title, subtitle = row.projectName ?: t.note,
                date = due, time = t.dueTime, done = t.status == TaskStatus.DONE,
            )
        }

        return items.sortedWith(agendaOrder)
    }

    companion object {
        val agendaOrder: Comparator<AgendaItem> = compareBy<AgendaItem> { it.date }
            .thenBy { it.time == null }
            .thenBy { it.time ?: LocalTime.MIDNIGHT }
            .thenBy { it.title }

        /** Item berikutnya di hari ini: berjam & belum lewat dulu, lalu item seharian yang belum selesai. */
        fun pickNext(todayItems: List<AgendaItem>, now: LocalTime): AgendaItem? {
            val open = todayItems.filter { !it.done }
            return open.firstOrNull { it.time != null && !it.time.isBefore(now) }
                ?: open.firstOrNull { it.time == null }
        }

        fun dueDatesInRange(bill: BillEntity, start: LocalDate, end: LocalDate): List<LocalDate> {
            val base = bill.nextDueDate
            val step: (Long) -> LocalDate = when (bill.repeat) {
                BillRepeat.NONE -> return if (!base.isBefore(start) && !base.isAfter(end)) listOf(base) else emptyList()
                BillRepeat.MONTHLY -> { k -> base.plusMonths(k) }
                BillRepeat.YEARLY -> { k -> base.plusYears(k) }
            }
            val out = mutableListOf<LocalDate>()
            var k = 0L
            while (true) {
                val d = step(k++)
                if (d.isAfter(end)) break
                if (!d.isBefore(start)) out += d
            }
            return out
        }

        private fun workoutSubtitle(count: Int, focus: String?) =
            if (count > 0) "$count gerakan" else focus.orEmpty()

        private fun jobEventLabel(type: JobEventType) = when (type) {
            JobEventType.CODING_TEST -> "Coding test"
            JobEventType.INTERVIEW -> "Interview"
            JobEventType.FOLLOW_UP -> "Follow-up"
            JobEventType.OTHER -> "Karier"
        }
    }
}
