package com.farhan.today.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
    val Display = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.8).sp)
    val Hero = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp)
    val Section = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp)
    val CardTitle = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = (-0.2).sp)
    val Number = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, lineHeight = 21.sp)
    val Body = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val Meta = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val Label = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp)
    val Eyebrow = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = 0.4.sp)
    val Tiny = TextStyle(fontFamily = AppFont, fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.4.sp)
}

@Composable
fun TodayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = TodayColors.Brand,
            onPrimary = Color.White,
            background = TodayColors.Background,
            surface = TodayColors.Surface,
            onBackground = TodayColors.Ink,
            onSurface = TodayColors.Ink,
        ),
        content = content,
    )
}
