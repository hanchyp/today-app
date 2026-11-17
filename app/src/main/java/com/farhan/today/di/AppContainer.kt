package com.farhan.today.di

import android.content.Context
import com.farhan.today.data.backup.DataManager
import com.farhan.today.data.db.SeedData
import com.farhan.today.data.db.TodayDatabase
import com.farhan.today.data.db.loadExerciseCatalog
import com.farhan.today.data.prefs.UserPrefs
import com.farhan.today.data.repo.AgendaRepository
import com.farhan.today.data.repo.CareerRepository
import com.farhan.today.data.repo.FinanceRepository
import com.farhan.today.data.repo.TaskRepository
import com.farhan.today.data.repo.WorkoutRepository
import com.farhan.today.domain.usecase.GetAgendaForRange
import com.farhan.today.domain.usecase.GetTodayOverview
import com.farhan.today.reminder.AndroidAlarmGateway
import com.farhan.today.reminder.ReminderScheduler
import com.farhan.today.workout.RestTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Clock

/** DI manual: satu instance per aplikasi (docs/SPRINTS.md 0.2). */
class AppContainer(
    context: Context,
    val clock: Clock = Clock.systemDefaultZone(),
    val db: TodayDatabase = TodayDatabase.create(context),
) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val prefs = UserPrefs(context)

    val reminderScheduler = ReminderScheduler(db.reminderDao(), AndroidAlarmGateway(context), clock)

    internal val exerciseCatalog by lazy { loadExerciseCatalog(context.assets, clock.millis()).associateBy { it.exercise.id } }
    val workoutRepository = WorkoutRepository(db, clock, reminderScheduler, { exerciseCatalog.keys }) { prefs.workoutRemindersOnce() }
    val restTimer = RestTimer(context, prefs, clock)
    val careerRepository = CareerRepository(db, clock, reminderScheduler)
    val financeRepository = FinanceRepository(db, clock, reminderScheduler)
    val taskRepository = TaskRepository(db, clock)
    val agendaRepository = AgendaRepository(workoutRepository, careerRepository, financeRepository, taskRepository, clock, db.calendarDao())

    val getTodayOverview = GetTodayOverview(agendaRepository, financeRepository, clock)
    val dataManager = DataManager(db, prefs, reminderScheduler, clock) { now -> exerciseCatalog.values.map { it.exercise.copy(createdAt = now, updatedAt = now) } }
    val getAgendaForRange = GetAgendaForRange(agendaRepository)

    private val _ready = MutableStateFlow(false)
    /** true setelah data contoh (jika perlu) selesai ditulis, supaya Today tidak berkedip kosong. */
    val ready: StateFlow<Boolean> = _ready

    fun start() {
        appScope.launch {
            if (!prefs.isSeeded()) {
                SeedData(db, clock).seed()
                prefs.markSeeded()
            }
            db.workoutDao().insertCatalogExercises(exerciseCatalog.values.map { it.exercise })
            workoutRepository.planSessions()
            _ready.value = true
        }
    }
}
