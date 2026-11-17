package com.farhan.today

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.farhan.today.data.db.TodayDatabase
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

val Jakarta: ZoneId = ZoneId.of("Asia/Jakarta")

/** Senin, 9 November 2026 pukul 09.41 WIB — sama dengan jam di desain. */
val TestToday: LocalDate = LocalDate.of(2026, 11, 9)

fun fixedClock(date: LocalDate = TestToday, time: LocalTime = LocalTime.of(9, 41)): Clock =
    Clock.fixed(ZonedDateTime.of(date, time, Jakarta).toInstant(), Jakarta)

fun millisAt(date: LocalDate, hour: Int, minute: Int = 0): Long =
    ZonedDateTime.of(date, LocalTime.of(hour, minute), Jakarta).toInstant().toEpochMilli()

fun inMemoryDb(): TodayDatabase = Room
    .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), TodayDatabase::class.java)
    .allowMainThreadQueries()
    .build()
