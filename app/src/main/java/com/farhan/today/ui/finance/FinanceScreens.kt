package com.farhan.today.ui.finance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import com.farhan.today.ui.components.DetailScaffold
import com.farhan.today.ui.components.LoadingSkeleton
import com.farhan.today.ui.components.ScreenColumn
import com.farhan.today.ui.components.SearchField
import com.farhan.today.ui.components.TodayFab
import com.farhan.today.ui.components.TopBarAction
import com.farhan.today.ui.components.fabExpanded
import com.farhan.today.ui.theme.Spacing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.farhan.today.R
import com.farhan.today.appContainer
import com.farhan.today.data.db.BillEntity
import com.farhan.today.data.db.BillRepeat
import com.farhan.today.data.db.CategoryEntity
import com.farhan.today.data.db.MoneyKind
import com.farhan.today.data.db.TransactionRow
import com.farhan.today.data.repo.BillInput
import com.farhan.today.data.repo.TransactionInput
import com.farhan.today.domain.formatLongDate
import com.farhan.today.domain.formatMonthYear
import com.farhan.today.domain.formatRupiah
import com.farhan.today.domain.formatRupiahCompact
import com.farhan.today.domain.formatSignedRupiah
import com.farhan.today.domain.formatTime
import com.farhan.today.domain.relativeDay
import com.farhan.today.ui.components.ChoiceChips
import com.farhan.today.ui.components.ConfirmDialog
import com.farhan.today.ui.components.DateField
import com.farhan.today.ui.components.EmptyState
import com.farhan.today.ui.components.FieldLabel
import com.farhan.today.ui.components.HeroCard
import com.farhan.today.ui.components.ListCard
import com.farhan.today.ui.components.Pill
import com.farhan.today.ui.components.PrimaryButton
import com.farhan.today.ui.components.RupiahTextField
import com.farhan.today.ui.components.ProgressBar
import com.farhan.today.ui.components.ScreenHeader
import com.farhan.today.ui.components.SectionTitle
import com.farhan.today.ui.components.TodayTextField
import com.farhan.today.ui.components.vmFactory
import com.farhan.today.ui.theme.TodayColors
import com.farhan.today.ui.theme.TodayShapes
import com.farhan.today.ui.theme.TodayType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

// ================================================================== Bersama

@Composable
private fun financeVm(): FinanceViewModel {
    val c = LocalContext.current.appContainer
    return viewModel(factory = vmFactory { FinanceViewModel(c.financeRepository, c.clock) })
}

/** Ikon vektor kategori (emoji tampil beda di tiap merek HP dan tidak ikut warna tema). */
fun categoryIcon(icon: String?): Int = when (icon) {
    "food" -> R.drawable.ic_cat_food; "train" -> R.drawable.ic_cat_train; "home" -> R.drawable.ic_cat_home
    "school" -> R.drawable.ic_cat_school; "fun" -> R.drawable.ic_cat_fun; "health" -> R.drawable.ic_cat_health
    "wallet" -> R.drawable.ic_cat_wallet; "trend" -> R.drawable.ic_cat_trend; "bill" -> R.drawable.ic_cat_bill
    else -> R.drawable.ic_cat_other
}

@Composable
private fun CategoryBadge(icon: Int, background: Color) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, Modifier.size(20.dp), tint = TodayColors.Ink)
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text, Modifier.heightIn(min = 48.dp).clip(TodayShapes.Chip).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp, vertical = 14.dp),
        style = TodayType.Label, color = TodayColors.Brand,
    )
}

@Composable
private fun SectionRow(title: String, link: String? = null, onLink: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = TodayType.Section, color = TodayColors.Ink)
        if (link != null) LinkText(link, onLink)
    }
}

/** Baris transaksi: nominal rata kanan, tanda +/− selalu tampil. */
@Composable
private fun TransactionLine(row: TransactionRow, zone: ZoneId, today: LocalDate, onClick: () -> Unit) {
    val tx = row.tx
    val income = tx.kind == MoneyKind.INCOME
    val at = Instant.ofEpochMilli(tx.occurredAt).atZone(zone)
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryBadge(categoryIcon(row.categoryIcon), if (income) TodayColors.WorkoutTint else TodayColors.FinanceTrack)
        Column(Modifier.weight(1f)) {
            Text(tx.note.ifBlank { row.categoryName ?: "Transaksi" }, style = TodayType.Label.copy(fontSize = 15.sp), color = TodayColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${relativeDay(at.toLocalDate(), today)} · ${formatTime(at.toLocalTime())}" + (row.categoryName?.let { " · $it" } ?: ""),
                style = TodayType.Meta, color = TodayColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatSignedRupiah(tx.amount, income), style = TodayType.Label.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"), color = if (income) TodayColors.Income else TodayColors.Ink, maxLines = 1)
            Text(if (income) "Masuk" else "Keluar", style = TodayType.Tiny, color = if (income) TodayColors.Income else TodayColors.Muted)
        }
    }
}

