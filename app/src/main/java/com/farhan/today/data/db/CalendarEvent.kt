package com.farhan.today.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.farhan.today.domain.model.AgendaItem
import com.farhan.today.domain.model.AgendaSource
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Entity(tableName = "calendar_event")
data class CalendarEvent(
    @PrimaryKey val id: String = newId(),
    val title: String,
    val notes: String,
    val date: LocalDate,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val weeklyUntil: LocalDate?,
) {
    fun occurrences(start: LocalDate, end: LocalDate): List<AgendaItem> {
        val last = minOf(end, weeklyUntil ?: date)
        if (last < start || last < date) return emptyList()
        val days = ChronoUnit.DAYS.between(date, maxOf(start, date))
        val first = date.plusWeeks((days + 6) / 7)
        return generateSequence(first) { it.plusWeeks(1) }.takeWhile { it <= last }.map { day ->
            val subtitle = listOfNotNull(endTime?.let { "Sampai $it" }, notes.takeIf { it.isNotBlank() }).joinToString(" · ")
            AgendaItem("calendar:$id:$day", AgendaSource.CALENDAR, id, "Agenda", title, subtitle, day, startTime, false)
        }.toList()
    }
}

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendar_event WHERE date <= :end AND COALESCE(weeklyUntil, date) >= :start")
    fun between(start: LocalDate, end: LocalDate): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM calendar_event WHERE id = :id")
    suspend fun find(id: String): CalendarEvent?

    @Upsert suspend fun save(event: CalendarEvent)
    @Delete suspend fun delete(event: CalendarEvent)
}
