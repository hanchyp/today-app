package com.farhan.today.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/*
 * Skema v1 — lihat docs/SPRINTS.md bagian 1.
 * Konvensi: id UUID (String), createdAt/updatedAt epoch millis, deletedAt = soft delete.
 * LocalDate disimpan sebagai epochDay, LocalTime sebagai menit sejak 00:00 (lihat Converters).
 * Uang: Long rupiah utuh, selalu positif; arah ditentukan MoneyKind.
 */

fun newId(): String = UUID.randomUUID().toString()

// ------------------------------------------------------------------ Latihan

@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val muscleGroup: MuscleGroup,
    val equipment: Equipment,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val focus: String,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "routine_exercise",
    foreignKeys = [
        ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = CASCADE),
    ],
    indices = [Index("routineId", "position"), Index("exerciseId")],
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String = newId(),
    val routineId: String,
    val exerciseId: String,
    val position: Int,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double?,
    val restSeconds: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val setTargets: List<RoutineSetTarget>? = null,
)

data class RoutineSetTarget(val reps: Int, val weightKg: Double?)

@Entity(
    tableName = "routine_schedule",
    foreignKeys = [ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = CASCADE)],
    indices = [Index("routineId"), Index("dayOfWeek")],
)
data class RoutineScheduleEntity(
    @PrimaryKey val id: String = newId(),
    val routineId: String,
    /** 1 = Senin … 7 = Minggu (java.time.DayOfWeek.value) */
    val dayOfWeek: Int,
    val time: LocalTime,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "workout_session",
    foreignKeys = [ForeignKey(entity = RoutineEntity::class, parentColumns = ["id"], childColumns = ["routineId"], onDelete = SET_NULL)],
    indices = [Index("routineId"), Index("plannedDate")],
)
data class WorkoutSessionEntity(
    @PrimaryKey val id: String = newId(),
    val routineId: String?,
    /** Salinan nama rutinitas agar riwayat tetap utuh walau rutinitas diubah. */
    val name: String,
    val plannedDate: LocalDate,
    val plannedTime: LocalTime?,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val status: SessionStatus,
    val note: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "set_log",
    foreignKeys = [
        ForeignKey(entity = WorkoutSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = CASCADE),
        ForeignKey(entity = ExerciseEntity::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = CASCADE),
    ],
    indices = [Index("sessionId", "exerciseOrder", "setIndex"), Index("exerciseId")],
)
data class SetLogEntity(
    @PrimaryKey val id: String = newId(),
    val sessionId: String,
    val exerciseId: String,
    val exerciseOrder: Int,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val done: Boolean,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

// ------------------------------------------------------------------ Karier

@Entity(tableName = "job_application", indices = [Index("status")])
data class JobApplicationEntity(
    @PrimaryKey val id: String = newId(),
    val company: String,
    val role: String,
    val location: String = "",
    val url: String = "",
    val source: String = "",
    val status: ApplicationStatus,
    val appliedDate: LocalDate? = null,
    val deadlineDate: LocalDate? = null,
    val salaryNote: String = "",
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "job_event",
    foreignKeys = [ForeignKey(entity = JobApplicationEntity::class, parentColumns = ["id"], childColumns = ["applicationId"], onDelete = CASCADE)],
    indices = [Index("applicationId"), Index("startAt")],
)
data class JobEventEntity(
    @PrimaryKey val id: String = newId(),
    val applicationId: String,
    val type: JobEventType,
    val title: String,
    val startAt: Long,
    val durationMinutes: Int,
    val mode: EventMode,
    val locationOrLink: String = "",
    val reminderMinutesBefore: Int? = null,
    val done: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "application_status_change",
    foreignKeys = [ForeignKey(entity = JobApplicationEntity::class, parentColumns = ["id"], childColumns = ["applicationId"], onDelete = CASCADE)],
    indices = [Index("applicationId")],
)
data class ApplicationStatusChangeEntity(
    @PrimaryKey val id: String = newId(),
    val applicationId: String,
    val fromStatus: ApplicationStatus?,
    val toStatus: ApplicationStatus,
    val changedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

// ------------------------------------------------------------------ Keuangan

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val kind: MoneyKind,
    val iconKey: String,
    val colorKey: String,
    val position: Int,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "budget",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = SET_NULL)],
    indices = [Index("categoryId")],
)
data class BudgetEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    /** null = semua pengeluaran */
    val categoryId: String?,
    val amount: Long,
    val period: BudgetPeriod,
    /** Untuk MONTHLY: hari mulai periode (1–28). */
    val startDayOfMonth: Int = 1,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "money_transaction",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = SET_NULL)],
    indices = [Index("categoryId"), Index("occurredAt")],
)
data class MoneyTransactionEntity(
    @PrimaryKey val id: String = newId(),
    val kind: MoneyKind,
    val amount: Long,
    val categoryId: String?,
    val note: String = "",
    val occurredAt: Long,
    val billPaymentId: String? = null,
    val walletId: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val walletFunding: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "bill",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = SET_NULL)],
    indices = [Index("categoryId"), Index("nextDueDate")],
)
data class BillEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val amount: Long,
    val categoryId: String?,
    val repeat: BillRepeat,
    val nextDueDate: LocalDate,
    val reminderDaysBefore: Int = 1,
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "bill_payment",
    foreignKeys = [
        ForeignKey(entity = BillEntity::class, parentColumns = ["id"], childColumns = ["billId"], onDelete = CASCADE),
        ForeignKey(entity = MoneyTransactionEntity::class, parentColumns = ["id"], childColumns = ["transactionId"], onDelete = SET_NULL),
    ],
    indices = [Index("billId"), Index("transactionId")],
)
data class BillPaymentEntity(
    @PrimaryKey val id: String = newId(),
    val billId: String,
    val dueDate: LocalDate,
    val paidAt: Long,
    val amount: Long,
    val transactionId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

// ------------------------------------------------------------------ Tugas & Proyek

@Entity(tableName = "project")
data class ProjectEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val description: String = "",
    val colorKey: String = "tasks",
    val deadlineDate: LocalDate? = null,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "task",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = CASCADE)],
    indices = [Index("projectId"), Index("status", "dueDate")],
)
data class TaskEntity(
    @PrimaryKey val id: String = newId(),
    val projectId: String?,
    val title: String,
    val note: String = "",
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val status: TaskStatus = TaskStatus.TODO,
    val completedAt: Long? = null,
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

// ------------------------------------------------------------------ Sistem

@Entity(tableName = "reminder", indices = [Index("sourceType", "sourceId"), Index("state", "triggerAt")])
data class ReminderEntity(
    @PrimaryKey val id: String = newId(),
    val sourceType: ReminderSource,
    val sourceId: String,
    val triggerAt: Long,
    val title: String,
    val body: String,
    val state: ReminderState,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
