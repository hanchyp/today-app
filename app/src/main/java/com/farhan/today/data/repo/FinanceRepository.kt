package com.farhan.today.data.repo

import androidx.room.withTransaction
import com.farhan.today.data.db.Wallet
import com.farhan.today.data.db.WalletAllocation
import kotlinx.coroutines.flow.first
import com.farhan.today.data.db.BillEntity
import com.farhan.today.data.db.BillPaymentEntity
import com.farhan.today.data.db.BillRepeat
import com.farhan.today.data.db.BudgetEntity
import com.farhan.today.data.db.BudgetPeriod
import com.farhan.today.data.db.CategoryEntity
import com.farhan.today.data.db.CategoryTotal
import com.farhan.today.data.db.MoneyKind
import com.farhan.today.data.db.MoneyTransactionEntity
import com.farhan.today.data.db.ReminderSource
import com.farhan.today.data.db.TodayDatabase
import com.farhan.today.data.db.TransactionRow
import com.farhan.today.data.db.newId
import com.farhan.today.domain.formatRupiah
import com.farhan.today.domain.model.BudgetSummary
import com.farhan.today.reminder.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class TransactionInput(
    val kind: MoneyKind,
    val amount: Long,
    val categoryId: String?,
    val note: String = "",
    val occurredAt: LocalDateTime? = null,
    val walletId: String? = null,
) {
    val isValid: Boolean get() = amount > 0
}

data class BillInput(
    val name: String,
    val amount: Long,
    val categoryId: String?,
    val repeat: BillRepeat,
    val nextDueDate: LocalDate,
    val reminderDaysBefore: Int = 1,
) {
    val isValid: Boolean get() = name.isNotBlank() && amount > 0
}

data class MonthSummary(
    val month: YearMonth,
    val income: Long,
    val expense: Long,
    val byCategory: List<CategoryTotal>,
) {
    val net: Long get() = income - expense
}

data class DayGroup(val date: LocalDate, val rows: List<TransactionRow>) {
    val income: Long get() = rows.filter { it.tx.kind == MoneyKind.INCOME }.sumOf { it.tx.amount }
    val expense: Long get() = rows.filter { it.tx.kind == MoneyKind.EXPENSE }.sumOf { it.tx.amount }
    val net: Long get() = income - expense
}

