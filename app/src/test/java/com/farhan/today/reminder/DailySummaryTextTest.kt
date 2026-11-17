package com.farhan.today.reminder

import com.farhan.today.TestToday
import com.farhan.today.domain.model.AgendaItem
import com.farhan.today.domain.model.AgendaSource
import com.farhan.today.domain.model.BudgetSummary
import com.farhan.today.domain.model.TodayOverview
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class DailySummaryTextTest {
    private fun item(title: String, time: LocalTime?, done: Boolean = false) =
        AgendaItem(title, AgendaSource.TASK, title, "", title, "", TestToday, time, done)

    private val walletBalance = 42_000L

    @Test fun bodyMatchesSpecExample() {
        val items = listOf(item("Push Day", LocalTime.of(17, 0)), item("Coding test", LocalTime.of(20, 0)), item("Revisi Bab 3", null), item("Sudah", null, done = true))
        val o = TodayOverview(TestToday, items, items.first(), walletBalance, emptyMap())
        assertEquals("3 agenda hari ini · saldo dompet Rp42.000\nBerikutnya: Push Day jam 17.00", DailySummaryText.body(o))
        assertEquals("Pagi, Farhan! 3 agenda hari ini", DailySummaryText.title(o, "Farhan"))
    }

    @Test fun emptyDay() {
        val o = TodayOverview(TestToday, emptyList(), null, null, emptyMap())
        assertEquals("Tidak ada agenda", DailySummaryText.body(o))
        assertEquals("Pagi, Farhan! Hari ini longgar ☀️", DailySummaryText.title(o, "Farhan"))
    }

    @Test fun delayUntilNextOccurrence() {
        val at = LocalTime.of(6, 30)
        assertEquals(Duration.ofMinutes(30), DailySummaryText.delayUntil(LocalDateTime.of(TestToday, LocalTime.of(6, 0)), at))
        assertEquals(Duration.ofHours(24), DailySummaryText.delayUntil(LocalDateTime.of(TestToday, at), at))
        assertEquals(Duration.ofHours(20).plusMinutes(49), DailySummaryText.delayUntil(LocalDateTime.of(TestToday, LocalTime.of(9, 41)), at))
    }
}
