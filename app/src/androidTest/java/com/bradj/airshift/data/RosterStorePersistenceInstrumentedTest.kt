package com.bradj.airshift.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.shift.ManualShiftGroup
import com.bradj.airshift.model.shift.ObservedShiftGroups
import com.bradj.airshift.model.shift.ShiftBusPlan
import com.bradj.airshift.model.shift.ShiftCalibration
import com.bradj.airshift.model.shift.ShiftClock
import com.bradj.airshift.model.shift.ShiftDayKind
import com.bradj.airshift.model.shift.ShiftSchedule
import com.bradj.airshift.model.shift.ShiftSlot
import com.bradj.airshift.model.shift.ShiftTeam
import com.bradj.airshift.model.shift.ShiftTier
import com.bradj.airshift.model.shift.ShiftTimeHistory
import com.bradj.airshift.model.shift.ShiftTimeObservation
import com.bradj.airshift.reminder.ShuttleAlarm
import com.bradj.airshift.reminder.ShuttleAlarmAttempt
import com.bradj.airshift.reminder.ShuttleAlarmOutcome
import com.bradj.airshift.reminder.ShuttleAlarmReason
import com.bradj.airshift.reminder.ShuttleAlarmRecord
import com.bradj.airshift.reminder.ShuttleAlarmState
import java.time.LocalDate
import java.time.LocalTime
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RosterStorePersistenceInstrumentedTest {
    private lateinit var targetContext: Context
    private lateinit var isolatedContext: Context
    private lateinit var preferences: SharedPreferences
    private val isolatedPreferenceNames = mutableSetOf<String>()

    @Before
    fun isolatePreferences() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "roster_store_test_${UUID.randomUUID()}"
        isolatedContext = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val isolatedName = "${prefix}_$name"
                isolatedPreferenceNames += isolatedName
                return super.getSharedPreferences(isolatedName, mode)
            }
        }
        preferences = isolatedContext.getSharedPreferences("air_shift", Context.MODE_PRIVATE)
    }

    @After
    fun removeOnlyIsolatedTestPreferences() {
        isolatedPreferenceNames.forEach { name ->
            // Flush pending apply() writes before deleting only this test's uniquely named files.
            targetContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            targetContext.deleteSharedPreferences(name)
        }
    }

    @Test
    fun roundTripsExtendedStandFieldsAndExistingRosterData() {
        val original = assignment()

        RosterStore(isolatedContext).saveAssignments(listOf(original))

        val encoded = JSONArray(preferences.getString("assignments", null)).getJSONObject(0)
        assertEquals("IN-DEP", encoded.getString("inboundDepartureStand"))
        assertEquals("OUT-ARR", encoded.getString("outboundArrivalStand"))
        assertEquals("2026-08-30T21:58", encoded.getString("inboundActualDeparture"))
        assertEquals("2026-08-31T03:20", encoded.getString("outboundActualArrival"))
        assertEquals("起飞", encoded.getString("outboundFlightState"))
        assertEquals(listOf(original), RosterStore(isolatedContext).loadAssignments())
    }

    @Test
    fun loadsLegacyJsonWithoutTheTwoNewStandFields() {
        val original = assignment()
        RosterStore(isolatedContext).saveAssignments(listOf(original))
        val legacy = JSONArray(preferences.getString("assignments", null))
        legacy.getJSONObject(0).apply {
            remove("inboundDepartureStand")
            remove("outboundArrivalStand")
        }
        assertTrue(preferences.edit().putString("assignments", legacy.toString()).commit())

        val restored = RosterStore(isolatedContext).loadAssignments()

        assertFalse(legacy.getJSONObject(0).has("inboundDepartureStand"))
        assertFalse(legacy.getJSONObject(0).has("outboundArrivalStand"))
        assertEquals(
            listOf(original.copy(inboundDepartureStand = null, outboundArrivalStand = null)),
            restored,
        )
    }

    @Test
    fun loadsLegacyJsonWithoutTheFlightPhaseFields() {
        // 0.14.0 及更早的存储没有另一端的实际时间与 FlightState，读回来应为 null 而不是解析失败。
        val original = assignment()
        RosterStore(isolatedContext).saveAssignments(listOf(original))
        val legacy = JSONArray(preferences.getString("assignments", null))
        legacy.getJSONObject(0).apply {
            remove("inboundActualDeparture")
            remove("outboundActualArrival")
            remove("inboundFlightState")
            remove("outboundFlightState")
        }
        assertTrue(preferences.edit().putString("assignments", legacy.toString()).commit())

        val restored = RosterStore(isolatedContext).loadAssignments()

        assertEquals(
            listOf(
                original.copy(
                    inboundActualDeparture = null,
                    outboundActualArrival = null,
                    inboundFlightState = null,
                    outboundFlightState = null,
                ),
            ),
            restored,
        )
    }

    @Test
    fun roundTripsNullStandFieldsAlongsidePopulatedAssignments() {
        val populated = assignment()
        val withoutStands = populated.copy(
            aircraftRegistration = "B0002",
            inboundDepartureStand = null,
            arrivalStand = null,
            departureStand = null,
            outboundArrivalStand = null,
        )
        val original = listOf(populated, withoutStands)

        RosterStore(isolatedContext).saveAssignments(original)

        val encoded = JSONArray(preferences.getString("assignments", null)).getJSONObject(1)
        assertTrue(encoded.isNull("inboundDepartureStand"))
        assertTrue(encoded.isNull("outboundArrivalStand"))
        assertEquals(original, RosterStore(isolatedContext).loadAssignments())
    }

    @Test
    fun shiftCalibrationSurvivesAJsonRoundTrip() {
        val calibration = ShiftCalibration(
            date = LocalDate.of(2026, 8, 25),
            observed = ObservedShiftGroups(
                early = listOf(8, 9, 2),
                mid = listOf(6, 4, 10, 3),
                night = listOf(1, 5, 11),
                members = mapOf(
                    3 to listOf("癸丑", "癸寅"),
                    11 to listOf("丙丑", "丙寅"),
                ),
            ),
        )

        RosterStore(isolatedContext).shiftCalibration = calibration
        val restored = RosterStore(isolatedContext).shiftCalibration

        assertEquals(calibration, restored)
        // 还原后必须仍能复现观测当天的顺序，否则相位就丢了。
        assertEquals(
            listOf(8, 9, 2, 6, 4, 10, 3, 1, 5, 11),
            ShiftSchedule(restored).orderFor(LocalDate.of(2026, 8, 25)),
        )
    }

    @Test
    fun shiftCalibrationDefaultsToNothingAndCanBeCleared() {
        val store = RosterStore(isolatedContext)
        assertEquals(null, store.shiftCalibration)

        store.shiftCalibration = ShiftCalibration(
            date = LocalDate.of(2026, 8, 24),
            observed = ObservedShiftGroups(listOf(1, 5, 11), listOf(8, 9, 2, 6), listOf(4, 10, 3), emptyMap()),
        )
        assertTrue(store.shiftCalibration != null)

        store.shiftCalibration = null
        assertEquals(null, RosterStore(isolatedContext).shiftCalibration)
    }

    @Test
    fun corruptShiftCalibrationJsonFallsBackToTheBuiltInTable() {
        preferences.edit().putString("shift_group_calibration", "{ not json").commit()

        assertEquals(null, RosterStore(isolatedContext).shiftCalibration)
        assertFalse(ShiftSchedule(RosterStore(isolatedContext).shiftCalibration).isCalibrated)
    }

    @Test
    fun reportMarginDefaultsAndClampsWhenStored() {
        val store = RosterStore(isolatedContext)
        assertEquals(ShiftBusPlan.DEFAULT_REPORT_MARGIN_MINUTES, store.shiftReportMarginMinutes)

        store.shiftReportMarginMinutes = 30
        assertEquals(30, RosterStore(isolatedContext).shiftReportMarginMinutes)

        store.shiftReportMarginMinutes = -5
        assertEquals(0, RosterStore(isolatedContext).shiftReportMarginMinutes)

        store.shiftReportMarginMinutes = 9_999
        assertEquals(120, RosterStore(isolatedContext).shiftReportMarginMinutes)
    }

    @Test
    fun manualShiftGroupIsStoredWithItsTeamAndCleared() {
        val store = RosterStore(isolatedContext)
        assertEquals(null, store.manualShiftGroup)

        store.manualShiftGroup = ManualShiftGroup(ShiftTeam.SECOND, 3)
        assertEquals(ManualShiftGroup(ShiftTeam.SECOND, 3), RosterStore(isolatedContext).manualShiftGroup)

        store.manualShiftGroup = null
        assertEquals(null, RosterStore(isolatedContext).manualShiftGroup)
    }

    @Test
    fun shuttleAlarmSettingsAndStateSurviveARoundTripAndClearTheirKeys() {
        val store = RosterStore(isolatedContext)
        assertFalse(store.shuttleAlarmEnabled)
        assertEquals(ShuttleAlarmState.EMPTY, store.shuttleAlarmState)

        val date = LocalDate.of(2026, 9, 10)
        val state = ShuttleAlarmState(
            records = listOf(
                ShuttleAlarmRecord(
                    date = date,
                    departure = LocalTime.of(5, 55),
                    times = listOf(LocalTime.of(5, 25), LocalTime.of(5, 30)),
                    setAt = LocalDateTime.of(2026, 9, 9, 6, 35),
                    outcome = ShuttleAlarmOutcome.CONFIRMED,
                    background = true,
                    attempts = 1,
                ),
            ),
            stale = listOf(ShuttleAlarm(date, LocalTime.of(5, 0))),
            pendingVerifyAt = LocalDateTime.of(2026, 9, 9, 11, 52),
            verifyHops = 1,
            lastAttempt = ShuttleAlarmAttempt(
                reason = ShuttleAlarmReason.WAKE,
                at = LocalDateTime.of(2026, 9, 9, 6, 35),
                outcome = ShuttleAlarmOutcome.CONFIRMED,
                background = true,
                summary = "明天 05:25–05:50 共 6 响",
            ),
        )
        store.shuttleAlarmEnabled = true
        store.shuttleAlarmState = state

        assertTrue(RosterStore(isolatedContext).shuttleAlarmEnabled)
        assertEquals(state, RosterStore(isolatedContext).shuttleAlarmState)
        assertTrue(preferences.contains("shuttle_alarm_state"))

        store.shuttleAlarmState = ShuttleAlarmState.EMPTY
        assertFalse(preferences.contains("shuttle_alarm_state"))

        preferences.edit().putString("shuttle_alarm_state", "{broken").commit()
        assertEquals(ShuttleAlarmState.EMPTY, RosterStore(isolatedContext).shuttleAlarmState)
    }

    @Test
    fun aLegacyManualGroupWithoutATeamBelongsToTheFirstTeam() {
        // 0.13 之前只存组号，那时只有一组。
        preferences.edit().putInt("shift_manual_group_id", 8).commit()

        assertEquals(ManualShiftGroup(ShiftTeam.FIRST, 8), RosterStore(isolatedContext).manualShiftGroup)
    }

    @Test
    fun manualShiftTeamIsStoredAndCleared() {
        val store = RosterStore(isolatedContext)
        assertEquals(null, store.manualShiftTeam)

        store.manualShiftTeam = ShiftTeam.SECOND
        assertEquals(ShiftTeam.SECOND, RosterStore(isolatedContext).manualShiftTeam)

        store.manualShiftTeam = null
        assertEquals(null, RosterStore(isolatedContext).manualShiftTeam)
    }

    @Test
    fun syntheticGroupIdsSurviveTheCalibrationRoundTrip() {
        val calibration = ShiftCalibration(
            date = LocalDate.of(2026, 9, 7),
            observed = ObservedShiftGroups(
                early = listOf(1, 2, 3),
                mid = listOf(4, 5, 6, 7),
                night = listOf(8, 9, 10),
                members = mapOf(1 to listOf("王甲子", "李乙丑")),
                hasSyntheticIds = true,
            ),
        )

        RosterStore(isolatedContext).shiftCalibration = calibration
        val restored = RosterStore(isolatedContext).shiftCalibration

        assertEquals(calibration, restored)
        assertTrue(restored!!.observed.hasSyntheticIds)
        assertEquals(ShiftTeam.SECOND, ShiftSchedule(restored).team)
        assertEquals("王甲子组", ShiftSchedule(restored).labelOf(1))
    }

    /** 一组第 2 天晚一：07:10 出港起、次日 00:40 止。 */
    private val nightObservation = ShiftTimeObservation(
        date = LocalDate.of(2026, 8, 24),
        team = ShiftTeam.FIRST,
        kind = ShiftDayKind.WORK_SECOND,
        slot = ShiftSlot(ShiftTier.NIGHT, 1),
        firstTaskMinutes = ShiftClock.of(7, 10),
        inbound = false,
        lastTaskMinutes = ShiftClock.of(0, 40, nextDay = true),
    )

    @Test
    fun shiftTimeHistorySurvivesAJsonRoundTrip() {
        val history = ShiftTimeHistory().record(
            listOf(
                nightObservation,
                ShiftTimeObservation(
                    date = LocalDate.of(2026, 9, 7),
                    team = ShiftTeam.SECOND,
                    kind = ShiftDayKind.WORK_FIRST,
                    slot = ShiftSlot(ShiftTier.MID, 2),
                    firstTaskMinutes = ShiftClock.of(12, 50),
                    inbound = true,
                    lastTaskMinutes = ShiftClock.of(21, 30),
                ),
            ),
        )

        RosterStore(isolatedContext).shiftTimeHistory = history
        val restored = RosterStore(isolatedContext).shiftTimeHistory

        assertEquals(history, restored)
        // 夜班跨零点的末项必须原样回来，否则下班时间会算到当天。
        assertEquals(ShiftClock.of(0, 40, nextDay = true), restored.observations.first().lastTaskMinutes)
    }

    @Test
    fun shiftTimeHistoryDefaultsToEmptyAndCanBeCleared() {
        val store = RosterStore(isolatedContext)
        assertTrue(store.shiftTimeHistory.isEmpty)

        store.shiftTimeHistory = ShiftTimeHistory().record(listOf(nightObservation))
        assertFalse(RosterStore(isolatedContext).shiftTimeHistory.isEmpty)

        store.shiftTimeHistory = ShiftTimeHistory.EMPTY
        assertTrue(RosterStore(isolatedContext).shiftTimeHistory.isEmpty)
        assertFalse(preferences.contains("shift_time_history"))
    }

    @Test
    fun corruptShiftTimeHistoryJsonFallsBackToEmpty() {
        preferences.edit().putString("shift_time_history", "{ not json").commit()

        val history = RosterStore(isolatedContext).shiftTimeHistory
        assertTrue(history.isEmpty)
        assertTrue(history.learnedTimes(ShiftTeam.FIRST).isEmpty)
    }

    private fun assignment() = RosterAssignment(
        aircraftRegistration = "B0001",
        aircraftType = "320",
        inboundFlight = "ZZ1001",
        origin = "测试始发",
        scheduledArrival = LocalDateTime.of(2026, 8, 30, 23, 30),
        outboundFlight = "ZZ1002",
        destination = "测试到达",
        scheduledDeparture = LocalDateTime.of(2026, 8, 31, 1, 0),
        assignees = "TESTUSER",
        estimatedArrival = LocalDateTime.of(2026, 8, 30, 23, 35),
        actualArrival = LocalDateTime.of(2026, 8, 30, 23, 36),
        estimatedDeparture = LocalDateTime.of(2026, 8, 31, 1, 10),
        actualDeparture = LocalDateTime.of(2026, 8, 31, 1, 12),
        inboundBoardingGate = "IN-GATE",
        inboundDepartureStand = "IN-DEP",
        boardingGate = "OUT-GATE",
        departureStand = "OUT-DEP",
        arrivalStand = "IN-ARR",
        inboundGateClosedObservedAt = LocalDateTime.of(2026, 8, 30, 21, 45),
        outboundGateClosedObservedAt = LocalDateTime.of(2026, 8, 31, 0, 55),
        inboundActualOffBlock = LocalDateTime.of(2026, 8, 30, 21, 55),
        outboundActualOffBlock = LocalDateTime.of(2026, 8, 31, 1, 5),
        inboundActualDeparture = LocalDateTime.of(2026, 8, 30, 21, 58),
        outboundActualArrival = LocalDateTime.of(2026, 8, 31, 3, 20),
        inboundFlightState = "到达",
        outboundFlightState = "起飞",
        outboundArrivalStand = "OUT-ARR",
        arrivalBridge = "测试廊桥",
        originCode = "AAA",
        destinationCode = "CCC",
        localAirportCode = "BBB",
        localAirportName = "测试本场",
        inboundHasVip = true,
        outboundHasVip = false,
    )
}