@Composable
private fun CardList(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(TodayShapes.Card).background(TodayColors.Surface)
            .border(BorderStroke(1.dp, TodayColors.NavStroke), TodayShapes.Card),
        content = content,
    )
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(TodayColors.NavStroke))

// ================================================================== 08 · Keuangan

@Composable
fun FinanceRoute(openAdd: Boolean, onNavigate: (String) -> Unit, onCloseAdd: (() -> Unit)? = null) {
    val vm = financeVm()
    val c = LocalContext.current.appContainer
    var paying by remember { mutableStateOf<BillEntity?>(null) }
    val recent by vm.recent.collectAsStateWithLifecycle()
    val bills by vm.bills.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val today = LocalDate.now(c.clock)
    var sheet by rememberSaveable { mutableStateOf<String?>(if (openAdd) NEW else null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize()) {
        ScreenColumn(state = scroll, bottomPadding = Spacing.FabClearance) {
            ScreenHeader("Finance tracker", "Keuangan", "Pantau pengeluaran dan tagihan.")

            WalletPanel(vm, onManage = { onNavigate(FinanceRoutes.BUDGETS) })
            SectionRow("Transaksi terbaru", "Lihat semua") { onNavigate(FinanceRoutes.TRANSACTIONS) }
            if (recent.isEmpty()) {
                Text("Belum ada transaksi. Ketuk “+ Catat” untuk mulai.", style = TodayType.Meta, color = TodayColors.Muted)
            } else CardList {
                recent.forEachIndexed { i, r ->
                    if (i > 0) Divider()
                    TransactionLine(r, c.clock.zone, today) { sheet = r.tx.id }
                }
            }

            val active = bills.filter { it.active }
            SectionRow("Tagihan berikutnya", "Semua tagihan") { onNavigate(FinanceRoutes.BILLS) }
            if (active.isEmpty()) Text("Tidak ada tagihan aktif.", style = TodayType.Meta, color = TodayColors.Muted)
            active.take(3).forEach { b ->
                BillCard(b, today, onEdit = { onNavigate(FinanceRoutes.bill(b.id)) }) {
                    paying = b
                }
            }

            summary?.let { s ->
                SectionRow("Bulan ini", "Ringkasan") { onNavigate(FinanceRoutes.SUMMARY) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat("Masuk", "+" + formatRupiahCompact(s.income), TodayColors.Income, Modifier.weight(1f))
                    MiniStat("Keluar", "−" + formatRupiahCompact(s.expense), TodayColors.Ink, Modifier.weight(1f))
                }
                MiniStat("Selisih", (if (s.net >= 0) "+" else "") + formatRupiahCompact(s.net), if (s.net >= 0) TodayColors.Income else TodayColors.Danger, Modifier.fillMaxWidth())
            }
            LinkText("Kelola kategori") { onNavigate(FinanceRoutes.CATEGORIES) }
        }
        TodayFab("Catat", scroll.fabExpanded, Modifier.align(Alignment.BottomEnd)) { sheet = NEW }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    paying?.let { bill -> PayWalletDialog(vm, bill) { paying = null } }
    sheet?.let { target ->
        TransactionSheet(
            vm = vm, categories = categories, transactionId = target.takeIf { it != NEW },
            onDismiss = {
                sheet = null
                if (openAdd) onCloseAdd?.invoke()
            },
            onDeleted = { id ->
                sheet = null
                scope.launch {
                    if (snackbar.showSnackbar("Transaksi dihapus", "Batalkan", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                        vm.restoreTransaction(id) { error ->
                            if (error != null) scope.launch { snackbar.showSnackbar(error) }
                        }
                    }
                }
            },
        )
    }
}

private const val NEW = "new"

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(TodayShapes.Card).background(TodayColors.Surface).border(BorderStroke(1.dp, TodayColors.NavStroke), TodayShapes.Card).padding(12.dp)) {
        Text(label.uppercase(), style = TodayType.Tiny, color = TodayColors.Muted)
        Text(value, style = TodayType.Label.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BillCard(b: BillEntity, today: LocalDate, onEdit: () -> Unit, onPay: () -> Unit) {
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, b.nextDueDate)
    val whenText = when {
        !b.active -> "Lunas"
        days < 0 -> "Terlambat ${-days} hari"
        days == 0L -> "Jatuh tempo hari ini"
        days == 1L -> "Besok"
        else -> "${relativeDay(b.nextDueDate, today)} · $days hari lagi"
    }
    val urgent = b.active && days <= 1
    ListCard(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryBadge(R.drawable.ic_cat_bill, TodayColors.Career)
            Column(Modifier.weight(1f)) {
                Text(b.name, style = TodayType.Label.copy(fontSize = 15.sp), color = TodayColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (urgent) Icon(painterResource(R.drawable.ic_warning), null, Modifier.size(14.dp), tint = TodayColors.Danger)
                    Text(
                        whenText + if (b.repeat != BillRepeat.NONE) " · ${if (b.repeat == BillRepeat.MONTHLY) "bulanan" else "tahunan"}" else "",
                        style = TodayType.Meta, color = if (urgent) TodayColors.Danger else TodayColors.Muted,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatRupiah(b.amount), style = TodayType.Label.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"), color = TodayColors.Ink)
                if (b.active) Pill("Bayar", background = TodayColors.WorkoutTint, onClick = onPay)
            }
        }
    }
}

// ================================================================== Sheet tambah/ubah transaksi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSheet(
    vm: FinanceViewModel,
    categories: List<CategoryEntity>,
    transactionId: String?,
    onDismiss: () -> Unit,
    onDeleted: (String) -> Unit,
) {
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    var walletId by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var newCategory by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(transactionId == null) }
    var digits by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(MoneyKind.EXPENSE) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var time by remember { mutableStateOf<LocalTime?>(null) }

    LaunchedEffect(transactionId) {
        if (transactionId != null) vm.loadTransaction(transactionId)?.let { t ->
            val at = Instant.ofEpochMilli(t.occurredAt).atZone(ZoneId.systemDefault())
            digits = t.amount.toString(); kind = t.kind; categoryId = t.categoryId; note = t.note
            date = at.toLocalDate(); time = at.toLocalTime()
            walletId = t.walletId
        }
        loaded = true
    }
    val ofKind = categories.filter { it.kind == kind }
    LaunchedEffect(kind, ofKind.size) { if (categoryId == null || ofKind.none { it.id == categoryId }) categoryId = ofKind.firstOrNull()?.id }

    val amount = digits.toLongOrNull() ?: 0
    fun submit() {
        if (amount > 0 && !saving) {
            val at = date?.let { LocalDateTime.of(it, time ?: LocalTime.now().withSecond(0).withNano(0)) }
            saving = true
            vm.saveTransaction(TransactionInput(kind, amount, categoryId, note, at, walletId), transactionId) { message ->
                saving = false
                error = message
                if (message == null) onDismiss()
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TodayColors.Background) {
        if (!loaded) {
            LoadingSkeleton(Modifier.padding(horizontal = Spacing.ScreenH).navigationBarsPadding(), rows = 2, showHeader = false)
            return@ModalBottomSheet
        }
      Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.ScreenH),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (transactionId == null) "Catat transaksi" else "Ubah transaksi", style = TodayType.Section, color = TodayColors.Ink)
            ChoiceChips(listOf(MoneyKind.EXPENSE to "− Keluar", MoneyKind.INCOME to "+ Masuk"), kind, { kind = it })
            val shown = (if (kind == MoneyKind.INCOME) "+" else "−") + formatRupiah(amount)
            // Nominal panjang mengecil, bukan terpotong.
            val amountSize = when {
                shown.length <= 12 -> 40.sp
                shown.length <= 16 -> 32.sp
                else -> 26.sp
            }
            Text(
                shown,
                Modifier.fillMaxWidth().semantics { contentDescription = "Nominal ${formatRupiah(amount)}" },
                style = TodayType.Display.copy(fontSize = amountSize, lineHeight = 46.sp, fontFeatureSettings = "tnum"), textAlign = TextAlign.End,
                color = if (kind == MoneyKind.INCOME) TodayColors.Income else TodayColors.Ink, maxLines = 1,
            )
            Keypad(
                onDigit = { d -> if (digits.length < 12) digits = (digits + d).trimStart('0') },
                onBackspace = { digits = digits.dropLast(1) },
            )
            if (kind == MoneyKind.EXPENSE) {
                FieldLabel("Bayar dari dompet")
                if (wallets.isEmpty()) Text("Buat dan isi dompet di Keuangan → Dompet mingguan.", style = TodayType.Meta, color = TodayColors.Muted)
                ChoiceChips(wallets.map { it.id as String? to (it.name + " · " + formatRupiah(it.balance)) }, walletId, { walletId = it })
            }
            FieldLabel("Kategori")
            LinkText("+ Kategori baru") { newCategory = true }
            ChoiceChips(ofKind.map { it.id as String? to it.name }, categoryId, { categoryId = it }, icon = { id -> categoryIcon(ofKind.firstOrNull { it.id == id }?.iconKey) })
            TodayTextField(note, { note = it }, "Catatan (opsional)", placeholder = if (kind == MoneyKind.EXPENSE) "Makan siang" else "Uang saku", imeAction = ImeAction.Done)
            if (transactionId != null || date != null) {
                FieldLabel("Tanggal")
                DateField(date, { date = it })
            } else {
                LinkText("Ubah tanggal (default: sekarang)") { date = LocalDate.now() }
            }
            error?.let { Text(it, style = TodayType.Meta, color = TodayColors.Danger) }
            if (transactionId != null) {
                TextButton(onClick = {
                    vm.deleteTransaction(transactionId) { message ->
                        error = message
                        if (message == null) onDeleted(transactionId)
                    }
                }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Hapus transaksi", color = TodayColors.Danger, style = TodayType.Label)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.ScreenH, vertical = 12.dp)) {
            PrimaryButton(
                if (amount > 0) "Simpan ${formatRupiah(amount)}" else "Masukkan nominal",
                enabled = amount > 0, loading = saving, onClick = ::submit,
            )
        }
      }
    }
    if (newCategory) WalletInputDialog("Kategori baru", "Nama kategori", onDismiss = { newCategory = false }) { name, done ->
        vm.saveCategory(name, kind)
        done(null)
    }
}

@Composable
private fun Keypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "000", "0", BACKSPACE)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(TodayShapes.Button)
                            .background(if (k == BACKSPACE) TodayColors.Chip else TodayColors.Surface)
                            .border(BorderStroke(1.dp, TodayColors.NavStroke), TodayShapes.Button)
                            .clickable(role = Role.Button) { if (k == BACKSPACE) onBackspace() else onDigit(k) }
                            .semantics { contentDescription = if (k == BACKSPACE) "Hapus angka" else k },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == BACKSPACE) Icon(painterResource(R.drawable.ic_backspace), null, tint = TodayColors.Ink)
                        else Text(k, style = TodayType.CardTitle.copy(fontFeatureSettings = "tnum"), color = TodayColors.Ink)
                    }
                }
            }
        }
    }
}

