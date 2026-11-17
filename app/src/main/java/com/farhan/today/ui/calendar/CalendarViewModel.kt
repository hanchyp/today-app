package com.farhan.today.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farhan.today.domain.model.AgendaItem
import com.farhan.today.domain.model.AgendaSource
import com.farhan.today.domain.usecase.GetAgendaForRange
import com.farhan.today.domain.usecase.sourcesByDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

data class CalendarUiState(
    val month: YearMonth,
    val selected: LocalDate,
    val today: LocalDate,
    val sourcesByDate: Map<LocalDate, Set<AgendaSource>> = emptyMap(),
    val selectedItems: List<AgendaItem> = emptyList(),
    val loading: Boolean = true,
    val weekStartsSunday: Boolean = false,
)

class CalendarViewModel(
    ready: Flow<Boolean>,
    private val getAgenda: GetAgendaForRange,
    private val clock: Clock,
    initialDate: LocalDate?,
    weekStartsSunday: Flow<Boolean> = kotlinx.coroutines.flow.flowOf(false),
) : ViewModel() {

    private val today = LocalDate.now(clock)
    private val selected = MutableStateFlow(initialDate ?: today)
    private val month = MutableStateFlow(YearMonth.from(initialDate ?: today))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthAgenda: Flow<Pair<YearMonth, List<AgendaItem>>> =
        ready.filter { it }.flatMapLatest { month }.flatMapLatest { m ->
            getAgenda(m.atDay(1), m.atEndOfMonth()).map { m to it }
        }

    val state: StateFlow<CalendarUiState> = combine(monthAgenda, selected, weekStartsSunday) { (m, items), sel, sunday ->
        CalendarUiState(
            month = m,
            selected = sel,
            today = today,
            sourcesByDate = items.sourcesByDate(),
            selectedItems = items.filter { it.date == sel },
            loading = false,
            weekStartsSunday = sunday,
        )
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        CalendarUiState(month.value, selected.value, today),
    )

    fun select(date: LocalDate) {
        selected.value = date
        month.value = YearMonth.from(date)
    }

    fun previousMonth() = moveMonth(-1)
    fun nextMonth() = moveMonth(1)

    fun goToToday() = select(LocalDate.now(clock))

    /** Pindah bulan; tanggal terpilih ikut ke hari yang sama (dipotong ke akhir bulan bila perlu). */
    private fun moveMonth(delta: Long) {
        val m = month.value.plusMonths(delta)
        month.value = m
        selected.value = m.atDay(minOf(selected.value.dayOfMonth, m.lengthOfMonth()))
    }
}
