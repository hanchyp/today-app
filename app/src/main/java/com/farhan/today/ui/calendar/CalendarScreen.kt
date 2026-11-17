package com.farhan.today.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.farhan.today.data.db.CalendarEvent
import com.farhan.today.ui.components.DateField
import com.farhan.today.ui.components.TimeField
import com.farhan.today.ui.components.TodayTextField
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.farhan.today.R
import com.farhan.today.appContainer
import com.farhan.today.data.prefs.AppModule
import com.farhan.today.domain.formatLongDate
import com.farhan.today.domain.formatMonthYear
import com.farhan.today.domain.formatTime
import com.farhan.today.domain.model.AgendaItem
import com.farhan.today.domain.model.AgendaSource
import com.farhan.today.domain.shortDayName
import com.farhan.today.ui.components.AgendaCard
import com.farhan.today.ui.components.AgendaDots
import com.farhan.today.ui.components.EmptyState
import com.farhan.today.ui.components.Pill
import com.farhan.today.ui.components.ScreenHeader
import com.farhan.today.ui.components.SectionTitle
import com.farhan.today.ui.theme.TodayColors
import com.farhan.today.ui.theme.TodayShapes
import com.farhan.today.ui.theme.TodayTheme
import com.farhan.today.ui.theme.TodayType
import com.farhan.today.ui.theme.cardColor
import com.farhan.today.ui.theme.dotColor
import com.farhan.today.ui.today.route
import com.farhan.today.ui.today.module
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Composable
fun CalendarRoute(
    initialDate: LocalDate?,
    onNavigate: (String) -> Unit,
    initialEvent: String? = null,
    openAdd: Boolean = false,
    modules: Set<AppModule> = AppModule.entries.toSet(),
) {
    val container = LocalContext.current.appContainer
    val vm: CalendarViewModel = viewModel(factory = viewModelFactory {
        initializer { CalendarViewModel(container.ready, container.getAgendaForRange, container.clock, initialDate, container.prefs.weekStartsSunday) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val dao = container.db.calendarDao()
    val scope = rememberCoroutineScope()
    var draft by remember { mutableStateOf<CalendarEvent?>(null) }
    var editing by remember { mutableStateOf(false) }
    var eventMissing by remember { mutableStateOf(false) }
    fun addEvent(date: LocalDate) {
        editing = false
        draft = CalendarEvent(title = "", notes = "", date = date, startTime = null, endTime = null, weeklyUntil = null)
    }
    LaunchedEffect(initialEvent, openAdd) {
        if (initialEvent != null) {
            val event = dao.find(initialEvent)
            editing = event != null
            draft = event
            eventMissing = event == null
        } else if (openAdd) {
            addEvent(initialDate ?: LocalDate.now(container.clock))
        }
    }
    CalendarScreen(
        state = state.copy(
            selectedItems = state.selectedItems.filter { it.source.module == null || it.source.module in modules },
            sourcesByDate = state.sourcesByDate.mapValues { (_, sources) ->
                sources.filterTo(mutableSetOf()) { it.module == null || it.module in modules }
            },
        ),
        onSelect = vm::select,
        onPrevious = vm::previousMonth,
        onNext = vm::nextMonth,
        onToday = vm::goToToday,
        onOpenItem = { item ->
            if (item.source == AgendaSource.CALENDAR) {
                scope.launch {
                    dao.find(item.sourceId)?.let {
                        editing = true
                        draft = it
                    }
                }
            } else onNavigate(item.route())
        },
        onAdd = { addEvent(state.selected) },
    )
    if (eventMissing) {
        AlertDialog(
            onDismissRequest = { eventMissing = false },
            title = { Text("Agenda sudah tidak tersedia") },
            confirmButton = { TextButton(onClick = { eventMissing = false }) { Text("Tutup") } },
        )
    }
    draft?.let { event ->
        CalendarEventDialog(
            event = event,
            onDismiss = { draft = null },
            onSave = { updated -> scope.launch { dao.save(updated); draft = null } },
            onDelete = if (editing) ({ scope.launch { dao.delete(event); draft = null } }) else null,
        )
    }
}

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onSelect: (LocalDate) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onOpenItem: (AgendaItem) -> Unit,
    onAdd: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Kalender", style = TodayType.Display, color = TodayColors.Headline)

        BoxWithConstraints {
            val showGrid = maxWidth >= 336.dp && LocalDensity.current.fontScale < 1.5f
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showGrid) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(formatMonthYear(state.month.atDay(1)), style = TodayType.Section, color = TodayColors.Ink, modifier = Modifier.weight(1f))
                        RoundButton("‹", "Bulan sebelumnya", onPrevious)
                        RoundButton("›", "Bulan berikutnya", onNext)
                    }
                    MonthGrid(state, onSelect)
                } else {
                    DateField(state.selected, { date -> if (date != null) onSelect(date) }, placeholder = "Pilih tanggal")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoundButton("‹", "Hari sebelumnya") { onSelect(state.selected.minusDays(1)) }
                        RoundButton("›", "Hari berikutnya") { onSelect(state.selected.plusDays(1)) }
                    }
                }
                Pill("Hari ini", selected = state.selected == state.today, onClick = onToday)
            }
        }

        SectionTitle(formatLongDate(state.selected), trailing = "${state.selectedItems.size} agenda")
        onAdd?.let { Pill("+ Tambah agenda", onClick = it) }
        if (state.selectedItems.isEmpty() && !state.loading) {
            EmptyState(
                title = "Tidak ada agenda",
                message = if (state.selected == state.today) "Hari ini bebas. Saatnya istirahat atau menyicil tugas."
                else "Belum ada jadwal di tanggal ini.",
            )
        }
        state.selectedItems.forEach { item ->
            AgendaCard(
                label = "${item.time?.let(::formatTime) ?: "Seharian"}  ·  ${item.label}",
                title = item.title,
                subtitle = item.subtitle,
                color = item.source.cardColor(),
                modifier = Modifier.fillMaxWidth(),
                done = item.done,
                onClick = { onOpenItem(item) },
            )
        }
    }
}

