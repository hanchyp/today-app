package com.farhan.today.ui.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farhan.today.data.db.Equipment
import com.farhan.today.data.db.ExerciseEntity
import com.farhan.today.data.db.HistoryRow
import com.farhan.today.data.db.MuscleGroup
import com.farhan.today.data.db.RoutineEntity
import com.farhan.today.data.db.RoutineSetTarget
import com.farhan.today.data.db.SessionRow
import com.farhan.today.data.db.SessionStatus
import com.farhan.today.data.db.SetLogEntity
import com.farhan.today.data.db.WeightPoint
import com.farhan.today.data.db.WorkoutSessionEntity
import com.farhan.today.data.prefs.UserPrefs
import com.farhan.today.data.repo.RoutineExerciseInput
import com.farhan.today.data.repo.RoutineInput
import com.farhan.today.data.repo.SessionSummary
import com.farhan.today.data.repo.WorkoutRepository
import com.farhan.today.domain.WorkoutMath
import com.farhan.today.workout.RestTimer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

// ------------------------------------------------------------------ 06 · Latihan

data class RoutineCard(
    val routine: RoutineEntity,
    val days: List<DayOfWeek>,
    val time: LocalTime?,
    val exerciseCount: Int,
    val setCount: Int,
)

data class WorkoutHomeState(
    val loading: Boolean = true,
    val sessions: List<SessionRow> = emptyList(),
    val week: WorkoutMath.WeekStats = WorkoutMath.WeekStats(0, 0, 0),
    val routines: List<RoutineCard> = emptyList(),
    val history: List<HistoryRow> = emptyList(),
    val reminders: Boolean = false,
)

