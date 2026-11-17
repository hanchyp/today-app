package com.farhan.today.ui.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.farhan.today.R
import com.farhan.today.appContainer
import com.farhan.today.data.db.ExerciseEntity
import com.farhan.today.data.db.HistoryRow
import com.farhan.today.data.db.MuscleGroup
import com.farhan.today.data.db.SessionStatus
import com.farhan.today.data.db.SetLogEntity
import com.farhan.today.data.db.animationFor
import com.farhan.today.domain.WorkoutMath
import com.farhan.today.domain.formatDuration
import com.farhan.today.domain.formatKg
import com.farhan.today.domain.formatLongDate
import com.farhan.today.domain.formatNumber
import com.farhan.today.domain.formatTime
import com.farhan.today.domain.formatVolume
import com.farhan.today.domain.relativeDay
import com.farhan.today.domain.shortDayName
import com.farhan.today.ui.components.ChoiceChips
import com.farhan.today.ui.components.ConfirmDialog
import com.farhan.today.ui.components.DetailTopBar
import com.farhan.today.ui.components.EmptyState
import com.farhan.today.ui.components.FieldLabel
import com.farhan.today.ui.components.ListCard
import com.farhan.today.ui.components.Pill
import com.farhan.today.ui.components.PrimaryButton
import com.farhan.today.ui.components.ProgressBar
import com.farhan.today.ui.components.ScreenHeader
import com.farhan.today.ui.components.SectionTitle
import com.farhan.today.ui.components.StatusBadge
import com.farhan.today.ui.components.TimeField
import com.farhan.today.ui.components.TodayTextField
import com.farhan.today.ui.components.vmFactory
import com.farhan.today.ui.settings.ExerciseImage
import com.farhan.today.ui.settings.ExerciseMedia
import com.farhan.today.ui.theme.TodayColors
import com.farhan.today.ui.theme.TodayShapes
import com.farhan.today.ui.theme.TodayType
import kotlinx.coroutines.delay
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