private const val BACKSPACE = "back"

// ================================================================== Semua transaksi

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TransactionsRoute(onBack: () -> Unit) {
    val vm = financeVm()
    val c = LocalContext.current.appContainer
    val state by vm.transactions.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val today = LocalDate.now(c.clock)
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun deleted(id: String) = scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        if (snackbar.showSnackbar("Transaksi dihapus", "Batalkan", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                        vm.restoreTransaction(id) { error ->
                            if (error != null) scope.launch { snackbar.showSnackbar(error) }
                        }
                    }
    }

    DetailScaffold(
        title = "Semua transaksi",
        onBack = onBack,
        scrollable = false,
        actions = { TopBarAction(R.drawable.ic_add, "Catat transaksi") { sheet = NEW } },
    ) {
    Box(Modifier.fillMaxSize()) {
        // LazyColumn + key per transaksi: daftar bisa panjang (ratusan baris per bulan).
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.ScreenH, end = Spacing.ScreenH, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MonthSwitcher(state.month, { vm.shiftMonth(-1) }, { vm.shiftMonth(1) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniStat("Masuk", "+" + formatRupiahCompact(state.income), TodayColors.Income, Modifier.weight(1f))
                        MiniStat("Keluar", "−" + formatRupiahCompact(state.expense), TodayColors.Ink, Modifier.weight(1f))
                    }
                    SearchField(state.query, vm::setQuery, "Cari catatan atau kategori")
                    ChoiceChips(KindFilter.entries.map { it to it.label }, state.kind, vm::setKind)
                    ChoiceChips(listOf<Pair<String?, String>>(null to "Semua kategori") + categories.map { it.id to it.name }, state.categoryId, vm::setCategory)
                    if (state.loading) LoadingSkeleton(rows = 3, showHeader = false)
                    else if (state.groups.isEmpty()) {
                        EmptyState("Tidak ada transaksi", "Belum ada catatan yang cocok di bulan ini.", mascot = R.drawable.mascot_coin)
                    } else {
                        Text("Geser transaksi ke kiri untuk menghapus.", style = TodayType.Meta, color = TodayColors.Muted)
                    }
                }
            }
            state.groups.forEach { g ->
                stickyHeader(key = "day-${g.date}") {
                    Row(
                        Modifier.fillMaxWidth().background(TodayColors.Background).padding(top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(relativeDay(g.date, today).let { if (it.length <= 8) "$it · ${formatLongDate(g.date)}" else formatLongDate(g.date) }, Modifier.weight(1f), style = TodayType.Label, color = TodayColors.Ink)
                        Text((if (g.net >= 0) "+" else "") + formatRupiah(g.net), style = TodayType.Label.copy(fontFeatureSettings = "tnum"), color = if (g.net >= 0) TodayColors.Income else TodayColors.Muted)
                    }
                }
                items(g.rows, key = { it.tx.id }) { r ->
                    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = {
                        if (it == SwipeToDismissBoxValue.EndToStart) {
                            vm.deleteTransaction(r.tx.id) { error ->
                                if (error == null) deleted(r.tx.id)
                                else scope.launch { snackbar.showSnackbar(error) }
                            }
                        }
                        false
                    })
                    SwipeToDismissBox(
                        state = swipe, enableDismissFromStartToEnd = false,
                        modifier = Modifier.animateItem().clip(TodayShapes.Card),
                        backgroundContent = {
                            Row(
                                Modifier.fillMaxSize().background(Color(0xFFFFE0DD)).padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(painterResource(R.drawable.ic_delete), null, Modifier.size(18.dp), tint = TodayColors.Danger)
                                Text("Hapus", style = TodayType.Label, color = TodayColors.Danger)
                            }
                        },
                    ) {
                        Box(Modifier.background(TodayColors.Surface).border(BorderStroke(1.dp, TodayColors.NavStroke), TodayShapes.Card)) {
                            TransactionLine(r, c.clock.zone, today) { sheet = r.tx.id }
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
    }
    sheet?.let { target ->
        TransactionSheet(vm, categories, target.takeIf { it != NEW }, onDismiss = { sheet = null }, onDeleted = { sheet = null; deleted(it) })
    }
}

@Composable
private fun MonthSwitcher(month: java.time.YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInHorizontally(tween(240)) { w -> (if (forward) 1 else -1) * w / 5 } + fadeIn(tween(200, delayMillis = 40))) togetherWith
                    (slideOutHorizontally(tween(240)) { w -> (if (forward) -1 else 1) * w / 5 } + fadeOut(tween(90)))
            },
            modifier = Modifier.weight(1f),
            label = "month",
        ) { m ->
            Text(formatMonthYear(m.atDay(1)), style = TodayType.Section, color = TodayColors.Headline)
        }
        Pill("", icon = R.drawable.ic_chevron_left, modifier = Modifier.semantics { contentDescription = "Bulan sebelumnya" }, onClick = onPrev)
        Pill("", icon = R.drawable.ic_chevron_right, modifier = Modifier.semantics { contentDescription = "Bulan berikutnya" }, onClick = onNext)
    }
}

