package com.farhan.today.ui.tasks

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.input.ImeAction
import com.farhan.today.ui.components.DetailScaffold
import com.farhan.today.ui.components.LoadingDetail
import com.farhan.today.ui.components.LoadingSkeleton
import com.farhan.today.ui.components.TodayFab
import com.farhan.today.ui.components.TopBarAction
import com.farhan.today.ui.components.rememberConfirmHaptic
import com.farhan.today.ui.components.screenInsets
import com.farhan.today.ui.theme.Spacing

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.farhan.today.R
import com.farhan.today.appContainer
import com.farhan.today.data.db.ProjectProgress
import com.farhan.today.data.db.TaskPriority
import com.farhan.today.data.db.TaskRow
import com.farhan.today.data.db.TaskStatus
import com.farhan.today.data.repo.DeadlineKind
import com.farhan.today.data.repo.TaskInput
import com.farhan.today.data.repo.deadlineBadge
import com.farhan.today.data.repo.projectProgress
import com.farhan.today.domain.formatLongDate
import com.farhan.today.domain.formatTime
import com.farhan.today.ui.components.ChoiceChips
import com.farhan.today.ui.components.ConfirmDialog
import com.farhan.today.ui.components.DateField
import com.farhan.today.ui.components.EmptyState
import com.farhan.today.ui.components.FieldLabel
import com.farhan.today.ui.components.Pill
import com.farhan.today.ui.components.PrimaryButton
import com.farhan.today.ui.components.ProgressBar
import com.farhan.today.ui.components.ScreenHeader
import com.farhan.today.ui.components.SectionTitle
import com.farhan.today.ui.components.StatusBadge
import com.farhan.today.ui.components.TimeField
import com.farhan.today.ui.components.TodayTextField
import com.farhan.today.ui.components.vmFactory
import com.farhan.today.ui.theme.TodayColors
import com.farhan.today.ui.theme.TodayShapes
import com.farhan.today.ui.theme.TodayType
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Sheet terbuka: null = tertutup, "" = tugas baru, selain itu id tugas. */
private const val NEW_TASK = ""

@Composable
fun TaskFormDestination(taskId: String?, onBack: () -> Unit) {
    val container = LocalContext.current.appContainer
    val vm: TasksViewModel = viewModel(factory = vmFactory { TasksViewModel(container.taskRepository, container.clock) })
    val state by vm.state.collectAsStateWithLifecycle()
    TaskFormSheet(
        vm, taskId, state.projects, null, onBack,
        onDeleted = { _, _ -> onBack() },
        allowDelete = false,
        initialDate = if (taskId == null) LocalDate.now(container.clock) else null,
    )
}

// ------------------------------------------------------------------ 09 · Tugas & Proyek

@Composable
fun TasksRoute(onOpenProject: (String) -> Unit) {
    val c = LocalContext.current.appContainer
    val vm: TasksViewModel = viewModel(factory = vmFactory { TasksViewModel(c.taskRepository, c.clock) })
    val state by vm.state.collectAsStateWithLifecycle()
    TasksContent(
        vm = vm, state = state, inDetail = false,
        header = { ScreenHeader("Tasks & projects", "Tugas", "Lihat langkah berikutnya.") },
        projectSection = { openProjectForm ->
            // Proyek sebagai carousel satu baris, supaya daftar tugas (isi utama tab) tidak terdorong ke bawah.
            when {
                state.loading -> {}
                state.projects.isEmpty() -> Pill("+ Buat proyek, misalnya Skripsi", Modifier.fillMaxWidth(), onClick = openProjectForm)
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Proyek", style = TodayType.Section, color = TodayColors.Ink, modifier = Modifier.weight(1f))
                        Pill("Baru", icon = R.drawable.ic_add, background = TodayColors.Background, onClick = openProjectForm)
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        // Kartu boleh melewati padding layar saat digulir.
                        modifier = Modifier.fillMaxWidth().layout { m, cons ->
                            val extra = Spacing.ScreenH.roundToPx() * 2
                            val p = m.measure(cons.copy(maxWidth = cons.maxWidth + extra))
                            layout(p.width - extra, p.height) { p.place(-extra / 2, 0) }
                        },
                        contentPadding = PaddingValues(horizontal = Spacing.ScreenH),
                    ) {
                        items(state.projects, key = { it.project.id }) { p ->
                            ProjectCard(p, state.today, Modifier.width(260.dp)) { onOpenProject(p.project.id) }
                        }
                    }
                }
            }
        },
        presetProject = null,
    )
}

