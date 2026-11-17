package com.farhan.today.domain

import com.farhan.today.data.db.BudgetPeriod
import com.farhan.today.data.repo.FinanceRepository
import com.farhan.today.domain.model.BudgetStatus
import com.farhan.today.domain.model.BudgetSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class FormattersTest {

    @Test fun rupiah() {
        assertEquals("Rp0", formatRupiah(0))
        assertEquals("Rp8.000", formatRupiah(8_000))
        assertEquals("Rp42.000", formatRupiah(42_000))
        assertEquals("Rp1.250.000", formatRupiah(1_250_000))
        assertEquals("−Rp5.000", formatRupiah(-5_000))
    }

    @Test fun signedRupiahAlwaysShowsSign() {
        assertEquals("−Rp24.000", formatSignedRupiah(24_000, isIncome = false))
        assertEquals("+Rp1.250.000", formatSignedRupiah(1_250_000, isIncome = true))
        assertEquals("−Rp24.000", formatSignedRupiah(-24_000, isIncome = false))
    }

    @Test fun compactRupiah() {
        assertEquals("Rp8.000", formatRupiahCompact(8_000))
        assertEquals("Rp950rb", formatRupiahCompact(950_000))
        assertEquals("Rp12,5 jt", formatRupiahCompact(12_500_000))
        assertEquals("Rp2 jt", formatRupiahCompact(2_000_000))
        assertEquals("Rp1,2 M", formatRupiahCompact(1_200_000_000))
    }

    @Test fun timeAndDates() {
        assertEquals("09.05", formatTime(LocalTime.of(9, 5)))
        assertEquals("17.00", formatTime(LocalTime.of(17, 0)))
        assertEquals("Senin, 9 November", formatLongDate(LocalDate.of(2026, 11, 9)))
        assertEquals("November 2026", formatMonthYear(LocalDate.of(2026, 11, 1)))
        assertEquals("Min", shortDayName(DayOfWeek.SUNDAY))
    }

    @Test fun relativeDays() {
        val today = LocalDate.of(2026, 11, 9)
        assertEquals("Hari ini", relativeDay(today, today))
        assertEquals("Besok", relativeDay(today.plusDays(1), today))
        assertEquals("Kemarin", relativeDay(today.minusDays(1), today))
        assertEquals("12 Nov", relativeDay(today.plusDays(3), today))
    }

    @Test fun weeklyBudgetPeriodStartsMonday() {
        val (start, end) = FinanceRepository.budgetPeriod(BudgetPeriod.WEEKLY, 1, LocalDate.of(2026, 11, 15)) // Minggu
        assertEquals(LocalDate.of(2026, 11, 9), start)
        assertEquals(LocalDate.of(2026, 11, 16), end)
    }

    @Test fun monthlyBudgetPeriodWithCustomStartDay() {
        // Mulai tanggal 25: pada 10 Nov periodenya 25 Okt–25 Nov
        assertEquals(
            LocalDate.of(2026, 10, 25) to LocalDate.of(2026, 11, 25),
            FinanceRepository.budgetPeriod(BudgetPeriod.MONTHLY, 25, LocalDate.of(2026, 11, 10)),
        )
        assertEquals(
            LocalDate.of(2026, 11, 25) to LocalDate.of(2026, 12, 25),
            FinanceRepository.budgetPeriod(BudgetPeriod.MONTHLY, 25, LocalDate.of(2026, 11, 25)),
        )
        // Hari mulai di atas 28 dipotong agar Februari aman
        assertEquals(
            LocalDate.of(2027, 1, 28) to LocalDate.of(2027, 2, 28),
            FinanceRepository.budgetPeriod(BudgetPeriod.MONTHLY, 31, LocalDate.of(2027, 2, 10)),
        )
    }

    @Test fun budgetStatus() {
        val d = LocalDate.of(2026, 11, 9)
        fun s(spent: Long) = BudgetSummary("b", "Makan", 100_000, spent, d, d.plusDays(7))
        assertEquals(BudgetStatus.SAFE, s(58_000).status)
        assertEquals(BudgetStatus.LOW, s(85_000).status)
        assertEquals(BudgetStatus.OVER, s(120_000).status)
        assertEquals(-20_000L, s(120_000).remaining)
    }
}