// ================================================================== Anggaran

@Composable
fun BudgetsRoute(onBack: () -> Unit) {
    val vm = financeVm()
    DetailScaffold(title = "Dompet", onBack = onBack) {
        WalletPanel(vm)
    }
}

// ================================================================== Tagihan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsRoute(onBack: () -> Unit, billId: String? = null) {
    val vm = financeVm()
    val c = LocalContext.current.appContainer
    val bills by vm.bills.collectAsStateWithLifecycle()
    val payments by vm.payments.collectAsStateWithLifecycle()
    var deletingPayment by rememberSaveable { mutableStateOf<String?>(null) }
    var paying by remember { mutableStateOf<BillEntity?>(null) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val today = LocalDate.now(c.clock)
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    DetailScaffold(
        title = "Tagihan",
        onBack = onBack,
        actions = { TopBarAction(R.drawable.ic_add, "Tagihan baru") { editing = NEW } },
    ) {
        Text("Ketuk “Bayar” untuk mencatat pengeluaran dan memajukan jatuh tempo.", style = TodayType.Body, color = TodayColors.Muted)
        val visibleBills = if (billId == null) bills else bills.filter { it.id == billId }
        if (visibleBills.isEmpty()) {
            if (billId == null) EmptyState("Belum ada tagihan", "Tambahkan tagihan rutin seperti gym, kos, atau langganan.", mascot = R.drawable.mascot_coin)
            else Text("Tagihan tidak tersedia.", style = TodayType.Body, color = TodayColors.Muted)
        }
        visibleBills.forEach { b ->
            BillCard(b, today, onEdit = { editing = b.id }) { paying = b }
            val history = payments.filter { it.billId == b.id }
            if (history.isNotEmpty()) {
                Pill(
                    if (expanded == b.id) "Sembunyikan riwayat" else "Riwayat pembayaran (${history.size})",
                    background = TodayColors.Background, contentColor = TodayColors.Brand,
                    trailingIcon = if (expanded == b.id) R.drawable.ic_expand_less else R.drawable.ic_expand_more,
                ) { expanded = if (expanded == b.id) null else b.id }
                AnimatedVisibility(expanded == b.id) {
                    Column {
                        history.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Jatuh tempo ${formatLongDate(p.dueDate)} · dibayar ${relativeDay(Instant.ofEpochMilli(p.paidAt).atZone(c.clock.zone).toLocalDate(), today).lowercase()} · ${formatRupiah(p.amount)}",
                                    style = TodayType.Meta, color = TodayColors.Muted, modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { deletingPayment = p.id }) {
                                    Icon(painterResource(R.drawable.ic_delete), "Hapus riwayat pembayaran", tint = TodayColors.Muted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    paying?.let { bill -> PayWalletDialog(vm, bill) { paying = null } }
    deletingPayment?.let { id ->
        ConfirmDialog("Hapus riwayat pembayaran?", "Pengeluaran yang terkait tetap tercatat di Transaksi.", "Hapus",
            onConfirm = { vm.deletePaymentHistory(id); deletingPayment = null },
            onDismiss = { deletingPayment = null }, destructive = true)
    }
    val defaultDays = c.prefs.settings.collectAsStateWithLifecycle(initialValue = null).value?.billReminderDays ?: 1
    editing?.let { target -> BillSheet(vm, categories, bills.firstOrNull { it.id == target }, defaultDays, onDismiss = { editing = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillSheet(vm: FinanceViewModel, categories: List<CategoryEntity>, bill: BillEntity?, defaultDays: Int, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(bill?.name.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(bill?.amount?.toString().orEmpty()) }
    var categoryId by rememberSaveable { mutableStateOf(bill?.categoryId) }
    var repeat by rememberSaveable { mutableStateOf(bill?.repeat ?: BillRepeat.MONTHLY) }
    var due by remember { mutableStateOf(bill?.nextDueDate ?: LocalDate.now().plusDays(7)) }
    var reminder by rememberSaveable { mutableStateOf(bill?.reminderDaysBefore ?: defaultDays) }
    var error by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = TodayColors.Background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.ScreenH).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (bill == null) "Tagihan baru" else "Ubah tagihan", style = TodayType.Section, color = TodayColors.Ink)
            TodayTextField(name, { name = it }, "Nama *", placeholder = "Membership gym", error = if (error && name.isBlank()) "Wajib diisi" else null, capitalization = KeyboardCapitalization.Words)
            RupiahTextField(amount, { amount = it }, "Nominal *",
                error = if (error && (amount.toLongOrNull() ?: 0) <= 0) "Isi nominal" else null)
            FieldLabel("Kategori")
            ChoiceChips(listOf<Pair<String?, String>>(null to "Tanpa kategori") + categories.filter { it.kind == MoneyKind.EXPENSE }.map { it.id to it.name }, categoryId, { categoryId = it })
            FieldLabel("Berulang")
            ChoiceChips(listOf(BillRepeat.MONTHLY to "Bulanan", BillRepeat.YEARLY to "Tahunan", BillRepeat.NONE to "Sekali"), repeat, { repeat = it })
            FieldLabel("Jatuh tempo berikutnya")
            DateField(due, { it?.let { d -> due = d } })
            FieldLabel("Ingatkan")
            ChoiceChips(listOf(0 to "Hari H", 1 to "H-1", 3 to "H-3", 7 to "H-7"), reminder, { reminder = it })
            PrimaryButton("Simpan tagihan") {
                val a = amount.toLongOrNull() ?: 0
                val input = BillInput(name, a, categoryId, repeat, due, reminder)
                if (!input.isValid) error = true else { vm.saveBill(input, bill?.id); onDismiss() }
            }
            if (bill != null) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Hapus tagihan", color = TodayColors.Danger, style = TodayType.Label)
            }
        }
    }
    if (confirmDelete && bill != null) {
        ConfirmDialog("Hapus tagihan?", "Riwayat pembayaran tetap tersimpan sebagai transaksi.", "Hapus",
            onConfirm = { vm.deleteBill(bill.id); confirmDelete = false; onDismiss() }, onDismiss = { confirmDelete = false }, destructive = true)
    }
}

// ================================================================== Ringkasan bulanan

@Composable
fun MonthSummaryRoute(onBack: () -> Unit) {
    val vm = financeVm()
    val s by vm.summary.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()

    DetailScaffold(title = "Ringkasan bulanan", onBack = onBack) {
        MonthSwitcher(month, { vm.shiftMonth(-1) }, { vm.shiftMonth(1) })
        val sum = s
        if (sum == null) {
            LoadingSkeleton(rows = 3, showHeader = false)
            return@DetailScaffold
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Masuk", "+" + formatRupiahCompact(sum.income), TodayColors.Income, Modifier.weight(1f))
            MiniStat("Keluar", "−" + formatRupiahCompact(sum.expense), TodayColors.Ink, Modifier.weight(1f))
        }
        HeroCard(if (sum.net >= 0) TodayColors.WorkoutTint else Color(0xFFFFE0DD)) {
            Text("SELISIH BULAN INI", style = TodayType.Eyebrow, color = TodayColors.Ink)
            Text((if (sum.net >= 0) "+" else "") + formatRupiah(sum.net), style = TodayType.Hero.copy(fontFeatureSettings = "tnum"), color = if (sum.net >= 0) TodayColors.Income else TodayColors.Danger, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (sum.net >= 0) "Pemasukan lebih besar dari pengeluaran." else "Pengeluaran melebihi pemasukan.", style = TodayType.Meta, color = TodayColors.Ink)
        }
        SectionTitle("Pengeluaran per kategori")
        if (sum.byCategory.isEmpty()) Text("Belum ada pengeluaran bulan ini.", style = TodayType.Meta, color = TodayColors.Muted)
        val max = sum.byCategory.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1
        sum.byCategory.forEach { cat ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(cat.name ?: "Tanpa kategori", Modifier.weight(1f), style = TodayType.Label, color = TodayColors.Ink)
                    Text("${formatRupiah(cat.total)} · ${(cat.total * 100 / sum.expense.coerceAtLeast(1))}%", style = TodayType.Label, color = TodayColors.Ink)
                }
                ProgressBar(cat.total.toFloat() / max, track = TodayColors.Chip, fill = TodayColors.DotFinance, height = 10.dp)
            }
        }
    }
}

// ================================================================== Kategori

@Composable
fun CategoriesRoute(onBack: () -> Unit) {
    val vm = financeVm()
    val all by vm.allCategories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var creating by remember { mutableStateOf<MoneyKind?>(null) }

    DetailScaffold(title = "Kategori", onBack = onBack) {
        listOf(MoneyKind.EXPENSE to "Pengeluaran", MoneyKind.INCOME to "Pemasukan").forEach { (kind, title) ->
            SectionRow(title, "+ Tambah") { creating = kind }
            val list = all.filter { it.kind == kind }
            CardList {
                list.forEachIndexed { i, cat ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp).heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).clip(TodayShapes.Chip).clickable(role = Role.Button, onClickLabel = "Ganti nama") { editing = cat }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(painterResource(categoryIcon(cat.iconKey)), null, Modifier.size(20.dp), tint = if (cat.archived) TodayColors.Muted else TodayColors.Ink)
                            Text(
                                cat.name + if (cat.archived) " (diarsipkan)" else "",
                                style = TodayType.Label, color = if (cat.archived) TodayColors.Muted else TodayColors.Ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!cat.archived) {
                            IconButton(onClick = { vm.moveCategory(cat.id, -1) }) { Icon(painterResource(R.drawable.ic_expand_less), "Naikkan ${cat.name}", tint = TodayColors.Muted) }
                            IconButton(onClick = { vm.moveCategory(cat.id, 1) }) { Icon(painterResource(R.drawable.ic_expand_more), "Turunkan ${cat.name}", tint = TodayColors.Muted) }
                        }
                        TextButton(onClick = { vm.archiveCategory(cat.id, !cat.archived) }) { Text(if (cat.archived) "Pulihkan" else "Arsip", color = TodayColors.Muted) }
                    }
                }
            }
        }
        Text("Kategori yang diarsipkan tidak muncul saat mencatat, tapi transaksi lamanya tetap ada.", style = TodayType.Meta, color = TodayColors.Muted)
    }

    val target = editing
    val kind = creating
    if (target != null || kind != null) {
        var name by remember(target, kind) { mutableStateOf(target?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null; creating = null },
            title = { Text(if (target == null) "Kategori baru" else "Ganti nama", style = TodayType.CardTitle) },
            text = { TodayTextField(name, { name = it }, "Nama kategori", capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        vm.saveCategory(name, target?.kind ?: kind!!, target?.id); editing = null; creating = null
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { editing = null; creating = null }) { Text("Batal") } },
        )
    }
}

