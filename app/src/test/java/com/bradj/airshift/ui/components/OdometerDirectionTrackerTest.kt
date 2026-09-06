package com.bradj.airshift.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OdometerDirectionTrackerTest {
    @Test
    fun firstValueRollsUp() {
        assertTrue(OdometerDirectionTracker().update("57"))
    }

    @Test
    fun clockAdvancingRollsUp() {
        val tracker = OdometerDirectionTracker()
        tracker.update("16:08")
        assertTrue(tracker.update("16:09"))
    }

    @Test
    fun countdownDecreasingRollsDownEvenWhenAUnitDigitIncreases() {
        val tracker = OdometerDirectionTracker()
        tracker.update("60")
        // 6→5 与 0→9 必须同向：整体变小，一起向下。
        assertFalse(tracker.update("59"))
    }

    @Test
    fun unchangedTextKeepsLastDirection() {
        val tracker = OdometerDirectionTracker()
        tracker.update("60")
        tracker.update("59")
        assertFalse(tracker.update("59"))
    }

    @Test
    fun nonNumericTextKeepsLastDirection() {
        val tracker = OdometerDirectionTracker()
        tracker.update("60")
        tracker.update("59")
        assertFalse(tracker.update("—"))
        assertTrue(tracker.update("99"))
    }
}
