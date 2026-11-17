package com.farhan.today.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.farhan.today.domain.formatLongDate
import com.farhan.today.domain.formatTime
import com.farhan.today.ui.theme.TodayColors
import com.farhan.today.ui.theme.TodayShapes
import com.farhan.today.ui.theme.TodayType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Factory ViewModel satu baris untuk DI manual. */
inline fun <reified VM : ViewModel> vmFactory(crossinline create: () -> VM) =
    viewModelFactory { initializer { create() } }

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 4.dp), style = TodayType.Label, color = TodayColors.Muted)
}

@Composable
fun TodayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    error: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder) }) else null,
        leadingIcon = leadingIcon,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        shape = TodayShapes.Chip,
        textStyle = TodayType.Body,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = TodayColors.Surface,
            unfocusedContainerColor = TodayColors.Surface,
            unfocusedBorderColor = TodayColors.NavStroke,
            focusedBorderColor = TodayColors.Brand,
        ),
    )
}

@Composable
fun RupiahTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String? = null,
) {
    TodayTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it in '0'..'9' }.take(12)) },
        label = label,
        error = error,
        capitalization = KeyboardCapitalization.None,
        keyboardType = KeyboardType.Number,
        visualTransformation = RupiahVisualTransformation,
    )
}

private val RupiahVisualTransformation = VisualTransformation { text ->
    val cursorPositions = IntArray(text.length + 1)
    val formatted = buildString {
        append("Rp")
        cursorPositions[0] = length
        text.text.forEachIndexed { index, digit ->
            if (index > 0 && (text.length - index) % 3 == 0) append('.')
            append(digit)
            cursorPositions[index + 1] = length
        }
    }
    TransformedText(AnnotatedString(formatted), object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int = cursorPositions[offset]

        override fun transformedToOriginal(offset: Int): Int =
            cursorPositions.indexOfLast { it <= offset }.coerceAtLeast(0)
    })
}

/** Baris kontrol yang terlihat bisa diketuk: nilai + "Ubah ›". */
@Composable
fun PickerRow(value: String, modifier: Modifier = Modifier, placeholder: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(TodayShapes.Chip)
            .background(TodayColors.Surface)
            .border(BorderStroke(1.dp, TodayColors.NavStroke), TodayShapes.Chip)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value, Modifier.weight(1f),
            style = TodayType.Label.copy(fontSize = TodayType.Body.fontSize),
            color = if (placeholder) TodayColors.Muted else TodayColors.Ink,
        )
        Text("Ubah ›", style = TodayType.Label, color = TodayColors.Brand)
    }
}

/** Pilihan tunggal sebagai chip yang bisa digulir horizontal. */
@Composable
fun <T> ChoiceChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            Pill(label, selected = value == selected, onClick = { onSelect(value) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate?,
    onChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Pilih tanggal",
    clearable: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    PickerRow(date?.let(::formatLongDate) ?: placeholder, modifier, placeholder = date == null) { open = true }
    if (open) {
        // DatePicker M3 bekerja dalam milidetik UTC.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("Pilih") }
            },
            dismissButton = {
                Row {
                    if (clearable && date != null) TextButton(onClick = { onChange(null); open = false }) { Text("Hapus") }
                    TextButton(onClick = { open = false }) { Text("Batal") }
                }
            },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    time: LocalTime?,
    onChange: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Pilih jam",
    clearable: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    PickerRow(time?.let { formatTime(it) + " WIB" } ?: placeholder, modifier, placeholder = time == null) { open = true }
    if (open) {
        val initial = time ?: LocalTime.of(10, 0)
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { onChange(LocalTime.of(state.hour, state.minute)); open = false }) { Text("Pilih") } },
            dismissButton = {
                Row {
                    if (clearable && time != null) TextButton(onClick = { onChange(null); open = false }) { Text("Hapus") }
                    TextButton(onClick = { open = false }) { Text("Batal") }
                }
            },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Batal",
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = TodayType.CardTitle) },
        text = { Text(message, style = TodayType.Body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) TodayColors.Danger else TodayColors.Brand, style = TodayType.Label)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

/** Header layar turunan: tombol kembali + aksi kanan opsional. */
@Composable
fun DetailTopBar(onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BackPill(onBack)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

@Composable
fun FormColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

val SpacerW = Modifier.width(8.dp)