@Composable
private fun ScrollColumn(
    background: Color = TodayColors.Background,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

fun MuscleGroup.label(): String = when (this) {
    MuscleGroup.CHEST -> "Dada"
    MuscleGroup.BACK -> "Punggung"
    MuscleGroup.LEGS -> "Kaki"
    MuscleGroup.SHOULDERS -> "Bahu"
    MuscleGroup.ARMS -> "Lengan"
    MuscleGroup.CORE -> "Core"
    MuscleGroup.FULL_BODY -> "Seluruh tubuh"
}

private fun daysLabel(days: List<DayOfWeek>) = if (days.isEmpty()) "Tanpa jadwal" else days.joinToString(" · ") { shortDayName(it) }

// ================================================================== 06 · Latihan

@Composable
fun WorkoutRoute(
    onOpenSession: (String) -> Unit,
    onNewRoutine: () -> Unit,
    onEditRoutine: (String) -> Unit,
    onOpenHistory: (String) -> Unit,
    focusId: String? = null,
    focusDate: LocalDate? = null,
) {
    val c = LocalContext.current.appContainer
    val vm: WorkoutHomeViewModel = viewModel(factory = vmFactory { WorkoutHomeViewModel(c.workoutRepository, c.prefs, focusDate, focusId) })
    val state by vm.state.collectAsStateWithLifecycle()
    var deletingHistory by remember { mutableStateOf<HistoryRow?>(null) }
    val today = LocalDate.now(c.clock)

    ScrollColumn {
        ScreenHeader("Workout tracker", "Latihan", "Rutinitas dan riwayat latihanmu.")

        when {
            state.loading -> {}
            state.sessions.isEmpty() && focusId != null -> Text("Sesi latihan tidak tersedia.", style = TodayType.Body, color = TodayColors.Muted)
            state.routines.isEmpty() && state.sessions.isEmpty() -> EmptyState(
                "Belum ada rutinitas",
                "Buat rutinitas seperti Push Day, pilih gerakannya, lalu jadwalkan tiap minggu.",
                mascot = R.drawable.mascot_lift,
                action = { Pill("+ Buat rutinitas", selected = true, onClick = onNewRoutine) },
            )
            state.sessions.isEmpty() -> Text(
                "Tidak ada sesi terjadwal yang tersisa hari ini.",
                style = TodayType.Body,
                color = TodayColors.Muted,
            )
            else -> state.sessions.forEach { row ->
                val s = row.session
                val running = s.status == SessionStatus.IN_PROGRESS
                val setCount = state.routines.firstOrNull { it.routine.id == s.routineId }?.setCount ?: 0
                Box(Modifier.fillMaxWidth().clip(TodayShapes.Hero).background(TodayColors.Workout).padding(20.dp)) {
                    Image(
                        painterResource(R.drawable.mascot_lift), "Maskot kapibara angkat barbel",
                        Modifier.align(Alignment.TopEnd).size(width = 104.dp, height = 84.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val whenText = if (running) "Sedang berlangsung" else buildString {
                            append(if (s.plannedDate == today) "Hari ini" else formatLongDate(s.plannedDate))
                            s.plannedTime?.let { append(" · ${formatTime(it)}") }
                        }
                        StatusBadge(whenText, background = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text(s.name, style = TodayType.Hero, color = TodayColors.Ink, modifier = Modifier.padding(end = 96.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!row.focus.isNullOrBlank()) Text(row.focus, style = TodayType.Body, color = TodayColors.Ink)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusBadge("${row.exerciseCount} gerakan", background = Color.White.copy(alpha = 0.6f))
                            if (setCount > 0) StatusBadge("$setCount set", background = Color.White.copy(alpha = 0.6f))
                        }
                        Spacer(Modifier.height(10.dp))
                        if (s.status == SessionStatus.DONE) {
                            PrimaryButton("Lihat ringkasan", background = TodayColors.Ink) { onOpenHistory(s.id) }
                        } else if (s.status != SessionStatus.SKIPPED && !s.plannedDate.isAfter(today)) {
                            PrimaryButton(if (running) "Lanjutkan latihan" else "Mulai latihan", background = TodayColors.Ink) {
                                if (running) onOpenSession(s.id) else vm.start(s.id, onOpenSession)
                            }
                        }
                    }
                }
            }
        }

        // Statistik minggu ini
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("${state.week.done} / ${state.week.target}", "sesi minggu ini", Modifier.weight(1f))
            StatCard(formatDuration(state.week.minutes), "total durasi", Modifier.weight(1f))
        }
        Row(
            Modifier.fillMaxWidth().clip(TodayShapes.Card).background(TodayColors.Surface)
                .border(BorderStroke(1.dp, TodayColors.NavStroke), TodayShapes.Card).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ingatkan sebelum latihan", style = TodayType.Label, color = TodayColors.Ink)
                Text("Notifikasi 30 menit sebelum jadwal", style = TodayType.Meta, color = TodayColors.Muted)
            }
            Switch(
                checked = state.reminders, onCheckedChange = vm::setReminders,
                colors = SwitchDefaults.colors(checkedTrackColor = TodayColors.Brand),
            )
        }

        if (state.routines.isNotEmpty()) {
            SectionTitle("Rutinitas", trailing = "${state.routines.size}")
            state.routines.forEach { r ->
                ListCard(onClick = { onEditRoutine(r.routine.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.routine.name, style = TodayType.CardTitle, color = TodayColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${r.exerciseCount} gerakan · ${daysLabel(r.days)}" + (r.time?.takeIf { r.days.isNotEmpty() }?.let { " · ${formatTime(it)}" } ?: ""),
                                style = TodayType.Meta, color = TodayColors.Muted,
                            )
                        }
                        Pill("Mulai", background = TodayColors.WorkoutTint, onClick = { vm.startRoutineNow(r.routine.id, onOpenSession) })
                    }
                }
            }
        }
        Pill("+ Rutinitas baru", Modifier.fillMaxWidth(), background = TodayColors.BrandTint, contentColor = TodayColors.BrandText, onClick = onNewRoutine)

        SectionTitle("Riwayat")
        if (state.history.isEmpty() && !state.loading) {
            EmptyState("Riwayat masih kosong", "Selesaikan sesi pertamamu, nanti progresnya muncul di sini.", mascot = R.drawable.mascot_focus)
        } else {
            Column(
                Modifier.fillMaxWidth().clip(TodayShapes.Card).background(TodayColors.Surface)
                    .border(BorderStroke(1.dp, TodayColors.NavStroke), TodayShapes.Card),
            ) {
                state.history.forEachIndexed { i, h ->
                    if (i > 0) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(TodayColors.NavStroke))
                    HistoryItem(h, today) { onOpenHistory(h.session.id) }
                    TextButton(onClick = { deletingHistory = h }, modifier = Modifier.align(Alignment.End)) {
                        Text("Hapus sesi", color = TodayColors.Danger)
                    }
                }
            }
        }
    }
    deletingHistory?.let { entry ->
        ConfirmDialog("Hapus sesi latihan?", "Set latihan ini juga dihapus dari grafik progres dan rekor.", "Hapus",
            onConfirm = { vm.deleteHistory(entry.session.id); deletingHistory = null },
            onDismiss = { deletingHistory = null }, destructive = true)
    }
}

