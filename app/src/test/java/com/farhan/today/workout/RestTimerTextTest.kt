package com.farhan.today.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RestTimerTextTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private val endsAt = LocalDateTime.of(2026, 11, 17, 14, 32, 10).atZone(zone).toInstant().toEpochMilli()

    @Test fun runningTextShowsDurationAndAbsoluteEndTime() {
        assertEquals("Istirahat 1:30 mnt · selesai 14:32:10", restRunningText(90, endsAt, zone))
        assertEquals("Istirahat 45 dtk · selesai 14:32:10", restRunningText(45, endsAt, zone))
        assertEquals("Istirahat 2 mnt · selesai 14:32:10", restRunningText(120, endsAt, zone))
    }

    @Test fun warnsOnlyForLongEnoughRests() {
        assertFalse(shouldWarn(20))
        assertTrue(shouldWarn(21))
        assertTrue(shouldWarn(90))
    }
}
