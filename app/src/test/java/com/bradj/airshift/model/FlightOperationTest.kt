package com.bradj.airshift.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class FlightOperationTest {
    private val scheduled = LocalDateTime.of(2026, 9, 3, 14, 0)

    @Test
    fun timesWithinTwelveHoursBelongToTheSameOperation() {
        assertTrue(FlightOperation.isSameOperation(scheduled, scheduled))
        assertTrue(FlightOperation.isSameOperation(scheduled, scheduled.plusHours(10)))
        assertTrue(FlightOperation.isSameOperation(scheduled, scheduled.minusHours(2)))
        assertTrue(FlightOperation.isSameOperation(scheduled, scheduled.plusHours(12)))
    }

    @Test
    fun theSameFlightNumberOnAnotherDayIsADifferentOperation() {
        assertFalse(FlightOperation.isSameOperation(scheduled, scheduled.plusHours(12).plusMinutes(1)))
        assertFalse(FlightOperation.isSameOperation(scheduled, scheduled.plusDays(1)))
        assertFalse(FlightOperation.isSameOperation(scheduled, scheduled.minusDays(1)))
    }

    @Test
    fun trustedKeepsSameOperationEstimatesAndDropsForeignOnes() {
        assertEquals(scheduled.plusMinutes(20), FlightOperation.trusted(scheduled, scheduled.plusMinutes(20)))
        assertNull(FlightOperation.trusted(scheduled, scheduled.plusDays(2)))
        assertNull(FlightOperation.trusted(scheduled, null))
    }

    @Test
    fun withoutAScheduledTimeThereIsNothingToCompareAgainst() {
        assertEquals(scheduled.plusDays(2), FlightOperation.trusted(null, scheduled.plusDays(2)))
    }
}
