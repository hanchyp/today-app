package com.farhan.today.ui.settings

import android.Manifest
import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.farhan.today.BuildConfig
import com.farhan.today.R
import com.farhan.today.appContainer
import com.farhan.today.data.backup.BackupException
import com.farhan.today.data.backup.BackupInfo
import com.farhan.today.data.backup.DataManager
import com.farhan.today.data.db.ExerciseEntity
import com.farhan.today.data.db.MuscleGroup
import com.farhan.today.data.prefs.AppModule
import com.farhan.today.data.prefs.Settings
import com.farhan.today.domain.formatLongDate
import com.farhan.today.domain.formatTime
import com.farhan.today.reminder.BackupReminderWorker
import com.farhan.today.reminder.DailySummaryWorker
import com.farhan.today.reminder.canPostNotifications
import com.farhan.today.ui.components.ChoiceChips
import com.farhan.today.ui.components.ConfirmDialog
import com.farhan.today.ui.components.DetailTopBar
import com.farhan.today.ui.components.FieldLabel
import com.farhan.today.ui.components.ListCard
import com.farhan.today.ui.components.Pill
import com.farhan.today.ui.components.PrimaryButton
import com.farhan.today.ui.components.SecondaryButton
import com.farhan.today.ui.components.SectionTitle
import com.farhan.today.ui.components.TimeField
import com.farhan.today.ui.components.TodayTextField
import com.farhan.today.ui.theme.TodayColors
import com.farhan.today.ui.theme.TodayShapes
import com.farhan.today.ui.theme.TodayType
import com.farhan.today.ui.workout.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// ================================================================== ViewModel

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.appContainer
    val settings: StateFlow<Settings?> = c.prefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val exercises: StateFlow<List<ExerciseEntity>> = c.workoutRepository.catalog().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setName(n: String) = viewModelScope.launch { c.prefs.setUserName(n) }
    fun setModules(m: Set<AppModule>) = viewModelScope.launch { if (m.isNotEmpty()) c.prefs.setModules(m) }
    fun setDailySummary(on: Boolean, time: LocalTime) = viewModelScope.launch {
        c.prefs.setDailySummary(on, time)
        DailySummaryWorker.schedule(getApplication(), on, time)
    }
    fun setInterviewReminder(m: Int?) = viewModelScope.launch { c.prefs.setInterviewReminder(m) }
    fun setBillReminderDays(d: Int) = viewModelScope.launch { c.prefs.setBillReminderDays(d) }
    fun setWorkoutReminders(on: Boolean) = viewModelScope.launch { c.prefs.setWorkoutReminders(on); c.workoutRepository.syncReminders() }
    fun setBandRestAlerts(on: Boolean) = viewModelScope.launch { c.prefs.setBandRestAlerts(on) }
    fun setWeekStartsSunday(on: Boolean) = viewModelScope.launch { c.prefs.setWeekStartsSunday(on) }
    fun setBackupReminder(on: Boolean) = viewModelScope.launch {
        c.prefs.setBackupReminder(on)
        BackupReminderWorker.schedule(getApplication(), on)
    }

    fun renameExercise(id: String, name: String, group: MuscleGroup) = viewModelScope.launch { c.workoutRepository.renameExercise(id, name, group) }
    fun addExercise(name: String, group: MuscleGroup) = viewModelScope.launch { c.workoutRepository.createExercise(name, group) }

    /** Tulis backup ke URI pilihan pengguna (SAF). */
    fun backup(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val msg = runCatching {
            val json = c.dataManager.export()
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
            }
            c.prefs.markBackedUp(c.clock.millis())
            "Backup tersimpan."
        }.getOrElse { "Backup gagal: ${it.message}" }
        onResult(msg)
    }

    suspend fun readBackup(uri: Uri): Pair<String, BackupInfo> = withContext(Dispatchers.IO) {
        val json = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        json to c.dataManager.inspect(json)
    }

    fun restore(json: String, onResult: (String) -> Unit) = viewModelScope.launch {
        val msg = runCatching {
            val info = c.dataManager.import(json)
            c.workoutRepository.planSessions()
            "Data dipulihkan: ${info.rows} baris."
        }.getOrElse { if (it is BackupException) it.message!! else "Pemulihan gagal: ${it.message}" }
        onResult(msg)
    }

    fun wipe(onDone: () -> Unit) = viewModelScope.launch {
        c.restTimer.cancel()
        c.dataManager.wipe()
        onDone()
    }

    /** Onboarding selesai. */
    fun finishOnboarding(name: String, modules: Set<AppModule>, startEmpty: Boolean) = viewModelScope.launch {
        c.prefs.setUserName(name.ifBlank { "kamu" })
        c.prefs.setModules(modules.ifEmpty { AppModule.entries.toSet() })
        if (startEmpty) c.dataManager.wipe()
        c.prefs.setOnboarded(true)
    }
}

