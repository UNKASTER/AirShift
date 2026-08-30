package com.bradj.airshift.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bradj.airshift.model.RosterAssignment
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RosterStorePersistenceInstrumentedTest {
    private lateinit var targetContext: Context
    private lateinit var isolatedContext: Context
    private lateinit var preferences: SharedPreferences
    private val isolatedPreferenceNames = mutableSetOf<String>()

    @Before
    fun isolatePreferences() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "roster_store_test_${UUID.randomUUID()}"
        isolatedContext = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val isolatedName = "${prefix}_$name"
                isolatedPreferenceNames += isolatedName
                return super.getSharedPreferences(isolatedName, mode)
            }
        }
        preferences = isolatedContext.getSharedPreferences("air_shift", Context.MODE_PRIVATE)
    }

    @After
    fun removeOnlyIsolatedTestPreferences() {
        isolatedPreferenceNames.forEach { name ->
            // Flush pending apply() writes before deleting only this test's uniquely named files.
            targetContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            targetContext.deleteSharedPreferences(name)
        }
    }

    @Test
    fun roundTripsExtendedStandFieldsAndExistingRosterData() {
        val original = assignment()

        RosterStore(isolatedContext).saveAssignments(listOf(original))

        val encoded = JSONArray(preferences.getString("assignments", null)).getJSONObject(0)
        assertEquals("IN-DEP", encoded.getString("inboundDepartureStand"))
        assertEquals("OUT-ARR", encoded.getString("outboundArrivalStand"))
        assertEquals(listOf(original), RosterStore(isolatedContext).loadAssignments())
    }

    @Test
    fun loadsLegacyJsonWithoutTheTwoNewStandFields() {
        val original = assignment()
        RosterStore(isolatedContext).saveAssignments(listOf(original))
        val legacy = JSONArray(preferences.getString("assignments", null))
        legacy.getJSONObject(0).apply {
            remove("inboundDepartureStand")
            remove("outboundArrivalStand")
        }
        assertTrue(preferences.edit().putString("assignments", legacy.toString()).commit())

        val restored = RosterStore(isolatedContext).loadAssignments()

        assertFalse(legacy.getJSONObject(0).has("inboundDepartureStand"))
        assertFalse(legacy.getJSONObject(0).has("outboundArrivalStand"))
        assertEquals(
            listOf(original.copy(inboundDepartureStand = null, outboundArrivalStand = null)),
            restored,
        )
    }

    @Test
    fun roundTripsNullStandFieldsAlongsidePopulatedAssignments() {
        val populated = assignment()
        val withoutStands = populated.copy(
            aircraftRegistration = "B0002",
            inboundDepartureStand = null,
            arrivalStand = null,
            departureStand = null,
            outboundArrivalStand = null,
        )
        val original = listOf(populated, withoutStands)

        RosterStore(isolatedContext).saveAssignments(original)

        val encoded = JSONArray(preferences.getString("assignments", null)).getJSONObject(1)
        assertTrue(encoded.isNull("inboundDepartureStand"))
        assertTrue(encoded.isNull("outboundArrivalStand"))
        assertEquals(original, RosterStore(isolatedContext).loadAssignments())
    }

    private fun assignment() = RosterAssignment(
        aircraftRegistration = "B0001",
        aircraftType = "320",
        inboundFlight = "ZZ1001",
        origin = "测试始发",
        scheduledArrival = LocalDateTime.of(2026, 8, 30, 23, 30),
        outboundFlight = "ZZ1002",
        destination = "测试到达",
        scheduledDeparture = LocalDateTime.of(2026, 8, 31, 1, 0),
        assignees = "TESTUSER",
        estimatedArrival = LocalDateTime.of(2026, 8, 30, 23, 35),
        actualArrival = LocalDateTime.of(2026, 8, 30, 23, 36),
        estimatedDeparture = LocalDateTime.of(2026, 8, 31, 1, 10),
        actualDeparture = LocalDateTime.of(2026, 8, 31, 1, 12),
        inboundBoardingGate = "IN-GATE",
        inboundDepartureStand = "IN-DEP",
        boardingGate = "OUT-GATE",
        departureStand = "OUT-DEP",
        arrivalStand = "IN-ARR",
        inboundGateClosedObservedAt = LocalDateTime.of(2026, 8, 30, 21, 45),
        outboundGateClosedObservedAt = LocalDateTime.of(2026, 8, 31, 0, 55),
        inboundActualOffBlock = LocalDateTime.of(2026, 8, 30, 21, 55),
        outboundActualOffBlock = LocalDateTime.of(2026, 8, 31, 1, 5),
        outboundArrivalStand = "OUT-ARR",
        arrivalBridge = "测试廊桥",
        originCode = "AAA",
        destinationCode = "CCC",
        localAirportCode = "BBB",
        localAirportName = "测试本场",
        inboundHasVip = true,
        outboundHasVip = false,
    )
}
