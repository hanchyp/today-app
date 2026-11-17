package com.farhan.today.ui.career

import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.farhan.today.ui.components.DetailScaffold
import com.farhan.today.ui.components.LoadingDetail
import com.farhan.today.ui.components.LoadingSkeleton
import com.farhan.today.ui.components.ScreenColumn
import com.farhan.today.ui.components.SearchField
import com.farhan.today.ui.components.TodayFab
import com.farhan.today.ui.components.TopBarAction
import com.farhan.today.ui.components.fabExpanded
import com.farhan.today.ui.components.rememberBackGuard
import com.farhan.today.ui.theme.Spacing

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.farhan.today.R
import com.farhan.today.appContainer
import com.farhan.today.data.db.ApplicationRow
import com.farhan.today.data.db.ApplicationStatus
import com.farhan.today.data.db.EventMode
import com.farhan.today.data.db.JobApplicationEntity
import com.farhan.today.data.db.JobEventEntity
import com.farhan.today.data.db.JobEventType
import com.farhan.today.data.repo.CareerRepository
import com.farhan.today.data.repo.label
import com.farhan.today.domain.formatLongDate
import com.farhan.today.domain.formatTime
import com.farhan.today.domain.relativeDay
import com.farhan.today.reminder.canPostNotifications
import com.farhan.today.ui.components.ChoiceChips
import com.farhan.today.ui.components.ConfirmDialog
import com.farhan.today.ui.components.DateField
import com.farhan.today.ui.components.EmptyState
import com.farhan.today.ui.components.FieldLabel
import com.farhan.today.ui.components.ListCard
import com.farhan.today.ui.components.Pill
import com.farhan.today.ui.components.PrimaryButton
import com.farhan.today.ui.components.ScreenHeader
import com.farhan.today.ui.components.SecondaryButton
import com.farhan.today.ui.components.SectionTitle
import com.farhan.today.ui.components.StatusBadge
import com.farhan.today.ui.components.TimeField
import com.farhan.today.ui.components.TodayTextField
import com.farhan.today.ui.components.vmFactory
import com.farhan.today.ui.theme.TodayColors
import com.farhan.today.ui.theme.TodayShapes
import com.farhan.today.ui.theme.TodayType
import java.net.URI
import java.time.Instant
import java.time.ZoneId

// ------------------------------------------------------------------ Bersama

fun ApplicationStatus.badgeColor(): Color = when (this) {
    ApplicationStatus.SAVED -> TodayColors.Chip
    ApplicationStatus.APPLIED -> Color(0xFFE5EDFF)
    ApplicationStatus.CODING_TEST, ApplicationStatus.INTERVIEW -> TodayColors.Career
    ApplicationStatus.OFFER -> TodayColors.Workout
    ApplicationStatus.REJECTED, ApplicationStatus.WITHDRAWN -> Color(0xFFFFE0DD)
}

private fun initials(company: String) =
    company.split(' ', '-', '.').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

@Composable
private fun CompanyLogo(company: String, size: Int, color: Color = TodayColors.Career) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.32f).dp)).background(color)
            .border(BorderStroke(1.5.dp, TodayColors.Ink), RoundedCornerShape((size * 0.32f).dp)),
        contentAlignment = Alignment.Center,
    ) { Text(initials(company), style = if (size > 48) TodayType.CardTitle else TodayType.Label, color = TodayColors.Ink) }
}

private fun eventWhen(millis: Long, zone: ZoneId): String {
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    return "${formatLongDate(at.toLocalDate())} · ${formatTime(at.toLocalTime())}"
}

// ------------------------------------------------------------------ 03 · Karier (daftar)

