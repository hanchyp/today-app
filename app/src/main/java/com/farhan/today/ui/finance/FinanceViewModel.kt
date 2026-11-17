package com.farhan.today.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.farhan.today.data.db.BillEntity
import com.farhan.today.data.db.BillPaymentEntity
import com.farhan.today.data.db.CategoryEntity
import com.farhan.today.data.db.MoneyKind
import com.farhan.today.data.db.MoneyTransactionEntity
import com.farhan.today.data.db.TransactionRow
import com.farhan.today.data.repo.BillInput
import com.farhan.today.data.repo.DayGroup
import com.farhan.today.data.repo.FinanceRepository
import com.farhan.today.data.repo.MonthSummary
import com.farhan.today.data.repo.TransactionInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.YearMonth

enum class KindFilter(val label: String) { ALL("Semua"), EXPENSE("Keluar"), INCOME("Masuk") }

data class TransactionsState(
    val month: YearMonth,
    val kind: KindFilter = KindFilter.ALL,
    val categoryId: String? = null,
    val query: String = "",
    val groups: List<DayGroup> = emptyList(),
    val income: Long = 0,
    val expense: Long = 0,
    val loading: Boolean = true,
)

/**
 * Satu ViewModel untuk semua layar Keuangan; setiap layar hanya mengumpulkan flow yang dipakainya
 * (stateIn WhileSubscribed), jadi query yang tidak tampil tidak berjalan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModel(private val repo: FinanceRepository, private val clock: Clock) : ViewModel() {
    private fun <T> share(flow: kotlinx.coroutines.flow.Flow<T>, initial: T) =
        flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val categories: StateFlow<List<CategoryEntity>> = share(repo.categories(), emptyList())
    val allCategories: StateFlow<List<CategoryEntity>> = share(repo.allCategories(), emptyList())
    val recent: StateFlow<List<TransactionRow>> = share(repo.recentRows(5), emptyList())
    val bills: StateFlow<List<BillEntity>> = share(repo.allBills(), emptyList())
    val payments: StateFlow<List<BillPaymentEntity>> = share(repo.allPayments(), emptyList())

    // ---------------- bulan & filter (Semua transaksi + Ringkasan)
    val month = MutableStateFlow(YearMonth.now(clock))
    private val kind = MutableStateFlow(KindFilter.ALL)
    private val category = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")

    val summary: StateFlow<MonthSummary?> = share(month.flatMapLatest { repo.monthSummary(it) }, null)

    val transactions: StateFlow<TransactionsState> = share(
        combine(month.flatMapLatest { m -> repo.transactionsIn(m).map { m to it } }, kind, category, query) { (m, rows), k, c, q ->
            val filtered = rows.filter { r ->
                (k == KindFilter.ALL || (k == KindFilter.EXPENSE) == (r.tx.kind == MoneyKind.EXPENSE)) &&
                    (c == null || r.tx.categoryId == c) &&
                    (q.isBlank() || r.tx.note.contains(q.trim(), true) || r.categoryName.orEmpty().contains(q.trim(), true))
            }
            TransactionsState(
                month = m, kind = k, categoryId = c, query = q,
                groups = FinanceRepository.groupByDay(filtered, clock.zone),
                income = filtered.filter { it.tx.kind == MoneyKind.INCOME }.sumOf { it.tx.amount },
                expense = filtered.filter { it.tx.kind == MoneyKind.EXPENSE }.sumOf { it.tx.amount },
                loading = false,
            )
        },
        TransactionsState(YearMonth.now(clock)),
    )

    fun shiftMonth(delta: Long) { month.value = month.value.plusMonths(delta) }
    fun setKind(k: KindFilter) { kind.value = k }
    fun setCategory(id: String?) { category.value = id }
    fun setQuery(q: String) { query.value = q }

    // ---------------- aksi
    val wallets = share(repo.wallets(), emptyList())
    val unallocated = share(repo.unallocated(), 0L)
    fun createWallet(name: String, done: (String?) -> Unit) = walletAction(done) { repo.createWallet(name) }
    fun allocateWallet(id: String, amount: Long, done: (String?) -> Unit) = walletAction(done) { repo.allocateWallet(id, amount) }
    fun saveTransaction(input: TransactionInput, id: String?, done: (String?) -> Unit) = walletAction(done) {
        require(input.kind != MoneyKind.EXPENSE || input.walletId != null) { "Pilih dompet pembayaran" }
        repo.saveTransaction(input, id)
    }
    private fun walletAction(done: (String?) -> Unit, action: suspend () -> Unit) = viewModelScope.launch {
        val error = try {
            action()
            null
        } catch (e: IllegalArgumentException) {
            e.message ?: "Periksa data yang dimasukkan"
        }
        done(error)
    }
    fun deleteTransaction(id: String, done: (String?) -> Unit) = walletAction(done) { repo.deleteTransaction(id) }
    fun restoreTransaction(id: String, done: (String?) -> Unit) = walletAction(done) { repo.restoreTransaction(id) }
    suspend fun loadTransaction(id: String): MoneyTransactionEntity? = repo.transaction(id)

    fun saveBill(input: BillInput, id: String?) = viewModelScope.launch { repo.saveBill(input, id) }
    fun deletePaymentHistory(id: String) = viewModelScope.launch { repo.deletePaymentHistory(id) }
    fun deleteBill(id: String) = viewModelScope.launch { repo.deleteBill(id) }
    fun payBill(id: String, walletId: String, done: (String?) -> Unit) = walletAction(done) {
        repo.payBill(id, walletId = walletId)
    }

    fun saveCategory(name: String, kind: MoneyKind, id: String? = null) = viewModelScope.launch { repo.saveCategory(name, kind, id) }
    fun archiveCategory(id: String, archived: Boolean) = viewModelScope.launch { repo.setCategoryArchived(id, archived) }
    fun moveCategory(id: String, delta: Int) = viewModelScope.launch { repo.moveCategory(id, delta) }
}
