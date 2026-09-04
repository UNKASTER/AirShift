package com.bradj.airshift.ui.all

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.ui.theme.AirShiftTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/** 全部执勤页把任务分进"当前 / 接下来 / 已完成"三个栏位，点条就地展开出机号等细节。 */
@RunWith(AndroidJUnit4::class)
class AllDutyScreenBaysInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tasksAreSplitIntoBaysAndTheCurrentStripExpandsOnTap() {
        val now = LocalDateTime.of(2026, 9, 4, 16, 8)
        val assignments = listOf(
            duty(1, now, complete = true),
            duty(2, now),
            duty(3, now),
        )
        composeRule.setContent {
            AirShiftTheme {
                AllDutyScreen(
                    currentAirport = null,
                    now = now,
                    isWorking = false,
                    isLiveRefreshing = false,
                    statusMessage = null,
                    warnings = emptyList(),
                    exactAlarmWarning = false,
                    assignments = assignments,
                    manuallyCompletedCount = 0,
                    specialServiceRecords = emptyList(),
                    gateChanges = emptyList(),
                    standChanges = emptyList(),
                    flightCancellations = emptyList(),
                    onImportImage = {},
                    onImportExcel = {},
                    onRefresh = {},
                    onOpenExactAlarmSettings = {},
                )
            }
        }

        composeRule.onNodeWithTag("bay_current").assertIsDisplayed()
        composeRule.onNodeWithTag("bay_upcoming").assertIsDisplayed()
        composeRule.onNodeWithTag("bay_completed").assertIsDisplayed()
        composeRule.onNodeWithText("机号").assertDoesNotExist()

        composeRule.onNodeWithTag("strip_${assignments[1].stableId}").performClick()

        composeRule.onNodeWithText("机号").assertIsDisplayed()
        composeRule.onNodeWithText("出港保障").assertIsDisplayed()
    }

    private fun duty(index: Int, now: LocalDateTime, complete: Boolean = false) = RosterAssignment(
        aircraftRegistration = "B000$index",
        aircraftType = "320",
        inboundFlight = null,
        origin = null,
        scheduledArrival = null,
        outboundFlight = "ZZ100$index",
        destination = "测试到达",
        destinationCode = "ZZZ",
        scheduledDeparture = if (complete) now.minusHours(1) else now.plusHours(index + 1L),
        assignees = "TESTUSER",
        actualDeparture = now.minusMinutes(10).takeIf { complete },
    )
}