@Composable
fun CareerRoute(onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val c = LocalContext.current.appContainer
    val vm: CareerListViewModel = viewModel(factory = vmFactory { CareerListViewModel(c.careerRepository) })
    val state by vm.state.collectAsStateWithLifecycle()
    val zone = c.clock.zone
    val scroll = rememberScrollState()

    Box(Modifier.fillMaxSize()) {
        ScreenColumn(state = scroll, bottomPadding = Spacing.FabClearance) {
            ScreenHeader("Lamaran", "Karier", "Pantau internship dan pekerjaan.")
            SearchField(state.query, vm::setQuery, "Cari perusahaan atau posisi")
            ChoiceChips(
                options = CareerFilter.entries.map { it to "${it.label} ${state.counts[it] ?: 0}" },
                selected = state.filter, onSelect = vm::setFilter,
            )
            when {
                state.loading -> LoadingSkeleton(rows = 3, showHeader = false)
                state.total == 0 -> EmptyState(
                    "Belum ada lamaran",
                    "Simpan lowongan yang menarik, lalu pantau statusnya sampai offer.",
                    action = { Pill("+ Tambah lamaran", selected = true, onClick = onAdd) },
                )
                state.rows.isEmpty() -> EmptyState("Tidak ada yang cocok", "Coba filter atau kata kunci lain.")
                else -> state.rows.forEach { ApplicationCard(it, zone) { onOpen(it.application.id) } }
            }
        }
        TodayFab("Lamaran", scroll.fabExpanded, Modifier.align(Alignment.BottomEnd), onClick = onAdd)
    }
}

