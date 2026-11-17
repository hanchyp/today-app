package com.farhan.today.ui.today

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
        initializer { TodayViewModel(container.ready, container.prefs, container.getTodayOverview, container.workoutRepository.inProgress()) }
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
    TodayScreen(state, modules, now, onNavigate)
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
fun TodayScreen(state: TodayUiState, modules: Set<AppModule>, now: LocalTime, onNavigate: (String) -> Unit) {
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

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
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
            when {
                running != null -> item {
                    FocusCard("Lanjutkan latihan", running.name, R.drawable.mascot_lift, TodayColors.Ink) {
                        onNavigate(Routes.workoutSession(running.id))
                    }
                }
                focus != null -> item {
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
                overview == null -> item { Text("Memuat agenda…", style = TodayType.Body, color = TodayColors.Muted) }
                agenda.isEmpty() -> item {
                    FocusCard("Belum ada agenda", "Tambahkan rencana untuk hari ini.", R.drawable.mascot_wave, TodayColors.BrandHero, "Tambah rencana") {
                        addOpen = true
                    }
                }
                open.isEmpty() -> item {
                    FocusCard("Agenda selesai! 🎉", "Semua agenda hari ini sudah selesai.", R.drawable.mascot_wave, TodayColors.BrandHero)
                }
                else -> item {
                    FocusCard("${past.size} agenda masih terbuka", "Jamnya sudah lewat. Buka agenda di bawah untuk melihat detailnya.", R.drawable.mascot_focus, TodayColors.BrandHero)
                }
            }
            listOf("Waktu sudah lewat" to past, "Berikutnya" to upcoming, "Seharian" to allDay).forEach { (heading, rows) ->
                if (rows.isNotEmpty()) {
                    item { AgendaHeading(heading) }
                    items(rows, key = { it.id }) { row -> AgendaRow(row) { onNavigate(row.route()) } }
                }
            }
            if (completed.isNotEmpty()) {
                item {
                    Pill(if (showDone) "Sembunyikan selesai (${completed.size})" else "Lihat selesai (${completed.size})", onClick = { showDone = !showDone })
                }
                if (showDone) items(completed, key = { it.id }) { row -> AgendaRow(row) { onNavigate(row.route()) } }
            }
            overview?.walletBalance?.takeIf { AppModule.FINANCE in modules }?.let { balance ->
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(TodayShapes.Card).background(TodayColors.FinanceTrack)
                            .clickable(role = Role.Button) { onNavigate(Routes.FINANCE) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_nav_finance), null, tint = TodayColors.Ink)
                        Column {
                            Text("Saldo seluruh dompet", style = TodayType.Body, color = TodayColors.Ink)
                            Text(formatRupiah(balance), style = TodayType.CardTitle, color = TodayColors.Ink)
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            text = { Text("Tambah", style = TodayType.Label) },
            icon = { Icon(painterResource(R.drawable.ic_add), null) },
            onClick = { addOpen = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = TodayColors.BrandHero,
            contentColor = TodayColors.OnBrandHero,
        )
    }

    if (addOpen) {
        ModalBottomSheet(onDismissRequest = { addOpen = false }, containerColor = TodayColors.Background) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
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
                            colors = ListItemDefaults.colors(containerColor = TodayColors.Background),
                            modifier = Modifier.clickable(role = Role.Button) {
                                addOpen = false
                                onNavigate(route)
                            },
                        )
                    }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Preview(showBackground = true, widthDp = 320, heightDp = 800, fontScale = 2f)
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
            TodayScreen(TodayUiState(overview = TodayOverview(date, agenda, agenda.first(), 425_000L, emptyMap())), AppModule.entries.toSet(), LocalTime.NOON) {}
        }
    }
}

@Composable
private fun AgendaHeading(title: String) {
    Text(title, style = TodayType.Section, color = TodayColors.Headline, modifier = Modifier.semantics { heading() })
}

@Composable
private fun SourceIcon(source: AgendaSource) {
    Box(Modifier.size(40.dp).clip(TodayShapes.Chip).background(source.cardColor()), contentAlignment = Alignment.Center) {
        Icon(painterResource(source.icon), null, Modifier.size(20.dp), tint = TodayColors.Ink)
    }
}

@Composable
private fun AgendaRow(item: AgendaItem, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(item.title, style = TodayType.Label, color = TodayColors.Ink) },
        supportingContent = {
            val status = if (item.done) " · Selesai" else ""
            Text("${item.time?.let(::formatTime) ?: "Seharian"} · ${item.label}$status", style = TodayType.Meta, color = TodayColors.Muted)
            if (item.subtitle.isNotBlank()) Text(item.subtitle, style = TodayType.Meta, color = TodayColors.Muted)
        },
        leadingContent = { SourceIcon(item.source) },
        modifier = Modifier.clip(TodayShapes.Chip).clickable(role = Role.Button, onClick = onClick),
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
    onClick: (() -> Unit)? = null,
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(TodayShapes.Hero).background(background).padding(20.dp),
    ) {
        val showMascot = maxWidth >= 240.dp && LocalDensity.current.fontScale < 1.3f
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = TodayType.Hero, color = TodayColors.OnBrandHero)
                Text(subtitle, style = TodayType.Body, color = TodayColors.OnBrandSoft)
                if (onClick != null) {
                    Pill(action ?: "Buka sesi", background = TodayColors.Brand, contentColor = Color.White, onClick = onClick)
                }
            }
            if (showMascot) Image(painterResource(mascot), null, Modifier.size(90.dp))
        }
    }
}
