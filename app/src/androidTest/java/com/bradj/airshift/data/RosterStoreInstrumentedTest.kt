package com.bradj.airshift.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bradj.airshift.api.FlightInfo
import com.bradj.airshift.api.FlightLookup
import com.bradj.airshift.api.FlightRefreshScope
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.allDutiesComplete
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RosterStoreInstrumentedTest {
    private lateinit var baseContext: Context
    private lateinit var isolatedContext: Context
    private lateinit var prefix: String
    private lateinit var store: RosterStore

    @Before
    fun setUp() {
        baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        prefix = "roster-store-test-${UUID.randomUUID()}-"
        isolatedContext = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences(prefix + name, mode)
        }
        store = RosterStore(isolatedContext)
    }

    @After
    fun tearDown() {
        listOf("air_shift", "air_shift_secrets").forEach { name ->
            baseContext.getSharedPreferences(prefix + name, Context.MODE_PRIVATE).edit().clear().commit()
            baseContext.deleteSharedPreferences(prefix + name)
        }
    }

    @Test
    fun replacingRosterResetsProgressAndAdvancesGenerationWhileRefreshPreservesBoth() {
        val duties = listOf(assignment("B0001"), assignment("B0002"))
        assertEquals(0L, store.rosterGeneration)
        val firstGeneration = store.replaceAssignments(duties)
        store.setCurrentDutyIndex(1)
        val refreshed = duties.map { it.copy(inboundDepartureStand = "A12", outboundArrivalStand = "B34") }

        store.saveAssignments(refreshed)
        assertEquals(firstGeneration, store.rosterGeneration)
        assertEquals(1, store.currentDutyIndex)
        assertEquals(refreshed, store.loadAssignments())

        val nextGeneration = store.replaceAssignments(listOf(assignment("B0003")))
        val snapshot = RosterStore(isolatedContext).loadSnapshot()
        assertEquals(firstGeneration + 1L, nextGeneration)
        assertEquals(nextGeneration, snapshot.generation)
        assertEquals(0, snapshot.manuallyCompletedCount)
        assertEquals("B0003", snapshot.assignments.single().aircraftRegistration)
    }

    @Test
    fun staleWorkerCannotOverwriteNewRosterOrItsRefreshTimestamp() {
        val old = listOf(assignment("B0001"))
        val oldGeneration = store.replaceAssignments(old)
        val new = listOf(assignment("B0002"))
        val currentGeneration = RosterStore(isolatedContext).replaceAssignments(new)

        assertFalse(store.saveAssignmentsIfGeneration(old, oldGeneration, 100L))
        assertEquals(new, store.loadAssignments())
        assertNull(store.lastLiveRefreshEpochMillis)
        var staleEffectCalled = false
        assertFalse(store.runIfGenerationCurrent(oldGeneration) { staleEffectCalled = true })
        assertFalse(staleEffectCalled)

        val refreshed = new.map { it.copy(arrivalStand = "C56") }
        assertTrue(store.saveAssignmentsIfGeneration(refreshed, currentGeneration, 200L))
        assertEquals(refreshed, store.loadAssignments())
        assertEquals(200L, store.lastLiveRefreshEpochMillis)
    }

    @Test
    fun progressIsClampedAndPreviousDayProgressIsNotReused() {
        store.replaceAssignments(listOf(assignment("B0001"), assignment("B0002")))
        store.setCurrentDutyIndex(10)
        assertEquals(2, store.currentDutyIndex)
        val preferences = isolatedContext.getSharedPreferences("air_shift", Context.MODE_PRIVATE)
        preferences.edit().putString("duty_progress_date", LocalDate.now().minusDays(1).toString()).commit()
        assertEquals(0, store.loadSnapshot().manuallyCompletedCount)

        store.setCurrentDutyIndex(-1)
        assertEquals(0, store.currentDutyIndex)
        assertEquals(LocalDate.now().toString(), preferences.getString("duty_progress_date", null))
    }

    @Test
    fun completingShownDutyIsAtomicAndReturnsOnlyNewlyTrackedFlights() {
        val duties = (1..3).map { upcomingAssignment("B000$it", "MU100$it") }
        val generation = store.replaceAssignments(duties)
        val now = LocalDateTime.now()

        val completion = store.completeCurrentDuty(generation, expectedDutyIndex = 0, now = now)

        assertNotNull(completion)
        assertEquals(1, store.currentDutyIndex)
        assertEquals(
            setOf(FlightLookup.of("MU1003", duties[2].scheduledArrival!!.toLocalDate())),
            completion!!.newlyTrackedFlights,
        )
        assertNull(store.completeCurrentDuty(generation, expectedDutyIndex = 0, now = now))
        assertEquals(1, store.currentDutyIndex)
        assertNull(store.completeCurrentDuty(generation - 1, expectedDutyIndex = 1, now = now))
        assertEquals(1, store.currentDutyIndex)
    }

    @Test
    fun oldBatchMergesOnlyCurrentWindowAndPreservesAnotherRefresh() {
        val duties = (1..4).map { upcomingAssignment("B000$it", "MU100$it") }
        val generation = store.replaceAssignments(duties)
        val date = duties.first().scheduledArrival!!.toLocalDate()
        store.setCurrentDutyIndex(1)

        store.mergeLiveInfoIfGeneration(
            mapOf(FlightLookup.of("MU1003", date) to liveLegs("MU1003", "NEW-C")),
            generation,
            date,
            100L,
        )
        val merged = store.mergeLiveInfoIfGeneration(
            mapOf(
                FlightLookup.of("MU1001", date) to liveLegs("MU1001", "STALE-A"),
                FlightLookup.of("MU1002", date) to liveLegs("MU1002", "NEW-B"),
            ),
            generation,
            date,
            200L,
        )!!

        assertEquals(duties[0], merged.assignments[0])
        assertEquals("NEW-B", merged.assignments[1].arrivalStand)
        assertEquals("NEW-C", merged.assignments[2].arrivalStand)
        assertEquals(duties[3], merged.assignments[3])
        assertEquals(1, merged.manuallyCompletedCount)
        assertEquals(generation, merged.generation)
        assertEquals(merged, store.loadSnapshot())
        assertEquals(200L, store.lastLiveRefreshEpochMillis)
    }

    @Test
    fun duplicateFlightOutsideWindowIsNotUpdated() {
        val duties = (1..3).map { upcomingAssignment("B000$it", "MU1001") }
        val generation = store.replaceAssignments(duties)
        val date = duties.first().scheduledArrival!!.toLocalDate()

        val merged = store.mergeLiveInfoIfGeneration(
            mapOf(FlightLookup.of("MU1001", date) to liveLegs("MU1001", "NEW")),
            generation,
            date,
        )!!

        assertEquals("NEW", merged.assignments[0].arrivalStand)
        assertEquals("NEW", merged.assignments[1].arrivalStand)
        assertEquals(duties[2], merged.assignments[2])
    }

    @Test
    fun staleGenerationOrResultsOutsideWindowDoNotChangeDataOrTimestamp() {
        val duties = (1..3).map { upcomingAssignment("B000$it", "MU100$it") }
        val oldGeneration = store.replaceAssignments(duties)
        val generation = store.replaceAssignments(duties)
        val date = duties.first().scheduledArrival!!.toLocalDate()
        val before = store.loadSnapshot()

        assertNull(
            store.mergeLiveInfoIfGeneration(
                mapOf(FlightLookup.of("MU1001", date) to liveLegs("MU1001", "STALE")),
                oldGeneration,
                date,
                100L,
            ),
        )
        assertEquals(
            before,
            store.mergeLiveInfoIfGeneration(
                mapOf(FlightLookup.of("MU1003", date) to liveLegs("MU1003", "OUTSIDE")),
                generation,
                date,
                200L,
            ),
        )
        assertEquals(before, store.loadSnapshot())
        assertNull(store.lastLiveRefreshEpochMillis)
    }

    @Test
    fun allRosterMergeUpdatesCompletedFlightsWithoutResettingManualProgress() {
        val duties = (1..3).map { upcomingAssignment("B000$it", "MU100$it") }
        val generation = store.replaceAssignments(duties)
        val date = duties.first().scheduledArrival!!.toLocalDate()
        store.setCurrentDutyIndex(duties.size)
        val live = mapOf(
            FlightLookup.of("MU1001", date) to liveLegs("MU1001", "NEW-FIRST"),
            FlightLookup.of("MU1003", date) to liveLegs("MU1003", "NEW-LAST"),
        )

        val ignored = store.mergeLiveInfoIfGeneration(live, generation, date, 100L)!!
        assertEquals(duties, ignored.assignments)
        assertNull(store.lastLiveRefreshEpochMillis)

        val merged = store.mergeLiveInfoIfGeneration(live, generation, date, 200L, FlightRefreshScope.ALL_ROSTER)!!

        assertEquals("NEW-FIRST", merged.assignments[0].arrivalStand)
        assertEquals(duties[1], merged.assignments[1])
        assertEquals("NEW-LAST", merged.assignments[2].arrivalStand)
        assertEquals(duties.size, merged.manuallyCompletedCount)
        assertEquals(generation, merged.generation)
        assertTrue(merged.assignments.allDutiesComplete(manuallyCompletedCount = merged.manuallyCompletedCount))
        assertEquals(merged, store.loadSnapshot())
        assertEquals(200L, store.lastLiveRefreshEpochMillis)
    }

    @Test
    fun allRosterMergeCanCorrectAnAutomaticallyCompletedDutyWithoutChangingProgress() {
        val now = LocalDateTime.now()
        val duty = upcomingAssignment("B0001", "MU1001").copy(scheduledArrival = now.minusDays(1))
        val generation = store.replaceAssignments(listOf(duty))
        val date = duty.scheduledArrival!!.toLocalDate()
        assertTrue(listOf(duty).allDutiesComplete(now))

        val merged = store.mergeLiveInfoIfGeneration(
            mapOf(FlightLookup.of("MU1001", date) to listOf(liveFlight("MU1001", "NEW").copy(estimatedArrival = now.plusHours(2)))),
            generation,
            date,
            scope = FlightRefreshScope.ALL_ROSTER,
        )!!

        assertEquals("NEW", merged.assignments.single().arrivalStand)
        assertEquals(now.plusHours(2), merged.assignments.single().estimatedArrival)
        assertFalse(merged.assignments.allDutiesComplete(now))
        assertEquals(0, merged.manuallyCompletedCount)
        assertEquals(generation, merged.generation)
    }

    @Test
    fun allRosterMergeRejectsResultsFromAnOlderImportedRoster() {
        val duties = listOf(upcomingAssignment("B0001", "MU1001"))
        val oldGeneration = store.replaceAssignments(duties)
        store.setCurrentDutyIndex(duties.size)
        store.replaceAssignments(duties)
        val before = store.loadSnapshot()
        val date = duties.first().scheduledArrival!!.toLocalDate()

        assertNull(
            store.mergeLiveInfoIfGeneration(
                mapOf(FlightLookup.of("MU1001", date) to liveLegs("MU1001", "STALE")),
                oldGeneration,
                date,
                100L,
                FlightRefreshScope.ALL_ROSTER,
            ),
        )
        assertEquals(before, store.loadSnapshot())
        assertNull(store.lastLiveRefreshEpochMillis)
    }

    private fun upcomingAssignment(registration: String, flight: String) = assignment(registration).copy(
        inboundFlight = flight,
        scheduledArrival = LocalDate.now().plusDays(1).atTime(12, 0),
        outboundFlight = null,
        scheduledDeparture = null,
    )

    private fun liveLegs(flight: String, stand: String) = listOf(liveFlight(flight, stand))

    private fun liveFlight(flight: String, stand: String) = FlightInfo(
        flightNumber = flight,
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
        arrivalStand = stand,
        arrivalBridge = null,
    )

    private fun assignment(registration: String) = RosterAssignment(
        aircraftRegistration = registration,
        aircraftType = "A320",
        inboundFlight = "MU1001",
        origin = "测试始发站",
        scheduledArrival = LocalDateTime.of(2026, 8, 30, 12, 0),
        outboundFlight = "MU1002",
        destination = "测试到达站",
        scheduledDeparture = LocalDateTime.of(2026, 8, 30, 14, 0),
        assignees = "测试甲",
    )
}
