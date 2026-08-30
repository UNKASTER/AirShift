package com.bradj.airshift.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.CancellationException

class FlightRefreshBatchTest {
    private val date = LocalDate.of(2026, 8, 30)
    private val firstInbound = FlightLookup.of("MU1234", date)
    private val firstOutbound = FlightLookup.of("MU1235", date)
    private val secondInbound = FlightLookup.of("CZ1234", date)
    private val secondOutbound = FlightLookup.of("CZ1235", date.plusDays(1))
    private val twoDuties = linkedSetOf(firstInbound, firstOutbound, secondInbound, secondOutbound)

    @Test
    fun twoTurnaroundDutiesExecuteTheirFourDistinctTargetsOnce() {
        val requested = mutableListOf<FlightLookup>()
        val targets = linkedSetOf(
            firstInbound, firstOutbound, firstInbound, secondInbound, secondOutbound, secondOutbound,
        )

        val result = refreshFlightBatch(targets, isCurrent = { true }) { lookup ->
            requested += lookup
            flight(lookup)
        }

        assertEquals(twoDuties.toList(), requested)
        assertEquals(twoDuties, result.live.keys)
        assertEquals(4, result.attemptedCount)
        assertTrue(result.errors.isEmpty())
        assertEquals(0, result.retryableFailures)
    }

    @Test
    fun progressChangeDuringFetchSkipsUnsentFlightsOutsideTheLatestWindow() {
        var window: Set<FlightLookup> = twoDuties
        val checked = mutableListOf<FlightLookup>()
        val requested = mutableListOf<FlightLookup>()
        val thirdFlight = FlightLookup.of("CA1234", date.plusDays(1))

        val result = refreshFlightBatch(
            twoDuties,
            isCurrent = { lookup ->
                checked += lookup
                lookup in window
            },
        ) { lookup ->
            requested += lookup
            if (lookup == firstInbound) window = setOf(secondInbound, secondOutbound, thirdFlight)
            flight(lookup)
        }

        assertEquals(twoDuties.toList(), checked)
        assertEquals(listOf(firstInbound, secondInbound, secondOutbound), requested)
        assertFalse(firstOutbound in result.live)
        assertFalse(thirdFlight in result.live)
        assertEquals(3, result.attemptedCount)
    }

    @Test
    fun replacingRosterDuringFetchStopsAllRemainingRequestsEvenForMatchingFlights() {
        val batchGeneration = 7L
        var currentGeneration = batchGeneration
        val requested = mutableListOf<FlightLookup>()

        val result = refreshFlightBatch(twoDuties, isCurrent = { currentGeneration == batchGeneration }) { lookup ->
            requested += lookup
            currentGeneration++
            flight(lookup)
        }

        assertEquals(listOf(firstInbound), requested)
        assertEquals(setOf(firstInbound), result.live.keys)
        assertEquals(1, result.attemptedCount)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun completingAllDutiesBeforeExecutionDoesNotQueryOrReportFailures() {
        val result = refreshFlightBatch(twoDuties, isCurrent = { false }) {
            error("No request should be made after all duties complete")
        }

        assertTrue(result.live.isEmpty())
        assertTrue(result.errors.isEmpty())
        assertEquals(0, result.attemptedCount)
        assertEquals(0, result.retryableFailures)
    }

    @Test
    fun flightRetiredInsideTheRequestProtectionDoesNotCountAsAnAttemptOrFailure() {
        val result = refreshFlightBatch(twoDuties, isCurrent = { true }) { lookup ->
            if (lookup == firstInbound) throw FlightRefreshSkippedException()
            flight(lookup)
        }

        assertEquals(twoDuties - firstInbound, result.live.keys)
        assertEquals(3, result.attemptedCount)
        assertTrue(result.errors.isEmpty())
        assertEquals(0, result.retryableFailures)
    }

    @Test
    fun partialFailuresKeepSuccessfulDataAndDoNotExposeUnexpectedExceptionDetails() {
        val result = refreshFlightBatch(twoDuties, isCurrent = { true }) { lookup ->
            when (lookup) {
                firstInbound -> flight(lookup)
                firstOutbound -> throw VariFlightClientException("连接飞常准超时，请稍后重试", retryable = true)
                secondInbound -> throw IllegalStateException("private token=secret response body")
                else -> throw VariFlightClientException("飞常准 API Key 无效或无权访问 Aviation MCP")
            }
        }

        assertEquals(mapOf(firstInbound to flight(firstInbound)), result.live)
        assertEquals(
            listOf(
                "MU1235：连接飞常准超时，请稍后重试",
                "CZ1234：实时航班更新失败",
                "CZ1235：飞常准 API Key 无效或无权访问 Aviation MCP",
            ),
            result.errors,
        )
        assertEquals(4, result.attemptedCount)
        assertEquals(2, result.retryableFailures)
    }

    @Test
    fun cancellationPropagatesAndStopsTheBatch() {
        val cancellation = CancellationException("Cancelled")
        val requested = mutableListOf<FlightLookup>()

        val thrown = assertThrows(CancellationException::class.java) {
            refreshFlightBatch(twoDuties, isCurrent = { true }) { lookup ->
                requested += lookup
                throw cancellation
            }
        }

        assertSame(cancellation, thrown)
        assertEquals(listOf(firstInbound), requested)
    }

    @Test
    fun interruptionPropagatesAndRestoresTheThreadInterruptFlag() {
        val interruption = InterruptedException("Interrupted")
        try {
            val thrown = assertThrows(InterruptedException::class.java) {
                refreshFlightBatch(twoDuties, isCurrent = { true }) { throw interruption }
            }

            assertSame(interruption, thrown)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    private fun flight(lookup: FlightLookup) = FlightInfo(
        flightNumber = lookup.flightNumber,
        origin = null,
        destination = null,
        plannedDeparture = null,
        estimatedDeparture = null,
        actualDeparture = null,
        plannedArrival = null,
        estimatedArrival = null,
        actualArrival = null,
        actualOffBlock = null,
        gateClosedObservedAt = null,
        boardingGate = null,
        departureStand = null,
        arrivalStand = null,
        arrivalBridge = null,
    )
}
