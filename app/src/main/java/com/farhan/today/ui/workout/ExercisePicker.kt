package com.farhan.today.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.farhan.today.R
import com.farhan.today.data.db.Equipment
import com.farhan.today.data.db.ExerciseEntity
import com.farhan.today.data.db.MuscleGroup
import com.farhan.today.ui.components.ChoiceChips
import com.farhan.today.ui.components.FieldLabel
import com.farhan.today.ui.components.Pill
import com.farhan.today.ui.components.TodayTextField
import com.farhan.today.ui.settings.ExerciseImage
import com.farhan.today.ui.theme.TodayColors
import com.farhan.today.ui.theme.TodayType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExercisePicker(
    catalog: List<ExerciseEntity>,
    loadRecent: suspend () -> List<ExerciseEntity>,
    animationFor: (ExerciseEntity) -> String?,
    onDismiss: () -> Unit,
    onPick: (ExerciseEntity) -> Unit,
    onCreate: (String, MuscleGroup, Equipment) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<MuscleGroup?>(null) }
    var equipment by rememberSaveable { mutableStateOf<Equipment?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var info by remember { mutableStateOf<ExerciseEntity?>(null) }
    var recent by remember { mutableStateOf(emptyList<ExerciseEntity>()) }
    LaunchedEffect(Unit) { recent = loadRecent() }

    val search = query.trim()
    val matchingRecent = remember(recent, search, group, equipment) {
        recent.filter { it.matches(search, group, equipment) }
    }
    val matchingCatalog = remember(catalog, recent, search, group, equipment) {
        val recentIds = recent.mapTo(mutableSetOf()) { it.id }
        catalog.filter { it.id !in recentIds && it.matches(search, group, equipment) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(search, group, equipment) { listState.scrollToItem(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = TodayColors.Background,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.95f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) {
                    Text("Batal", color = TodayColors.Brand)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Tambah Gerakan", style = TodayType.Section, color = TodayColors.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { creating = true }) {
                    Text("Buat", color = TodayColors.Brand)
                }
            }
            HorizontalDivider(color = TodayColors.NavStroke)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TodayTextField(query, { query = it }, "Cari gerakan",
                    capitalization = KeyboardCapitalization.None,
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, tint = TodayColors.Muted) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickerFilter(
                        "Semua alat", equipment, Equipment.entries, { it.pickerLabel() },
                        Modifier.weight(1f), { equipment = it },
                    )
                    PickerFilter(
                        "Semua otot", group, MuscleGroup.entries, { it.label() },
                        Modifier.weight(1f), { group = it },
                    )
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
                if (matchingRecent.isNotEmpty()) {
                    item { ExerciseListHeading("Gerakan terbaru") }
                    items(matchingRecent, key = { it.id }) { exercise ->
                        PickerExerciseRow(exercise, animationFor(exercise), { onPick(exercise) }, { info = exercise })
                    }
                }
                if (matchingCatalog.isNotEmpty()) {
                    item { ExerciseListHeading(if (search.isEmpty() && group == null && equipment == null) "Semua gerakan" else "Hasil pencarian") }
                    items(matchingCatalog, key = { it.id }) { exercise ->
                        PickerExerciseRow(exercise, animationFor(exercise), { onPick(exercise) }, { info = exercise })
                    }
                }
                if (matchingRecent.isEmpty() && matchingCatalog.isEmpty()) {
                    item { ExerciseListHeading("Tidak ada gerakan yang cocok.") }
                }
            }
        }
    }

    info?.let { exercise ->
        ExerciseGuideDialog(
            exercise.name, animationFor(exercise),
            subtitle = "${exercise.muscleGroup.label()} · ${exercise.equipment.pickerLabel()}",
        ) { info = null }
    }
    if (creating) {
        var name by rememberSaveable { mutableStateOf(search) }
        var muscle by rememberSaveable { mutableStateOf(group ?: MuscleGroup.FULL_BODY) }
        var gear by rememberSaveable { mutableStateOf(equipment ?: Equipment.OTHER) }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Gerakan baru", style = TodayType.CardTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TodayTextField(name, { name = it }, "Nama gerakan")
                    FieldLabel("Otot")
                    ChoiceChips(MuscleGroup.entries.map { it to it.label() }, muscle, { muscle = it })
                    FieldLabel("Alat")
                    ChoiceChips(Equipment.entries.map { it to it.pickerLabel() }, gear, { gear = it })
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    creating = false
                    onCreate(name, muscle, gear)
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Batal") } },
        )
    }
}

private fun ExerciseEntity.matches(query: String, group: MuscleGroup?, equipment: Equipment?): Boolean =
    (group == null || muscleGroup == group) && (equipment == null || this.equipment == equipment) &&
        name.contains(query, ignoreCase = true)

private fun Equipment.pickerLabel(): String = when (this) {
    Equipment.BARBELL -> "Barbel"
    Equipment.DUMBBELL -> "Dumbbell"
    Equipment.MACHINE -> "Mesin"
    Equipment.BODYWEIGHT -> "Berat badan"
    Equipment.CABLE -> "Kabel"
    Equipment.OTHER -> "Lainnya"
}

@Composable
private fun <T : Any> PickerFilter(
    all: String,
    selected: T?,
    options: List<T>,
    label: (T) -> String,
    modifier: Modifier,
    onSelect: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Pill(
            (selected?.let(label) ?: all) + " ▾",
            Modifier.fillMaxWidth(),
            background = if (selected == null) TodayColors.Chip else TodayColors.BrandTint,
            contentColor = if (selected == null) TodayColors.Ink else TodayColors.Brand,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(all) }, onClick = { expanded = false; onSelect(null) })
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = { expanded = false; onSelect(option) })
            }
        }
    }
}

@Composable
private fun ExerciseListHeading(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = TodayType.Label, color = TodayColors.Muted)
}

@Composable
private fun PickerExerciseRow(exercise: ExerciseEntity, animation: String?, onPick: () -> Unit, onInfo: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(TodayColors.Surface), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Tambahkan gerakan", onClick = onPick)
                .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(TodayColors.Chip), contentAlignment = Alignment.Center) {
                if (animation == null) Text(exercise.name.take(1), style = TodayType.Label, color = TodayColors.Muted)
                else ExerciseImage(animation, Modifier.fillMaxSize()) {
                    Text(exercise.name.take(1), style = TodayType.Label, color = TodayColors.Muted)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(exercise.name, style = TodayType.Label, color = TodayColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(exercise.muscleGroup.label(), style = TodayType.Meta, color = TodayColors.Muted)
            }
        }
        Box(
            Modifier.padding(end = 8.dp).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onInfo)
                .semantics { contentDescription = "Info ${exercise.name}" },
            contentAlignment = Alignment.Center,
        ) {
            Text("ⓘ", style = TodayType.CardTitle, color = TodayColors.Brand)
        }
    }
    HorizontalDivider(color = TodayColors.NavStroke)
}
