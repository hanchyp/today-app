package com.farhan.today.domain.usecase

import com.farhan.today.data.repo.AgendaRepository
import com.farhan.today.data.repo.FinanceRepository
import com.farhan.today.domain.model.AgendaItem
import com.farhan.today.domain.model.AgendaSource
import com.farhan.today.domain.model.TodayOverview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** Agenda gabungan untuk rentang tanggal (Kalender). */
class GetAgendaForRange(private val agenda: AgendaRepository) {
    operator fun invoke(start: LocalDate, end: LocalDate): Flow<List<AgendaItem>> = agenda.agendaBetween(start, end)
}

/** Semua yang dibutuhkan layar Today: agenda hari ini, item berikutnya, ringkasan anggaran, titik minggu ini. */
class GetTodayOverview(
    private val agenda: AgendaRepository,
    private val finance: FinanceRepository,
    private val clock: Clock,
) {
    operator fun invoke(weekStartsSunday: Boolean = false): Flow<TodayOverview> {
        val today = LocalDate.now(clock)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(if (weekStartsSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)

        return combine(agenda.agendaBetween(weekStart, weekEnd), finance.wallets()) { week, wallets ->
            val todayItems = week.filter { it.date == today }
            TodayOverview(
                date = today,
                items = todayItems,
                next = AgendaRepository.pickNext(todayItems, LocalTime.now(clock)),
                walletBalance = wallets.takeIf { it.isNotEmpty() }?.sumOf { it.balance },
                weekAgenda = week.groupBy { it.date }.mapValues { (_, v) -> v.map(AgendaItem::source).toSet() },
                weekStart = weekStart,
            )
        }
    }
}

/** Untuk titik di kalender: sumber agenda per tanggal. */
fun List<AgendaItem>.sourcesByDate(): Map<LocalDate, Set<AgendaSource>> =
    groupBy { it.date }.mapValues { (_, v) -> v.map(AgendaItem::source).toSet() }
