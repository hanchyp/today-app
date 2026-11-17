package com.farhan.today.data.repo

import androidx.room.withTransaction
import com.farhan.today.data.db.ProjectEntity
import com.farhan.today.data.db.TaskEntity
import com.farhan.today.data.db.TaskPriority
import com.farhan.today.data.db.TaskStatus
import com.farhan.today.data.db.TodayDatabase
import com.farhan.today.data.db.newId
import com.farhan.today.domain.relativeDay
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class TaskInput(
    val title: String,
    val projectId: String? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val note: String = "",
) {
    val isValid: Boolean get() = title.isNotBlank()
}

class TaskRepository(private val db: TodayDatabase, private val clock: Clock) {
    private val dao = db.taskDao()

    fun tasksDueBetween(start: LocalDate, end: LocalDate) = dao.tasksDueBetween(start, end)
    fun allTasks() = dao.allTasks()
    fun projects() = dao.projects()
    fun project(id: String) = dao.project(id)
    fun tasksForProject(projectId: String) = dao.tasksForProject(projectId)
    suspend fun task(id: String) = dao.task(id)

    suspend fun saveProject(name: String, description: String = "", deadline: LocalDate? = null, id: String? = null): String {
        require(name.isNotBlank()) { "Nama proyek wajib diisi" }
        val now = clock.millis()
        val existing = id?.let { pid -> db.taskDao().projectOnce(pid) }
        val entity = ProjectEntity(
            id = existing?.id ?: newId(), name = name.trim(), description = description.trim(),
            colorKey = existing?.colorKey ?: "tasks", deadlineDate = deadline, archived = existing?.archived ?: false,
            createdAt = existing?.createdAt ?: now, updatedAt = now,
        )
        dao.upsertProject(entity)
        return entity.id
    }

    suspend fun deleteProject(id: String) = dao.softDeleteProject(id, clock.millis())

    /** Buat/ubah tugas. Tugas baru ditaruh di akhir urutan proyeknya. */
    suspend fun saveTask(input: TaskInput, id: String? = null): String {
        require(input.isValid) { "Judul tugas wajib diisi" }
        val now = clock.millis()
        return db.withTransaction {
            val existing = id?.let { dao.task(it) }
            val position = if (existing != null && existing.projectId == input.projectId) existing.position
            else dao.maxPosition(input.projectId) + 1
            val entity = TaskEntity(
                id = existing?.id ?: newId(), projectId = input.projectId, title = input.title.trim(),
                note = input.note.trim(), dueDate = input.dueDate, dueTime = input.dueTime.takeIf { input.dueDate != null },
                priority = input.priority, status = existing?.status ?: TaskStatus.TODO,
                completedAt = existing?.completedAt, position = position,
                createdAt = existing?.createdAt ?: now, updatedAt = now,
            )
            dao.upsertTasks(listOf(entity))
            entity.id
        }
    }

    /** Membalik status selesai; mengembalikan status baru. */
    suspend fun toggleDone(id: String): TaskStatus? {
        val t = dao.task(id) ?: return null
        val now = clock.millis()
        val next = if (t.status == TaskStatus.DONE) TaskStatus.TODO else TaskStatus.DONE
        dao.upsertTasks(listOf(t.copy(status = next, completedAt = if (next == TaskStatus.DONE) now else null, updatedAt = now)))
        return next
    }

    suspend fun deleteTask(id: String) = dao.softDeleteTask(id, clock.millis())
    suspend fun restoreTask(id: String) = dao.restoreTask(id, clock.millis())

    /** Urutan manual: posisi mengikuti urutan [orderedIds]. */
    suspend fun reorder(orderedIds: List<String>) {
        val now = clock.millis()
        db.withTransaction { orderedIds.forEachIndexed { i, id -> dao.setPosition(id, i, now) } }
    }
}

// ------------------------------------------------------------------ Aturan tampilan tugas

enum class DeadlineKind { OVERDUE, TODAY, TOMORROW, UPCOMING, DONE }

data class DeadlineBadge(val kind: DeadlineKind, val label: String)

/** Badge deadline: dibedakan lewat teks (dan ikon di UI), tidak hanya warna. */
fun deadlineBadge(due: LocalDate?, today: LocalDate, done: Boolean): DeadlineBadge? = when {
    done -> DeadlineBadge(DeadlineKind.DONE, "Selesai")
    due == null -> null
    due.isBefore(today) -> {
        val days = ChronoUnit.DAYS.between(due, today)
        DeadlineBadge(DeadlineKind.OVERDUE, if (days == 1L) "Terlambat 1 hari" else "Terlambat $days hari")
    }
    due == today -> DeadlineBadge(DeadlineKind.TODAY, "Hari ini")
    due == today.plusDays(1) -> DeadlineBadge(DeadlineKind.TOMORROW, "Besok")
    else -> DeadlineBadge(DeadlineKind.UPCOMING, relativeDay(due, today))
}

/** Progres proyek 0..1; proyek tanpa tugas = 0. */
fun projectProgress(done: Int, total: Int): Float = if (total <= 0) 0f else done.toFloat() / total