@Composable
private fun ApplicationCard(row: ApplicationRow, zone: ZoneId, onClick: () -> Unit) {
    val app = row.application
    val hasAgenda = row.nextEventAt != null
    Column(
        Modifier
            .fillMaxWidth()
            .clip(TodayShapes.Card)
            .background(if (hasAgenda) TodayColors.Career else TodayColors.Surface)
            .border(BorderStroke(1.dp, if (hasAgenda) TodayColors.Career else TodayColors.NavStroke), TodayShapes.Card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CompanyLogo(app.company, 44, if (hasAgenda) Color.White else app.status.badgeColor())
            Column(Modifier.weight(1f)) {
                Text(app.company, style = TodayType.CardTitle, color = TodayColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.role, style = TodayType.Meta, color = TodayColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            StatusBadge(app.status.label(), background = if (hasAgenda) Color.White else app.status.badgeColor())
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(if (hasAgenda) TodayColors.Ink.copy(alpha = 0.15f) else TodayColors.NavStroke))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_calendar), null, tint = if (hasAgenda) TodayColors.Ink else TodayColors.Muted, modifier = Modifier.size(18.dp))
            Text(
                if (row.nextEventAt != null) "${row.nextEventTitle} · ${eventWhen(row.nextEventAt, zone)}"
                else app.deadlineDate?.let { "Deadline lamaran · ${formatLongDate(it)}" } ?: "Belum ada jadwal",
                style = if (hasAgenda) TodayType.Label else TodayType.Meta,
                color = if (hasAgenda) TodayColors.Ink else TodayColors.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------------ 04 · Detail lamaran

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ApplicationDetailRoute(
    id: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSchedule: (eventId: String?) -> Unit,
    onOpenCalendar: (java.time.LocalDate?) -> Unit,
    focusEventId: String? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val c = context.appContainer
    val vm: ApplicationDetailViewModel = viewModel(factory = vmFactory { ApplicationDetailViewModel(c.careerRepository, id, c.clock) })
    val state by vm.state.collectAsStateWithLifecycle()
    val zone = c.clock.zone
    val today = java.time.LocalDate.now(c.clock)
    var statusDialog by remember { mutableStateOf(false) }
    var deletingChange by remember { mutableStateOf<String?>(null) }
    var deleteDialog by remember { mutableStateOf(false) }

    val app = state.application
    if (state.loading) {
        LoadingDetail("Detail lamaran", onBack)
        return
    }
    if (app == null) {
        LaunchedEffect(Unit) { onBack() }
        LoadingDetail("Detail lamaran", onBack)
        return
    }

    DetailScaffold(
        title = "Detail lamaran",
        onBack = onBack,
        actions = { TopBarAction(R.drawable.ic_edit, "Ubah lamaran", onClick = onEdit) },
        // Aksi utama selalu terlihat, tidak terkubur di bawah riwayat (B5).
        bottomBar = { PrimaryButton("Jadwalkan interview", icon = R.drawable.ic_calendar) { onSchedule(null) } },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CompanyLogo(app.company, 56)
            Column(Modifier.weight(1f)) {
                Text("LAMARAN", style = TodayType.Eyebrow, color = TodayColors.Brand)
                Text(app.company, style = TodayType.Title, color = TodayColors.Headline, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(app.role, style = TodayType.CardTitle, color = TodayColors.Ink)
        val meta = listOf(app.location, app.source).filter { it.isNotBlank() }
        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = TodayType.Meta, color = TodayColors.Muted)
        if (app.url.isNotBlank()) {
            SecondaryButton("Buka link lowongan", icon = R.drawable.ic_open_in_new) {
                val url = lowonganWebUrl(app.url)
                val message = if (url == null) {
                    "Link lowongan tidak valid."
                } else {
                    try {
                        uriHandler.openUri(url)
                        null
                    } catch (_: IllegalArgumentException) {
                        "Tidak ada aplikasi yang bisa membuka link ini."
                    }
                }
                message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(
                "Tahap: ${app.status.label()}", background = app.status.badgeColor(),
                trailingIcon = R.drawable.ic_expand_more,
                modifier = Modifier.semantics { contentDescription = "Tahap ${app.status.label()}, ketuk untuk mengubah" },
                onClick = { statusDialog = true },
            )
            state.lastUpdated?.let { Text("Diperbarui ${relativeDay(it, today).lowercase()}", style = TodayType.Meta, color = TodayColors.Muted, modifier = Modifier.weight(1f)) }
        }

        StatusStepper(app.status)

        val focusEvent = state.events.firstOrNull { it.id == focusEventId }
        if (focusEvent != null) {
            SectionTitle("Agenda yang dipilih")
            EventRow(focusEvent, zone, onClick = { onSchedule(focusEvent.id) }, onToggle = { vm.setEventDone(focusEvent.id, !focusEvent.done) })
        } else {
            if (focusEventId != null) Text("Agenda sudah tidak tersedia.", style = TodayType.Body, color = TodayColors.Muted)
            NextEventCard(state.nextEvent, zone, onEdit = { onSchedule(it) }, onDone = { vm.setEventDone(it, true) })
        }

        if (app.notes.isNotBlank()) {
            SectionTitle("Catatan")
            Text(app.notes, style = TodayType.Body, color = TodayColors.Muted)
        }

        if (state.events.isNotEmpty()) {
            SectionTitle("Semua agenda", trailing = "${state.events.size}")
            state.events.filter { it.id != focusEvent?.id }.forEach { e ->
                EventRow(e, zone, onClick = { onSchedule(e.id) }, onToggle = { vm.setEventDone(e.id, !e.done) })
            }
        }

        Spacer(Modifier.height(8.dp))
        if (state.history.isNotEmpty()) {
            SectionTitle("Riwayat tahap lamaran")
            state.history.asReversed().forEach { change ->
                val changedDate = Instant.ofEpochMilli(change.changedAt).atZone(zone).toLocalDate()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(listOfNotNull(change.fromStatus?.label(), change.toStatus.label()).joinToString(" → "), style = TodayType.Label)
                        Text(com.farhan.today.domain.formatLongDate(changedDate), style = TodayType.Meta)
                    }
                    IconButton(onClick = { deletingChange = change.id }) {
                        Icon(painterResource(R.drawable.ic_delete), "Hapus riwayat tahap", tint = TodayColors.Muted)
                    }
                }
            }
        }
        SecondaryButton("Lihat kalender") {
            onOpenCalendar(state.nextEvent?.let { Instant.ofEpochMilli(it.startAt).atZone(zone).toLocalDate() })
        }
        TextButton(onClick = { deleteDialog = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Hapus lamaran", color = TodayColors.Danger, style = TodayType.Label)
        }
    }

    deletingChange?.let { changeId ->
        ConfirmDialog("Hapus riwayat tahap?", "Tahap lamaran saat ini tetap sama.", "Hapus",
            onConfirm = { vm.deleteStatusHistory(changeId); deletingChange = null },
            onDismiss = { deletingChange = null }, destructive = true)
    }
    if (statusDialog) {
        ModalBottomSheet(onDismissRequest = { statusDialog = false }, containerColor = TodayColors.Background) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp).selectableGroup()) {
                Text("Ubah tahap lamaran", style = TodayType.Section, color = TodayColors.Ink, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                ApplicationStatus.entries.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp)
                            .selectable(selected = s == app.status, role = Role.RadioButton) { vm.changeStatus(s); statusDialog = false }
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = s == app.status, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = TodayColors.Ink))
                        Text(s.label(), style = TodayType.Body, color = TodayColors.Ink, modifier = Modifier.weight(1f))
                        Box(Modifier.size(12.dp).clip(CircleShape).background(s.badgeColor()).border(1.dp, TodayColors.NavStroke, CircleShape))
                    }
                }
            }
        }
    }
    if (deleteDialog) {
        ConfirmDialog(
            "Hapus lamaran?", "${app.company} beserta semua agenda dan pengingatnya akan dihapus.", "Hapus",
            onConfirm = { deleteDialog = false; vm.delete(onBack) }, onDismiss = { deleteDialog = false }, destructive = true,
        )
    }
}

