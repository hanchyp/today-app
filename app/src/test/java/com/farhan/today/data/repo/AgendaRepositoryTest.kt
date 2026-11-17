package com.farhan.today.data.repo

import android.app.Application
import com.farhan.today.TestToday
import com.farhan.today.data.db.BillEntity
import com.farhan.today.data.db.BillRepeat
import com.farhan.today.data.db.SeedData
import com.farhan.today.data.db.TodayDatabase
import com.farhan.today.domain.model.AgendaItem
import com.farhan.today.domain.model.AgendaSource
import com.farhan.today.domain.usecase.GetTodayOverview
import com.farhan.today.fixedClock
import com.farhan.today.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AgendaRepositoryTest {
    private var db: TodayDatabase? = null

    @After fun tearDown() { db?.close() }

    private suspend fun seeded(clock: Clock): Pair<AgendaRepository, GetTodayOverview> {
        val d = inMemoryDb().also { db = it }
        SeedData(d, clock).seed()
        val finance = FinanceRepository(d, clock)
        val agenda = AgendaRepository(
            WorkoutRepository(d, clock), CareerRepository(d, clock),
            finance, TaskRepository(d, clock), clock,
        )
        return agenda to GetTodayOverview(agenda, finance, clock)
    }

    @Test fun seededToday_matchesDesign() = runTest {
        val (_, overview) = seeded(fixedClock())
        val o = overview().first()

        assertEquals(TestToday, o.date)
        assertEquals(listOf("Push Day", "Coding test"), o.items.map { it.title })
        assertEquals("Push Day", o.next?.title)
        assertEquals(LocalTime.of(17, 0), o.next?.time)
        assertEquals(null, o.walletBalance)
        assertEquals(setOf(AgendaSource.WORKOUT, AgendaSource.CAREER), o.weekAgenda[TestToday])
        // Besok: interview + deadline Revisi Bab 3 + jadwal Pull Day
        assertEquals(setOf(AgendaSource.WORKOUT, AgendaSource.CAREER, AgendaSource.TASK), o.weekAgenda[TestToday.plusDays(1)])
    }

    @Test fun nextMovesWithClock() = runTest {
        val (_, overview) = seeded(fixedClock(time = LocalTime.of(18, 0)))
        assertEquals("Coding test", overview().first().next?.title)
    }

    @Test fun nextIsNullAfterEverything() = runTest {
        val (_, overview) = seeded(fixedClock(time = LocalTime.of(21, 0)))
        assertNull(overview().first().next)
    }

    @Test fun orderingTimedThenAllDay_andNoDuplicateFromSchedule() = runTest {
        val (agenda, _) = seeded(fixedClock())
        val tomorrow = agenda.agendaBetween(TestToday.plusDays(1), TestToday.plusDays(1)).first()
        assertEquals(listOf("Interview GDP Labs", "Pull Day", "Revisi Bab 3"), tomorrow.map { it.title })
        assertNull(tomorrow.last().time)

        // Hari ini Push Day punya sesi nyata dan jadwal mingguan → hanya muncul sekali
        val today = agenda.agendaBetween(TestToday, TestToday).first()
        assertEquals(1, today.count { it.title == "Push Day" })
    }

    @Test fun pastScheduleIsNotProjected_butRealSessionsAre() = runTest {
        val (agenda, _) = seeded(fixedClock())
        val past = agenda.agendaBetween(TestToday.minusDays(7), TestToday.minusDays(1)).first()
        // Riwayat nyata (Pull 3 hari lalu, Leg 5 hari lalu) tampil sebagai selesai; jadwal masa lalu tidak diproyeksikan
        assertEquals(listOf("Leg Day", "Pull Day"), past.filter { it.source == AgendaSource.WORKOUT }.map { it.title })
        assertTrue(past.filter { it.source == AgendaSource.WORKOUT }.all { it.done })
    }

    @Test fun monthlyBillProjectedAcrossRange() {
        val bill = BillEntity(
            name = "Gym", amount = 150_000, categoryId = null, repeat = BillRepeat.MONTHLY,
            nextDueDate = LocalDate.of(2026, 1, 31), createdAt = 0, updatedAt = 0,
        )
        val dates = AgendaRepository.dueDatesInRange(bill, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30))
        // Dihitung dari tanggal dasar, jadi tidak "bergeser" ke 28 setelah Februari
        assertEquals(listOf(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)), dates)

        val once = bill.copy(repeat = BillRepeat.NONE)
        assertTrue(AgendaRepository.dueDatesInRange(once, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30)).isEmpty())
    }

    @Test fun pickNext_prefersUpcomingTimed_thenAllDay_skipsDone() {
        fun item(title: String, time: LocalTime?, done: Boolean = false) =
            AgendaItem(title, AgendaSource.TASK, title, "Tugas", title, "", TestToday, time, done)
        val items = listOf(item("Pagi", LocalTime.of(8, 0)), item("Sore", LocalTime.of(17, 0), done = true), item("Malam", LocalTime.of(20, 0)), item("Deadline", null))
        assertEquals("Malam", AgendaRepository.pickNext(items, LocalTime.of(9, 0))?.title)
        assertEquals("Deadline", AgendaRepository.pickNext(items, LocalTime.of(21, 0))?.title)
        assertNull(AgendaRepository.pickNext(items.map { it.copy(done = true) }, LocalTime.of(9, 0)))
    }
}