class FinanceRepository(
    private val db: TodayDatabase,
    private val clock: Clock,
    private val reminders: ReminderScheduler? = null,
) {
    private val dao = db.financeDao()

    // ------------------------------------------------------------ baca

    private val walletDao = db.walletDao()
    private fun currentWeek() = budgetPeriod(BudgetPeriod.WEEKLY, 1, LocalDate.now(clock)).first.toEpochDay()
    fun wallets() = walletDao.balances(currentWeek())
    fun unallocated() = walletDao.unallocated()
    suspend fun createWallet(name: String) {
        require(name.isNotBlank()) { "Nama dompet wajib diisi" }
        walletDao.create(Wallet(name = name.trim()))
    }
    suspend fun allocateWallet(id: String, amount: Long) = db.withTransaction {
        require(amount > 0 && amount <= unallocated().first()) { "Saldo belum dialokasikan tidak cukup" }
        require(wallets().first().any { it.id == id }) { "Dompet tidak ditemukan" }
        walletDao.allocate(WalletAllocation(walletId = id, amount = amount, weekStart = currentWeek()))
    }
    private suspend fun checkWallet(id: String, amount: Long, existing: MoneyTransactionEntity? = null) {
        val wallet = wallets().first().firstOrNull { it.id == id }
        val credit = existing?.takeIf { it.walletId == id && it.kind == MoneyKind.EXPENSE }?.amount ?: 0L
        require(wallet != null && amount <= wallet.balance + credit) { "Saldo dompet tidak cukup" }
    }
    fun budgets() = dao.budgets()
    fun categories() = dao.categories()
    fun allCategories() = dao.allCategories()
    fun recentTransactions(limit: Int = 20) = dao.recentTransactions(limit)
    fun recentRows(limit: Int = 5) = dao.recentRows(limit)
    fun activeBills() = dao.activeBills()
    fun allBills() = dao.allBills()
    fun payments(billId: String) = dao.payments(billId)
    fun allPayments() = dao.allPayments()
    suspend fun deletePaymentHistory(id: String) = dao.softDeletePayment(id, clock.millis())
    suspend fun transaction(id: String) = dao.transactionOnce(id)
    suspend fun bill(id: String) = dao.billOnce(id)
    suspend fun budget(id: String) = dao.budgetOnce(id)

    private fun millis(date: LocalDate) = date.atStartOfDay(clock.zone).toInstant().toEpochMilli()

    fun budgetSummary(budget: BudgetEntity, today: LocalDate): Flow<BudgetSummary> {
        val (start, end) = budgetPeriod(budget.period, budget.startDayOfMonth, today)
        val from = millis(start)
        val to = millis(end)
        val spent = budget.categoryId?.let { dao.expenseTotalForCategory(it, from, to) } ?: dao.expenseTotal(from, to)
        return spent.map { BudgetSummary(budget.id, budget.name, budget.amount, it, start, end) }
    }

    /** Ringkasan semua anggaran pada periode berjalan. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun budgetSummaries(): Flow<List<BudgetSummary>> = dao.budgets().flatMapLatest { list ->
        val today = LocalDate.now(clock)
        if (list.isEmpty()) flowOf(emptyList()) else combine(list.map { budgetSummary(it, today) }) { it.toList() }
    }

    fun transactionsIn(month: YearMonth): Flow<List<TransactionRow>> =
        dao.transactionsBetween(millis(month.atDay(1)), millis(month.plusMonths(1).atDay(1)))

    fun monthSummary(month: YearMonth): Flow<MonthSummary> {
        val from = millis(month.atDay(1))
        val to = millis(month.plusMonths(1).atDay(1))
        return combine(
            dao.totalOfKind(MoneyKind.INCOME, from, to),
            dao.totalOfKind(MoneyKind.EXPENSE, from, to),
            dao.expenseByCategory(from, to),
        ) { inc, exp, cats -> MonthSummary(month, inc, exp, cats) }
    }

    // ------------------------------------------------------------ transaksi

    suspend fun saveTransaction(input: TransactionInput, id: String? = null): String = db.withTransaction {
        require(input.isValid) { "Nominal harus lebih dari 0" }
        val now = clock.millis()
        val existing = id?.let { dao.transactionOnce(it) }
        val walletId = input.walletId.takeIf { input.kind == MoneyKind.EXPENSE }
        val walletFunding = input.kind == MoneyKind.INCOME && (existing == null || existing.walletFunding || existing.kind != MoneyKind.INCOME)
        val previousFunding = existing?.takeIf { it.walletFunding }?.amount ?: 0L
        val newFunding = if (walletFunding) input.amount else 0L
        if (newFunding < previousFunding) {
            require(unallocated().first() >= previousFunding - newFunding) { "Sebagian pemasukan sudah dialokasikan ke dompet" }
        }
        if (walletId != null) checkWallet(walletId, input.amount, existing)
        val at = input.occurredAt?.atZone(clock.zone)?.toInstant()?.toEpochMilli() ?: existing?.occurredAt ?: now
        val tx = MoneyTransactionEntity(
            id = existing?.id ?: newId(), kind = input.kind, amount = input.amount, categoryId = input.categoryId,
            note = input.note.trim(), occurredAt = at, billPaymentId = existing?.billPaymentId, walletId = walletId, walletFunding = walletFunding,
            createdAt = existing?.createdAt ?: now, updatedAt = now,
        )
        dao.upsertTransactions(listOf(tx))
        tx.id
    }

    suspend fun deleteTransaction(id: String) = db.withTransaction {
        val existing = dao.transactionOnce(id) ?: return@withTransaction
        require(!existing.walletFunding || unallocated().first() >= existing.amount) { "Sebagian pemasukan sudah dialokasikan ke dompet" }
        dao.softDeleteTransaction(id, clock.millis())
    }
    suspend fun restoreTransaction(id: String) = db.withTransaction {
        val existing = dao.transactionOnce(id)?.takeIf { it.deletedAt != null } ?: return@withTransaction
        existing.walletId?.let { checkWallet(it, existing.amount) }
        dao.restoreTransaction(id, clock.millis())
    }

    // ------------------------------------------------------------ anggaran

    suspend fun saveBudget(name: String, categoryId: String?, amount: Long, period: BudgetPeriod, startDay: Int = 1, id: String? = null): String {
        require(name.isNotBlank() && amount > 0) { "Nama dan nominal anggaran wajib diisi" }
        val now = clock.millis()
        val existing = id?.let { dao.budgetOnce(it) }
        val b = BudgetEntity(
            id = existing?.id ?: newId(), name = name.trim(), categoryId = categoryId, amount = amount,
            period = period, startDayOfMonth = startDay.coerceIn(1, 28),
            createdAt = existing?.createdAt ?: now, updatedAt = now,
        )
        dao.upsertBudget(b)
        return b.id
    }

    suspend fun deleteBudget(id: String) = dao.softDeleteBudget(id, clock.millis())

    // ------------------------------------------------------------ kategori

    suspend fun saveCategory(name: String, kind: MoneyKind, id: String? = null): String {
        require(name.isNotBlank()) { "Nama kategori wajib diisi" }
        val now = clock.millis()
        val existing = id?.let { dao.categoryOnce(it) }
        val c = existing?.copy(name = name.trim(), updatedAt = now) ?: CategoryEntity(
            name = name.trim(), kind = kind, iconKey = "other", colorKey = if (kind == MoneyKind.INCOME) "income" else "finance",
            position = dao.maxCategoryPosition(kind) + 1, createdAt = now, updatedAt = now,
        )
        dao.upsertCategories(listOf(c))
        return c.id
    }

    suspend fun setCategoryArchived(id: String, archived: Boolean) {
        val c = dao.categoryOnce(id) ?: return
        dao.upsertCategories(listOf(c.copy(archived = archived, updatedAt = clock.millis())))
    }

    /** Geser kategori satu posisi (delta −1 / +1) di antara kategori sejenis yang aktif. */
    suspend fun moveCategory(id: String, delta: Int) {
        val c = dao.categoryOnce(id) ?: return
        val list = dao.categoriesOfKindOnce(c.kind).toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list.add(j, list.removeAt(i))
        val now = clock.millis()
        dao.upsertCategories(list.mapIndexed { pos, e -> e.copy(position = pos, updatedAt = now) })
    }

    // ------------------------------------------------------------ tagihan

    suspend fun saveBill(input: BillInput, id: String? = null): String {
        require(input.isValid) { "Nama dan nominal tagihan wajib diisi" }
        val now = clock.millis()
        val existing = id?.let { dao.billOnce(it) }
        val b = BillEntity(
            id = existing?.id ?: newId(), name = input.name.trim(), amount = input.amount, categoryId = input.categoryId,
            repeat = input.repeat, nextDueDate = input.nextDueDate, reminderDaysBefore = input.reminderDaysBefore.coerceIn(0, 30),
            active = true, createdAt = existing?.createdAt ?: now, updatedAt = now,
        )
        dao.upsertBill(b)
        syncBillReminder(b)
        return b.id
    }

    suspend fun deleteBill(id: String) {
        dao.softDeleteBill(id, clock.millis())
        reminders?.cancel(ReminderSource.BILL, id)
    }

    /**
     * Lunasi tagihan jatuh tempo berikutnya dalam satu transaksi DB:
     * buat transaksi pengeluaran + catatan pembayaran, lalu majukan jatuh tempo
     * (atau nonaktifkan bila tidak berulang). Pengingat dijadwalkan ulang.
     */
    suspend fun payBill(billId: String, paidAt: LocalDateTime? = null, walletId: String? = null): BillEntity? {
        val now = clock.millis()
        val paidMillis = paidAt?.atZone(clock.zone)?.toInstant()?.toEpochMilli() ?: now
        val updated = db.withTransaction {
            val bill = dao.billOnce(billId) ?: return@withTransaction null
            if (!bill.active) return@withTransaction null
            if (walletId != null) checkWallet(walletId, bill.amount)
            val payment = BillPaymentEntity(
                billId = bill.id, dueDate = bill.nextDueDate, paidAt = paidMillis, amount = bill.amount,
                transactionId = null, createdAt = now, updatedAt = now,
            )
            val tx = MoneyTransactionEntity(
                kind = MoneyKind.EXPENSE, amount = bill.amount, categoryId = bill.categoryId, note = bill.name,
                occurredAt = paidMillis, billPaymentId = payment.id, walletId = walletId, createdAt = now, updatedAt = now,
            )
            val anchor = dao.firstPayment(bill.id)?.dueDate?.dayOfMonth ?: bill.nextDueDate.dayOfMonth
            dao.upsertTransactions(listOf(tx))
            dao.upsertBillPayment(payment.copy(transactionId = tx.id))
            val next = when (bill.repeat) {
                BillRepeat.NONE -> bill.copy(active = false, updatedAt = now)
                else -> bill.copy(nextDueDate = nextDueDate(bill.nextDueDate, bill.repeat, anchor), updatedAt = now)
            }
            dao.upsertBill(next)
            next
        }
        updated?.let { syncBillReminder(it) }
        return updated
    }

    /** Jadwalkan ulang pengingat semua tagihan aktif (dipakai setelah impor/backup). */
    suspend fun syncAllBillReminders() = dao.activeBillsOnce().forEach { syncBillReminder(it) }

    private suspend fun syncBillReminder(bill: BillEntity) {
        val r = reminders ?: return
        if (!bill.active || bill.deletedAt != null) {
            r.cancel(ReminderSource.BILL, bill.id); return
        }
        val day = bill.nextDueDate.minusDays(bill.reminderDaysBefore.toLong())
        val at = day.atTime(LocalTime.of(9, 0)).atZone(clock.zone).toInstant().toEpochMilli()
        val whenText = when (bill.reminderDaysBefore) { 0 -> "hari ini"; 1 -> "besok"; else -> "${bill.reminderDaysBefore} hari lagi" }
        r.schedule(ReminderSource.BILL, bill.id, at, "Tagihan ${bill.name} jatuh tempo $whenText", "${formatRupiah(bill.amount)} · ketuk untuk menandai lunas")
    }

    companion object {
        /** Periode anggaran yang memuat [today]: [start, end) — minggu mulai Senin. */
        fun budgetPeriod(period: BudgetPeriod, startDayOfMonth: Int, today: LocalDate): Pair<LocalDate, LocalDate> =
            when (period) {
                BudgetPeriod.WEEKLY -> {
                    val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    start to start.plusWeeks(1)
                }
                BudgetPeriod.MONTHLY -> {
                    val day = startDayOfMonth.coerceIn(1, 28)
                    val start = if (today.dayOfMonth >= day) today.withDayOfMonth(day)
                    else today.minusMonths(1).withDayOfMonth(day)
                    start to start.plusMonths(1)
                }
            }

        /**
         * Jatuh tempo berikutnya. [anchorDay] = tanggal asli tagihan, agar tagihan tanggal 31
         * jatuh di 28/29 Feb lalu kembali ke 31 Maret (tidak "bergeser" permanen).
         */
        fun nextDueDate(current: LocalDate, repeat: BillRepeat, anchorDay: Int): LocalDate = when (repeat) {
            BillRepeat.NONE -> current
            BillRepeat.MONTHLY -> {
                val m = YearMonth.from(current).plusMonths(1)
                m.atDay(minOf(anchorDay, m.lengthOfMonth()))
            }
            BillRepeat.YEARLY -> {
                val m = YearMonth.from(current).plusYears(1)
                m.atDay(minOf(anchorDay, m.lengthOfMonth()))
            }
        }

        /** Kelompokkan transaksi per hari (terbaru dulu) dengan subtotal. */
        fun groupByDay(rows: List<TransactionRow>, zone: java.time.ZoneId): List<DayGroup> =
            rows.groupBy { java.time.Instant.ofEpochMilli(it.tx.occurredAt).atZone(zone).toLocalDate() }
                .toSortedMap(compareByDescending { it })
                .map { (d, r) -> DayGroup(d, r.sortedByDescending { it.tx.occurredAt }) }
    }
}