private fun lowonganWebUrl(raw: String): String? {
    val value = raw.trim()
    val address = when {
        value.startsWith("//") -> "https:$value"
        Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(value) -> value
        else -> "https://$value"
    }
    val uri = runCatching { URI(address) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (uri.host == null || (scheme != "https" && scheme != "http")) return null
    return scheme + uri.toASCIIString().substring(uri.scheme.length)
}

@Composable
private fun StatusStepper(status: ApplicationStatus) {
    val stages = CareerRepository.PIPELINE
    val labels = listOf("Simpan", "Apply", "Tes", "Interview", "Offer")
    val current = stages.indexOf(status)
    val closed = current < 0
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            stages.indices.forEach { i ->
                val reached = !closed && i <= current
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .background(
                            when {
                                closed -> TodayColors.Chip
                                i < current -> TodayColors.Ink
                                i == current -> TodayColors.Career
                                else -> TodayColors.Surface
                            }
                        )
                        .border(2.dp, if (reached) TodayColors.Ink else TodayColors.NavStroke, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!closed && i < current) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(14.dp), tint = Color.White)
                    if (!closed && i == current) Box(Modifier.size(8.dp).clip(CircleShape).background(TodayColors.Ink))
                }
                if (i < stages.lastIndex) {
                    Box(Modifier.weight(1f).height(3.dp).background(if (!closed && i < current) TodayColors.Ink else TodayColors.NavStroke))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(
                    l, Modifier.weight(1f), style = TodayType.Meta.copy(fontSize = 12.sp),
                    color = if (!closed && i <= current) TodayColors.Ink else TodayColors.Muted,
                    textAlign = when (i) { 0 -> TextAlign.Start; labels.lastIndex -> TextAlign.End; else -> TextAlign.Center },
                )
            }
        }
        if (closed) Text("Lamaran ini sudah ${status.label().lowercase()}.", style = TodayType.Meta, color = TodayColors.Danger)
    }
}

