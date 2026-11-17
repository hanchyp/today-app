package com.farhan.today.reminder

import android.app.Application
import com.farhan.today.TestToday
import com.farhan.today.data.db.ReminderSource
import com.farhan.today.data.db.ReminderState
import com.farhan.today.data.db.TodayDatabase
import com.farhan.today.fixedClock
import com.farhan.today.inMemoryDb
import com.farhan.today.millisAt
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
import java.time.LocalTime

/** AlarmManager palsu: mencatat alarm yang sedang terpasang. */
class FakeAlarmGateway : AlarmGateway {
    val active = mutableMapOf<String, Long>()
    val cancelled = mutableListOf<String>()
    override fun set(reminderId: String, triggerAt: Long) { active[reminderId] = triggerAt }
    override fun cancel(reminderId: String) { active.remove(reminderId); cancelled += reminderId }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderSchedulerTest {
    private lateinit var db: TodayDatabase
    private val gateway = FakeAlarmGateway()
    private val clock = fixedClock()
    private lateinit var scheduler: ReminderScheduler

    @Before fun setUp() {
        db = inMemoryDb()
        scheduler = ReminderScheduler(db.reminderDao(), gateway, clock)
    }

    @After fun tearDown() = db.close()

    @Test fun schedule_setsAlarm_andReplacesPreviousForSameSource() = runTest {
        val first = scheduler.schedule(ReminderSource.JOB_EVENT, "e1", millisAt(TestToday, 19, 30), "Coding test", "Awan Biru Labs")!!
        assertEquals(millisAt(TestToday, 19, 30), gateway.active[first.id])

        val second = scheduler.schedule(ReminderSource.JOB_EVENT, "e1", millisAt(TestToday, 19, 0), "Coding test", "Awan Biru Labs")!!
        assertEquals(listOf(second.id), gateway.active.keys.toList())
        assertEquals(ReminderState.CANCELLED, db.reminderDao().byId(first.id)?.state)
        assertEquals(1, db.reminderDao().scheduled().size)
    }

    @Test fun schedule_ignoresPastTrigger() = runTest {
        assertNull(scheduler.schedule(ReminderSource.TASK, "t1", millisAt(TestToday, 8), "Lewat", ""))
        assertTrue(gateway.active.isEmpty())
        assertTrue(db.reminderDao().scheduled().isEmpty())
    }

    @Test fun cancel_removesAlarmAndMarksCancelled() = runTest {
        val r = scheduler.schedule(ReminderSource.BILL, "b1", millisAt(TestToday.plusDays(2), 9), "Gym", "")!!
        scheduler.cancel(ReminderSource.BILL, "b1")
        assertTrue(gateway.active.isEmpty())
        assertEquals(ReminderState.CANCELLED, db.reminderDao().byId(r.id)?.state)
    }

    @Test fun rescheduleAll_restoresFutureAlarms_andRetiresMissedOnes() = runTest {
        val future = scheduler.schedule(ReminderSource.JOB_EVENT, "e1", millisAt(TestToday, 19, 30), "A", "")!!
        val soon = scheduler.schedule(ReminderSource.JOB_EVENT, "e2", millisAt(TestToday, 10, 0), "B", "")!!
        gateway.active.clear() // simulasi reboot: sistem menghapus semua alarm

        // Satu jam kemudian: e2 (10.00) sudah terlewat saat HP mati
        val later = ReminderScheduler(db.reminderDao(), gateway, fixedClock(time = LocalTime.of(10, 41)))
        assertEquals(1, later.rescheduleAll())
        assertEquals(setOf(future.id), gateway.active.keys)
        assertEquals(ReminderState.FIRED, db.reminderDao().byId(soon.id)?.state)
    }
}