object FinanceRoutes {
    const val ADD = "finance/add"
    const val TRANSACTIONS = "finance/transactions"
    const val BUDGETS = "finance/budgets"
    const val BILLS = "finance/bills"
    const val BILL = "finance/bills/{billId}"
    fun bill(id: String) = "finance/bills/$id"
    const val SUMMARY = "finance/summary"
    const val CATEGORIES = "finance/categories"
}

@Suppress("unused") private val keepFill = Modifier.fillMaxHeight().width(1.dp)

@Composable
private fun WalletPanel(vm: FinanceViewModel, onManage: (() -> Unit)? = null) {
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val unallocated by vm.unallocated.collectAsStateWithLifecycle()
    var funding by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var allocating by remember { mutableStateOf<com.farhan.today.data.db.WalletBalance?>(null) }

    Text("Saldo sekarang", style = TodayType.Label, color = TodayColors.Muted)
    Text(formatRupiah(unallocated + wallets.sumOf { it.balance }), style = TodayType.Display.copy(fontFeatureSettings = "tnum"), color = TodayColors.Headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text("Belum dialokasikan " + formatRupiah(unallocated), style = TodayType.Body, color = TodayColors.Muted)
    SectionRow("Dompet mingguan", if (onManage == null) "+ Dompet" else "Kelola") {
        if (onManage == null) creating = true else onManage()
    }
    if (onManage == null) LinkText("+ Tambah saldo") { funding = true }
    if (wallets.isEmpty()) Text("Tambahkan saldo awal, lalu buat dan isi dompet.", style = TodayType.Meta, color = TodayColors.Muted)
    if (funding) WalletInputDialog("Tambah saldo", "Nominal", rupiah = true, onDismiss = { funding = false }) { amount, done ->
        vm.saveTransaction(TransactionInput(MoneyKind.INCOME, amount.toLongOrNull() ?: 0, null, "Tambah saldo"), null, done)
    }
    wallets.forEach { wallet ->
        ListCard(onClick = onManage ?: { allocating = wallet }) {
            Text(wallet.name, style = TodayType.CardTitle, color = TodayColors.Ink)
            Text("Saldo " + formatRupiah(wallet.balance), style = TodayType.Label, color = TodayColors.Ink)
            Text("Diisi minggu ini " + formatRupiah(wallet.allocated), style = TodayType.Meta, color = TodayColors.Muted)
        }
    }
    if (creating) WalletInputDialog("Dompet baru", "Nama dompet", onDismiss = { creating = false }) { name, done ->
        vm.createWallet(name, done)
    }
    allocating?.let { wallet ->
        WalletInputDialog("Isi " + wallet.name, "Nominal", rupiah = true, onDismiss = { allocating = null }) { amount, done ->
            vm.allocateWallet(wallet.id, amount.toLongOrNull() ?: 0, done)
        }
    }
}

@Composable
private fun WalletInputDialog(
    title: String,
    label: String,
    rupiah: Boolean = false,
    onDismiss: () -> Unit,
    save: (String, (String?) -> Unit) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rupiah) {
                    RupiahTextField(value, { value = it }, label)
                } else {
                    TodayTextField(value, { value = it }, label)
                }
                error?.let { Text(it, style = TodayType.Meta, color = TodayColors.Danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank() && !saving, onClick = {
                saving = true
                save(value) { message ->
                    saving = false
                    error = message
                    if (message == null) onDismiss()
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun PayWalletDialog(vm: FinanceViewModel, bill: BillEntity, onDismiss: () -> Unit) {
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    var walletId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val eligible = wallets.filter { it.balance >= bill.amount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bayar " + bill.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatRupiah(bill.amount), style = TodayType.CardTitle.copy(fontFeatureSettings = "tnum"), color = TodayColors.Ink)
                if (eligible.isEmpty()) Text("Isi dompet dengan saldo yang cukup untuk membayar tagihan ini.", style = TodayType.Body, color = TodayColors.Muted)
                else FieldLabel("Bayar dari dompet")
                ChoiceChips(eligible.map { it.id as String? to (it.name + " · " + formatRupiah(it.balance)) }, walletId, { walletId = it })
                error?.let { Text(it, color = TodayColors.Danger) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && eligible.any { it.id == walletId }, onClick = {
                walletId?.let { id ->
                    saving = true
                    vm.payBill(bill.id, id) { message ->
                        saving = false
                        error = message
                        if (message == null) onDismiss()
                    }
                }
            }) { Text("Bayar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
