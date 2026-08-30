package com.bradj.airshift.api

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.model.RosterAssignment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class FlightRefreshSchedulerInstrumentedTest {
    private lateinit var baseContext: Context
    private lateinit var isolatedContext: Context
    private lateinit var prefix: String
    private lateinit var store: RosterStore
    private lateinit var manager: WorkManager

    @Before
    fun setUp() {
        baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        assumeFalse("Scheduling tests must not touch a configured API credential", RosterStore(baseContext).hasVariFlightApiKey)
        prefix = "flight-refresh-scheduler-test-${UUID.randomUUID()}-"
        isolatedContext = object : ContextWrapper(baseContext) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                super.getSharedPreferences(prefix + name, mode)
        }
        store = RosterStore(isolatedContext)
        store.variFlightApiKey = "synthetic-scheduler-test-key"
        store.replaceAssignments(duties())
        manager = WorkManager.getInstance(baseContext)
    }

    @After
    fun tearDown() {
        if (!this::store.isInitialized) return
        store.setCurrentDutyIndex(store.loadAssignments().size)
        FlightRefreshScheduler.configure(isolatedContext, false).get(10, TimeUnit.SECONDS)
        store.clearVariFlightApiKey()
        listOf("air_shift", "air_shift_secrets").forEach { name ->
            baseContext.getSharedPreferences(prefix + name, Context.MODE_PRIVATE).edit().clear().commit()
            baseContext.deleteSharedPreferences(prefix + name)
        }
    }

    @Test
    fun slidingTheWindowKeepsTheSamePeriodicWorkAndNextRunTime() {
        FlightRefreshScheduler.configure(isolatedContext, true).get(10, TimeUnit.SECONDS)
        val before = activeWork().single()
        store.setCurrentDutyIndex(1)

        FlightRefreshScheduler.configure(isolatedContext, true).get(10, TimeUnit.SECONDS)
        val after = activeWork().single()

        assertEquals(before.id, after.id)
        assertEquals(before.nextScheduleTimeMillis, after.nextScheduleTimeMillis)
        assertEquals(TimeUnit.MINUTES.toMillis(15), after.initialDelayMillis)
    }

    @Test
    fun importingARosterReplacesOnlyThePreviousGeneration() {
        FlightRefreshScheduler.configure(isolatedContext, true).get(10, TimeUnit.SECONDS)
        val before = activeWork().single()
        store.replaceAssignments(duties())

        FlightRefreshScheduler.configure(isolatedContext, true).get(10, TimeUnit.SECONDS)
        val after = activeWork().single()

        assertNotEquals(before.id, after.id)
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(before.id).get(10, TimeUnit.SECONDS)!!.state)
        assertTrue("flight-live-refresh-${store.rosterGeneration}" in after.tags)
    }

    @Test
    fun staleDisableDoesNotCancelEligibleWorkButCompletionDoes() {
        FlightRefreshScheduler.configure(isolatedContext, true).get(10, TimeUnit.SECONDS)
        val before = activeWork().single()

        FlightRefreshScheduler.configure(isolatedContext, false).get(10, TimeUnit.SECONDS)
        assertEquals(before.id, activeWork().single().id)

        store.setCurrentDutyIndex(store.loadAssignments().size)
        FlightRefreshScheduler.configure(isolatedContext, false).get(10, TimeUnit.SECONDS)
        assertTrue(activeWork().isEmpty())
    }

    @Test
    fun legacyFixedNameWorkIsRetiredWhenWindowSchedulingStarts() {
        val legacy = PeriodicWorkRequestBuilder<FlightRefreshWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        manager.enqueueUniquePeriodicWork("flight-live-refresh", ExistingPeriodicWorkPolicy.KEEP, legacy)
            .result.get(10, TimeUnit.SECONDS)

        FlightRefreshScheduler.configure(isolatedContext, true).get(10, TimeUnit.SECONDS)

        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(legacy.id).get(10, TimeUnit.SECONDS)!!.state)
        assertEquals(1, activeWork().size)
    }

    private fun activeWork(): List<WorkInfo> = manager.getWorkInfosByTag(FlightRefreshScheduler.WORK_TAG)
        .get(10, TimeUnit.SECONDS)
        .filter { !it.state.isFinished }

    private fun duties() = (1..3).map { index ->
        RosterAssignment(
            aircraftRegistration = "B000$index",
            aircraftType = null,
            inboundFlight = "MU100$index",
            origin = null,
            scheduledArrival = LocalDate.now().plusDays(1).atTime(12 + index, 0),
            outboundFlight = null,
            destination = null,
            scheduledDeparture = null,
            assignees = "TESTUSER",
        )
    }
}
