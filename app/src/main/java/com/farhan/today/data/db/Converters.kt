package com.farhan.today.data.db

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter fun dateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
    @TypeConverter fun epochDayToDate(day: Long?): LocalDate? = day?.let(LocalDate::ofEpochDay)

    /** Menit sejak 00:00 — cukup untuk jadwal, dan mudah diurutkan di SQL. */
    @TypeConverter fun timeToMinutes(time: LocalTime?): Int? = time?.let { it.hour * 60 + it.minute }
    @TypeConverter fun minutesToTime(minutes: Int?): LocalTime? = minutes?.let { LocalTime.of(it / 60, it % 60) }

    @TypeConverter
    fun setTargetsToJson(targets: List<RoutineSetTarget>?): String? = targets?.let {
        JSONArray().apply {
            it.forEach { target ->
                put(JSONArray().put(target.reps).put(target.weightKg ?: JSONObject.NULL))
            }
        }.toString()
    }

    @TypeConverter
    fun jsonToSetTargets(json: String?): List<RoutineSetTarget>? = json?.let {
        val array = JSONArray(it)
        List(array.length()) { i ->
            val row = array.getJSONArray(i)
            RoutineSetTarget(row.getInt(0), if (row.isNull(1)) null else row.getDouble(1))
        }
    }
}
