package com.bradj.airshift.ui.calendar

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bradj.airshift.model.shift.LearnedTimes
import com.bradj.airshift.model.shift.ShiftBusPlan
import com.bradj.airshift.model.shift.ShiftCalendarRows
import com.bradj.airshift.model.shift.ShiftSchedule
import com.bradj.airshift.model.shift.ShuttleAlarmPlan
import com.bradj.airshift.reminder.ShuttleAlarm
import com.bradj.airshift.reminder.ShuttleAlarmOutcome
import com.bradj.airshift.reminder.ShuttleAlarmRecord
import com.bradj.airshift.reminder.ShuttleAlarmState
import com.bradj.airshift.reminder.ShuttleAlarmText
import com.bradj.airshift.ui.theme.AirShiftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.LocalTime

/** 进入排班日历时列表第一条就是今天：今天离列表顶一个条间距，昨天一像素都不露，往上划仍能看到前 7 天。 */
@RunWith(AndroidJUnit4::class)
class ShiftCalendarScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val shiftStrips = SemanticsMatcher("shift strip") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("shift_") == true
    }

    private fun setCalendar(
        now: LocalDateTime,
        shuttleAlarmEnabled: Boolean = false,
        shuttleAlarmState: ShuttleAlarmState = ShuttleAlarmState.EMPTY,
    ) {
        composeRule.setContent {
            AirShiftTheme {
                ShiftCalendarScreen(
                    userName = "TESTUSER",
                    schedule = ShiftSchedule(),
                    groupId = 1,
                    assignments = emptyList(),
                    reportMarginMinutes = ShiftBusPlan.DEFAULT_REPORT_MARGIN_MINUTES,
                    learned = LearnedTimes.NONE,
                    now = now,
                    onGoToSettings = {},
                    shuttleAlarmEnabled = shuttleAlarmEnabled,
                    shuttleAlarmState = shuttleAlarmState,
                )
            }
        }
    }

    /** 班车闹铃打开时：到岗行多一行闹铃序列与状态灯，顶部列出要手动关的旧闹铃；今天仍是首项。 */
    @Test
    fun shuttleAlarmLinesAndStaleNoticeAppearWhenEnabled() {
        // 组 1：8-30 早二，班车 05:55 → 05:25 起 6 响；序列文案从模型算，不手写。
        val now = LocalDateTime.of(2026, 8, 30, 3, 0)
        val today = now.toLocalDate()
        val row = ShiftCalendarRows.build(ShiftSchedule(), 1, today, today, today).single()
        val plan = ShuttleAlarmPlan.fromRow(row)!!
        val state = ShuttleAlarmState(
            records = listOf(
                ShuttleAlarmRecord(
                    date = today,
                    departure = plan.departure,
                    times = plan.times,
                    setAt = now,
                    outcome = ShuttleAlarmOutcome.CONFIRMED,
                    background = true,
                    attempts = 1,
                ),
            ),
            stale = listOf(ShuttleAlarm(today, LocalTime.of(4, 0)), ShuttleAlarm(today, LocalTime.of(4, 5))),
        )
        setCalendar(now, shuttleAlarmEnabled = true, shuttleAlarmState = state)

        composeRule.onNodeWithText("班车时间变了，请在时钟里关掉旧闹铃").assertIsDisplayed()
        composeRule.onNodeWithText("今天：04:00、04:05").assertIsDisplayed()
        composeRule.onNodeWithTag("shift_$today").assertIsDisplayed()
        assertEquals("闹铃 05:25 起 · 6 响", ShuttleAlarmText.baseLine(plan))
        assertTrue(composeRule.onAllNodesWithText("闹铃 05:25 起 · 6 响").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("已设").assertIsDisplayed()
        val listTop = composeRule.onNodeWithTag("calendar_list").getBoundsInRoot().top
        val todayTop = composeRule.onNodeWithTag("shift_$today").getUnclippedBoundsInRoot().top
        assertEquals("提示条在列表之外，今天仍离列表顶一个条间距", 8f, (todayTop - listTop).value, 1f)
    }

    /** 国庆当天：板头日期带节日名，放假日的行写节日名，调休上班日的行写"补班"。 */
    @Test
    fun publicHolidaysAreMarkedOnTheBoardAndInTheRows() {
        setCalendar(LocalDateTime.of(2026, 10, 1, 10, 0))

        composeRule.onNodeWithText("10月1日 周四 · 国庆节").assertIsDisplayed()
        composeRule.onNodeWithTag("shift_2026-10-01").assertIsDisplayed()
        // 板头一处 + 首屏里 10/1 起的放假行，至少两处写着节日名。
        assertTrue(composeRule.onAllNodesWithText("国庆节", substring = true).fetchSemanticsNodes().size >= 2)

        composeRule.onNodeWithTag("calendar_list").performScrollToKey("2026-10-10")
        composeRule.onNodeWithText("补班").assertIsDisplayed()
    }

    @Test
    fun theCalendarOpensWithTodayAsTheFirstVisibleRow() {
        val now = LocalDateTime.of(2026, 9, 4, 16, 8)
        val today = now.toLocalDate()
        setCalendar(now)

        composeRule.onNodeWithTag("shift_$today").assertIsDisplayed()
        val listTop = composeRule.onNodeWithTag("calendar_list").getBoundsInRoot().top
        val todayTop = composeRule.onNodeWithTag("shift_$today").getUnclippedBoundsInRoot().top
        assertEquals("今天离列表顶应正好一个条间距", 8f, (todayTop - listTop).value, 1f)

        val strips = composeRule.onAllNodes(shiftStrips)
        val nodes = strips.fetchSemanticsNodes()
        val topmost = nodes.indices
            .filter { strips[it].isDisplayed() }
            .minByOrNull { strips[it].getUnclippedBoundsInRoot().top }
        assertEquals("shift_$today", nodes[topmost!!].config[SemanticsProperties.TestTag])

        // 昨天要么根本没被组合出来，要么组合了但一像素都不可见。
        val yesterday = "shift_${today.minusDays(1)}"
        if (composeRule.onAllNodesWithTag(yesterday).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag(yesterday).assertIsNotDisplayed()
        }
    }
}