@Composable
private fun StatCard(big: String, small: String, modifier: Modifier) {
    Column(
        modifier.clip(TodayShapes.Card).background(TodayColors.Surface)
            .border(BorderStroke(1.dp, TodayColors.NavStroke), TodayShapes.Card).padding(16.dp),
    ) {
        Text(big, style = TodayType.CardTitle, color = TodayColors.Ink)
        Text(small, style = TodayType.Meta, color = TodayColors.Muted)
    }
}

@Composable
private fun HistoryItem(h: HistoryRow, today: LocalDate, onClick: () -> Unit) {
    val minutes = WorkoutMath.durationMinutes(h.session)
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(h.session.name, style = TodayType.Label.copy(fontSize = 15.sp), color = TodayColors.Ink)
            Text(
                "${relativeDay(h.session.plannedDate, today)} · ${h.exerciseCount} gerakan · ${h.setsDone} set" +
                    if (h.volumeKg > 0) " · ${formatVolume(h.volumeKg)}" else "",
                style = TodayType.Meta, color = TodayColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(formatDuration(minutes), style = TodayType.Label, color = TodayColors.Ink)
    }
}

// ================================================================== Form rutinitas

@Composable
fun RoutineFormRoute(id: String?, onBack: () -> Unit) {
    val c = LocalContext.current.appContainer
    val vm: RoutineFormViewModel = viewModel(factory = vmFactory {
        RoutineFormViewModel(c.workoutRepository, id, c.exerciseCatalog::animationFor)
    })
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var guide by remember { mutableStateOf<ExerciseDraft?>(null) }
    if (!vm.loaded) return

    ScrollColumn(background = TodayColors.Surface, modifier = Modifier.imePadding()) {
        DetailTopBar(onBack) {
            if (vm.isEdit) Pill("Hapus", contentColor = TodayColors.Danger, onClick = { deleteDialog = true })
        }
        Text(if (vm.isEdit) "UBAH RUTINITAS" else "RUTINITAS BARU", style = TodayType.Eyebrow, color = TodayColors.Brand)
        Text(vm.name.ifBlank { "Rutinitas" }, style = TodayType.Display, color = TodayColors.Headline, maxLines = 2, overflow = TextOverflow.Ellipsis)

        TodayTextField(vm.name, { vm.name = it }, "Nama *", placeholder = "Push Day", error = if (vm.showErrors && vm.name.isBlank()) "Wajib diisi" else null, capitalization = KeyboardCapitalization.Words)
        TodayTextField(vm.focus, { vm.focus = it }, "Fokus", placeholder = "Dada, bahu, dan trisep")

        FieldLabel("Hari latihan")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { d -> Pill(shortDayName(d), selected = d in vm.days, onClick = { vm.toggleDay(d) }) }
        }
        if (vm.days.isNotEmpty()) {
            FieldLabel("Jam")
            TimeField(vm.time, { it?.let { t -> vm.time = t } })
        }

        SectionTitle("Gerakan", trailing = "${vm.items.size}")
        if (vm.showErrors && vm.items.isEmpty()) Text("Tambahkan minimal satu gerakan.", style = TodayType.Meta, color = TodayColors.Danger)
        vm.items.forEachIndexed { index, exercise ->
            key(exercise) {
                RoutineExerciseEditor(
                    exercise = exercise,
                    showErrors = vm.showErrors,
                    onGuide = { guide = exercise },
                    onMoveUp = if (index > 0) ({ vm.move(index, -1) }) else null,
                    onMoveDown = if (index < vm.items.lastIndex) ({ vm.move(index, 1) }) else null,
                    onRemove = { vm.remove(index) },
                )
            }
        }
        Pill("+ Tambah gerakan", Modifier.fillMaxWidth(), background = TodayColors.WorkoutTint, onClick = { picker = true })

        Spacer(Modifier.height(8.dp))
        PrimaryButton(if (vm.isEdit) "Simpan perubahan" else "Simpan rutinitas") { vm.save(onBack) }
        Text(
            "Mengubah rutinitas hanya memengaruhi sesi yang akan datang. Riwayat latihan tetap seperti saat dicatat.",
            style = TodayType.Meta, color = TodayColors.Muted,
        )
    }

    guide?.let { ExerciseGuideDialog(it.name, it.animation) { guide = null } }
    if (picker) {
        ExercisePicker(
            catalog, c.workoutRepository::recentExercises, c.exerciseCatalog::animationFor,
            onDismiss = { picker = false },
            onPick = { vm.add(it); picker = false },
            onCreate = { name, group, equipment -> vm.createExercise(name, group, equipment); picker = false },
        )
    }
    if (deleteDialog) {
        ConfirmDialog(
            "Hapus rutinitas?", "Jadwal ke depan ikut dihapus. Riwayat latihan tetap tersimpan.", "Hapus",
            onConfirm = { deleteDialog = false; vm.delete(onBack) }, onDismiss = { deleteDialog = false }, destructive = true,
        )
    }
}

