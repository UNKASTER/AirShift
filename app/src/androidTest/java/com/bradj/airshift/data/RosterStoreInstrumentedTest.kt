package com.bradj.airshift.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bradj.airshift.model.RosterAssignment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