class WorkoutHomeViewModel(
    private val repo: WorkoutRepository,
    private val prefs: UserPrefs,
    private val focusDate: LocalDate? = null,
    private val focusId: String? = null,
) : ViewModel() {

    private val routines: Flow<List<RoutineCard>> = combine(repo.routines(), repo.schedules(), repo.allRoutineExercises()) { rs, scs, exs ->
        rs.map { r ->
            val sc = scs.filter { it.schedule.routineId == r.id }
            val ex = exs.filter { it.item.routineId == r.id }
            RoutineCard(
                r, sc.map { DayOfWeek.of(it.schedule.dayOfWeek) }.sorted(), sc.firstOrNull()?.schedule?.time,
                ex.size, ex.sumOf { it.item.targetSets },
            )
        }
    }

    private val sessions = if (focusDate == null) repo.homeSessions() else repo.sessionsBetween(focusDate, focusDate)

    val state: StateFlow<WorkoutHomeState> = combine(
        sessions, repo.weekStats(), routines, repo.history(10), prefs.workoutReminders,
    ) { sessions, week, rs, hist, rem ->
        val running = sessions.firstOrNull { it.session.status == SessionStatus.IN_PROGRESS }
        WorkoutHomeState(
            loading = false, sessions = if (focusId == null) running?.let { listOf(it) } ?: sessions
                else sessions.filter { it.session.id == focusId },
            week = week, routines = rs, history = hist, reminders = rem,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutHomeState())

    fun start(sessionId: String, onStarted: (String) -> Unit) = viewModelScope.launch { onStarted(repo.startSession(sessionId)) }

    fun startRoutineNow(routineId: String, onStarted: (String) -> Unit) = viewModelScope.launch {
        onStarted(repo.startSession(repo.createSessionNow(routineId)))
    }

    fun deleteHistory(id: String) = viewModelScope.launch { repo.deleteHistory(id) }

    fun setReminders(on: Boolean) = viewModelScope.launch {
        prefs.setWorkoutReminders(on)
        repo.syncReminders()
    }
}

// ------------------------------------------------------------------ Form rutinitas

class RoutineSetDraft(reps: String, weight: String) {
    var reps by mutableStateOf(reps)
    var weight by mutableStateOf(weight)
    val targetReps get() = reps.toIntOrNull()?.takeIf { it in 1..100 }
    val targetWeight get() = weight.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
    val validWeight get() = weight.isBlank() || targetWeight != null
}

class ExerciseDraft(
    val exerciseId: String,
    val name: String,
    val group: MuscleGroup,
    targets: List<RoutineSetTarget> = List(3) { RoutineSetTarget(8, null) },
    restSeconds: Int = 90,
    val animation: String? = null,
) {
    val sets = mutableStateListOf<RoutineSetDraft>().apply {
        addAll(targets.map { RoutineSetDraft(it.reps.toString(), it.weightKg?.toBigDecimal()?.stripTrailingZeros()?.toPlainString().orEmpty()) })
    }
    var restSeconds by mutableStateOf(restSeconds)

    fun addSet() {
        if (sets.size >= 20) return
        val last = sets.last()
        sets += RoutineSetDraft(last.reps, last.weight)
    }
}

class RoutineFormViewModel(
    private val repo: WorkoutRepository,
    private val id: String?,
    private val animationFor: (ExerciseEntity) -> String?,
) : ViewModel() {
    var name by mutableStateOf("")
    var focus by mutableStateOf("")
    val items = mutableStateListOf<ExerciseDraft>()
    var days by mutableStateOf(setOf<DayOfWeek>())
    var time by mutableStateOf(LocalTime.of(17, 0))
    var showErrors by mutableStateOf(false)
    var loaded by mutableStateOf(id == null)
        private set
    var saving by mutableStateOf(false)
        private set
    val isEdit = id != null

    /** Ringkasan seluruh isian; dibandingkan dengan saat form dibuka untuk "Buang perubahan?". */
    private fun snapshot(): String = buildString {
        append(name).append('|').append(focus).append('|').append(days.sorted()).append('|').append(time)
        items.forEach { d -> append('|').append(d.exerciseId).append(':').append(d.restSeconds); d.sets.forEach { append(',').append(it.reps).append('x').append(it.weight) } }
    }
    private var initial by mutableStateOf(snapshot())
    val dirty: Boolean get() = loaded && !saving && snapshot() != initial

    val catalog: StateFlow<List<ExerciseEntity>> =
        repo.catalog().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        if (id != null) viewModelScope.launch {
            repo.routine(id)?.let { name = it.name; focus = it.focus }
            repo.routineExercises(id).forEach { row ->
                val item = row.item
                val targets = item.setTargets?.takeIf { it.isNotEmpty() }
                    ?: List(item.targetSets.coerceIn(1, 20)) { RoutineSetTarget(item.targetReps, item.targetWeightKg) }
                items += ExerciseDraft(
                    item.exerciseId, row.exerciseName, row.muscleGroup, targets, item.restSeconds,
                    repo.exercise(item.exerciseId)?.let(animationFor),
                )
            }
            val sc = repo.schedulesFor(id)
            days = sc.map { DayOfWeek.of(it.dayOfWeek) }.toSet()
            sc.firstOrNull()?.let { time = it.time }
            initial = snapshot()
            loaded = true
        }
    }

    fun add(e: ExerciseEntity) { items += ExerciseDraft(e.id, e.name, e.muscleGroup, animation = animationFor(e)) }
    fun remove(i: Int) { items.removeAt(i) }
    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j in items.indices) items.add(j, items.removeAt(i))
    }
    fun toggleDay(d: DayOfWeek) { days = if (d in days) days - d else days + d }

    fun createExercise(name: String, group: MuscleGroup, equipment: Equipment) = viewModelScope.launch {
        val newId = repo.createExercise(name, group, equipment)
        repo.exercise(newId)?.let(::add)
    }

    fun save(onSaved: () -> Unit) {
        if (saving) return
        if (name.isBlank() || items.isEmpty() || items.any { item ->
                item.sets.any { it.targetReps == null || !it.validWeight }
            }) {
            showErrors = true
            return
        }
        val input = RoutineInput(
            name, focus,
            items.map { item ->
                RoutineExerciseInput(item.exerciseId, restSeconds = item.restSeconds, setTargets = item.sets.map {
                    RoutineSetTarget(requireNotNull(it.targetReps), it.targetWeight)
                })
            },
            days, time,
        )
        saving = true
        viewModelScope.launch { repo.saveRoutine(input, id); onSaved() }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch { id?.let { repo.deleteRoutine(it) }; onDone() }
}

// ------------------------------------------------------------------ 07 · Sesi latihan

data class ExerciseBlock(
    val order: Int,
    val exerciseId: String,
    val name: String,
    val sets: List<SetLogEntity>,
    val animation: String? = null,
)

data class SessionState(
    val loading: Boolean = true,
    val session: WorkoutSessionEntity? = null,
    val blocks: List<ExerciseBlock> = emptyList(),
    /** Sisa detik istirahat; null = tidak ada timer. */
    val restRemaining: Int? = null,
    val restTotal: Int = 0,
)