@Composable
private fun RoutineExerciseEditor(
    exercise: ExerciseDraft,
    showErrors: Boolean,
    onGuide: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var restDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(TodayShapes.Card).clickable(
                    role = Role.Button, onClickLabel = "Lihat animasi", onClick = onGuide,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(TodayColors.Chip), contentAlignment = Alignment.Center) {
                    val animation = exercise.animation
                    if (animation != null) {
                        ExerciseImage(animation, Modifier.fillMaxSize()) {
                            Text("▶", color = TodayColors.Brand)
                        }
                    } else {
                        Text(exercise.name.take(1), style = TodayType.Label, color = TodayColors.Muted)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, style = TodayType.CardTitle, color = TodayColors.Brand)
                    Text(exercise.group.label(), style = TodayType.Meta, color = TodayColors.Muted)
                }
                if (exercise.animation != null) Text("▶", style = TodayType.Meta, color = TodayColors.Brand)
            }
            Box {
                SmallButton("⋮", "Opsi gerakan") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Pindah ke atas") }, enabled = onMoveUp != null,
                        onClick = { menu = false; onMoveUp?.invoke() })
                    DropdownMenuItem(text = { Text("Pindah ke bawah") }, enabled = onMoveDown != null,
                        onClick = { menu = false; onMoveDown?.invoke() })
                    DropdownMenuItem(text = { Text("Hapus gerakan", color = TodayColors.Danger) },
                        onClick = { menu = false; onRemove() })
                }
            }
        }
        TextButton(onClick = { restDialog = true }) {
            val duration = if (exercise.restSeconds == 0) "Nonaktif"
                else "%d:%02d".format(exercise.restSeconds / 60, exercise.restSeconds % 60)
            Text("Timer istirahat · $duration", color = TodayColors.Brand)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SET", Modifier.width(48.dp), style = TodayType.Tiny, color = TodayColors.Muted, textAlign = TextAlign.Center)
            Text("KG", Modifier.weight(1f), style = TodayType.Tiny, color = TodayColors.Muted, textAlign = TextAlign.Center)
            Text("REP", Modifier.weight(1f), style = TodayType.Tiny, color = TodayColors.Muted, textAlign = TextAlign.Center)
            Spacer(Modifier.width(48.dp))
        }
        exercise.sets.forEachIndexed { index, set ->
            key(set) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clip(TodayShapes.Chip).background(TodayColors.Chip), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", style = TodayType.Label, color = TodayColors.Ink)
                    }
                    RoutineSetField(set.weight, { set.weight = it }, "Beban set ${index + 1}", Modifier.weight(1f),
                        KeyboardType.Decimal, showErrors && !set.validWeight, "—")
                    RoutineSetField(set.reps, { set.reps = it }, "Repetisi set ${index + 1}", Modifier.weight(1f),
                        KeyboardType.Number, showErrors && set.targetReps == null, "—")
                    SmallButton("✕", "Hapus set ${index + 1}", enabled = exercise.sets.size > 1) { exercise.sets.remove(set) }
                }
            }
        }
        if (showErrors && exercise.sets.any { it.targetReps == null || !it.validWeight }) {
            Text("Isi 1–100 repetisi dan beban yang valid, atau kosongkan beban.", style = TodayType.Meta, color = TodayColors.Danger)
        }
        Pill("+ Tambah set", Modifier.fillMaxWidth(), background = TodayColors.Chip,
            onClick = if (exercise.sets.size < 20) exercise::addSet else null)
        HorizontalDivider(color = TodayColors.NavStroke, modifier = Modifier.padding(top = 8.dp))
    }

    if (restDialog) {
        var seconds by remember { mutableStateOf(exercise.restSeconds) }
        AlertDialog(
            onDismissRequest = { restDialog = false },
            title = { Text("Timer istirahat") },
            text = {
                MiniStepper("Durasi", "$seconds dtk", Modifier.fillMaxWidth(),
                    { seconds = (seconds - 15).coerceAtLeast(0) },
                    { seconds = (seconds + 15).coerceAtMost(600) })
            },
            confirmButton = {
                TextButton(onClick = { exercise.restSeconds = seconds; restDialog = false }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { restDialog = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun RoutineSetField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    keyboardType: KeyboardType,
    isError: Boolean,
    placeholder: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.semantics { contentDescription = label },
        placeholder = { Text(placeholder, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        singleLine = true,
        isError = isError,
        shape = TodayShapes.Chip,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = TodayType.Label.copy(textAlign = TextAlign.Center),
    )
}

@Composable
private fun SmallButton(symbol: String, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = TodayType.Label, color = if (enabled) TodayColors.Ink else TodayColors.NavStroke) }
}

@Composable
private fun MiniStepper(label: String, value: String, modifier: Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier.clip(TodayShapes.Chip).background(TodayColors.Chip).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SmallButton("−", "Kurangi $label", onClick = onMinus)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(), style = TodayType.Tiny, color = TodayColors.Muted)
            Text(value, style = TodayType.Label, color = TodayColors.Ink, maxLines = 1)
        }
        SmallButton("+", "Tambah $label", onClick = onPlus)
    }
}

