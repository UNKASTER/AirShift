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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.abs

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
        // 密文写在带前缀的隔离 SharedPreferences 里；Keystore 别名与正式应用共用，因此下面绝不能调用
        // clearVariFlightApiKey()（它会删除别名，让手机上已配置的真实 Key 永远解不开），只删隔离文件。
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
    fun aRosterForTomorrowSleepsUntilThreeHoursBeforeItsFirstTask() {
        // 头天晚上导入明天的排班：周期任务照常登记，但首轮直接延迟到跟踪起点，上班前不联网。
        val roster = duties(firstTaskAt = LocalDateTime.now().plusDays(1).withHour(12).withMinute(0))
        store.replaceAssignments(roster)
        val expectedMillis = TimeUnit.MINUTES.toMillis(initialDelayMinutes(roster, LocalDateTime.now()))

        FlightRefreshScheduler.configure(isolatedContext, true).get(10, TimeUnit.SECONDS)
        val work = activeWork().single()

        assertTrue(work.initialDelayMillis > TimeUnit.HOURS.toMillis(1))
        assertTrue(
            "expected about $expectedMillis ms, got ${work.initialDelayMillis} ms",
            abs(work.initialDelayMillis - expectedMillis) <= TimeUnit.MINUTES.toMillis(2),
        )
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

    /** 默认首个任务在一小时后：未完成，且已进入排班日的跟踪时段（首个任务前 3 小时）。 */
    private fun duties(firstTaskAt: LocalDateTime = LocalDateTime.now().plusHours(1)) = (1..3).map { index ->
        RosterAssignment(
            aircraftRegistration = "B000$index",
            aircraftType = null,
            inboundFlight = "MU100$index",
            origin = null,
            scheduledArrival = firstTaskAt.plusHours(index - 1L),
            outboundFlight = null,
            destination = null,
            scheduledDeparture = null,
            assignees = "TESTUSER",
        )
    }
}
