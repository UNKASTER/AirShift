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
import com.bradj.airshift.model.shift.ShiftSchedule
import com.bradj.airshift.ui.theme.AirShiftTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/** 进入排班日历时列表第一条就是今天：今天离列表顶一个条间距，昨天一像素都不露，往上划仍能看到前 7 天。 */
@RunWith(AndroidJUnit4::class)
class ShiftCalendarScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val shiftStrips = SemanticsMatcher("shift strip") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("shift_") == true
    }

    private fun setCalendar(now: LocalDateTime) {
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
                )
            }
        }
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