class SessionViewModel(
    private val repo: WorkoutRepository,
    private val prefs: UserPrefs,
    private val restTimer: RestTimer,
    private val sessionId: String,
    private val clock: Clock,
    private val animationFor: (ExerciseEntity) -> String?,
) : ViewModel() {
    var selectedSetId by mutableStateOf<String?>(null)
    /** "terakhir: 40 kg × 8" per gerakan. */
    val lastSets = androidx.compose.runtime.mutableStateMapOf<String, List<SetLogEntity>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rest: Flow<Pair<Int, Int>?> = prefs.restTimer.flatMapLatest { t ->
        if (t == null) flowOf(null) else flow {
            while (true) {
                val left = ((t.first - clock.millis() + 999) / 1000).toInt()
                if (left <= 0) { emit(null); break }
                emit(left to t.second)
                delay(1000)
            }
        }
    }

    val state: StateFlow<SessionState> = combine(repo.session(sessionId), repo.sets(sessionId), repo.exercises(), rest) { s, sets, ex, r ->
        val exercises = ex.associateBy { it.id }
        val blocks = sets.groupBy { it.exerciseOrder }.toSortedMap().map { (order, list) ->
            val exerciseId = list.first().exerciseId
            val exercise = exercises[exerciseId]
            ExerciseBlock(order, exerciseId, exercise?.name ?: "Gerakan", list.sortedBy { it.setIndex }, exercise?.let(animationFor))
        }
        SessionState(false, s, blocks, r?.first, r?.second ?: 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState())

    fun loadLast(exerciseId: String) {
        if (lastSets.containsKey(exerciseId)) return
        lastSets[exerciseId] = emptyList()
        viewModelScope.launch { lastSets[exerciseId] = repo.lastSets(exerciseId, sessionId) }
    }

    fun adjustWeight(set: SetLogEntity, delta: Double) = viewModelScope.launch {
        repo.updateSet(set.id, WorkoutMath.stepWeight(set.weightKg, delta), set.reps)
    }

    fun adjustReps(set: SetLogEntity, delta: Int) = viewModelScope.launch {
        repo.updateSet(set.id, set.weightKg, (set.reps + delta).coerceAtLeast(0))
    }

    fun toggleDone(set: SetLogEntity, exerciseName: String) = viewModelScope.launch {
        val rest = repo.toggleSetDone(set.id)
        if (rest != null) {
            restTimer.start(rest, sessionId, exerciseName)
            // Pilih set berikutnya yang belum selesai
            val next = state.value.blocks.flatMap { it.sets }.firstOrNull { !it.done && it.id != set.id }
            selectedSetId = next?.id
        }
    }

    fun skipRest() = viewModelScope.launch { restTimer.cancel() }
    fun addSet(order: Int) = viewModelScope.launch { repo.addSet(sessionId, order) }
    fun deleteSet(set: SetLogEntity) = viewModelScope.launch { repo.deleteSet(set.id); if (selectedSetId == set.id) selectedSetId = null }
    fun addExercise(exerciseId: String) = viewModelScope.launch { repo.addExerciseToSession(sessionId, exerciseId) }
    fun createExercise(name: String, group: MuscleGroup, equipment: Equipment) = viewModelScope.launch {
        val exerciseId = repo.createExercise(name, group, equipment)
        repo.addExerciseToSession(sessionId, exerciseId)
    }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        restTimer.cancel()
        repo.finishSession(sessionId)
        onDone()
    }

    fun discard(onDone: () -> Unit) = viewModelScope.launch {
        restTimer.cancel()
        repo.discardSession(sessionId)
        onDone()
    }

    /** Pastikan sesi sudah berjalan (mis. dibuka dari notifikasi/Today saat masih PLANNED). */
    fun ensureStarted() = viewModelScope.launch { repo.startSession(sessionId) }

    suspend fun catalog(): List<ExerciseEntity> = repo.catalog().first()
}

// ------------------------------------------------------------------ Ringkasan / detail riwayat

data class SummaryState(
    val loading: Boolean = true,
    val summary: SessionSummary? = null,
    val blocks: List<ExerciseBlock> = emptyList(),
)

class SessionSummaryViewModel(private val repo: WorkoutRepository, private val sessionId: String) : ViewModel() {
    private val summary = MutableStateFlow<SessionSummary?>(null)

    init { viewModelScope.launch { summary.value = runCatching { repo.summary(sessionId) }.getOrNull() } }

    val state: StateFlow<SummaryState> = combine(summary, repo.sets(sessionId), repo.exercises()) { s, sets, ex ->
        val names = ex.associate { it.id to it.name }
        SummaryState(
            loading = s == null,
            summary = s,
            blocks = sets.groupBy { it.exerciseOrder }.toSortedMap().map { (order, list) ->
                ExerciseBlock(order, list.first().exerciseId, names[list.first().exerciseId] ?: "Gerakan", list)
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryState())
}

// ------------------------------------------------------------------ Progres per gerakan

data class ProgressState(val name: String = "", val points: List<WeightPoint> = emptyList(), val loading: Boolean = true)

class ExerciseProgressViewModel(repo: WorkoutRepository, exerciseId: String) : ViewModel() {
    private val name = MutableStateFlow("")

    init { viewModelScope.launch { name.value = repo.exercise(exerciseId)?.name.orEmpty() } }

    val state: StateFlow<ProgressState> = combine(name, repo.weightHistory(exerciseId)) { n, pts -> ProgressState(n, pts, false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())
}
