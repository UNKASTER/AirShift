package com.bradj.airshift.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class OdometerSlotsTest {
    @Test
    fun `digits animate and everything else stays put`() {
        val slots = odometerSlots("16:08")
        assertEquals(listOf('1', '6', ':', '0', '8'), slots.map { it.char })
        assertEquals(listOf(true, true, false, true, true), slots.map { it.animated })
    }

    @Test
    fun `chinese units are static slots`() {
        val slots = odometerSlots("57分")
        assertEquals(listOf(true, true, false), slots.map { it.animated })
    }
}
