package com.bradj.airshift.ui.components

import com.bradj.airshift.specialservice.Confidence
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.ReviewStatus
import com.bradj.airshift.specialservice.ServiceType
import com.bradj.airshift.specialservice.WheelchairLevel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SpecialServiceLabelsTest {
    private val date = LocalDate.of(2026, 9, 4)

    @Test
    fun wheelchairLabelsUseSingleLetterInsteadOfFullCode() {
        val wchs = record(ServiceType.WHEELCHAIR, WheelchairLevel.WCHS, count = 2)
        assertEquals("S ×2", wchs.badgeLabel())
        assertEquals("S", wchs.typeLabel())

        assertEquals("C", record(ServiceType.WHEELCHAIR, WheelchairLevel.WCHC, count = null).badgeLabel())
        assertEquals("R", record(ServiceType.WHEELCHAIR, WheelchairLevel.WCHR, count = null).typeLabel())
    }

    @Test
    fun wheelchairWithoutLevelLeavesOnlyCountForTheIcon() {
        val unknown = record(ServiceType.WHEELCHAIR, level = null, count = 1)
        assertEquals("×1", unknown.badgeLabel())
        assertEquals("轮椅旅客", unknown.typeLabel())
        assertEquals("", record(ServiceType.WHEELCHAIR, level = null, count = null).badgeLabel())
    }

    @Test
    fun otherServiceLabelsAreUnchanged() {
        assertEquals("UM ×2", record(ServiceType.UNACCOMPANIED_MINOR, level = null, count = 2).badgeLabel())
        assertEquals("客舱宠物", record(ServiceType.CABIN_PET, level = null, count = null).badgeLabel())
        assertEquals("MAAS 全流程陪伴", record(ServiceType.MAAS, level = null, count = null).typeLabel())
    }

    private fun record(type: ServiceType, level: WheelchairLevel?, count: Int?) = FlightServiceRecord(
        flightNumber = "MU5102",
        operationDate = date,
        serviceType = type,
        wheelchairLevel = level,
        count = count,
        updatedAtEpochMillis = 1_000L,
        confidence = Confidence.HIGH,
        reviewStatus = ReviewStatus.CONFIRMED,
        expiresAtEpochMillis = 2_000L,
        fingerprint = "svc",
    )
}
