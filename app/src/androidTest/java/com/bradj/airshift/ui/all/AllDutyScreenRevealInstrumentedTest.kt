package com.bradj.airshift.ui.all

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.ui.theme.AirShiftTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import kotlin.math.abs

/** 最后一条在视口底部点开：展开的内容不能伸到视口之下，条随弹簧顶上去；再点一下收起，条落回原处。 */
@RunWith(AndroidJUnit4::class)
class AllDutyScreenRevealInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun expandingTheLastStripKeepsItInsideTheViewportAndCollapsingBringsItBack() {
        val now = LocalDateTime.of(2026, 9, 4, 16, 8)
        val assignments = (1..20).map { duty(it, now) }
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
        val lastId = assignments.last().stableId
        val tag = "strip_$lastId"
        composeRule.onNodeWithTag("duty_list").performScrollToKey(lastId)
        composeRule.waitForIdle()
        val viewportBottom = composeRule.onNodeWithTag("duty_list").getBoundsInRoot().bottom
        val collapsed = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertTrue("前置：滚到末尾后最后一条应完整在视口里", collapsed.bottom <= viewportBottom)

        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
        val expanded = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertTrue("展开后条应变高：${expanded.height} vs ${collapsed.height}", expanded.height > collapsed.height)
        assertTrue("展开后底边 ${expanded.bottom} 不得超出视口 $viewportBottom", expanded.bottom <= viewportBottom + 1.dp)
        assertTrue("展开时条应顶上去：${expanded.top} vs ${collapsed.top}", expanded.top < collapsed.top)

        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
        val restored = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        assertTrue(
            "收起后应落回原处：${restored.top} vs ${collapsed.top}",
            abs((restored.top - collapsed.top).value) <= 1f,
        )
    }

    private fun duty(index: Int, now: LocalDateTime) = RosterAssignment(
        aircraftRegistration = "B%04d".format(index),
        aircraftType = "320",
        inboundFlight = null,
        origin = null,
        scheduledArrival = null,
        outboundFlight = "ZZ1%03d".format(index),
        destination = "测试到达",
        destinationCode = "ZZZ",
        scheduledDeparture = now.plusMinutes(10L * index),
        assignees = "TESTUSER",
    )
}
