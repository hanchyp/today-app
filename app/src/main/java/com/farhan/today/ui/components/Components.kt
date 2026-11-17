package com.farhan.today.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farhan.today.R
import com.farhan.today.domain.formatRupiah
import com.farhan.today.domain.formatSignedRupiah
import com.farhan.today.ui.theme.TodayColors
import com.farhan.today.ui.theme.TodayShapes
import com.farhan.today.ui.theme.TodayType

private fun Modifier.clickableIf(onClick: (() -> Unit)?, role: Role = Role.Button) =
    if (onClick != null) clickable(role = role, onClick = onClick) else this

/** Eyebrow + judul besar + subjudul, dipakai di semua layar modul. */
@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow.uppercase(), style = TodayType.Eyebrow, color = TodayColors.Brand)
            Spacer(Modifier.height(4.dp))
            Text(title, style = TodayType.Display, color = TodayColors.Headline)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = TodayType.Body, color = TodayColors.Muted)
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = TodayType.Section, color = TodayColors.Ink, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing.uppercase(), style = TodayType.Eyebrow, color = TodayColors.Muted)
    }
}

/** Kartu berwarna modul: label kecil, judul, keterangan. Teks panjang dipotong dengan elipsis. */
@Composable
fun AgendaCard(
    label: String,
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = TodayShapes.Card,
    titleStyle: TextStyle = TodayType.CardTitle,
    done: Boolean = false,
    @DrawableRes trailingIcon: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .clip(shape)
            .background(color)
            .clickableIf(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            Text(
                (if (done) "Selesai ✓  ·  " else "") + label.uppercase(),
                style = TodayType.Eyebrow, color = TodayColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(title, style = titleStyle, color = TodayColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = TodayType.Meta, color = TodayColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailingIcon != null) {
            Icon(
                painterResource(trailingIcon), null, tint = TodayColors.Ink,
                modifier = Modifier.align(Alignment.BottomEnd).size(16.dp),
            )
        }
    }
}

/** Kartu utama modul, radius 28, dengan maskot opsional di kanan atas. */
@Composable
fun HeroCard(
    color: Color,
    modifier: Modifier = Modifier,
    @DrawableRes mascot: Int? = null,
    mascotDescription: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(TodayShapes.Hero)
            .background(color)
            .clickableIf(onClick)
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), content = content)
        if (mascot != null) {
            Image(painterResource(mascot), mascotDescription, Modifier.size(width = 80.dp, height = 64.dp))
        }
    }
}

/** Kartu netral untuk daftar panjang: riwayat, transaksi, tugas. */
@Composable
fun ListCard(
    modifier: Modifier = Modifier,
    background: Color = TodayColors.Surface,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(TodayShapes.Card)
            .background(background)
            .border(BorderStroke(1.dp, TodayColors.NavStroke), TodayShapes.Card)
            .clickableIf(onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        content = content,
    )
}

/** Chip / tombol kecil dengan tinggi sentuh minimal 44dp. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    background: Color = if (selected) TodayColors.Ink else TodayColors.Chip,
    contentColor: Color = if (selected) Color.White else TodayColors.Ink,
    @DrawableRes icon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(TodayShapes.Chip)
            .background(background)
            .clickableIf(onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(painterResource(icon), null, tint = contentColor, modifier = Modifier.size(18.dp))
        if (text.isNotEmpty()) Text(text, style = TodayType.Label, color = contentColor, maxLines = 1)
        if (trailingIcon != null) Icon(painterResource(trailingIcon), null, tint = contentColor, modifier = Modifier.size(18.dp))
    }
}

/** Badge status: selalu ada teks (dan ikon opsional) agar makna tidak bergantung warna. */
@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = TodayColors.Chip,
    contentColor: Color = TodayColors.Ink,
    @DrawableRes icon: Int? = null,
) {
    Row(
        modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(painterResource(icon), null, tint = contentColor, modifier = Modifier.size(14.dp))
        Text(text, style = TodayType.Eyebrow, color = contentColor, maxLines = 1)
    }
}

/**
 * Tombol utama full-width. [loading] menampilkan spinner dan menolak ketukan,
 * sehingga simpan tidak terkirim dua kali; [enabled] = false meredupkan tombol.
 */
@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = TodayColors.Brand,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    loading: Boolean = false,
    @DrawableRes icon: Int? = null,
    onClick: () -> Unit,
) {
    val active = enabled && !loading
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(TodayShapes.Button)
            .background(if (enabled) background else background.copy(alpha = 0.38f))
            .clickable(enabled = active, role = Role.Button, onClick = onClick)
            .semantics { if (loading) stateDescription = "Sedang memproses" }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) contentColor else contentColor.copy(alpha = 0.6f)
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = tint, strokeWidth = 2.dp)
        else if (icon != null) Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, style = TodayType.Label.copy(fontSize = 15.sp), color = tint, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = PrimaryButton(text, modifier, background = TodayColors.Chip, contentColor = TodayColors.Ink, enabled = enabled, icon = icon, onClick = onClick)

@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    track: Color = Color.White,
    fill: Color = TodayColors.Ink,
    height: androidx.compose.ui.unit.Dp = 8.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(fill)
        )
    }
}

/** Nominal uang. signed = tampilkan +/−; pemasukan berwarna hijau tua tapi tetap bertanda. */
@Composable
fun MoneyText(
    amount: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = TodayType.Label,
    signed: Boolean = false,
    isIncome: Boolean = false,
    color: Color = if (signed && isIncome) TodayColors.Income else TodayColors.Ink,
) {
    Text(
        if (signed) formatSignedRupiah(amount, isIncome) else formatRupiah(amount),
        modifier = modifier, style = style.copy(fontFeatureSettings = "tnum"), color = color, textAlign = TextAlign.End, maxLines = 1,
    )
}

/** Keadaan kosong dengan maskot besar — dipakai di agenda kosong, riwayat kosong, dst. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    @DrawableRes mascot: Int = R.drawable.mascot_wave,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(TodayShapes.Card)
            .background(TodayColors.BrandTint)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(painterResource(mascot), null, modifier = Modifier.size(width = 150.dp, height = 120.dp))
        Text(title, style = TodayType.CardTitle, color = TodayColors.Ink, textAlign = TextAlign.Center)
        Text(message, style = TodayType.Body, color = TodayColors.Muted, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/** Titik-titik kecil berwarna modul. */
@Composable
fun AgendaDots(colors: List<Color>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        colors.take(4).forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(it)) }
    }
}


val ArrowIcon = R.drawable.ic_arrow_open
