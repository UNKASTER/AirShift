package com.bradj.airshift.reminder

import com.bradj.airshift.api.FlightInfo
import com.bradj.airshift.api.FlightLookup
import com.bradj.airshift.api.FlightRefreshScope
import com.bradj.airshift.data.DutyCompletion
import com.bradj.airshift.data.RosterRepository
import com.bradj.airshift.data.RosterSnapshot
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.shift.LearnedTimes
import com.bradj.airshift.model.shift.ManualShiftGroup
import com.bradj.airshift.model.shift.ShiftBusPlan
import com.bradj.airshift.model.shift.ShiftCalendarRows
import com.bradj.airshift.model.shift.ShiftCalibration
import com.bradj.airshift.model.shift.ShiftSchedule
import com.bradj.airshift.model.shift.ShiftTeam
import com.bradj.airshift.model.shift.ShiftTimeHistory
import com.bradj.airshift.model.shift.ShuttleAlarmPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 闹铃序列的来源与排班日历同一套输入：同一天两边算出的班车必须一致。 */
class ShuttleAlarmSourceTest {
    private val today = LocalDate.of(2026, 8, 30)

    private fun inputs(groupId: Int? = 1) = ShuttleAlarmInputs(
        schedule = ShiftSchedule(),
        groupId = groupId,
        assignments = emptyList(),
        marginMinutes = ShiftBusPlan.DEFAULT_REPORT_MARGIN_MINUTES,
        learned = LearnedTimes.NONE,
    )

    @Test
    fun `the window starts today and matches the calendar rows`() {
        val window = ShuttleAlarmSource.window(inputs(), today)
        val rows = ShiftCalendarRows.build(
            schedule = ShiftSchedule(),
            groupId = 1,
            from = today,
            toInclusive = today.plusDays(ShuttleAlarmPolicy.HORIZON_DAYS.toLong()),
            today = today,
        )

        assertEquals(ShuttleAlarmPolicy.HORIZON_DAYS + 1, window.size)
        rows.forEachIndexed { index, row ->
            assertEquals(row.day.date, today.plusDays(index.toLong()))
            assertEquals(row.bus?.takeIf { row.day.attends }?.departure, window[index]?.departure)
        }
        // 8-30 早二 05:55 → 05:25 起 6 响；9-1 交接班不到岗、9-2 / 9-3 休息。
        assertEquals(ShuttleAlarmPlan.alarmTimes(LocalTime.of(5, 55)), window[0]?.times)
        assertNull(window[2])
        assertNull(window[3])
        assertNull(window[4])
    }

    @Test
    fun `without a resolved group every day is null`() {
        val window = ShuttleAlarmSource.window(inputs(groupId = null), today)

        assertEquals(ShuttleAlarmPolicy.HORIZON_DAYS + 1, window.size)
        assertTrue(window.all { it == null })
    }

    @Test
    fun `inputs are assembled from the store the way the app does`() {
        val store = MemoryRepository().apply {
            userName = "无此人"
            manualShiftGroup = ManualShiftGroup(ShiftTeam.FIRST, 1)
            shiftReportMarginMinutes = 30
        }

        val inputs = ShuttleAlarmSource.inputs(store)

        assertEquals(1, inputs.groupId)
        assertEquals(30, inputs.marginMinutes)
        assertEquals(ShiftTeam.FIRST, inputs.schedule.team)
        // 余量 30：05:55 车到场 06:00 晚于 06:10−30，退到 05:25。
        assertEquals(LocalTime.of(5, 25), ShuttleAlarmSource.window(store, today)[0]?.departure)
    }
}

/** 只实现来源用到的成员；其余抛错以免测试悄悄依赖它们。 */
private class MemoryRepository : RosterRepository {
    override var userName: String? = null
    override var variFlightApiKey: String? = null
    override val hasVariFlightApiKey: Boolean get() = false
    override fun clearVariFlightApiKey() = Unit
    override var shiftReportMarginMinutes: Int = ShiftBusPlan.DEFAULT_REPORT_MARGIN_MINUTES
    override var manualShiftTeam: ShiftTeam? = null
    override var manualShiftGroup: ManualShiftGroup? = null
    override var shiftCalibration: ShiftCalibration? = null
    override var shiftTimeHistory: ShiftTimeHistory = ShiftTimeHistory.EMPTY
    override var shuttleAlarmEnabled: Boolean = false
    override var shuttleAlarmState: ShuttleAlarmState = ShuttleAlarmState.EMPTY
    override val currentDutyIndex: Int get() = 0
    override val rosterGeneration: Long get() = 0
    override fun loadSnapshot(): RosterSnapshot = RosterSnapshot(emptyList(), 0, 0)
    override fun replaceAssignments(assignments: List<RosterAssignment>): Long = error("unused")
    override fun completeCurrentDuty(
        expectedGeneration: Long,
        expectedDutyIndex: Int,
        now: LocalDateTime,
    ): DutyCompletion? = error("unused")

    override fun mergeLiveInfoIfGeneration(
        live: Map<FlightLookup, List<FlightInfo>>,
        expectedGeneration: Long,
        fallbackDate: LocalDate,
        refreshedAtEpochMillis: Long?,
        scope: FlightRefreshScope,
    ): RosterSnapshot? = error("unused")

    override fun runIfGenerationCurrent(expectedGeneration: Long, action: () -> Unit): Boolean = error("unused")
}