// ================================================================== 07 · Sesi latihan

private val SessionBackground = Color(0xFF111116)
private val SessionSurface = Color(0xFF23232C)
private val SessionAccent = TodayColors.BrandHero
private val SessionMuted = Color(0xFFBAB7C5)
private val SessionCompleted = Color(0xFF26351C)

@Composable
fun SessionRoute(sessionId: String, onBack: () -> Unit, onFinished: (String) -> Unit) {
    val c = LocalContext.current.appContainer
    val vm: SessionViewModel = viewModel(factory = vmFactory {
        SessionViewModel(c.workoutRepository, c.prefs, c.restTimer, sessionId, c.clock, c.exerciseCatalog::animationFor)
    })
    val state by vm.state.collectAsStateWithLifecycle()
    var finishDialog by remember { mutableStateOf(false) }
    var discardDialog by remember { mutableStateOf(false) }
    var addExercise by remember { mutableStateOf(false) }
    var guideOrder by rememberSaveable { mutableStateOf<Int?>(null) }

    val session = state.session
    LaunchedEffect(session?.status) {
        when (session?.status) {
            SessionStatus.PLANNED -> vm.ensureStarted()
            SessionStatus.DONE -> onFinished(sessionId)
            else -> {}
        }
    }
    if (state.loading || session == null) return

    val blocks = state.blocks
    val allSets = blocks.flatMap { it.sets }
    val completed = allSets.count { it.done }

    Column(Modifier.fillMaxSize().background(SessionBackground).statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().background(SessionSurface).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Pill("‹", background = SessionBackground, contentColor = Color.White,
                modifier = Modifier.semantics { contentDescription = "Kembali" }, onClick = onBack)
            Text("Catat latihan", Modifier.weight(1f), style = TodayType.Section, color = Color.White)
            Pill("Selesai", background = TodayColors.BrandHero, contentColor = TodayColors.OnBrandHero) {
                if (completed < allSets.size) finishDialog = true else vm.finish { onFinished(sessionId) }
            }
        }
        ProgressBar(
            if (allSets.isEmpty()) 0f else completed.toFloat() / allSets.size,
            track = SessionSurface, fill = SessionAccent, height = 4.dp,
        )
        state.restRemaining?.let { RestBar(it, state.restTotal, vm::skipRest) }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(session.name, style = TodayType.Section, color = Color.White)
                    SessionStats(session.startedAt, c.clock, WorkoutMath.volume(allSets), completed, allSets.size)
                    HorizontalDivider(color = SessionSurface)
                }
            }
            if (blocks.isEmpty()) {
                item { Text("Tambahkan gerakan untuk mulai mencatat set.", color = SessionMuted, style = TodayType.Body) }
            }
            items(blocks, key = { it.order }) { block ->
                LaunchedEffect(block.exerciseId) { vm.loadLast(block.exerciseId) }
                SetTable(
                    block = block,
                    last = vm.lastSets[block.exerciseId].orEmpty(),
                    selectedId = vm.selectedSetId,
                    onSelect = { vm.selectedSetId = if (it.id == vm.selectedSetId) null else it.id },
                    onGuide = { guideOrder = block.order },
                    onToggle = { vm.toggleDone(it, block.name) },
                    onWeight = vm::adjustWeight,
                    onReps = vm::adjustReps,
                    onAddSet = { vm.addSet(block.order) },
                    onDeleteSet = vm::deleteSet,
                )
            }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Pill("+ Tambah gerakan", Modifier.fillMaxWidth(), background = SessionAccent, contentColor = TodayColors.Ink) {
                        addExercise = true
                    }
                    TextButton(onClick = { discardDialog = true }) {
                        Text("Batalkan sesi", style = TodayType.Label, color = SessionMuted)
                    }
                }
            }
        }
    }

    blocks.firstOrNull { it.order == guideOrder }?.let { block ->
        ExerciseGuideDialog(block.name, block.animation) { guideOrder = null }
    }

    if (finishDialog) {
        ConfirmDialog(
            "Selesaikan latihan?", "Masih ada ${allSets.count { !it.done }} set yang belum ditandai. Set itu tidak dihitung ke volume.",
            "Selesai", onConfirm = { finishDialog = false; vm.finish { onFinished(sessionId) } }, onDismiss = { finishDialog = false },
        )
    }
    if (discardDialog) {
        ConfirmDialog(
            "Batalkan sesi?", "Catatan set di sesi ini dihapus dan jadwalnya kembali seperti semula.", "Batalkan sesi",
            onConfirm = { discardDialog = false; vm.discard(onBack) }, onDismiss = { discardDialog = false }, dismissLabel = "Lanjut latihan", destructive = true,
        )
    }
    if (addExercise) {
        var catalog by remember { mutableStateOf<List<ExerciseEntity>>(emptyList()) }
        LaunchedEffect(Unit) { catalog = vm.catalog() }
        ExercisePicker(
            catalog, c.workoutRepository::recentExercises, c.exerciseCatalog::animationFor,
            onDismiss = { addExercise = false },
            onPick = { vm.addExercise(it.id); addExercise = false },
            onCreate = { name, group, equipment -> vm.createExercise(name, group, equipment); addExercise = false },
        )
    }
}

