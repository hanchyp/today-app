package com.farhan.today.data.repo

import android.app.Application
import com.farhan.today.TestToday
import com.farhan.today.data.db.ApplicationStatus
import com.farhan.today.data.db.EventMode
import com.farhan.today.data.db.JobEventType
import com.farhan.today.data.db.TodayDatabase
import com.farhan.today.fixedClock
import com.farhan.today.inMemoryDb
import com.farhan.today.millisAt
import com.farhan.today.reminder.FakeAlarmGateway
import com.farhan.today.reminder.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CareerRepositoryTest {
    private lateinit var db: TodayDatabase
    private val clock = fixedClock()
    private val gateway = FakeAlarmGateway()
    private lateinit var repo: CareerRepository
    private lateinit var agenda: AgendaRepository

    @Before fun setUp() {
        db = inMemoryDb()
        repo = CareerRepository(db, clock, ReminderScheduler(db.reminderDao(), gateway, clock))
        agenda = AgendaRepository(
            WorkoutRepository(db, clock), repo, FinanceRepository(db, clock), TaskRepository(db, clock), clock,
        )
    }

    @After fun tearDown() = db.close()

    private suspend fun newApp(status: ApplicationStatus = ApplicationStatus.APPLIED) =
        repo.saveApplication(ApplicationInput(company = "GDP Labs", role = "Software Development Intern", status = status))

    private fun interview(appId: String, at: LocalDateTime = LocalDateTime.of(TestToday.plusDays(1), LocalTime.of(10, 0)), reminder: Int? = 30) =
        EventInput(appId, JobEventType.INTERVIEW, "", at, 60, EventMode.ONLINE, "meet.google.com/abc", reminder)

    @Test(expected = IllegalArgumentException::class)
    fun saveApplication_requiresCompanyAndRole() = runTest {
        repo.saveApplication(ApplicationInput(company = " ", role = "Intern"))
    }

    @Test fun statusChanges_areRecordedInHistory() = runTest {
        val id = newApp(ApplicationStatus.SAVED)
        repo.changeStatus(id, ApplicationStatus.APPLIED)
        repo.changeStatus(id, ApplicationStatus.APPLIED) // tidak berubah → tidak dicatat
        repo.saveApplication(ApplicationInput("GDP Labs", "Software Development Intern", status = ApplicationStatus.CODING_TEST), id)
        repo.saveApplication(ApplicationInput("GDP Labs", "SDE Intern", status = ApplicationStatus.CODING_TEST), id) // status sama

        val history = repo.statusHistory(id).first()
        assertEquals(
            listOf(null to ApplicationStatus.SAVED, ApplicationStatus.SAVED to ApplicationStatus.APPLIED, ApplicationStatus.APPLIED to ApplicationStatus.CODING_TEST),
            history.map { it.fromStatus to it.toStatus },
        )
        assertEquals("SDE Intern", repo.application(id).first()?.role)
    }

    @Test fun saveEvent_appearsInAgenda_withDefaultTitle_andSchedulesReminder() = runTest {
        val id = newApp()
        val event = repo.saveEvent(interview(id))
        assertEquals("Interview", event.title)

        val items = agenda.agendaBetween(TestToday.plusDays(1), TestToday.plusDays(1)).first()
        assertEquals(listOf("Interview"), items.map { it.title })
        assertEquals(id, items.single().sourceId) // kartu Kalender membuka detail lamaran
        assertEquals("GDP Labs", items.single().subtitle)

        assertEquals(listOf(millisAt(TestToday.plusDays(1), 9, 30)), gateway.active.values.toList())
    }

    @Test fun editingEvent_reschedulesReminder_andRemovingReminderCancelsIt() = runTest {
        val id = newApp()
        val e = repo.saveEvent(interview(id))
        repo.saveEvent(interview(id, at = LocalDateTime.of(TestToday.plusDays(2), LocalTime.of(14, 0)), reminder = 60), e.id)
        assertEquals(listOf(millisAt(TestToday.plusDays(2), 13, 0)), gateway.active.values.toList())

        repo.saveEvent(interview(id, reminder = null), e.id)
        assertTrue(gateway.active.isEmpty())
    }

    @Test fun markingDone_cancelsReminder() = runTest {
        val id = newApp()
        val e = repo.saveEvent(interview(id))
        repo.setEventDone(e.id, true)
        assertTrue(gateway.active.isEmpty())
        assertTrue(agenda.agendaBetween(TestToday.plusDays(1), TestToday.plusDays(1)).first().single().done)
    }

    @Test fun deleteApplication_hidesEventsFromAgenda_andCancelsReminders() = runTest {
        val id = newApp()
        repo.saveEvent(interview(id))
        repo.deleteApplication(id)

        assertNull(repo.application(id).first())
        assertTrue(agenda.agendaBetween(TestToday, TestToday.plusDays(7)).first().isEmpty())
        assertTrue(gateway.active.isEmpty())
        assertTrue(repo.applicationsWithNextEvent().first().isEmpty())
    }

    @Test fun applicationsList_showsNextUpcomingEvent() = runTest {
        val id = newApp()
        repo.saveEvent(interview(id, at = LocalDateTime.of(TestToday.plusDays(3), LocalTime.of(9, 0))))
        repo.saveEvent(EventInput(id, JobEventType.CODING_TEST, "", LocalDateTime.of(TestToday, LocalTime.of(20, 0)), 90, EventMode.ONLINE))
        // Event yang sudah lewat tidak dihitung
        repo.saveEvent(EventInput(id, JobEventType.FOLLOW_UP, "", LocalDateTime.of(TestToday.minusDays(1), LocalTime.of(9, 0)), 15, EventMode.ONLINE))

        val row = repo.applicationsWithNextEvent().first().single()
        assertEquals("Coding test", row.nextEventTitle)
        assertEquals(millisAt(TestToday, 20), row.nextEventAt)
    }

    @Test fun promotionOffer_onlyMovesForward() {
        assertEquals(ApplicationStatus.INTERVIEW, CareerRepository.shouldOfferPromotion(ApplicationStatus.CODING_TEST, JobEventType.INTERVIEW))
        assertEquals(ApplicationStatus.CODING_TEST, CareerRepository.shouldOfferPromotion(ApplicationStatus.APPLIED, JobEventType.CODING_TEST))
        assertNull(CareerRepository.shouldOfferPromotion(ApplicationStatus.OFFER, JobEventType.INTERVIEW))
        assertNull(CareerRepository.shouldOfferPromotion(ApplicationStatus.REJECTED, JobEventType.INTERVIEW))
        assertNull(CareerRepository.shouldOfferPromotion(ApplicationStatus.SAVED, JobEventType.FOLLOW_UP))
    }

    @Test fun reminderLabels() {
        assertEquals("15 menit", CareerRepository.reminderLabel(15))
        assertEquals("1 jam", CareerRepository.reminderLabel(60))
        assertEquals("1 hari", CareerRepository.reminderLabel(1440))
        assertEquals("90 menit", CareerRepository.reminderLabel(90))
    }
}
