package com.farhan.today.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farhan.today.R
import com.farhan.today.domain.model.AgendaSource

/** Palet "Matahari" (logo K): putih hangat sebagai dasar, kuning untuk sorotan, navy untuk aksi & teks, coral sebagai aksen. */
object TodayColors {
    val Background = Color(0xFFFDFCF8)
    val Ink = Color(0xFF171822)
    val Headline = Color(0xFF1A1A2E)
    val Muted = Color(0xFF64625B)
    /** Aksi utama (tombol, switch, eyebrow): navy, teks di atasnya putih. */
    val Brand = Color(0xFF1A1A2E)
    /** Kartu fokus & FAB: kuning logo, teks di atasnya [OnBrandHero]. */
    val BrandHero = Color(0xFFFFD23F)
    val OnBrandHero = Color(0xFF1A1A2E)
    val BrandTint = Color(0xFFFFF3C4)
    val BrandText = Color(0xFF1A1A2E)
    /** Subjudul di atas [BrandHero]. */
    val OnBrandSoft = Color(0xFF4D4523)
    val Coral = Color(0xFFFF5A36)
    val Chip = Color(0xFFF3F1EA)
    val NavStroke = Color(0xFFECE8DD)
    val Surface = Color(0xFFFFFFFF)

    // Warna modul
    val Workout = Color(0xFFC1EF7D)
    val WorkoutTint = Color(0xFFF1FFD9)
    val Career = Color(0xFFFFE094)
    val Finance = Color(0xFFF7BDE0)
    val FinanceTrack = Color(0xFFF8D5EA)
    val Tasks = Color(0xFFC6D8FF)
    val Income = Color(0xFF245830)
    val Danger = Color(0xFFB3261E)

    // Titik agenda di kalender: versi lebih pekat agar terlihat di atas putih
    val DotWorkout = Color(0xFF6FAE2A)
    val DotCareer = Color(0xFFE0A100)
    val DotFinance = Color(0xFFD64C9F)
    val DotTasks = Color(0xFF4F7BE8)
}

fun AgendaSource.cardColor(): Color = when (this) {
    AgendaSource.WORKOUT -> TodayColors.Workout
    AgendaSource.CAREER -> TodayColors.Career
    AgendaSource.FINANCE -> TodayColors.Finance
    AgendaSource.TASK -> TodayColors.Tasks
    AgendaSource.CALENDAR -> TodayColors.BrandTint
}

fun AgendaSource.dotColor(): Color = when (this) {
    AgendaSource.WORKOUT -> TodayColors.DotWorkout
    AgendaSource.CAREER -> TodayColors.DotCareer
    AgendaSource.FINANCE -> TodayColors.DotFinance
    AgendaSource.TASK -> TodayColors.DotTasks
    AgendaSource.CALENDAR -> TodayColors.Brand
}

/** Radius kartu: utama 28, agenda 22, chip 15, tombol 14 */
object TodayShapes {
    val Hero = RoundedCornerShape(28.dp)
    val Card = RoundedCornerShape(22.dp)
    val Chip = RoundedCornerShape(15.dp)
    val Button = RoundedCornerShape(14.dp)
}

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) = Font(
    R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val AppFont: FontFamily = FontFamily(inter(400), inter(500), inter(600), inter(700), inter(800))

object TodayType {
    val Display = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.7).sp)
    /** Judul layar detail/form: lebih kecil dari [Display] agar konten cepat terlihat. */
    val Title = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp)
    val Hero = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp)
    val Section = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp)
    val CardTitle = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp)
    val Number = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 21.sp, fontFeatureSettings = "tnum")
    val Body = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val Meta = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val Label = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp)
    val Eyebrow = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = 0.4.sp)
    val Tiny = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp)
}

/** Jarak tetap antarlayar, supaya konten tidak bergeser saat pindah layar. */
object Spacing {
    /** Padding kiri-kanan semua layar. */
    val ScreenH = 20.dp
    val ScreenTop = 12.dp
    /** Ruang bawah agar item terakhir tidak tertutup FAB. */
    val FabClearance = 88.dp
}

/**
 * Skema M3 lengkap. Tanpa ini, komponen bawaan (DatePicker, TimePicker, dialog, sheet, snackbar)
 * memakai warna ungu bawaan Material 3 untuk slot yang tidak diisi.
 */
private val TodayColorScheme = lightColorScheme(
    primary = TodayColors.Brand,
    onPrimary = Color.White,
    primaryContainer = TodayColors.BrandTint,
    onPrimaryContainer = TodayColors.BrandText,
    inversePrimary = TodayColors.BrandHero,
    secondary = TodayColors.Ink,
    onSecondary = Color.White,
    secondaryContainer = TodayColors.BrandHero,
    onSecondaryContainer = TodayColors.OnBrandHero,
    tertiary = TodayColors.Coral,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3DB),
    onTertiaryContainer = Color(0xFF5C1A0B),
    background = TodayColors.Background,
    onBackground = TodayColors.Ink,
    surface = TodayColors.Surface,
    onSurface = TodayColors.Ink,
    surfaceVariant = TodayColors.Chip,
    onSurfaceVariant = TodayColors.Muted,
    surfaceTint = Color.Transparent,
    inverseSurface = TodayColors.Ink,
    inverseOnSurface = Color.White,
    error = TodayColors.Danger,
    onError = Color.White,
    errorContainer = Color(0xFFFFE0DD),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFFCFCABC),
    outlineVariant = TodayColors.NavStroke,
    scrim = Color(0xFF000000),
    surfaceBright = TodayColors.Surface,
    surfaceDim = Color(0xFFE8E4D8),
    surfaceContainerLowest = TodayColors.Surface,
    surfaceContainerLow = TodayColors.Background,
    surfaceContainer = Color(0xFFF8F6F0),
    surfaceContainerHigh = TodayColors.Chip,
    surfaceContainerHighest = TodayColors.NavStroke,
)

/** Tipografi M3 memakai Inter, supaya judul dialog, tombol, dan ListItem bawaan sama dengan sisa aplikasi. */
private val TodayTypography = Typography().let { base ->
    fun TextStyle.inter() = copy(fontFamily = AppFont)
    base.copy(
        displayLarge = base.displayLarge.inter(), displayMedium = base.displayMedium.inter(), displaySmall = base.displaySmall.inter(),
        headlineLarge = base.headlineLarge.inter(), headlineMedium = base.headlineMedium.inter(),
        headlineSmall = base.headlineSmall.inter().copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = base.titleLarge.inter().copy(fontWeight = FontWeight.Bold), titleMedium = base.titleMedium.inter(), titleSmall = base.titleSmall.inter(),
        bodyLarge = base.bodyLarge.inter(), bodyMedium = base.bodyMedium.inter(), bodySmall = base.bodySmall.inter(),
        labelLarge = base.labelLarge.inter().copy(fontWeight = FontWeight.Bold), labelMedium = base.labelMedium.inter(), labelSmall = base.labelSmall.inter(),
    )
}

@Composable
fun TodayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TodayColorScheme, typography = TodayTypography, content = content)
}