@Composable
private fun NextEventCard(event: JobEventEntity?, zone: ZoneId, onEdit: (String?) -> Unit, onDone: (String) -> Unit) {
    if (event == null) {
        ListCard(onClick = { onEdit(null) }) {
            Text("AGENDA BERIKUTNYA", style = TodayType.Eyebrow, color = TodayColors.Muted)
            Text("Belum ada jadwal", style = TodayType.CardTitle, color = TodayColors.Ink)
            Text("Ketuk untuk menjadwalkan coding test, interview, atau follow-up.", style = TodayType.Meta, color = TodayColors.Muted)
        }
        return
    }
    val at = Instant.ofEpochMilli(event.startAt).atZone(zone)
    val end = at.plusMinutes(event.durationMinutes.toLong())
    Column(
        Modifier.fillMaxWidth().clip(TodayShapes.Hero).background(TodayColors.Career).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("AGENDA BERIKUTNYA  ·  ${event.type.label().uppercase()}", style = TodayType.Eyebrow, color = TodayColors.Ink)
        Text(event.title, style = TodayType.Section, color = TodayColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${formatLongDate(at.toLocalDate())} · ${formatTime(at.toLocalTime())}–${formatTime(end.toLocalTime())}", style = TodayType.Label, color = TodayColors.Ink)
        Text(
            listOfNotNull(
                if (event.mode == EventMode.ONLINE) "Online" else "Onsite",
                event.locationOrLink.takeIf { it.isNotBlank() },
                event.reminderMinutesBefore?.let { "Pengingat ${CareerRepository.reminderLabel(it)} sebelumnya" },
            ).joinToString(" · "),
            style = TodayType.Meta, color = TodayColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Tandai selesai", background = Color.White, onClick = { onDone(event.id) })
            Pill("Ubah", background = Color.White, onClick = { onEdit(event.id) })
        }
    }
}

@Composable
private fun EventRow(e: JobEventEntity, zone: ZoneId, onClick: () -> Unit, onToggle: () -> Unit) {
    ListCard(onClick = onClick, background = if (e.done) TodayColors.Chip else TodayColors.Surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.type.label().uppercase(), style = TodayType.Eyebrow, color = TodayColors.Muted)
                Text(e.title, style = TodayType.Label.copy(fontSize = TodayType.Body.fontSize), color = TodayColors.Ink)
                Text(eventWhen(e.startAt, zone), style = TodayType.Meta, color = TodayColors.Muted)
            }
            Pill(
                if (e.done) "Selesai" else "Tandai", selected = e.done,
                icon = if (e.done) R.drawable.ic_check else null,
                modifier = Modifier.semantics { stateDescription = if (e.done) "Selesai" else "Belum selesai" },
                onClick = onToggle,
            )
        }
    }
}

// ------------------------------------------------------------------ Form lamaran

@Composable
fun ApplicationFormRoute(id: String?, onBack: () -> Unit, onSaved: (String) -> Unit) {
    val c = LocalContext.current.appContainer
    val vm: ApplicationFormViewModel = viewModel(factory = vmFactory { ApplicationFormViewModel(c.careerRepository, id) })
    val title = if (vm.isEdit) "Ubah lamaran" else "Lamaran baru"
    if (!vm.loaded) {
        LoadingDetail(title, onBack)
        return
    }
    val back = rememberBackGuard(vm.dirty, onBack)

    DetailScaffold(
        title = title,
        onBack = back,
        bottomBar = {
            PrimaryButton(if (vm.isEdit) "Simpan perubahan" else "Simpan lamaran", loading = vm.saving) { vm.save(onSaved) }
        },
    ) {
        TodayTextField(vm.company, { vm.company = it }, "Perusahaan *", error = if (vm.showErrors && vm.company.isBlank()) "Wajib diisi" else null, capitalization = KeyboardCapitalization.Words)
        TodayTextField(vm.role, { vm.role = it }, "Posisi *", error = if (vm.showErrors && vm.role.isBlank()) "Wajib diisi" else null, capitalization = KeyboardCapitalization.Words)
        TodayTextField(vm.location, { vm.location = it }, "Lokasi", placeholder = "Jakarta / Remote", capitalization = KeyboardCapitalization.Words)
        TodayTextField(vm.source, { vm.source = it }, "Sumber", placeholder = "LinkedIn, Glints, referral…")
        TodayTextField(vm.url, { vm.url = it }, "Link lowongan", capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Uri)

        FieldLabel("Tahap")
        ChoiceChips(ApplicationStatus.entries.map { it to it.label() }, vm.status, { vm.status = it })
        FieldLabel("Tanggal apply")
        DateField(vm.appliedDate, { vm.appliedDate = it }, placeholder = "Belum apply", clearable = true)
        FieldLabel("Deadline lamaran")
        DateField(vm.deadlineDate, { vm.deadlineDate = it }, placeholder = "Tidak ada", clearable = true)
        TodayTextField(vm.notes, { vm.notes = it }, "Catatan", singleLine = false)
    }
}

// ------------------------------------------------------------------ 05 · Jadwalkan interview