@Composable
fun ProjectDetailRoute(id: String, onBack: () -> Unit) {
    val c = LocalContext.current.appContainer
    val vm: TasksViewModel = viewModel(factory = vmFactory { TasksViewModel(c.taskRepository, c.clock, id) })
    val state by vm.state.collectAsStateWithLifecycle()
    var deleteDialog by remember { mutableStateOf(false) }
    var editDialog by remember { mutableStateOf(false) }

    if (state.projectMissing) {
        LaunchedEffect(Unit) { onBack() }
        LoadingDetail("Proyek", onBack)
        return
    }
    val p = state.project
    DetailScaffold(
        title = p?.project?.name ?: "Proyek",
        onBack = onBack,
        scrollable = false,
        actions = {
            TopBarAction(R.drawable.ic_edit, "Ubah proyek") { editDialog = true }
            TopBarAction(R.drawable.ic_delete, "Hapus proyek", tint = TodayColors.Danger) { deleteDialog = true }
        },
    ) {
        TasksContent(
            vm = vm, state = state, presetProject = id, inDetail = true,
            header = {
                if (!p?.project?.description.isNullOrBlank()) Text(p?.project?.description.orEmpty(), style = TodayType.Body, color = TodayColors.Muted)
            },
            projectSection = { if (p != null) ProjectCard(p, state.today, Modifier.fillMaxWidth(), onClick = null) },
        )
    }
    if (deleteDialog && p != null) {
        ConfirmDialog(
            "Hapus proyek?", "${p.project.name} dan ${p.total} tugasnya akan disembunyikan.", "Hapus",
            onConfirm = { deleteDialog = false; vm.deleteProject(id, onBack) }, onDismiss = { deleteDialog = false }, destructive = true,
        )
    }
    if (editDialog && p != null) {
        ProjectFormDialog(p.project.name, p.project.description, onDismiss = { editDialog = false }) { name, desc ->
            vm.saveProject(name, desc, id); editDialog = false
        }
    }
}

@Composable
private fun TasksContent(
    vm: TasksViewModel,
    state: TasksState,
    presetProject: String?,
    inDetail: Boolean,
    header: @Composable () -> Unit,
    projectSection: @Composable (openProjectForm: () -> Unit) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = rememberConfirmHaptic()
    var sheetFor by rememberSaveable { mutableStateOf<String?>(null) }
    var projectForm by remember { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()

    fun toggle(row: TaskRow) = vm.toggle(row.task.id) { status ->
        if (status == TaskStatus.DONE) {
            haptic()
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val r = snackbar.showSnackbar("“${row.task.title}” selesai", actionLabel = "Batalkan", duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) vm.toggle(row.task.id)
            }
        }
    }

    Box(Modifier.fillMaxSize().then(if (inDetail) Modifier else Modifier.screenInsets())) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.ScreenH, end = Spacing.ScreenH,
                top = if (inDetail) 4.dp else Spacing.ScreenTop, bottom = Spacing.FabClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { header() } }
            item(key = "projects") { projectSection { projectForm = true } }
            item(key = "next") { SectionTitle("Berikutnya", trailing = "${state.active.size} aktif") }
            if (state.loading) {
                item(key = "loading") { LoadingSkeleton(rows = 3, showHeader = false) }
            } else if (state.active.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        if (state.done.isEmpty()) "Belum ada tugas" else "Semua tugas beres!",
                        "Tambahkan langkah kecil berikutnya supaya progres tetap jalan.",
                        mascot = R.drawable.mascot_focus,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            items(state.active, key = { it.task.id }) { row ->
                TaskRowCard(row, state.today, showProject = presetProject == null, Modifier.animateItem(), onToggle = { toggle(row) }, onOpen = { sheetFor = row.task.id })
            }
            if (state.done.isNotEmpty()) {
                item(key = "done-heading") {
                    Row(
                        Modifier.animateItem().fillMaxWidth().heightIn(min = 48.dp).clip(TodayShapes.Chip)
                            .clickable(role = Role.Button, onClickLabel = if (showDone) "Lipat" else "Buka") { showDone = !showDone }
                            .semantics { stateDescription = if (showDone) "Terbuka" else "Terlipat" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Selesai", style = TodayType.Section, color = TodayColors.Ink, modifier = Modifier.weight(1f))
                        Text("${state.done.size}", style = TodayType.Label, color = TodayColors.Muted)
                        Icon(painterResource(if (showDone) R.drawable.ic_expand_less else R.drawable.ic_expand_more), null, tint = TodayColors.Muted)
                    }
                }
                if (showDone) items(state.done, key = { it.task.id }) { row ->
                    TaskRowCard(row, state.today, showProject = presetProject == null, Modifier.animateItem(), onToggle = { toggle(row) }, onOpen = { sheetFor = row.task.id })
                }
            }
        }
        val expanded by remember { derivedStateOf { !list.canScrollBackward || list.lastScrolledBackward } }
        TodayFab("Tugas", expanded, Modifier.align(Alignment.BottomEnd)) { sheetFor = NEW_TASK }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    sheetFor?.let { target ->
        TaskFormSheet(
            vm = vm,
            taskId = target.ifEmpty { null },
            projects = state.projects,
            presetProject = presetProject,
            onDismiss = { sheetFor = null },
            onDeleted = { title, id ->
                sheetFor = null
                scope.launch {
                    val r = snackbar.showSnackbar("“$title” dihapus", actionLabel = "Batalkan", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) vm.restore(id)
                }
            },
        )
    }
    if (projectForm) {
        ProjectFormDialog("", "", onDismiss = { projectForm = false }) { name, desc -> vm.saveProject(name, desc); projectForm = false }
    }
}