@Composable
private fun RoundButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(TodayColors.Chip)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = TodayType.CardTitle, color = TodayColors.Ink) }
}

/** Kolom Senin–Minggu. Beda visual: dipilih = isi violet, hari ini = cincin gelap, ada agenda = titik warna modul. */
@Composable
private fun MonthGrid(state: CalendarUiState, onSelect: (LocalDate) -> Unit) {
    val first = state.month.atDay(1)
    val offset = if (state.weekStartsSunday) first.dayOfWeek.value % 7 else first.dayOfWeek.value - 1
    val days = if (state.weekStartsSunday) listOf(DayOfWeek.SUNDAY) + DayOfWeek.entries.dropLast(1) else DayOfWeek.entries
    val cells = offset + state.month.lengthOfMonth()
    val weeks = (cells + 6) / 7

    Column(
        Modifier
            .fillMaxWidth()
            .clip(TodayShapes.Card)
            .background(TodayColors.Surface)
            .border(BorderStroke(1.dp, TodayColors.NavStroke), TodayShapes.Card)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row {
            days.forEach {
                Text(
                    shortDayName(it), Modifier.weight(1f), style = TodayType.Eyebrow,
                    color = if (it == DayOfWeek.SUNDAY) TodayColors.DotFinance else TodayColors.Muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        repeat(weeks) { w ->
            Row {
                repeat(7) { col ->
                    val dayNum = w * 7 + col - offset + 1
                    Box(Modifier.weight(1f).heightIn(min = 52.dp), contentAlignment = Alignment.TopCenter) {
                        if (dayNum in 1..state.month.lengthOfMonth()) {
                            DayCell(state.month.atDay(dayNum), state, onSelect)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, state: CalendarUiState, onSelect: (LocalDate) -> Unit) {
    val isSelected = date == state.selected
    val isToday = date == state.today
    val sources = state.sourcesByDate[date].orEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(TodayShapes.Chip)
            .clickable(role = Role.Button) { onSelect(date) }
            .semantics {
                selected = isSelected
                contentDescription = buildString {
                    append(formatLongDate(date))
                    if (isToday) append(", hari ini")
                    if (sources.isNotEmpty()) append(", ${sources.size} jenis agenda")
                }
            }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isSelected) TodayColors.Brand else Color.Transparent)
                .then(if (isToday && !isSelected) Modifier.border(2.dp, TodayColors.Ink, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = TodayType.Body.copy(fontWeight = if (isSelected || isToday) FontWeight.ExtraBold else FontWeight.Medium),
                color = if (isSelected) Color.White else TodayColors.Ink,
            )
        }
        Spacer(Modifier.height(3.dp))
        AgendaDots(sources.sortedBy { it.ordinal }.map { it.dotColor() })
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(AgendaSource.WORKOUT to "Latihan", AgendaSource.CAREER to "Karier", AgendaSource.FINANCE to "Tagihan", AgendaSource.TASK to "Tugas", AgendaSource.CALENDAR to "Agenda")
            .forEach { (s, label) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AgendaDots(listOf(s.dotColor()))
                    Text(label, style = TodayType.Meta, color = TodayColors.Muted)
                }
            }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFDFCF8, widthDp = 393, heightDp = 900)
@Composable
private fun CalendarPreview() = TodayTheme {
    val today = LocalDate.of(2026, 11, 9)
    CalendarScreen(
        CalendarUiState(
            month = YearMonth.of(2026, 11), selected = today.plusDays(1), today = today,
            sourcesByDate = mapOf(today to setOf(AgendaSource.WORKOUT, AgendaSource.CAREER), today.plusDays(1) to setOf(AgendaSource.CAREER, AgendaSource.TASK)),
            selectedItems = listOf(
                AgendaItem("a", AgendaSource.CAREER, "a", "Interview", "Interview GDP Labs", "GDP Labs", today.plusDays(1), LocalTime.of(10, 0), false),
            ),
            loading = false,
        ),
        {}, {}, {}, {}, {},
    )
}

@Composable
private fun CalendarEventDialog(
    event: CalendarEvent,
    onDismiss: () -> Unit,
    onSave: (CalendarEvent) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var draft by remember(event) { mutableStateOf(event) }
    var confirmDelete by remember(event) { mutableStateOf(false) }
    val start = draft.startTime
    val end = draft.endTime
    val until = draft.weeklyUntil
    val valid = draft.title.isNotBlank() && (end == null || (start != null && end > start)) &&
        (until == null || until >= draft.date)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (onDelete == null) "Tambah agenda" else "Edit agenda") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TodayTextField(draft.title, { draft = draft.copy(title = it) }, "Judul")
                TodayTextField(draft.notes, { draft = draft.copy(notes = it) }, "Catatan atau lokasi")
                DateField(draft.date, { if (it != null) draft = draft.copy(date = it) })
                TimeField(start, { draft = draft.copy(startTime = it, endTime = if (it == null) null else end) }, placeholder = "Jam mulai (opsional)", clearable = true)
                if (start != null) TimeField(end, { draft = draft.copy(endTime = it) }, placeholder = "Jam selesai (opsional)", clearable = true)
                DateField(until, { draft = draft.copy(weeklyUntil = it) }, placeholder = "Ulang tiap minggu sampai…", clearable = true)
                if (until != null && until < draft.date) Text("Tanggal akhir harus pada atau setelah tanggal mulai.")
                if (end != null && start != null && end <= start) Text("Jam selesai harus setelah jam mulai.")
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text("Hapus agenda") }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(draft.copy(title = draft.title.trim())) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus agenda?") },
            text = { Text("Semua kemunculan agenda ini akan dihapus.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Hapus") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}