@Composable
internal fun ExerciseGuideDialog(
    name: String,
    animation: String?,
    subtitle: String? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                subtitle?.let { Text(it, style = TodayType.Meta, color = TodayColors.Muted) }
                if (animation != null) ExerciseMedia(animation)
                else Text("Gerakan ini belum punya animasi di katalog.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

@Composable
private fun SessionStats(startedAt: Long?, clock: Clock, volume: Double, completed: Int, total: Int) {
    val elapsed by produceState(0L, startedAt, clock) {
        if (startedAt == null) return@produceState
        while (true) {
            value = ((clock.millis() - startedAt) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(
            "Durasi" to "%d:%02d".format(elapsed / 60, elapsed % 60),
            "Volume" to formatVolume(volume),
            "Set selesai" to "$completed / $total",
        ).forEach { (label, value) ->
            Column(Modifier.weight(1f)) {
                Text(label, style = TodayType.Meta, color = SessionMuted)
                Text(value, style = TodayType.CardTitle, color = Color.White)
            }
        }
    }
}

@Composable
private fun RestBar(left: Int, total: Int, onSkip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(SessionSurface).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("TIMER ISTIRAHAT", style = TodayType.Eyebrow, color = SessionAccent)
            Text("%d:%02d".format(left / 60, left % 60), style = TodayType.Hero.copy(fontSize = 28.sp), color = Color.White)
            Spacer(Modifier.height(4.dp))
            ProgressBar(if (total > 0) 1f - left.toFloat() / total else 0f, track = Color.White.copy(alpha = 0.2f), fill = TodayColors.Workout, height = 6.dp)
        }
        Spacer(Modifier.width(12.dp))
        Pill("Lewati", background = Color.White, onClick = onSkip)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetTable(
    block: ExerciseBlock,
    last: List<SetLogEntity>,
    selectedId: String?,
    onSelect: (SetLogEntity) -> Unit,
    onGuide: () -> Unit,
    onToggle: (SetLogEntity) -> Unit,
    onWeight: (SetLogEntity, Double) -> Unit,
    onReps: (SetLogEntity, Int) -> Unit,
    onAddSet: () -> Unit,
    onDeleteSet: (SetLogEntity) -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<SetLogEntity?>(null) }
    val lastByIndex = remember(last) { last.associateBy { it.setIndex } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(TodayShapes.Card).clickable(role = Role.Button, onClick = onGuide),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(SessionSurface), contentAlignment = Alignment.Center) {
                if (block.animation != null) {
                    ExerciseImage(block.animation, Modifier.fillMaxSize()) {
                        Text("▶", style = TodayType.Label, color = SessionAccent)
                    }
                } else {
                    Text(block.name.take(1), style = TodayType.Label, color = SessionMuted)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(block.name, style = TodayType.Section, color = SessionAccent)
                if (block.animation == null) {
                    Text("Animasi belum tersedia", style = TodayType.Tiny, color = SessionMuted)
                }
            }
            if (block.animation != null) {
                Text("▶", style = TodayType.Label, color = SessionAccent,
                    modifier = Modifier.padding(horizontal = 8.dp).semantics { contentDescription = "Lihat animasi" })
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SET", Modifier.width(48.dp), style = TodayType.Tiny, color = SessionMuted, textAlign = TextAlign.Center)
            listOf("SEBELUMNYA" to 1.4f, "KG" to 1f, "REP" to 0.8f).forEach { (label, weight) ->
                Text(label, Modifier.weight(weight), style = TodayType.Tiny, color = SessionMuted, textAlign = TextAlign.Center)
            }
            Text("✓", Modifier.width(48.dp), style = TodayType.Label, color = SessionMuted, textAlign = TextAlign.Center)
        }
        block.sets.forEachIndexed { i, set ->
            val selected = set.id == selectedId
            Column(
                Modifier.fillMaxWidth().clip(TodayShapes.Card)
                    .background(if (set.done) SessionCompleted else SessionSurface)
                    .border(1.dp, if (selected && !set.done) SessionAccent else Color.Transparent, TodayShapes.Card)
                    .combinedClickable(
                        onClickLabel = "Ubah beban atau repetisi",
                        onLongClickLabel = "Hapus set",
                        onClick = { onSelect(set) },
                        onLongClick = { deleteTarget = set },
                    ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", Modifier.width(48.dp), style = TodayType.Label, color = Color.White, textAlign = TextAlign.Center)
                    val previous = lastByIndex[set.setIndex]
                    Text(
                        previous?.let { "${formatNumber(it.weightKg)} × ${it.reps}" } ?: "–",
                        Modifier.weight(1.4f), style = TodayType.Meta, color = SessionMuted, textAlign = TextAlign.Center,
                    )
                    Text(formatNumber(set.weightKg), Modifier.weight(1f), style = TodayType.Label, color = Color.White, textAlign = TextAlign.Center)
                    Text("${set.reps}", Modifier.weight(0.8f), style = TodayType.Label, color = Color.White, textAlign = TextAlign.Center)
                    Box(
                        Modifier.size(48.dp)
                            .clickable(role = Role.Checkbox) { onToggle(set) }
                            .semantics { contentDescription = "Set ${i + 1}"; stateDescription = if (set.done) "Selesai" else "Belum selesai" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (set.done) TodayColors.Workout else SessionBackground),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✓", style = TodayType.Label, color = if (set.done) TodayColors.Ink else SessionMuted)
                        }
                    }
                }
                if (selected && !set.done) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MiniStepper("Beban ±2,5", formatKg(set.weightKg), Modifier.fillMaxWidth(), { onWeight(set, -WorkoutMath.WEIGHT_STEP) }, { onWeight(set, WorkoutMath.WEIGHT_STEP) })
                        MiniStepper("Rep", "${set.reps}", Modifier.fillMaxWidth(), { onReps(set, -1) }, { onReps(set, 1) })
                    }
                }
            }
        }
        Pill("+ Tambah set", Modifier.fillMaxWidth(), background = SessionSurface, contentColor = Color.White, onClick = onAddSet)
        HorizontalDivider(color = SessionSurface, modifier = Modifier.padding(top = 8.dp))
    }
    deleteTarget?.let { t ->
        ConfirmDialog("Hapus set?", "Set ${t.setIndex + 1} (${formatKg(t.weightKg)} × ${t.reps}) akan dihapus.", "Hapus",
            onConfirm = { onDeleteSet(t); deleteTarget = null }, onDismiss = { deleteTarget = null }, destructive = true)
    }
}

