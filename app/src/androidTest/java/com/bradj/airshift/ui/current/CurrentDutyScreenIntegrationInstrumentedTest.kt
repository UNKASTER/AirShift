package com.bradj.airshift.ui.current

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.nextIncompleteDutyIndex
import com.bradj.airshift.ui.theme.AirShiftTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class CurrentDutyScreenIntegrationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun manualCompletionAndAutomaticCompletionSelectTheCorrectCurrentAndNextDuty() {
        val now = LocalDateTime.now()
        val assignments = listOf(
            duty(1, now, complete = true),
            duty(2, now),
            duty(3, now, complete = true),
            duty(4, now),
        )
        var manuallyCompletedCount by mutableStateOf(0)
        composeRule.setContent {
            val activeIndex = assignments.nextIncompleteDutyIndex(manuallyCompletedCount, now)
            AirShiftTheme {
                CurrentDutyScreen(
                    assignments = assignments,
                    dutyIndex = activeIndex,
                    specialServiceRecords = emptyList(),
                    gateChanges = emptyList(),
                    standChanges = emptyList(),
                    flightCancellations = emptyList(),
                    onDutyComplete = { manuallyCompletedCount = activeIndex + 1 },
                    onGoToAllDuty = {},
                )
            }
        }

        composeRule.onNodeWithText("下一任务 ZZ1004", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("ZZ1003", substring = true).assertDoesNotExist()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("ZZ1002"))
        composeRule.onNodeWithText("ZZ1002").assertIsDisplayed()

        completeCurrentDuty()

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("ZZ1004"))
        composeRule.onNodeWithText("ZZ1004").assertIsDisplayed()
        completeCurrentDuty()
        composeRule.onNodeWithText("今日执勤全部完成").assertIsDisplayed()
    }

    @Test
    fun importingANewNonEmptyRosterRestoresTheCurrentDutyPageAfterCompletion() {
        val now = LocalDateTime.now()
        var assignments by mutableStateOf(listOf(duty(1, now, complete = true)))
        var manuallyCompletedCount by mutableStateOf(1)
        composeRule.setContent {
            val activeIndex = assignments.nextIncompleteDutyIndex(manuallyCompletedCount, now)
            AirShiftTheme {
                CurrentDutyScreen(
                    assignments = assignments,
                    dutyIndex = activeIndex,
                    specialServiceRecords = emptyList(),
                    gateChanges = emptyList(),
                    standChanges = emptyList(),
                    flightCancellations = emptyList(),
                    onDutyComplete = { manuallyCompletedCount = activeIndex + 1 },
                    onGoToAllDuty = {},
                )
            }
        }
        composeRule.onNodeWithText("今日执勤全部完成").assertIsDisplayed()

        composeRule.runOnIdle {
            assignments = listOf(duty(2, now))
            manuallyCompletedCount = 0
        }

        composeRule.onNodeWithText("今日执勤全部完成").assertDoesNotExist()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("ZZ1002"))
        composeRule.onNodeWithText("ZZ1002").assertIsDisplayed()
    }

    private fun completeCurrentDuty() {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("执勤完成"))
        composeRule.onNodeWithText("执勤完成").performClick()
    }

    private fun duty(index: Int, now: LocalDateTime, complete: Boolean = false) = RosterAssignment(
        aircraftRegistration = "B000$index",
        aircraftType = "320",
        inboundFlight = null,
        origin = null,
        scheduledArrival = null,
        outboundFlight = "ZZ100$index",
        destination = "测试到达",
        scheduledDeparture = if (complete) now.minusHours(1) else now.plusHours(index + 1L),
        assignees = "TESTUSER",
        actualDeparture = now.minusMinutes(10).takeIf { complete },
    )
}
