package com.farhan.today.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.farhan.today.data.db.TaskStatus
import com.farhan.today.ui.components.LoadingSkeleton
import com.farhan.today.ui.components.TodayFab
import com.farhan.today.ui.components.rememberConfirmHaptic
import com.farhan.today.ui.components.screenInsets
import com.farhan.today.ui.tasks.TaskFormDestination
import kotlinx.coroutines.launch

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.farhan.today.R
import com.farhan.today.appContainer
import com.farhan.today.data.prefs.AppModule
import com.farhan.today.data.repo.AgendaRepository
import com.farhan.today.domain.formatLongDate
import com.farhan.today.domain.formatRupiah
import com.farhan.today.domain.formatTime
import com.farhan.today.domain.model.AgendaItem
import com.farhan.today.domain.model.AgendaSource
import com.farhan.today.domain.model.TodayOverview
import com.farhan.today.ui.Routes
import com.farhan.today.ui.components.Pill
import com.farhan.today.ui.finance.FinanceRoutes
import com.farhan.today.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.LocalDate
import java.time.LocalTime

fun AgendaItem.route(): String = when (source) {
    AgendaSource.CAREER -> Routes.careerDetail(sourceId, id.removePrefix("event:"))
    AgendaSource.CALENDAR -> Routes.calendar(date, event = sourceId)
    AgendaSource.TASK -> Routes.taskForm(sourceId)
    AgendaSource.FINANCE -> FinanceRoutes.bill(sourceId)
    AgendaSource.WORKOUT -> when {
        id.startsWith("schedule:") -> Routes.routine(sourceId)
        done -> Routes.workoutSummary(sourceId)
        else -> Routes.workoutPlan(sourceId, date)
    }
}