// ================================================================== Selesai latihan / detail riwayat

@Composable
fun SessionSummaryRoute(sessionId: String, celebrate: Boolean, onBack: () -> Unit, onOpenExercise: (String) -> Unit) {
    val c = LocalContext.current.appContainer
    val vm: SessionSummaryViewModel = viewModel(factory = vmFactory { SessionSummaryViewModel(c.workoutRepository, sessionId) })
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state.summary ?: return
    val today = LocalDate.now(c.clock)

    ScrollColumn(background = if (celebrate) TodayColors.Workout else TodayColors.Background) {
        DetailTopBar(onBack)
        if (celebrate) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.mascot_lift), "Maskot kapibara angkat barbel", Modifier.size(width = 200.dp, height = 160.dp))
                Text("Latihan selesai!", style = TodayType.Display, color = TodayColors.Ink, textAlign = TextAlign.Center)
                Text(s.session.name, style = TodayType.Body, color = TodayColors.Ink)
            }
        } else {
            Text("RIWAYAT LATIHAN", style = TodayType.Eyebrow, color = TodayColors.Brand)
            Text(s.session.name, style = TodayType.Display, color = TodayColors.Headline)
            Text(
                formatLongDate(s.session.plannedDate) + (s.session.startedAt?.let { " · mulai ${formatTime(Instant.ofEpochMilli(it).atZone(c.clock.zone).toLocalTime())}" } ?: ""),
                style = TodayType.Body, color = TodayColors.Muted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(formatDuration(s.minutes), "durasi", Modifier.weight(1f))
            StatCard(formatVolume(s.volumeKg), "volume", Modifier.weight(1f))
            StatCard("${s.setsDone}/${s.setsTotal}", "set", Modifier.weight(0.8f))
        }
        if (s.records.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(TodayShapes.Card).background(TodayColors.Career).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🏆  REKOR BARU", style = TodayType.Eyebrow, color = TodayColors.Ink)
                s.records.forEach { pr ->
                    Text("${pr.exerciseName}: ${formatKg(pr.weightKg)} (sebelumnya ${formatKg(pr.previousKg)})", style = TodayType.Label, color = TodayColors.Ink)
                }
            }
        }
        SectionTitle("Detail set")
        if (state.blocks.isEmpty()) Text("Tidak ada set yang tercatat di sesi ini.", style = TodayType.Meta, color = TodayColors.Muted)
        state.blocks.forEach { b ->
            ListCard(onClick = { onOpenExercise(b.exerciseId) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.name, Modifier.weight(1f), style = TodayType.Label.copy(fontSize = 15.sp), color = TodayColors.Ink)
                    Text("Grafik ›", style = TodayType.Label, color = TodayColors.Brand)
                }
                b.sets.forEachIndexed { i, set ->
                    Text(
                        "Set ${i + 1}: ${formatKg(set.weightKg)} × ${set.reps}" + if (set.done) "" else "  (tidak selesai)",
                        style = TodayType.Meta, color = if (set.done) TodayColors.Ink else TodayColors.Muted,
                    )
                }
            }
        }
        if (celebrate) PrimaryButton("Kembali ke Latihan", background = TodayColors.Ink, onClick = onBack)
        else Text("Dicatat ${relativeDay(s.session.plannedDate, today).lowercase()}", style = TodayType.Meta, color = TodayColors.Muted)
    }
}

