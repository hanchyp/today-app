package com.farhan.today.domain.model

import java.time.LocalDate
import java.time.LocalTime

enum class AgendaSource { WORKOUT, CAREER, FINANCE, TASK, CALENDAR }

/** Satu baris agenda gabungan untuk Today & Kalender (docs/SPRINTS.md 0.5). */
data class AgendaItem(
    val id: String,
    val source: AgendaSource,
    val sourceId: String,
    /** Label kecil di atas judul, misal "Latihan" atau "Coding test". */
    val label: String,
    val title: String,
    val subtitle: String,
    val date: LocalDate,
    /** null = seharian (deadline, jatuh tempo). */
    val time: LocalTime?,
    val done: Boolean,
)

data class BudgetSummary(
    val budgetId: String,
    val name: String,
    val limit: Long,
    val spent: Long,
    val periodStart: LocalDate,
    /** Eksklusif. */
    val periodEnd: LocalDate,
) {
    val remaining: Long get() = limit - spent
    val fractionUsed: Float get() = if (limit <= 0) 0f else (spent.toFloat() / limit).coerceIn(0f, 1f)
    val status: BudgetStatus
        get() = when {
            remaining < 0 -> BudgetStatus.OVER
            fractionUsed >= 0.8f -> BudgetStatus.LOW
            else -> BudgetStatus.SAFE
        }
}

enum class BudgetStatus(val label: String) { SAFE("Aman"), LOW("Hampir habis"), OVER("Lewat batas") }

data class TodayOverview(
    val date: LocalDate,
    val items: List<AgendaItem>,
    /** Item pertama yang belum selesai dan belum lewat; item seharian dipakai bila tak ada yang berjam. */
    val next: AgendaItem?,
    val walletBalance: Long?,
    /** Tanggal-tanggal minggu ini yang punya agenda, untuk titik di strip tanggal. */
    val weekAgenda: Map<LocalDate, Set<AgendaSource>>,
    /** Hari pertama strip minggu (Senin, atau Minggu sesuai pengaturan). */
    val weekStart: LocalDate = date.minusDays(date.dayOfWeek.value - 1L),
)