@Composable
fun ScheduleEventRoute(
    applicationId: String,
    eventId: String?,
    onBack: () -> Unit,
    onSaved: (java.time.LocalDate) -> Unit,
) {
    val context = LocalContext.current
    val c = context.appContainer
    val vm: ScheduleEventViewModel = viewModel(factory = vmFactory { ScheduleEventViewModel(c.careerRepository, applicationId, eventId, c.clock) { c.prefs.settingsOnce().interviewReminder } })
    var promotion by rememberSaveable { mutableStateOf<ApplicationStatus?>(null) }
    var deleteDialog by remember { mutableStateOf(false) }
    val app: JobApplicationEntity? = vm.application

    // Izin notifikasi diminta saat pertama menyimpan pengingat, bukan saat aplikasi dibuka.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun save() {
        if (vm.reminder != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canPostNotifications(context)) {
            askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.save { _, promote -> if (promote != null) promotion = promote else onSaved(vm.date) }
    }

    val screenTitle = if (vm.isEdit) "Ubah agenda" else "Jadwalkan agenda"
    if (!vm.loaded) {
        LoadingDetail(screenTitle, onBack)
        return
    }
    val back = rememberBackGuard(vm.dirty, onBack)

    DetailScaffold(
        title = screenTitle,
        onBack = back,
        actions = {
            if (vm.isEdit) TopBarAction(R.drawable.ic_delete, "Hapus agenda", tint = TodayColors.Danger) { deleteDialog = true }
        },
        bottomBar = { PrimaryButton("Simpan ke kalender", loading = vm.saving) { save() } },
    ) {
        if (app != null) Text(app.company.uppercase(), style = TodayType.Eyebrow, color = TodayColors.Brand)

        FieldLabel("Jenis")
        ChoiceChips(JobEventType.entries.map { it to it.label() }, vm.type, { vm.type = it })
        TodayTextField(vm.title, { vm.title = it }, "Judul", placeholder = CareerRepository.defaultTitle(vm.type))

        FieldLabel("Tanggal")
        DateField(vm.date, { it?.let { d -> vm.date = d } })
        FieldLabel("Waktu")
        TimeField(vm.time, { it?.let { t -> vm.time = t } })
        if (vm.isInPast) Text("Waktu ini sudah lewat — pengingat tidak akan dikirim.", style = TodayType.Meta, color = TodayColors.Danger)
        FieldLabel("Durasi")
        ChoiceChips(DurationOptions, vm.duration, { vm.duration = it })
        FieldLabel("Tempat")
        ChoiceChips(listOf(EventMode.ONLINE to "Online", EventMode.ONSITE to "Onsite"), vm.mode, { vm.mode = it })
        TodayTextField(
            vm.link, { vm.link = it },
            if (vm.mode == EventMode.ONLINE) "Link meeting" else "Alamat",
            capitalization = if (vm.mode == EventMode.ONLINE) KeyboardCapitalization.None else KeyboardCapitalization.Words,
            keyboardType = if (vm.mode == EventMode.ONLINE) KeyboardType.Uri else KeyboardType.Text,
            imeAction = ImeAction.Done,
        )
        FieldLabel("Pengingat")
        ChoiceChips(ReminderOptions, vm.reminder, { vm.reminder = it })

        Text("Jadwal akan muncul di Kalender dengan warna Karier.", style = TodayType.Meta, color = TodayColors.Muted)
    }

    promotion?.let { target ->
        ConfirmDialog(
            title = "Naikkan tahap ke ${target.label()}?",
            message = "${app?.company ?: "Lamaran ini"} masih di tahap ${app?.status?.label()?.lowercase()}.",
            confirmLabel = "Ya, naikkan",
            dismissLabel = "Biarkan",
            onConfirm = { promotion = null; vm.promote(target) { onSaved(vm.date) } },
            onDismiss = { promotion = null; onSaved(vm.date) },
        )
    }
    if (deleteDialog) {
        ConfirmDialog(
            "Hapus agenda?", "Agenda dan pengingatnya akan dihapus.", "Hapus",
            onConfirm = { deleteDialog = false; vm.delete(onBack) }, onDismiss = { deleteDialog = false }, destructive = true,
        )
    }
}
