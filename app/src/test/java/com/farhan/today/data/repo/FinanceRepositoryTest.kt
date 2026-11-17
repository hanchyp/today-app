package com.farhan.today.data.repo

import android.app.Application
import com.farhan.today.TestToday
import com.farhan.today.data.db.BillRepeat
import com.farhan.today.data.db.BudgetPeriod
import com.farhan.today.data.db.MoneyKind
import com.farhan.today.data.db.MoneyTransactionEntity
import com.farhan.today.data.db.ReminderSource
import com.farhan.today.data.db.TodayDatabase
import com.farhan.today.data.db.TransactionRow
import com.farhan.today.fixedClock
import com.farhan.today.inMemoryDb
import com.farhan.today.millisAt
import com.farhan.today.Jakarta
import com.farhan.today.reminder.FakeAlarmGateway
import com.farhan.today.reminder.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FinanceRepositoryTest {
    private lateinit var db: TodayDatabase
    private val clock = fixedClock()
    private val gateway = FakeAlarmGateway()
    private lateinit var repo: FinanceRepository
    private lateinit var food: String
    private lateinit var transport: String
    private lateinit var salary: String

    @Before fun setUp() = runTest {
        db = inMemoryDb()
        repo = FinanceRepository(db, clock, ReminderScheduler(db.reminderDao(), gateway, clock))
        food = repo.saveCategory("Makan", MoneyKind.EXPENSE)
        transport = repo.saveCategory("Transport", MoneyKind.EXPENSE)
        salary = repo.saveCategory("Gaji", MoneyKind.INCOME)
    }

    @After fun tearDown() = db.close()

    private fun at(day: LocalDate, h: Int = 12) = LocalDateTime.of(day, LocalTime.of(h, 0))

    @Test(expected = IllegalArgumentException::class)
    fun zeroAmountRejected() = runTest { repo.saveTransaction(TransactionInput(MoneyKind.EXPENSE, 0, food)) }

    @Test fun budgetRemainingFollowsTransactions() = runTest {
        repo.saveBudget("Uang makan", food, 100_000, BudgetPeriod.WEEKLY)
        assertEquals(100_000L, repo.budgetSummaries().first().single().remaining)

        val lunch = repo.saveTransaction(TransactionInput(MoneyKind.EXPENSE, 24_000, food, "Makan siang"))
        repo.saveTransaction(TransactionInput(MoneyKind.EXPENSE, 8_000, transport, "KRL"))          // kategori lain
        repo.saveTransaction(TransactionInput(MoneyKind.INCOME, 500_000, salary, "Freelance"))      // pemasukan
        repo.saveTransaction(TransactionInput(MoneyKind.EXPENSE, 99_000, food, "Minggu lalu", at(TestToday.minusDays(3)))) // di luar periode
        assertEquals(76_000L, repo.budgetSummaries().first().single().remaining)

        repo.deleteTransaction(lunch)
        assertEquals(100_000L, repo.budgetSummaries().first().single().remaining)
        repo.restoreTransaction(lunch)
        assertEquals(76_000L, repo.budgetSummaries().first().single().remaining)
    }

    @Test fun payMonthlyBillOn31st_keepsAnchorDay() = runTest {
        val gym = repo.saveBill(BillInput("Gym", 150_000, null, BillRepeat.MONTHLY, LocalDate.of(2027, 1, 31), reminderDaysBefore = 1))
        assertEquals(LocalDate.of(2027, 2, 28), repo.payBill(gym)!!.nextDueDate)
        assertEquals(LocalDate.of(2027, 3, 31), repo.payBill(gym)!!.nextDueDate) // kembali ke 31, tidak tertahan di 28
        assertEquals(LocalDate.of(2027, 4, 30), repo.payBill(gym)!!.nextDueDate)
    }

    @Test fun payBill_createsLinkedExpenseAndPayment_andReschedulesReminder() = runTest {
        val gym = repo.saveBill(BillInput("Membership gym", 150_000, food, BillRepeat.MONTHLY, TestToday.plusDays(3), reminderDaysBefore = 1))
        assertEquals(listOf(millisAt(TestToday.plusDays(2), 9)), gateway.active.values.toList())

        repo.payBill(gym)
        val tx = repo.recentRows(5).first().single().tx
        assertEquals(MoneyKind.EXPENSE, tx.kind)
        assertEquals(150_000L, tx.amount)
        assertEquals("Membership gym", tx.note)
        val payment = repo.payments(gym).first().single()
        assertEquals(tx.id, payment.transactionId)
        assertEquals(payment.id, tx.billPaymentId)
        assertEquals(TestToday.plusDays(3), payment.dueDate)

        // Pengingat pindah ke H-1 jatuh tempo berikutnya
        val next = TestToday.plusDays(3).plusMonths(1)
        assertEquals(listOf(millisAt(next.minusDays(1), 9)), gateway.active.values.toList())
    }

    @Test fun oneTimeBillBecomesInactiveAfterPayment() = runTest {
        val id = repo.saveBill(BillInput("Wisuda", 300_000, null, BillRepeat.NONE, TestToday.plusDays(10), reminderDaysBefore = 3))
        assertFalse(repo.payBill(id)!!.active)
        assertTrue(repo.activeBills().first().isEmpty())
        assertTrue(gateway.active.isEmpty())
        assertTrue(db.reminderDao().scheduled().none { it.sourceType == ReminderSource.BILL })
    }

    @Test fun monthSummary_totalsAndCategoryBreakdown() = runTest {
        repo.saveTransaction(TransactionInput(MoneyKind.EXPENSE, 24_000, food, "", at(TestToday)))
        repo.saveTransaction(TransactionInput(MoneyKind.EXPENSE, 34_000, food, "", at(TestToday.minusDays(2))))
        repo.saveTransaction(TransactionInput(MoneyKind.EXPENSE, 8_000, transport, "", at(TestToday)))
        repo.saveTransaction(TransactionInput(MoneyKind.INCOME, 1_250_000, salary, "", at(TestToday)))
        repo.saveTransaction(TransactionInput(MoneyKind.EXPENSE, 50_000, food, "", at(LocalDate.of(2026, 10, 31)))) // bulan lalu

        val s = repo.monthSummary(YearMonth.of(2026, 11)).first()
        assertEquals(1_250_000L, s.income)
        assertEquals(66_000L, s.expense)
        assertEquals(1_184_000L, s.net)
        assertEquals(listOf("Makan" to 58_000L, "Transport" to 8_000L), s.byCategory.map { it.name to it.total })
    }

    @Test fun groupByDay_newestFirstWithSubtotals() {
        fun row(day: LocalDate, h: Int, kind: MoneyKind, amount: Long) = TransactionRow(
            MoneyTransactionEntity(kind = kind, amount = amount, categoryId = null, occurredAt = millisAt(day, h), createdAt = 0, updatedAt = 0),
            null, null,
        )
        val groups = FinanceRepository.groupByDay(
            listOf(row(TestToday.minusDays(1), 9, MoneyKind.EXPENSE, 10_000), row(TestToday, 8, MoneyKind.EXPENSE, 8_000), row(TestToday, 12, MoneyKind.INCOME, 50_000)),
            Jakarta,
        )
        assertEquals(listOf(TestToday, TestToday.minusDays(1)), groups.map { it.date })
        assertEquals(42_000L, groups.first().net)
        assertEquals(listOf(12, 8), groups.first().rows.map { java.time.Instant.ofEpochMilli(it.tx.occurredAt).atZone(Jakarta).hour })
        assertEquals(-10_000L, groups.last().net)
    }

    @Test fun categories_archiveAndReorder() = runTest {
        val other = repo.saveCategory("Hiburan", MoneyKind.EXPENSE)
        repo.moveCategory(other, -1)
        assertEquals(listOf("Makan", "Hiburan", "Transport"), repo.categories().first().filter { it.kind == MoneyKind.EXPENSE }.map { it.name })

        repo.setCategoryArchived(other, true)
        assertEquals(listOf("Makan", "Transport"), repo.categories().first().filter { it.kind == MoneyKind.EXPENSE }.map { it.name })
        assertTrue(repo.allCategories().first().any { it.id == other && it.archived })
    }

    @Test fun nextDueDateYearly_handlesLeapDay() {
        assertEquals(LocalDate.of(2029, 2, 28), FinanceRepository.nextDueDate(LocalDate.of(2028, 2, 29), BillRepeat.YEARLY, 29))
        assertEquals(LocalDate.of(2032, 2, 29), FinanceRepository.nextDueDate(LocalDate.of(2031, 2, 28), BillRepeat.YEARLY, 29))
    }
}
