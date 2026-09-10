package com.bradj.airshift.data

import com.bradj.airshift.reminder.ShuttleAlarm
import com.bradj.airshift.reminder.ShuttleAlarmAttempt
import com.bradj.airshift.reminder.ShuttleAlarmOutcome
import com.bradj.airshift.reminder.ShuttleAlarmReason
import com.bradj.airshift.reminder.ShuttleAlarmRecord
import com.bradj.airshift.reminder.ShuttleAlarmState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ShuttleAlarmCodecTest {
    private val date = LocalDate.of(2026, 9, 10)

    private val state = ShuttleAlarmState(
        records = listOf(
            ShuttleAlarmRecord(
                date = date,
                departure = LocalTime.of(5, 55),
                times = listOf(LocalTime.of(5, 25), LocalTime.of(5, 30)),
                setAt = LocalDateTime.of(2026, 9, 9, 6, 35, 12),
                outcome = ShuttleAlarmOutcome.CONFIRMED,
                background = true,
                attempts = 1,
            ),
            ShuttleAlarmRecord(
                date = date.plusDays(1),
                departure = null,
                times = emptyList(),
                setAt = LocalDateTime.of(2026, 9, 10, 20, 0),
                outcome = ShuttleAlarmOutcome.BLOCKED,
                background = false,
                attempts = 3,
            ),
        ),
        stale = listOf(ShuttleAlarm(date, LocalTime.of(5, 0))),
        pendingVerifyAt = LocalDateTime.of(2026, 9, 9, 11, 52),
        verifyHops = 2,
        lastAttempt = ShuttleAlarmAttempt(
            reason = ShuttleAlarmReason.WAKE,
            at = LocalDateTime.of(2026, 9, 9, 6, 35, 12),
            outcome = ShuttleAlarmOutcome.CONFIRMED,
            background = true,
            summary = "明天 05:25–05:50 共 6 响",
        ),
    )

    @Test
    fun roundTripsEveryField() {
        assertEquals(state, ShuttleAlarmCodec.decode(ShuttleAlarmCodec.encode(state)))
    }

    @Test
    fun roundTripsTheEmptyState() {
        val decoded = ShuttleAlarmCodec.decode(ShuttleAlarmCodec.encode(ShuttleAlarmState.EMPTY))
        assertEquals(ShuttleAlarmState.EMPTY, decoded)
        assertTrue(decoded!!.isEmpty)
    }

    @Test
    fun corruptInputDecodesToNull() {
        assertNull(ShuttleAlarmCodec.decode("not json"))
        assertNull(ShuttleAlarmCodec.decode("""{"records":[{"date":"2026-09-10"}],"stale":[]}"""))
        assertNull(ShuttleAlarmCodec.decode(ShuttleAlarmCodec.encode(state).replace("CONFIRMED", "MAYBE")))
    }
}
