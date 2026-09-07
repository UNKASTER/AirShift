package com.bradj.airshift.data

import com.bradj.airshift.model.shift.ShiftClock
import com.bradj.airshift.model.shift.ShiftDayKind
import com.bradj.airshift.model.shift.ShiftSlot
import com.bradj.airshift.model.shift.ShiftTeam
import com.bradj.airshift.model.shift.ShiftTier
import com.bradj.airshift.model.shift.ShiftTimeHistory
import com.bradj.airshift.model.shift.ShiftTimeObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShiftTimeHistoryCodecTest {
    private val night = ShiftTimeObservation(
        date = LocalDate.of(2026, 8, 24),
        team = ShiftTeam.FIRST,
        kind = ShiftDayKind.WORK_SECOND,
        slot = ShiftSlot(ShiftTier.NIGHT, 1),
        firstTaskMinutes = ShiftClock.of(7, 10),
        inbound = false,
        lastTaskMinutes = ShiftClock.of(0, 40, nextDay = true),
    )
    private val mid = ShiftTimeObservation(
        date = LocalDate.of(2026, 9, 7),
        team = ShiftTeam.SECOND,
        kind = ShiftDayKind.WORK_FIRST,
        slot = ShiftSlot(ShiftTier.MID, 2),
        firstTaskMinutes = ShiftClock.of(12, 50),
        inbound = true,
        lastTaskMinutes = ShiftClock.of(21, 30),
    )

    @Test
    fun `a history survives the json round trip`() {
        val history = ShiftTimeHistory().record(listOf(night, mid))
        val restored = ShiftTimeHistoryCodec.decode(ShiftTimeHistoryCodec.encode(history))
        assertEquals(history, restored)
        assertEquals(ShiftClock.of(0, 40, nextDay = true), restored!!.observations.first().lastTaskMinutes)
        assertTrue(restored.observations.last().inbound)
    }

    @Test
    fun `the encoding is a flat array without names`() {
        val encoded = ShiftTimeHistoryCodec.encode(ShiftTimeHistory(listOf(night)))
        assertTrue(encoded.startsWith("[{"))
        assertTrue(encoded.contains("\"kind\":\"WORK_SECOND\""))
        assertTrue(encoded.contains("\"tier\":\"NIGHT\""))
        assertEquals("[]", ShiftTimeHistoryCodec.encode(ShiftTimeHistory.EMPTY))
    }

    @Test
    fun `an empty array decodes to the empty history`() {
        assertEquals(ShiftTimeHistory.EMPTY, ShiftTimeHistoryCodec.decode("[]"))
    }

    @Test
    fun `corrupt json decodes to nothing`() {
        assertNull(ShiftTimeHistoryCodec.decode("{ not json"))
        assertNull(ShiftTimeHistoryCodec.decode("{\"date\":\"2026-08-24\"}"))
    }

    @Test
    fun `an unknown enum value fails the whole decode`() {
        val encoded = ShiftTimeHistoryCodec.encode(ShiftTimeHistory(listOf(night, mid)))
        assertNull(ShiftTimeHistoryCodec.decode(encoded.replace("\"SECOND\"", "\"THIRD\"")))
        assertNull(ShiftTimeHistoryCodec.decode(encoded.replace("\"NIGHT\"", "\"DAWN\"")))
    }
}
