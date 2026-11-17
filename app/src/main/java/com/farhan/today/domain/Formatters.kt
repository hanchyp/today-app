package com.farhan.today.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

val IndonesianLocale: Locale = Locale.forLanguageTag("id-ID")

/** Rp42.000 — tanpa tanda. */
fun formatRupiah(amount: Long): String {
    val digits = abs(amount).toString().reversed().chunked(3).joinToString(".").reversed()
    return (if (amount < 0) "−" else "") + "Rp" + digits
}

/** −Rp24.000 / +Rp1.250.000 — tanda selalu tampil agar makna tidak bergantung warna. */
fun formatSignedRupiah(amount: Long, isIncome: Boolean): String =
    (if (isIncome) "+" else "−") + formatRupiah(abs(amount))

/** Versi ringkas untuk ruang sempit: Rp950rb, Rp12,5 jt. */
fun formatRupiahCompact(amount: Long): String {
    val a = abs(amount)
    val sign = if (amount < 0) "−" else ""
    return when {
        a >= 1_000_000_000 -> sign + "Rp" + trimDecimal(a / 1_000_000_000.0) + " M"
        a >= 1_000_000 -> sign + "Rp" + trimDecimal(a / 1_000_000.0) + " jt"
        a >= 10_000 -> sign + "Rp" + (a / 1_000) + "rb"
        else -> formatRupiah(amount)
    }
}

private fun trimDecimal(v: Double): String {
    val rounded = Math.round(v * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString().replace('.', ',')
}

/** "17.00" — format jam Indonesia memakai titik. */
fun formatTime(time: LocalTime): String = "%02d.%02d".format(time.hour, time.minute)

private val longDate = DateTimeFormatter.ofPattern("EEEE, d MMMM", IndonesianLocale)
private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", IndonesianLocale)

/** "Senin, 9 November" */
fun formatLongDate(date: LocalDate): String = longDate.format(date)

/** "November 2026" */
fun formatMonthYear(date: LocalDate): String = monthYear.format(date).replaceFirstChar { it.uppercase() }

/** "Sen", "Sel", … */
fun shortDayName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Sen"
    DayOfWeek.TUESDAY -> "Sel"
    DayOfWeek.WEDNESDAY -> "Rab"
    DayOfWeek.THURSDAY -> "Kam"
    DayOfWeek.FRIDAY -> "Jum"
    DayOfWeek.SATURDAY -> "Sab"
    DayOfWeek.SUNDAY -> "Min"
}

/** "Hari ini", "Besok", "Kemarin", atau "12 Nov". */
fun relativeDay(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hari ini"
    today.plusDays(1) -> "Besok"
    today.minusDays(1) -> "Kemarin"
    else -> "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, IndonesianLocale).removeSuffix(".")}"
}
