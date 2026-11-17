package com.farhan.today.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farhan.today.data.db.WorkoutSessionEntity
import com.farhan.today.data.prefs.UserPrefs
import com.farhan.today.domain.model.AgendaSource
import com.farhan.today.domain.model.TodayOverview
import com.farhan.today.domain.shortDayName
import com.farhan.today.domain.usecase.GetTodayOverview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WeekDay(
    val date: LocalDate,
    val dayLabel: String,
    val number: String,
    val isToday: Boolean,
    val sources: Set<AgendaSource>,
)

data class TodayUiState(
    val loading: Boolean = true,
    val userName: String = "",
    val overview: TodayOverview? = null,
    val week: List<WeekDay> = emptyList(),
    /** Sesi latihan yang belum diselesaikan → kartu "Lanjutkan latihan". */
    val inProgress: WorkoutSessionEntity? = null,
)

class TodayViewModel(
    ready: Flow<Boolean>,
    prefs: UserPrefs,
    getTodayOverview: GetTodayOverview,
    inProgress: Flow<WorkoutSessionEntity?>,
    private val tasks: com.farhan.today.data.repo.TaskRepository,
) : ViewModel() {

    /** Dinaikkan saat layar kembali tampil agar "Berikutnya" dan tanggal ikut waktu sekarang. */
    private val refresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayUiState> = combine(
        refresh.flatMapLatest { ready.filter { it } }.flatMapLatest { prefs.weekStartsSunday }.flatMapLatest { getTodayOverview(it) },
        prefs.userName,
        inProgress,
    ) { overview, name, running ->
        val weekStart = overview.weekStart
        TodayUiState(
            loading = false,
            userName = name,
            inProgress = running,
            overview = overview,
            week = (0L..6L).map { i ->
                val d = weekStart.plusDays(i)
                WeekDay(
                    date = d,
                    dayLabel = shortDayName(d.dayOfWeek).uppercase(),
                    number = "%02d".format(d.dayOfMonth),
                    isToday = d == overview.date,
                    sources = overview.weekAgenda[d].orEmpty(),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun refresh() {
        refresh.value++
    }

    /** Centang tugas langsung dari Today; mengembalikan status baru untuk snackbar "Batalkan". */
    fun toggleTask(id: String, onResult: (com.farhan.today.data.db.TaskStatus?) -> Unit = {}) =
        viewModelScope.launch { onResult(tasks.toggleDone(id)) }
}