@Composable
private fun settingsVm(): SettingsViewModel = viewModel()

@Composable
private fun ScrollColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(TodayShapes.Card).background(TodayColors.Surface)
            .border(BorderStroke(1.dp, TodayColors.NavStroke), TodayShapes.Card)
            .clickable(role = Role.Switch) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = TodayType.Label, color = TodayColors.Ink)
            if (subtitle != null) Text(subtitle, style = TodayType.Meta, color = TodayColors.Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = TodayColors.Brand))
    }
}

@Composable
private fun NavRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    ListCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = TodayType.Label, color = TodayColors.Ink)
                if (subtitle != null) Text(subtitle, style = TodayType.Meta, color = TodayColors.Muted)
            }
            Text("›", style = TodayType.CardTitle, color = TodayColors.Muted)
        }
    }
}

@Composable
private fun ModulePicker(selected: Set<AppModule>, onChange: (Set<AppModule>) -> Unit) {
    AppModule.entries.forEach { m ->
        SwitchRow(m.label, null, m in selected) { on ->
            val next = if (on) selected + m else selected - m
            if (next.isNotEmpty()) onChange(next)
        }
    }
}

// ================================================================== Onboarding

@Composable
fun OnboardingScreen() {
    val vm = settingsVm()
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var modules by rememberSaveable { mutableStateOf(AppModule.entries.toSet()) }
    var startEmpty by rememberSaveable { mutableStateOf(false) }
    var notifGranted by remember { mutableStateOf(canPostNotifications(context)) }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifGranted = it }

    Column(
        Modifier.fillMaxSize().background(TodayColors.Background).verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Spacer(Modifier.weight(1f).height(6.dp).clip(TodayShapes.Chip).background(if (i <= page) TodayColors.Brand else TodayColors.NavStroke))
            }
        }
        val mascot = listOf(R.drawable.mascot_wave, R.drawable.mascot_lift, R.drawable.mascot_coin)[page]
        Image(painterResource(mascot), "Maskot kapibara Today", Modifier.align(Alignment.CenterHorizontally).size(width = 220.dp, height = 176.dp))

        when (page) {
            0 -> {
                Text("Halo! Aku Capy.", style = TodayType.Display, color = TodayColors.Headline)
                Text("Today membantumu melihat latihan, lamaran kerja, uang, dan tugas hari ini dalam satu layar.", style = TodayType.Body, color = TodayColors.Muted)
                TodayTextField(name, { name = it }, "Nama panggilan", placeholder = "Farhan", capitalization = KeyboardCapitalization.Words)
                PrimaryButton("Lanjut") { page = 1 }
            }
            1 -> {
                Text("Mau pakai apa saja?", style = TodayType.Display, color = TodayColors.Headline)
                Text("Modul yang tidak dipilih disembunyikan dari navigasi. Bisa diubah lagi di Pengaturan.", style = TodayType.Body, color = TodayColors.Muted)
                ModulePicker(modules) { modules = it }
                PrimaryButton("Lanjut") { page = 2 }
                TextButton(onClick = { page = 0 }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Kembali") }
            }
            else -> {
                Text("Hampir siap", style = TodayType.Display, color = TodayColors.Headline)
                Text("Izinkan notifikasi agar pengingat interview, tagihan, dan timer istirahat bisa muncul.", style = TodayType.Body, color = TodayColors.Muted)
                if (notifGranted) {
                    Text("✓ Notifikasi diizinkan", style = TodayType.Label, color = TodayColors.Income)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    SecondaryButton("Izinkan notifikasi") { askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
                FieldLabel("Mulai dengan")
                ChoiceChips(listOf(false to "Data contoh", true to "Kosong"), startEmpty, { startEmpty = it })
                Text(
                    if (startEmpty) "Semua data yang ada sekarang dihapus. Katalog gerakan dan kategori keuangan tetap disediakan."
                    else "Data contoh (Push Day, GDP Labs, Skripsi, anggaran makan) bisa kamu ubah atau hapus kapan saja.",
                    style = TodayType.Meta, color = if (startEmpty) TodayColors.Danger else TodayColors.Muted,
                )
                PrimaryButton("Mulai pakai Today") { vm.finishOnboarding(name, modules, startEmpty) }
                TextButton(onClick = { page = 1 }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Kembali") }
            }
        }
    }
}

// ================================================================== Pengaturan

@Composable
fun SettingsRoute(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val vm = settingsVm()
    val s = vm.settings.collectAsStateWithLifecycle().value ?: return
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<Pair<String, BackupInfo>?>(null) }
    var wipeDialog by remember { mutableStateOf(false) }
    var name by rememberSaveable(s.userName) { mutableStateOf(s.userName) }
    val today = LocalDate.now()

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.backup(it) { m -> message = m } }
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                runCatching { vm.readBackup(it) }
                    .onSuccess { r -> pendingRestore = r }
                    .onFailure { e -> message = if (e is BackupException) e.message else "File tidak bisa dibaca: ${e.message}" }
            }
        }
    }

    ScrollColumn {
        DetailTopBar(onBack)
        Text("PENGATURAN", style = TodayType.Eyebrow, color = TodayColors.Brand)
        Text("Pengaturan", style = TodayType.Display, color = TodayColors.Headline)

        SectionTitle("Profil")
        TodayTextField(name, { name = it }, "Nama panggilan", capitalization = KeyboardCapitalization.Words)
        if (name.trim() != s.userName && name.isNotBlank()) Pill("Simpan nama", selected = true, onClick = { vm.setName(name) })

        SectionTitle("Modul aktif")
        ModulePicker(s.modules, vm::setModules)

        SectionTitle("Notifikasi")
        SwitchRow("Ringkasan pagi", "Agenda hari ini dan sisa anggaran, jam ${formatTime(s.dailySummaryTime)}", s.dailySummary) { vm.setDailySummary(it, s.dailySummaryTime) }
        if (s.dailySummary) TimeField(s.dailySummaryTime, { it?.let { t -> vm.setDailySummary(true, t) } })
        FieldLabel("Pengingat default interview & coding test")
        ChoiceChips(listOf<Pair<Int?, String>>(15 to "15 mnt", 30 to "30 mnt", 60 to "1 jam", 1440 to "1 hari", null to "Tidak ada"), s.interviewReminder, vm::setInterviewReminder)
        FieldLabel("Pengingat default tagihan")
        ChoiceChips(listOf(0 to "Hari H", 1 to "H-1", 3 to "H-3", 7 to "H-7"), s.billReminderDays, vm::setBillReminderDays)
        SwitchRow("Pengingat latihan", "30 menit sebelum jadwal", s.workoutReminders, vm::setWorkoutReminders)
        SwitchRow(
            "Timer istirahat ke smartband",
            "Getar saat mulai, 10 detik terakhir, dan selesai. Aktifkan Today di notifikasi Huawei Health",
            s.bandRestAlerts, vm::setBandRestAlerts,
        )

        SectionTitle("Tampilan")
        FieldLabel("Awal minggu di Kalender & Today")
        ChoiceChips(listOf(false to "Senin", true to "Minggu"), s.weekStartsSunday, vm::setWeekStartsSunday)
        Text("Anggaran mingguan tetap dihitung Senin–Minggu.", style = TodayType.Meta, color = TodayColors.Muted)

        SectionTitle("Data")
        NavRow("Kelola kategori keuangan") { onNavigate(SettingsRoutes.CATEGORIES) }
        NavRow("Kelola gerakan latihan", "${vm.exercises.collectAsStateWithLifecycle().value.size} gerakan") { onNavigate(SettingsRoutes.EXERCISES) }
        PrimaryButton("Backup ke file") { createDoc.launch(DataManager.fileName(today)) }
        Text(
            s.lastBackupAt?.let { "Backup terakhir: ${formatLongDate(Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate())}" }
                ?: "Belum pernah backup.",
            style = TodayType.Meta, color = TodayColors.Muted,
        )
        SecondaryButton("Pulihkan dari backup") { openDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        SwitchRow("Ingatkan backup mingguan", "Hanya jika belum backup 7 hari terakhir", s.backupReminder, vm::setBackupReminder)
        TextButton(onClick = { wipeDialog = true }) { Text("Hapus semua data…", color = TodayColors.Danger, style = TodayType.Label) }

        SectionTitle("Tentang")
        Text("Today ${BuildConfig.VERSION_NAME}", style = TodayType.Label, color = TodayColors.Ink)
        Text(
            "Semua data tersimpan di HP ini saja (offline). Font Inter berlisensi SIL Open Font License. Maskot kapibara dibuat dengan bantuan AI.",
            style = TodayType.Meta, color = TodayColors.Muted,
        )
        Spacer(Modifier.height(24.dp))
    }

    message?.let { m ->
        AlertDialog(onDismissRequest = { message = null }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }, text = { Text(m, style = TodayType.Body) })
    }
    pendingRestore?.let { (json, info) ->
        ConfirmDialog(
            title = "Timpa semua data?",
            message = "Backup tanggal ${formatLongDate(Instant.ofEpochMilli(info.exportedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())} berisi ${info.rows} baris " +
                "(${info.tables["money_transaction"]} transaksi, ${info.tables["task"]} tugas, ${info.tables["job_application"]} lamaran). " +
                "Data yang ada sekarang akan diganti seluruhnya.",
            confirmLabel = "Timpa & pulihkan",
            onConfirm = { pendingRestore = null; vm.restore(json) { message = it } },
            onDismiss = { pendingRestore = null },
            destructive = true,
        )
    }
    if (wipeDialog) WipeDialog(onDismiss = { wipeDialog = false }) { wipeDialog = false; vm.wipe { message = "Semua data dihapus." } }
}