@Composable
fun TodayRoute(modules: Set<AppModule>, onNavigate: (String) -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: TodayViewModel = viewModel(factory = viewModelFactory {
        initializer { TodayViewModel(container.ready, container.prefs, container.getTodayOverview, container.workoutRepository.inProgress(), container.taskRepository) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(LocalTime.now(container.clock)) }
    LifecycleResumeEffect(Unit) {
        now = LocalTime.now(container.clock)
        vm.refresh()
        onPauseOrDispose { }
    }
    LaunchedEffect(state.overview?.date) {
        val date = state.overview?.date
        while (isActive) {
            now = LocalTime.now(container.clock)
            if (date != null && LocalDate.now(container.clock) != date) {
                vm.refresh()
                break
            }
            delay(60_000L - now.second * 1_000L - now.nano / 1_000_000L)
        }
    }
    // Form tugas dibuka langsung di atas Today (tanpa navigasi ke halaman kosong).
    var taskSheet by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = rememberConfirmHaptic()
    TodayScreen(
        state, modules, now,
        onNavigate = { route ->
            if (route.startsWith("tasks/form")) taskSheet = route.substringAfter("task=", "").substringBefore('&')
            else onNavigate(route)
        },
        snackbar = snackbar,
        onToggleTask = { item ->
            vm.toggleTask(item.sourceId) { status ->
                if (status == TaskStatus.DONE) {
                    haptic()
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        val r = snackbar.showSnackbar("“${item.title}” selesai", actionLabel = "Batalkan", duration = SnackbarDuration.Short)
                        if (r == SnackbarResult.ActionPerformed) vm.toggleTask(item.sourceId)
                    }
                }
            }
        },
    )
    taskSheet?.let { id ->
        TaskFormDestination(taskId = id.ifEmpty { null }, onBack = { taskSheet = null })
    }
}

internal val AgendaSource.module: AppModule?
    get() = when (this) {
        AgendaSource.CAREER -> AppModule.CAREER
        AgendaSource.CALENDAR -> null
        AgendaSource.TASK -> AppModule.TASKS
        AgendaSource.FINANCE -> AppModule.FINANCE
        AgendaSource.WORKOUT -> AppModule.WORKOUT
    }

private val AgendaSource.icon: Int
    get() = when (this) {
        AgendaSource.CAREER -> R.drawable.ic_nav_career
        AgendaSource.CALENDAR -> R.drawable.ic_calendar
        AgendaSource.TASK -> R.drawable.ic_nav_tasks
        AgendaSource.FINANCE -> R.drawable.ic_nav_finance
        AgendaSource.WORKOUT -> R.drawable.ic_nav_workout
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    state: TodayUiState,
    modules: Set<AppModule>,
    now: LocalTime,
    onNavigate: (String) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onToggleTask: (AgendaItem) -> Unit = {},
) {
    val overview = state.overview
    val agenda = overview?.items.orEmpty().filter { it.source.module == null || it.source.module in modules }
    val running = state.inProgress?.takeIf { AppModule.WORKOUT in modules }
    val minute = now.withSecond(0).withNano(0)
    val focus = if (running == null) AgendaRepository.pickNext(agenda, minute) else null
    val remaining = agenda.filter { it.id != focus?.id && it.id != "session:${running?.id}" }
    val (completed, open) = remaining.partition { it.done }
    val (allDay, timed) = open.partition { it.time == null }
    val (past, upcoming) = timed.partition { it.time?.isBefore(minute) == true }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var showPast by rememberSaveable { mutableStateOf(true) }
    val list = rememberLazyListState()

    Box(Modifier.fillMaxSize().screenInsets()) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.ScreenH, end = Spacing.ScreenH, top = Spacing.ScreenTop, bottom = Spacing.FabClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hari ini", style = TodayType.Display, color = TodayColors.Headline, modifier = Modifier.semantics { heading() })
                        val date = overview?.date ?: LocalDate.now()
                        Text(formatLongDate(date), style = TodayType.Body, color = TodayColors.Muted)
                    }
                    IconButton(onClick = { onNavigate(Routes.calendar(null)) }) {
                        Icon(painterResource(R.drawable.ic_calendar), "Kalender", tint = TodayColors.BrandText)
                    }
                    IconButton(onClick = { onNavigate(Routes.SETTINGS) }) {
                        Icon(painterResource(R.drawable.ic_nav_settings), "Pengaturan", tint = TodayColors.BrandText)
                    }
                }
            }
            item(key = "focus") {
                when {
                    running != null -> FocusCard("Lanjutkan latihan", running.name, R.drawable.mascot_lift, TodayColors.Ink, "Buka sesi", dark = true) {
                        onNavigate(Routes.workoutSession(running.id))
                    }
                    focus != null -> {
                        val action = when (focus.source) {
                            AgendaSource.CAREER -> "Buka lamaran"
                            AgendaSource.CALENDAR -> "Buka acara"
                            AgendaSource.TASK -> "Buka tugas"
                            AgendaSource.FINANCE -> "Lihat tagihan"
                            AgendaSource.WORKOUT -> "Buka latihan"
                        }
                        val mascot = when (focus.source) {
                            AgendaSource.WORKOUT -> R.drawable.mascot_lift
                            AgendaSource.FINANCE -> R.drawable.mascot_coin
                            else -> R.drawable.mascot_focus
                        }
                        FocusCard(focus.title, "${focus.time?.let(::formatTime) ?: "Seharian"} · ${focus.label}", mascot, TodayColors.BrandHero, action) {
                            onNavigate(focus.route())
                        }
                    }
                    overview == null -> LoadingSkeleton(rows = 3, showHeader = false)
                    agenda.isEmpty() -> FocusCard("Belum ada agenda", "Tambahkan rencana untuk hari ini.", R.drawable.mascot_wave, TodayColors.BrandHero, "Tambah rencana") {
                        addOpen = true
                    }
                    open.isEmpty() -> FocusCard("Agenda selesai! 🎉", "Semua agenda hari ini sudah selesai.", R.drawable.mascot_wave, TodayColors.BrandHero)
                    else -> FocusCard("${past.size} agenda terlewat", "Jamnya sudah lewat. Cek di bagian bawah.", R.drawable.mascot_focus, TodayColors.BrandHero)
                }
            }
            // Yang akan datang lebih penting di dashboard harian; yang terlewat di bawah dan bisa dilipat.
            listOf("Berikutnya" to upcoming, "Seharian" to allDay).forEach { (heading, rows) ->
                if (rows.isNotEmpty()) {
                    item(key = "h-$heading") { AgendaHeading(heading, Modifier.animateItem()) }
                    items(rows, key = { it.id }) { row -> AgendaRow(row, Modifier.animateItem(), onToggleTask) { onNavigate(row.route()) } }
                }
            }
            if (past.isNotEmpty()) {
                item(key = "h-past") {
                    CollapsibleHeading("Waktu sudah lewat", past.size, showPast, Modifier.animateItem()) { showPast = !showPast }
                }
                if (showPast) items(past, key = { it.id }) { row -> AgendaRow(row, Modifier.animateItem(), onToggleTask) { onNavigate(row.route()) } }
            }
            if (completed.isNotEmpty()) {
                item(key = "h-done") {
                    CollapsibleHeading("Selesai", completed.size, showDone, Modifier.animateItem()) { showDone = !showDone }
                }
                if (showDone) items(completed, key = { it.id }) { row -> AgendaRow(row, Modifier.animateItem(), onToggleTask) { onNavigate(row.route()) } }
            }
            overview?.walletBalance?.takeIf { AppModule.FINANCE in modules }?.let { balance ->
                item(key = "wallet") {
                    Row(
                        Modifier.animateItem().fillMaxWidth().clip(TodayShapes.Card).background(TodayColors.FinanceTrack)
                            .clickable(role = Role.Button) { onNavigate(Routes.FINANCE) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_nav_finance), null, tint = TodayColors.Ink)
                        Column(Modifier.weight(1f)) {
                            Text("Saldo seluruh dompet", style = TodayType.Body, color = TodayColors.Ink)
                            Text(formatRupiah(balance), style = TodayType.CardTitle.copy(fontFeatureSettings = "tnum"), color = TodayColors.Ink)
                        }
                        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = TodayColors.Ink)
                    }
                }
            }
        }
        val expanded by remember { derivedStateOf { !list.canScrollBackward || list.lastScrolledBackward } }
        TodayFab("Tambah", expanded, Modifier.align(Alignment.BottomEnd)) { addOpen = true }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (addOpen) {
        ModalBottomSheet(onDismissRequest = { addOpen = false }, containerColor = TodayColors.Background) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.ScreenH, vertical = 12.dp)) {
                AgendaHeading("Tambah")
                listOf(
                    Triple("Tugas", AgendaSource.TASK, Routes.taskForm()),
                    Triple("Acara", AgendaSource.CALENDAR, Routes.calendar(overview?.date, add = true)),
                    Triple("Transaksi", AgendaSource.FINANCE, FinanceRoutes.ADD),
                    Triple("Lamaran", AgendaSource.CAREER, Routes.CAREER_NEW),
                ).filter { (_, source, _) -> source.module == null || source.module in modules }
                    .forEach { (label, source, route) ->
                        ListItem(
                            headlineContent = { Text(label, style = TodayType.Label, color = TodayColors.Ink) },
                            leadingContent = { SourceIcon(source) },
                            trailingContent = { Icon(painterResource(R.drawable.ic_chevron_right), null, tint = TodayColors.Muted) },
                            colors = ListItemDefaults.colors(containerColor = TodayColors.Background),
                            modifier = Modifier.clip(TodayShapes.Chip).clickable(role = Role.Button) {
                                addOpen = false
                                onNavigate(route)
                            },
                        )
                    }
            }
        }
    }
}