@Composable
private fun ProjectCard(p: ProjectProgress, today: LocalDate, modifier: Modifier = Modifier, onClick: (() -> Unit)?) {
    val progress = projectProgress(p.done, p.total)
    Column(
        modifier
            .clip(TodayShapes.Card)
            .background(TodayColors.Tasks)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("PROYEK  ·  ${p.done} DARI ${p.total} SELESAI", style = TodayType.Eyebrow, color = TodayColors.Ink)
        Text(p.project.name, style = TodayType.CardTitle, color = TodayColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            p.nextDue?.let { "Deadline berikutnya ${formatLongDate(it)}" + if (it.isBefore(today)) " · terlambat" else "" }
                ?: if (p.total > 0 && p.done == p.total) "Semua tugas selesai 🎉" else "Belum ada deadline",
            style = TodayType.Meta, color = TodayColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        ProgressBar(progress, track = Color.White, fill = TodayColors.Brand)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRowCard(row: TaskRow, today: LocalDate, showProject: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit, onOpen: () -> Unit) {
    val t = row.task
    val done = t.status == TaskStatus.DONE
    val boxColor by animateColorAsState(if (done) TodayColors.Ink else Color.White, label = "check")
    val badge = deadlineBadge(t.dueDate, today, done)
    Row(
        modifier
            .fillMaxWidth()
            .clip(TodayShapes.Card)
            .background(if (done) TodayColors.Chip else TodayColors.Surface)
            .border(BorderStroke(1.dp, TodayColors.NavStroke), TodayShapes.Card)
            .combinedClickable(onClick = onOpen, onLongClick = onOpen)
            .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Checkbox: area sentuh 48dp
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Checkbox, onClick = onToggle)
                .semantics { contentDescription = t.title; stateDescription = if (done) "Selesai" else "Belum selesai" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(boxColor)
                    .border(2.dp, TodayColors.Ink, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) { if (done) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp), tint = Color.White) }
        }
        Column(Modifier.weight(1f)) {
            Text(
                t.title, style = TodayType.Label.copy(fontSize = TodayType.Body.fontSize),
                color = if (done) TodayColors.Muted else TodayColors.Ink,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                row.projectName.takeIf { showProject },
                t.note.takeIf { it.isNotBlank() },
                t.dueTime?.let { "jam ${formatTime(it)}" },
                if (t.priority == TaskPriority.HIGH && !done) "Prioritas tinggi" else null,
            )
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = TodayType.Meta, color = TodayColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (badge != null) {
            val (bg, fg) = when (badge.kind) {
                DeadlineKind.OVERDUE -> Color(0xFFFFE0DD) to TodayColors.Danger
                DeadlineKind.TODAY -> Color(0xFFFFE6C7) to Color(0xFF8A4B00)
                DeadlineKind.TOMORROW -> TodayColors.Career to TodayColors.Ink
                DeadlineKind.UPCOMING -> TodayColors.Chip to TodayColors.Ink
                DeadlineKind.DONE -> TodayColors.WorkoutTint to TodayColors.Income
            }
            StatusBadge(
                badge.label,
                background = bg, contentColor = fg,
                icon = when (badge.kind) {
                    DeadlineKind.DONE -> null
                    DeadlineKind.OVERDUE -> R.drawable.ic_warning
                    else -> R.drawable.ic_calendar
                },
            )
        }
    }
}

// ------------------------------------------------------------------ Form tugas (bottom sheet)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskFormSheet(
    vm: TasksViewModel,
    taskId: String?,
    projects: List<ProjectProgress>,
    presetProject: String?,
    onDismiss: () -> Unit,
    onDeleted: (title: String, id: String) -> Unit,
    allowDelete: Boolean = true,
    initialDate: LocalDate? = null,
) {
    var loaded by rememberSaveable { mutableStateOf(taskId == null) }
    var missing by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var projectId by rememberSaveable { mutableStateOf(presetProject) }
    var dueDate by rememberSaveable { mutableStateOf(initialDate) }
    var dueTime by rememberSaveable { mutableStateOf<java.time.LocalTime?>(null) }
    var priority by rememberSaveable { mutableStateOf(TaskPriority.NORMAL) }
    var note by rememberSaveable { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(taskId != null || initialDate == null) }
    var dirty by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(taskId) {
        if (taskId != null && !loaded) {
            val task = vm.load(taskId)
            missing = task == null
            task?.let {
                title = it.title; projectId = it.projectId; dueDate = it.dueDate; dueTime = it.dueTime; priority = it.priority; note = it.note
            }
            loaded = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!saving) {
                if (dirty) confirmDiscard = true else onDismiss()
            }
        },
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { value ->
                val closing = value == androidx.compose.material3.SheetValue.Hidden
                if (closing && dirty && !saving) confirmDiscard = true
                !closing || (!dirty && !saving)
            },
        ),
        containerColor = TodayColors.Background,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.ScreenH).navigationBarsPadding().imePadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!loaded) {
                Text("Memuat tugas…", style = TodayType.Body)
                return@Column
            }
            if (missing) {
                Text("Tugas sudah tidak tersedia.", style = TodayType.Section, color = TodayColors.Ink)
                PrimaryButton("Kembali", onClick = onDismiss)
                return@Column
            }
            Text(if (taskId == null) "Tugas baru" else "Ubah tugas", style = TodayType.Section, color = TodayColors.Ink)
            TodayTextField(title, { title = it; dirty = true; showError = false }, "Judul *", error = if (showError) "Wajib diisi" else null)
            FieldLabel("Deadline")
            DateField(dueDate, { dueDate = it; dirty = true; if (it == null) dueTime = null }, placeholder = "Tanpa deadline", clearable = true)
            if (showDetails) {
                if (dueDate != null) TimeField(dueTime, { dueTime = it; dirty = true }, placeholder = "Seharian (opsional jam)", clearable = true)
                FieldLabel("Proyek")
                ChoiceChips(listOf<Pair<String?, String>>(null to "Tanpa proyek") + projects.map { it.project.id to it.project.name }, projectId, { projectId = it; dirty = true })
                FieldLabel("Prioritas")
                ChoiceChips(listOf(TaskPriority.LOW to "Rendah", TaskPriority.NORMAL to "Normal", TaskPriority.HIGH to "Tinggi"), priority, { priority = it; dirty = true })
                TodayTextField(note, { note = it; dirty = true }, "Catatan", singleLine = false)
            } else {
                TextButton(onClick = { showDetails = true }) { Text("Tambah detail") }
            }
            Spacer(Modifier.height(4.dp))
            if (saveFailed) Text("Tugas belum tersimpan. Coba lagi.", color = TodayColors.Danger, style = TodayType.Body)
            PrimaryButton(
                if (taskId == null) "Simpan tugas" else "Simpan perubahan",
                loading = saving,
                onClick = {
                    val input = TaskInput(title, projectId, dueDate, dueTime, priority, note)
                    if (!input.isValid) {
                        showError = true
                    } else if (!saving) {
                        saving = true
                        saveFailed = false
                        vm.saveTask(input, taskId, onSaved = onDismiss, onError = {
                            saving = false
                            saveFailed = true
                        })
                    }
                },
            )
            if (taskId != null && allowDelete && !saving) {
                TextButton(onClick = { vm.delete(taskId); onDeleted(title, taskId) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Hapus tugas", color = TodayColors.Danger, style = TodayType.Label)
                }
            }
        }
        if (confirmDiscard) {
            ConfirmDialog(
                "Buang perubahan?", "Perubahan pada tugas ini belum tersimpan.", "Buang",
                onConfirm = onDismiss, onDismiss = { confirmDiscard = false }, destructive = true,
            )
        }
    }
}

@Composable
private fun ProjectFormDialog(initialName: String, initialDescription: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var desc by rememberSaveable { mutableStateOf(initialDescription) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isEmpty()) "Proyek baru" else "Ubah proyek", style = TodayType.CardTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TodayTextField(name, { name = it; error = false }, "Nama proyek *", error = if (error) "Wajib diisi" else null)
                TodayTextField(desc, { desc = it }, "Deskripsi", imeAction = ImeAction.Done)
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isBlank()) error = true else onSave(name, desc) }) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Suppress("unused")
private val keepIcon = R.drawable.ic_nav_tasks