/** Konfirmasi dengan mengetik "HAPUS". */
@Composable
private fun WipeDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus semua data?", style = TodayType.CardTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Latihan, lamaran, transaksi, tagihan, tugas, dan pengingat dihapus permanen. Buat backup dulu kalau ragu. Ketik HAPUS untuk melanjutkan.", style = TodayType.Body)
                TodayTextField(typed, { typed = it }, "Ketik HAPUS", capitalization = KeyboardCapitalization.Characters)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == "HAPUS") { Text("Hapus permanen", color = if (typed.trim() == "HAPUS") TodayColors.Danger else TodayColors.Muted) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

// ================================================================== Kelola gerakan

@Composable
fun ExercisesRoute(onBack: () -> Unit) {
    val vm = settingsVm()
    val list by vm.exercises.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ExerciseEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    var query by rememberSaveable { mutableStateOf("") }
    val catalog = LocalContext.current.appContainer.exerciseCatalog
    val filtered = list.filter { it.name.contains(query.trim(), ignoreCase = true) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(TodayColors.Background).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            DetailTopBar(onBack) { Pill("+ Gerakan", selected = true, onClick = { creating = true }) }
            Text("Exercise Library", style = TodayType.Display, color = TodayColors.Headline)
            TodayTextField(query, { query = it }, "Search exercises")
        }
        items(filtered, key = { it.id }) { exercise ->
            val details = catalog[exercise.id]
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(TodayShapes.Chip).background(TodayColors.Surface)
                    .clickable(role = Role.Button) { editing = exercise }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(exercise.name, Modifier.weight(1f), style = TodayType.Label, color = TodayColors.Ink)
                Text(details?.equipment ?: exercise.muscleGroup.label(), style = TodayType.Meta, color = TodayColors.Muted)
            }
        }
    }
    val catalogDetails = editing?.let { LocalContext.current.appContainer.exerciseCatalog[it.id] }
    if (catalogDetails != null) {
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(catalogDetails.exercise.name) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseMedia(catalogDetails.animation)
                    Text("Equipment: " + catalogDetails.equipment)
                    catalogDetails.steps.forEachIndexed { index, step -> Text("${index + 1}. $step") }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Tutup") } },
        )
    } else if (editing != null || creating) {
        val target = editing
        var name by remember(target) { mutableStateOf(target?.name.orEmpty()) }
        var group by remember(target) { mutableStateOf(target?.muscleGroup ?: MuscleGroup.FULL_BODY) }
        AlertDialog(
            onDismissRequest = { editing = null; creating = false },
            title = { Text(if (target == null) "Gerakan baru" else "Ubah gerakan", style = TodayType.CardTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TodayTextField(name, { name = it }, "Nama gerakan")
                    ChoiceChips(MuscleGroup.entries.map { it to it.label() }, group, { group = it })
                    if (target != null) Text("Nama baru juga tampil di riwayat lama.", style = TodayType.Meta, color = TodayColors.Muted, textAlign = TextAlign.Start)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        if (target == null) vm.addExercise(name, group) else vm.renameExercise(target.id, name, group)
                        editing = null; creating = false
                    }
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { editing = null; creating = false }) { Text("Batal") } },
        )
    }
}

object SettingsRoutes {
    const val EXERCISES = "settings/exercises"
    const val CATEGORIES = "finance/categories"
}