@com.farhan.today.ui.components.PhonePreviews
@Composable
private fun TodayAgendaPreview() {
    val date = LocalDate.of(2026, 11, 11)
    val agenda = listOf(
        AgendaItem("event:1", AgendaSource.CAREER, "1", "Interview", "Interview Android Developer", "GDP Labs", date, LocalTime.of(14, 30), false),
        AgendaItem("task:2", AgendaSource.TASK, "2", "Tugas", "Kirim revisi Bab 3", "Skripsi", date, LocalTime.of(9, 0), false),
        AgendaItem("bill:3", AgendaSource.FINANCE, "3", "Tagihan", "Internet rumah", "Rp250.000", date, null, false),
    )
    TodayTheme {
        Surface(color = TodayColors.Background) {
            TodayScreen(TodayUiState(overview = TodayOverview(date, agenda, agenda.first(), 425_000L, emptyMap())), AppModule.entries.toSet(), LocalTime.NOON, onNavigate = {})
        }
    }
}

@Composable
private fun AgendaHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, style = TodayType.Section, color = TodayColors.Headline, modifier = modifier.padding(top = 4.dp).semantics { heading() })
}

@Composable
private fun CollapsibleHeading(title: String, count: Int, expanded: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).clip(TodayShapes.Chip)
            .clickable(role = Role.Button, onClickLabel = if (expanded) "Lipat" else "Buka", onClick = onToggle)
            .semantics { heading(); stateDescription = if (expanded) "Terbuka" else "Terlipat" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = TodayType.Section, color = TodayColors.Headline, modifier = Modifier.weight(1f))
        Text("$count", style = TodayType.Label, color = TodayColors.Muted)
        Icon(painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more), null, tint = TodayColors.Muted)
    }
}