// ================================================================== Grafik progres gerakan

@Composable
fun ExerciseProgressRoute(exerciseId: String, onBack: () -> Unit) {
    val c = LocalContext.current.appContainer
    val vm: ExerciseProgressViewModel = viewModel(factory = vmFactory { ExerciseProgressViewModel(c.workoutRepository, exerciseId) })
    val state by vm.state.collectAsStateWithLifecycle()
    val zone = c.clock.zone

    ScrollColumn {
        DetailTopBar(onBack)
        Text("PROGRES BEBAN", style = TodayType.Eyebrow, color = TodayColors.Brand)
        Text(state.name, style = TodayType.Display, color = TodayColors.Headline)
        val pts = state.points
        if (pts.isEmpty() && !state.loading) {
            EmptyState("Belum ada data", "Catat gerakan ini di sesi latihan untuk melihat grafiknya.", mascot = R.drawable.mascot_focus)
        } else if (pts.isNotEmpty()) {
            val best = pts.maxOf { it.maxKg }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard(formatKg(pts.last().maxKg), "terakhir", Modifier.weight(1f))
                StatCard(formatKg(best), "terberat", Modifier.weight(1f))
                StatCard("${pts.size}", "sesi", Modifier.weight(0.7f))
            }
            WeightChart(pts.map { it.maxKg }, Modifier.fillMaxWidth().height(200.dp))
            pts.reversed().forEach { p ->
                val d = Instant.ofEpochMilli(p.endedAt).atZone(zone).toLocalDate()
                Row(Modifier.fillMaxWidth()) {
                    Text(formatLongDate(d), Modifier.weight(1f), style = TodayType.Meta, color = TodayColors.Muted)
                    Text(formatKg(p.maxKg), style = TodayType.Label, color = if (p.maxKg == best) TodayColors.Income else TodayColors.Ink)
                }
            }
        }
    }
}

/** Grafik garis sederhana beban maksimum per sesi. */
@Composable
private fun WeightChart(values: List<Double>, modifier: Modifier) {
    val line = TodayColors.Brand
    val grid = TodayColors.NavStroke
    Box(modifier.clip(TodayShapes.Card).background(TodayColors.Surface).border(BorderStroke(1.dp, grid), TodayShapes.Card).padding(16.dp)) {
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Grafik beban: " + values.joinToString { formatKg(it) } }) {
            val min = (values.minOrNull() ?: 0.0) * 0.9
            val max = (values.maxOrNull() ?: 1.0).coerceAtLeast(min + 1)
            fun y(v: Double) = size.height - ((v - min) / (max - min) * size.height).toFloat()
            fun x(i: Int) = if (values.size == 1) size.width / 2 else i * size.width / (values.size - 1)
            repeat(4) { k ->
                val gy = size.height * k / 3f
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            }
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
            drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            values.forEachIndexed { i, v -> drawCircle(line, radius = 5.dp.toPx(), center = Offset(x(i), y(v))) }
        }
    }
}
