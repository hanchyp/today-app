package com.farhan.today.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

// Semua query baca menyaring deletedAt IS NULL (soft delete).

// ------------------------------------------------------------------ Latihan

data class SessionRow(
    @Embedded val session: WorkoutSessionEntity,
    val focus: String?,
    val exerciseCount: Int,
)

data class ScheduleRow(
    @Embedded val schedule: RoutineScheduleEntity,
    val routineName: String,
    val focus: String,
    val exerciseCount: Int,
)

data class RoutineExerciseRow(
    @Embedded val item: RoutineExerciseEntity,
    val exerciseName: String,
    val muscleGroup: MuscleGroup,
)

data class HistoryRow(
    @Embedded val session: WorkoutSessionEntity,
    val setsDone: Int,
    val volumeKg: Double,
    val exerciseCount: Int,
)

data class WeightPoint(val sessionId: String, val endedAt: Long, val maxKg: Double)

data class ExerciseBest(val exerciseId: String, val bestKg: Double)

@Dao
interface WorkoutDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCatalogExercises(items: List<ExerciseEntity>)

    @Upsert suspend fun upsertExercises(items: List<ExerciseEntity>)
    @Upsert suspend fun upsertRoutine(item: RoutineEntity)
    @Upsert suspend fun upsertRoutineExercises(items: List<RoutineExerciseEntity>)
    @Upsert suspend fun upsertSchedules(items: List<RoutineScheduleEntity>)
    @Upsert suspend fun upsertSession(item: WorkoutSessionEntity)
    @Upsert suspend fun upsertSets(items: List<SetLogEntity>)

    @Query("SELECT * FROM exercise WHERE deletedAt IS NULL ORDER BY name")
    fun exercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM routine WHERE deletedAt IS NULL AND archived = 0 ORDER BY name")
    fun routines(): Flow<List<RoutineEntity>>

    @Query(
        """
        SELECT s.*, r.focus AS focus,
          (SELECT COUNT(*) FROM routine_exercise re WHERE re.routineId = s.routineId AND re.deletedAt IS NULL) AS exerciseCount
        FROM workout_session s LEFT JOIN routine r ON r.id = s.routineId
        WHERE s.deletedAt IS NULL AND s.plannedDate BETWEEN :start AND :end
        ORDER BY s.plannedDate, s.plannedTime
        """
    )
    fun sessionsBetween(start: LocalDate, end: LocalDate): Flow<List<SessionRow>>

    @Query(
        """
        SELECT sc.*, r.name AS routineName, r.focus AS focus,
          (SELECT COUNT(*) FROM routine_exercise re WHERE re.routineId = r.id AND re.deletedAt IS NULL) AS exerciseCount
        FROM routine_schedule sc JOIN routine r ON r.id = sc.routineId
        WHERE sc.deletedAt IS NULL AND r.deletedAt IS NULL AND r.archived = 0
        """
    )
    fun schedules(): Flow<List<ScheduleRow>>

    @Query("SELECT * FROM set_log WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY exerciseOrder, setIndex")
    fun sets(sessionId: String): Flow<List<SetLogEntity>>

    @Query("UPDATE workout_session SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteSession(id: String, now: Long)

    @Query("UPDATE routine SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteRoutine(id: String, now: Long)

    // ---- Sprint 3

    @Upsert suspend fun upsertExercise(item: ExerciseEntity)

    @Query("SELECT * FROM routine WHERE id = :id AND deletedAt IS NULL")
    suspend fun routineOnce(id: String): RoutineEntity?

    @Query(
        """
        SELECT re.*, e.name AS exerciseName, e.muscleGroup AS muscleGroup
        FROM routine_exercise re JOIN exercise e ON e.id = re.exerciseId
        WHERE re.routineId = :routineId AND re.deletedAt IS NULL
        ORDER BY re.position
        """
    )
    suspend fun routineExercisesOnce(routineId: String): List<RoutineExerciseRow>

    @Query(
        """
        SELECT re.*, e.name AS exerciseName, e.muscleGroup AS muscleGroup
        FROM routine_exercise re JOIN exercise e ON e.id = re.exerciseId
        WHERE re.deletedAt IS NULL ORDER BY re.routineId, re.position
        """
    )
    fun allRoutineExercises(): Flow<List<RoutineExerciseRow>>

    @Query("SELECT * FROM routine_schedule WHERE routineId = :routineId AND deletedAt IS NULL ORDER BY dayOfWeek")
    suspend fun schedulesForRoutineOnce(routineId: String): List<RoutineScheduleEntity>

    @Query("UPDATE routine_exercise SET deletedAt = :now, updatedAt = :now WHERE routineId = :routineId AND deletedAt IS NULL")
    suspend fun softDeleteRoutineExercises(routineId: String, now: Long)

    @Query("UPDATE routine_schedule SET deletedAt = :now, updatedAt = :now WHERE routineId = :routineId AND deletedAt IS NULL")
    suspend fun softDeleteSchedules(routineId: String, now: Long)

    @Query(
        """
        SELECT sc.* FROM routine_schedule sc JOIN routine r ON r.id = sc.routineId
        WHERE sc.deletedAt IS NULL AND r.deletedAt IS NULL AND r.archived = 0
        """
    )
    suspend fun activeSchedulesOnce(): List<RoutineScheduleEntity>

    @Query("SELECT * FROM workout_session WHERE id = :id AND deletedAt IS NULL")
    fun session(id: String): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE id = :id AND deletedAt IS NULL")
    suspend fun sessionOnce(id: String): WorkoutSessionEntity?

    /** Untuk idempotensi perencana: sesi apa pun (termasuk SKIPPED) di tanggal itu. */
    @Query("SELECT COUNT(*) FROM workout_session WHERE routineId = :routineId AND plannedDate = :date AND (deletedAt IS NULL OR status = 'DONE')")
    suspend fun countSessionsOn(routineId: String, date: LocalDate): Int

    @Query("SELECT * FROM workout_session WHERE routineId = :routineId AND status = 'PLANNED' AND plannedDate >= :from AND deletedAt IS NULL")
    suspend fun plannedFrom(routineId: String, from: LocalDate): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_session WHERE status = 'PLANNED' AND plannedDate >= :from AND deletedAt IS NULL")
    suspend fun allPlannedFrom(from: LocalDate): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_session WHERE status = 'IN_PROGRESS' AND deletedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun inProgress(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE status = 'IN_PROGRESS' AND deletedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun inProgressOnce(): WorkoutSessionEntity?

    /** Sesi berjalan dan sesi yang masih terjadwal pada tanggal ini. */
    @Query(
        """
        SELECT s.*, r.focus AS focus,
          (SELECT COUNT(*) FROM routine_exercise re WHERE re.routineId = s.routineId AND re.deletedAt IS NULL) AS exerciseCount
        FROM workout_session s LEFT JOIN routine r ON r.id = s.routineId
        WHERE s.deletedAt IS NULL AND (s.status = 'IN_PROGRESS' OR (s.status = 'PLANNED' AND s.plannedDate = :date))
        ORDER BY s.status = 'IN_PROGRESS' DESC, s.startedAt DESC, s.plannedTime, s.id
        """
    )
    fun homeSessions(date: LocalDate): Flow<List<SessionRow>>

    @Query(
        """
        SELECT s.*,
          (SELECT COUNT(*) FROM set_log sl WHERE sl.sessionId = s.id AND sl.deletedAt IS NULL AND sl.done = 1) AS setsDone,
          (SELECT COALESCE(SUM(sl.weightKg * sl.reps), 0) FROM set_log sl WHERE sl.sessionId = s.id AND sl.deletedAt IS NULL AND sl.done = 1) AS volumeKg,
          (SELECT COUNT(DISTINCT sl.exerciseId) FROM set_log sl WHERE sl.sessionId = s.id AND sl.deletedAt IS NULL) AS exerciseCount
        FROM workout_session s
        WHERE s.deletedAt IS NULL AND s.status = 'DONE'
        ORDER BY s.endedAt DESC LIMIT :limit
        """
    )
    fun history(limit: Int): Flow<List<HistoryRow>>

    @Query("SELECT * FROM workout_session WHERE deletedAt IS NULL AND status = 'DONE' AND plannedDate BETWEEN :start AND :end")
    fun doneBetween(start: LocalDate, end: LocalDate): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM set_log WHERE sessionId = :sessionId AND deletedAt IS NULL ORDER BY exerciseOrder, setIndex")
    suspend fun setsOnce(sessionId: String): List<SetLogEntity>

    @Query("SELECT * FROM set_log WHERE id = :id")
    suspend fun setOnce(id: String): SetLogEntity?

    @Query("UPDATE set_log SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteSet(id: String, now: Long)

    @Query("UPDATE set_log SET deletedAt = :now, updatedAt = :now WHERE sessionId = :sessionId AND deletedAt IS NULL")
    suspend fun softDeleteSetsOf(sessionId: String, now: Long)

    /** Set selesai dari sesi DONE terakhir yang memuat gerakan ini (untuk "terakhir: 40 kg × 8"). */
    @Query(
        """
        SELECT sl.* FROM set_log sl
        WHERE sl.exerciseId = :exerciseId AND sl.deletedAt IS NULL AND sl.done = 1
          AND sl.sessionId = (
            SELECT s2.id FROM set_log sl2 JOIN workout_session s2 ON s2.id = sl2.sessionId
            WHERE sl2.exerciseId = :exerciseId AND sl2.deletedAt IS NULL AND sl2.done = 1
              AND s2.deletedAt IS NULL AND s2.status = 'DONE' AND s2.id != :excludeSessionId
            ORDER BY s2.endedAt DESC LIMIT 1
          )
        ORDER BY sl.setIndex
        """
    )
    suspend fun lastSets(exerciseId: String, excludeSessionId: String): List<SetLogEntity>

    @Query(
        """
        SELECT e.* FROM exercise e JOIN (
            SELECT sl.exerciseId, MAX(s.endedAt) AS lastUsed
            FROM set_log sl JOIN workout_session s ON s.id = sl.sessionId
            WHERE sl.deletedAt IS NULL AND sl.done = 1
              AND s.deletedAt IS NULL AND s.status = 'DONE'
            GROUP BY sl.exerciseId
        ) recent ON recent.exerciseId = e.id
        WHERE e.deletedAt IS NULL
        ORDER BY recent.lastUsed DESC, e.name COLLATE NOCASE, e.id
        LIMIT :limit
        """
    )
    suspend fun recentExercises(limit: Int): List<ExerciseEntity>

    /** Beban terberat per gerakan dari sesi DONE yang selesai sebelum [beforeMillis] (untuk PR). */
    @Query(
        """
        SELECT sl.exerciseId AS exerciseId, MAX(sl.weightKg) AS bestKg
        FROM set_log sl JOIN workout_session s ON s.id = sl.sessionId
        WHERE sl.deletedAt IS NULL AND sl.done = 1 AND s.deletedAt IS NULL AND s.status = 'DONE'
          AND s.endedAt < :beforeMillis AND s.id != :excludeSessionId
        GROUP BY sl.exerciseId
        """
    )
    suspend fun bestsBefore(beforeMillis: Long, excludeSessionId: String): List<ExerciseBest>

    @Query(
        """
        SELECT s.id AS sessionId, s.endedAt AS endedAt, MAX(sl.weightKg) AS maxKg
        FROM set_log sl JOIN workout_session s ON s.id = sl.sessionId
        WHERE sl.exerciseId = :exerciseId AND sl.deletedAt IS NULL AND sl.done = 1
          AND s.deletedAt IS NULL AND s.status = 'DONE' AND s.endedAt IS NOT NULL
        GROUP BY s.id ORDER BY s.endedAt
        """
    )
    fun weightHistory(exerciseId: String): Flow<List<WeightPoint>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun exerciseOnce(id: String): ExerciseEntity?
}

// ------------------------------------------------------------------ Karier

data class ApplicationRow(
    @Embedded val application: JobApplicationEntity,
    val nextEventTitle: String?,
    val nextEventType: JobEventType?,
    val nextEventAt: Long?,
)

data class JobEventRow(
    @Embedded val event: JobEventEntity,
    val company: String,
    val role: String,
)

@Dao
interface CareerDao {
    @Upsert suspend fun upsertApplication(item: JobApplicationEntity)
    @Upsert suspend fun upsertEvent(item: JobEventEntity)
    @Upsert suspend fun upsertStatusChange(item: ApplicationStatusChangeEntity)

    @Query("SELECT * FROM job_application WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun applications(): Flow<List<JobApplicationEntity>>

    @Query(
        """
        SELECT e.*, a.company AS company, a.role AS role
        FROM job_event e JOIN job_application a ON a.id = e.applicationId
        WHERE e.deletedAt IS NULL AND a.deletedAt IS NULL AND e.startAt >= :fromMillis AND e.startAt < :toMillis
        ORDER BY e.startAt
        """
    )
    fun eventsBetween(fromMillis: Long, toMillis: Long): Flow<List<JobEventRow>>

    @Query(
        """
        SELECT a.*,
          (SELECT e.title FROM job_event e WHERE e.applicationId = a.id AND e.deletedAt IS NULL AND e.done = 0 AND e.startAt >= :nowMillis ORDER BY e.startAt LIMIT 1) AS nextEventTitle,
          (SELECT e.type FROM job_event e WHERE e.applicationId = a.id AND e.deletedAt IS NULL AND e.done = 0 AND e.startAt >= :nowMillis ORDER BY e.startAt LIMIT 1) AS nextEventType,
          (SELECT e.startAt FROM job_event e WHERE e.applicationId = a.id AND e.deletedAt IS NULL AND e.done = 0 AND e.startAt >= :nowMillis ORDER BY e.startAt LIMIT 1) AS nextEventAt
        FROM job_application a WHERE a.deletedAt IS NULL ORDER BY a.updatedAt DESC
        """
    )
    fun applicationsWithNextEvent(nowMillis: Long): Flow<List<ApplicationRow>>

    @Query("SELECT * FROM job_application WHERE id = :id AND deletedAt IS NULL")
    fun application(id: String): Flow<JobApplicationEntity?>

    @Query("SELECT * FROM job_application WHERE id = :id AND deletedAt IS NULL")
    suspend fun applicationOnce(id: String): JobApplicationEntity?

    @Query("SELECT * FROM job_event WHERE applicationId = :applicationId AND deletedAt IS NULL ORDER BY startAt")
    fun eventsForApplication(applicationId: String): Flow<List<JobEventEntity>>

    @Query("SELECT * FROM job_event WHERE applicationId = :applicationId AND deletedAt IS NULL")
    suspend fun eventsForApplicationOnce(applicationId: String): List<JobEventEntity>

    @Query("SELECT * FROM job_event WHERE id = :id AND deletedAt IS NULL")
    suspend fun event(id: String): JobEventEntity?

    @Query("UPDATE job_event SET deletedAt = :now, updatedAt = :now WHERE applicationId = :applicationId AND deletedAt IS NULL")
    suspend fun softDeleteEventsOf(applicationId: String, now: Long)

    @Query("SELECT * FROM application_status_change WHERE applicationId = :applicationId AND deletedAt IS NULL ORDER BY changedAt")
    fun statusHistory(applicationId: String): Flow<List<ApplicationStatusChangeEntity>>

    @Query("UPDATE application_status_change SET deletedAt = :now, updatedAt = :now WHERE id = :id AND applicationId = :applicationId AND deletedAt IS NULL")
    suspend fun softDeleteStatusChange(applicationId: String, id: String, now: Long)

    @Query("UPDATE job_application SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteApplication(id: String, now: Long)

    @Query("UPDATE job_event SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteEvent(id: String, now: Long)
}

// ------------------------------------------------------------------ Keuangan

data class TransactionRow(
    @Embedded val tx: MoneyTransactionEntity,
    val categoryName: String?,
    val categoryIcon: String?,
)

data class CategoryTotal(val categoryId: String?, val name: String?, val total: Long)

@Dao
interface FinanceDao {
    @Upsert suspend fun upsertCategories(items: List<CategoryEntity>)
    @Upsert suspend fun upsertBudget(item: BudgetEntity)
    @Upsert suspend fun upsertTransactions(items: List<MoneyTransactionEntity>)
    @Upsert suspend fun upsertBill(item: BillEntity)
    @Upsert suspend fun upsertBillPayment(item: BillPaymentEntity)

    @Query("SELECT * FROM category WHERE deletedAt IS NULL AND archived = 0 ORDER BY kind, position")
    fun categories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM budget WHERE deletedAt IS NULL ORDER BY createdAt")
    fun budgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM money_transaction WHERE deletedAt IS NULL ORDER BY occurredAt DESC LIMIT :limit")
    fun recentTransactions(limit: Int): Flow<List<MoneyTransactionEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM money_transaction
        WHERE deletedAt IS NULL AND kind = 'EXPENSE' AND occurredAt >= :fromMillis AND occurredAt < :toMillis
        """
    )
    fun expenseTotal(fromMillis: Long, toMillis: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM money_transaction
        WHERE deletedAt IS NULL AND kind = 'EXPENSE' AND categoryId = :categoryId
          AND occurredAt >= :fromMillis AND occurredAt < :toMillis
        """
    )
    fun expenseTotalForCategory(categoryId: String, fromMillis: Long, toMillis: Long): Flow<Long>

    @Query("SELECT * FROM bill WHERE deletedAt IS NULL AND active = 1 ORDER BY nextDueDate")
    fun activeBills(): Flow<List<BillEntity>>

    @Query("UPDATE money_transaction SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteTransaction(id: String, now: Long)

    @Query("UPDATE bill SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteBill(id: String, now: Long)

    // ---- Sprint 4

    @Query(
        """
        SELECT t.*, c.name AS categoryName, c.iconKey AS categoryIcon
        FROM money_transaction t LEFT JOIN category c ON c.id = t.categoryId
        WHERE t.deletedAt IS NULL AND t.occurredAt >= :fromMillis AND t.occurredAt < :toMillis
        ORDER BY t.occurredAt DESC
        """
    )
    fun transactionsBetween(fromMillis: Long, toMillis: Long): Flow<List<TransactionRow>>

    @Query(
        """
        SELECT t.*, c.name AS categoryName, c.iconKey AS categoryIcon
        FROM money_transaction t LEFT JOIN category c ON c.id = t.categoryId
        WHERE t.deletedAt IS NULL ORDER BY t.occurredAt DESC LIMIT :limit
        """
    )
    fun recentRows(limit: Int): Flow<List<TransactionRow>>

    @Query("SELECT * FROM money_transaction WHERE id = :id")
    suspend fun transactionOnce(id: String): MoneyTransactionEntity?

    @Query("UPDATE money_transaction SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restoreTransaction(id: String, now: Long)

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM money_transaction
        WHERE deletedAt IS NULL AND kind = :kind AND occurredAt >= :fromMillis AND occurredAt < :toMillis
        """
    )
    fun totalOfKind(kind: MoneyKind, fromMillis: Long, toMillis: Long): Flow<Long>

    @Query(
        """
        SELECT t.categoryId AS categoryId, c.name AS name, SUM(t.amount) AS total
        FROM money_transaction t LEFT JOIN category c ON c.id = t.categoryId
        WHERE t.deletedAt IS NULL AND t.kind = 'EXPENSE' AND t.occurredAt >= :fromMillis AND t.occurredAt < :toMillis
        GROUP BY t.categoryId ORDER BY total DESC
        """
    )
    fun expenseByCategory(fromMillis: Long, toMillis: Long): Flow<List<CategoryTotal>>

    @Query("SELECT * FROM category WHERE deletedAt IS NULL ORDER BY kind, archived, position")
    fun allCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun categoryOnce(id: String): CategoryEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM category WHERE deletedAt IS NULL AND kind = :kind")
    suspend fun maxCategoryPosition(kind: MoneyKind): Int

    @Query("SELECT * FROM category WHERE deletedAt IS NULL AND kind = :kind AND archived = 0 ORDER BY position")
    suspend fun categoriesOfKindOnce(kind: MoneyKind): List<CategoryEntity>

    @Query("SELECT * FROM budget WHERE id = :id AND deletedAt IS NULL")
    suspend fun budgetOnce(id: String): BudgetEntity?

    @Query("UPDATE budget SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteBudget(id: String, now: Long)

    @Query("SELECT * FROM bill WHERE id = :id AND deletedAt IS NULL")
    suspend fun billOnce(id: String): BillEntity?

    @Query("SELECT * FROM bill WHERE deletedAt IS NULL ORDER BY active DESC, nextDueDate")
    fun allBills(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bill WHERE deletedAt IS NULL AND active = 1")
    suspend fun activeBillsOnce(): List<BillEntity>

    @Query("SELECT * FROM bill_payment WHERE billId = :billId AND deletedAt IS NULL ORDER BY dueDate DESC")
    fun payments(billId: String): Flow<List<BillPaymentEntity>>

    @Query("SELECT * FROM bill_payment WHERE billId = :billId ORDER BY dueDate LIMIT 1")
    suspend fun firstPayment(billId: String): BillPaymentEntity?

    @Query("UPDATE bill_payment SET deletedAt = :now, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeletePayment(id: String, now: Long)

    @Query("SELECT * FROM bill_payment WHERE deletedAt IS NULL ORDER BY paidAt DESC")
    fun allPayments(): Flow<List<BillPaymentEntity>>
}

// ------------------------------------------------------------------ Tugas & Proyek

data class TaskRow(
    @Embedded val task: TaskEntity,
    val projectName: String?,
)

data class ProjectProgress(
    @Embedded val project: ProjectEntity,
    val total: Int,
    val done: Int,
    val nextDue: LocalDate?,
)

@Dao
interface TaskDao {
    @Upsert suspend fun upsertProject(item: ProjectEntity)
    @Upsert suspend fun upsertTasks(items: List<TaskEntity>)

    @Query(
        """
        SELECT t.*, p.name AS projectName FROM task t LEFT JOIN project p ON p.id = t.projectId
        WHERE t.deletedAt IS NULL AND (p.id IS NULL OR p.deletedAt IS NULL)
          AND t.dueDate BETWEEN :start AND :end
        ORDER BY t.dueDate, t.dueTime
        """
    )
    fun tasksDueBetween(start: LocalDate, end: LocalDate): Flow<List<TaskRow>>

    @Query(
        """
        SELECT t.*, p.name AS projectName FROM task t LEFT JOIN project p ON p.id = t.projectId
        WHERE t.deletedAt IS NULL AND (p.id IS NULL OR p.deletedAt IS NULL)
        ORDER BY t.status = 'DONE', t.dueDate IS NULL, t.dueDate, t.position
        """
    )
    fun allTasks(): Flow<List<TaskRow>>

    @Query(
        """
        SELECT p.*,
          (SELECT COUNT(*) FROM task t WHERE t.projectId = p.id AND t.deletedAt IS NULL) AS total,
          (SELECT COUNT(*) FROM task t WHERE t.projectId = p.id AND t.deletedAt IS NULL AND t.status = 'DONE') AS done,
          (SELECT MIN(t.dueDate) FROM task t WHERE t.projectId = p.id AND t.deletedAt IS NULL AND t.status = 'TODO') AS nextDue
        FROM project p WHERE p.deletedAt IS NULL AND p.archived = 0 ORDER BY p.createdAt
        """
    )
    fun projects(): Flow<List<ProjectProgress>>

    @Query(
        """
        SELECT p.*,
          (SELECT COUNT(*) FROM task t WHERE t.projectId = p.id AND t.deletedAt IS NULL) AS total,
          (SELECT COUNT(*) FROM task t WHERE t.projectId = p.id AND t.deletedAt IS NULL AND t.status = 'DONE') AS done,
          (SELECT MIN(t.dueDate) FROM task t WHERE t.projectId = p.id AND t.deletedAt IS NULL AND t.status = 'TODO') AS nextDue
        FROM project p WHERE p.id = :id AND p.deletedAt IS NULL
        """
    )
    fun project(id: String): Flow<ProjectProgress?>

    @Query(
        """
        SELECT t.*, p.name AS projectName FROM task t LEFT JOIN project p ON p.id = t.projectId
        WHERE t.deletedAt IS NULL AND t.projectId = :projectId
        ORDER BY t.status = 'DONE', t.dueDate IS NULL, t.dueDate, t.position
        """
    )
    fun tasksForProject(projectId: String): Flow<List<TaskRow>>

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun task(id: String): TaskEntity?

    @Query("SELECT * FROM project WHERE id = :id AND deletedAt IS NULL")
    suspend fun projectOnce(id: String): ProjectEntity?

    @Query("SELECT COALESCE(MAX(position), -1) FROM task WHERE deletedAt IS NULL AND ((:projectId IS NULL AND projectId IS NULL) OR projectId = :projectId)")
    suspend fun maxPosition(projectId: String?): Int

    @Query("UPDATE task SET position = :position, updatedAt = :now WHERE id = :id")
    suspend fun setPosition(id: String, position: Int, now: Long)

    @Query("UPDATE task SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteTask(id: String, now: Long)

    @Query("UPDATE task SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restoreTask(id: String, now: Long)

    @Query("UPDATE project SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteProject(id: String, now: Long)
}

// ------------------------------------------------------------------ Sistem

@Dao
interface ReminderDao {
    @Upsert suspend fun upsert(item: ReminderEntity)

    @Query("SELECT * FROM reminder WHERE deletedAt IS NULL AND state = 'SCHEDULED' ORDER BY triggerAt")
    suspend fun scheduled(): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE deletedAt IS NULL AND state = 'SCHEDULED' AND sourceType = :type AND sourceId = :sourceId")
    suspend fun scheduledFor(type: ReminderSource, sourceId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun byId(id: String): ReminderEntity?

    @Query("UPDATE reminder SET state = :state, updatedAt = :now WHERE id = :id")
    suspend fun setState(id: String, state: ReminderState, now: Long)
}