@Composable
private fun SourceIcon(source: AgendaSource) {
    Box(Modifier.size(40.dp).clip(TodayShapes.Chip).background(source.cardColor()), contentAlignment = Alignment.Center) {
        Icon(painterResource(source.icon), null, Modifier.size(20.dp), tint = TodayColors.Ink)
    }
}

@Composable
private fun AgendaRow(item: AgendaItem, modifier: Modifier = Modifier, onToggleTask: (AgendaItem) -> Unit, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                item.title, style = TodayType.Label.copy(fontSize = TodayType.Body.fontSize),
                color = if (item.done) TodayColors.Muted else TodayColors.Ink,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        },
        // Slot supporting hanya menampilkan satu anak; dua baris dibungkus Column.
        supportingContent = {
            Column {
                val status = if (item.done) " · Selesai" else ""
                Text("${item.time?.let(::formatTime) ?: "Seharian"} · ${item.label}$status", style = TodayType.Meta, color = TodayColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.subtitle.isNotBlank()) Text(item.subtitle, style = TodayType.Meta, color = TodayColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = { SourceIcon(item.source) },
        trailingContent = if (item.source == AgendaSource.TASK) ({
            Checkbox(
                checked = item.done, onCheckedChange = { onToggleTask(item) },
                modifier = Modifier.semantics { contentDescription = "Tandai ${item.title} selesai" },
                colors = CheckboxDefaults.colors(checkedColor = TodayColors.Ink, uncheckedColor = TodayColors.Ink),
            )
        }) else null,
        modifier = modifier.clip(TodayShapes.Card).border(BorderStroke(1.dp, TodayColors.NavStroke), TodayShapes.Card).clickable(role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = TodayColors.Surface),
    )
}

@Composable
private fun FocusCard(
    title: String,
    subtitle: String,
    mascot: Int,
    background: Color,
    action: String? = null,
    dark: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val ink = if (dark) Color.White else TodayColors.OnBrandHero
    val soft = if (dark) Color.White.copy(alpha = 0.75f) else TodayColors.OnBrandSoft
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(TodayShapes.Hero).background(background)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(20.dp),
    ) {
        // Maskot hanya bila teks masih punya ruang yang cukup; judul maksimal 2 baris.
        val showMascot = maxWidth >= 280.dp && LocalDensity.current.fontScale < 1.3f
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = TodayType.Section, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = TodayType.Body, color = soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (onClick != null) {
                    Spacer(Modifier.height(2.dp))
                    Pill(
                        action ?: "Buka", background = if (dark) TodayColors.BrandHero else TodayColors.Brand,
                        contentColor = if (dark) TodayColors.OnBrandHero else Color.White,
                        trailingIcon = R.drawable.ic_chevron_right, onClick = onClick,
                    )
                }
            }
            if (showMascot) Image(painterResource(mascot), null, Modifier.size(72.dp))
        }
    }
}
