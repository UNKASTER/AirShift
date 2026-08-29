package com.bradj.airshift.data

import android.content.Context
import androidx.core.content.edit
import com.bradj.airshift.model.RosterAssignment
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

class RosterStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val variFlightApiKeyStore = VariFlightApiKeyStore(context.applicationContext)

    init {
        if (preferences.contains(KEY_LEGACY_SUPPLEMENT) || preferences.contains(KEY_LEGACY_GATEWAY_URL)) {
            preferences.edit {
                remove(KEY_LEGACY_SUPPLEMENT)
                remove(KEY_LEGACY_GATEWAY_URL)
            }
        }
    }

    var userName: String?
        get() = preferences.getString(KEY_USER_NAME, null)?.takeIf { it.isNotBlank() }
        set(value) {
            preferences.edit { putString(KEY_USER_NAME, value?.trim()) }
        }

    var variFlightApiKey: String?
        get() = variFlightApiKeyStore.value
        set(value) {
            variFlightApiKeyStore.value = value
        }

    val hasVariFlightApiKey: Boolean
        get() = variFlightApiKey != null

    fun clearVariFlightApiKey() {
        variFlightApiKeyStore.clear()
    }

    var lastLiveRefreshEpochMillis: Long?
        get() = if (preferences.contains(KEY_LAST_LIVE_REFRESH)) {
            preferences.getLong(KEY_LAST_LIVE_REFRESH, 0L)
        } else {
            null
        }
        set(value) {
            preferences.edit {
                if (value == null) remove(KEY_LAST_LIVE_REFRESH) else putLong(KEY_LAST_LIVE_REFRESH, value)
            }
        }

    val currentDutyIndex: Int
        get() {
            val progressDate = preferences.getString(KEY_DUTY_PROGRESS_DATE, null)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (progressDate != LocalDate.now()) return 0
            return preferences.getInt(KEY_DUTY_INDEX, 0).coerceAtLeast(0)
        }

    fun resetDutyProgress() {
        preferences.edit {
            putString(KEY_DUTY_PROGRESS_DATE, LocalDate.now().toString())
            putInt(KEY_DUTY_INDEX, 0)
        }
    }

    fun advanceDutyIndex() {
        preferences.edit {
            putString(KEY_DUTY_PROGRESS_DATE, LocalDate.now().toString())
            putInt(KEY_DUTY_INDEX, currentDutyIndex + 1)
        }
    }

    fun saveAssignments(assignments: List<RosterAssignment>) {
        val array = JSONArray()
        assignments.forEach { assignment -> array.put(assignment.toJson()) }
        preferences.edit { putString(KEY_ASSIGNMENTS, array.toString()) }
    }

    fun loadAssignments(): List<RosterAssignment> {
        val raw = preferences.getString(KEY_ASSIGNMENTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toAssignment())
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun RosterAssignment.toJson() = JSONObject().apply {
        put("aircraftRegistration", aircraftRegistration)
        putNullable("aircraftType", aircraftType)
        putNullable("inboundFlight", inboundFlight)
        putNullable("origin", origin)
        putNullable("scheduledArrival", scheduledArrival?.toString())
        putNullable("outboundFlight", outboundFlight)
        putNullable("destination", destination)
        putNullable("scheduledDeparture", scheduledDeparture?.toString())
        put("assignees", assignees)
        putNullable("estimatedArrival", estimatedArrival?.toString())
        putNullable("actualArrival", actualArrival?.toString())
        putNullable("estimatedDeparture", estimatedDeparture?.toString())
        putNullable("actualDeparture", actualDeparture?.toString())
        putNullable("inboundBoardingGate", inboundBoardingGate)
        putNullable("inboundDepartureStand", inboundDepartureStand)
        putNullable("boardingGate", boardingGate)
        putNullable("departureStand", departureStand)
        putNullable("arrivalStand", arrivalStand)
        putNullable("inboundGateClosedObservedAt", inboundGateClosedObservedAt?.toString())
        putNullable("outboundGateClosedObservedAt", outboundGateClosedObservedAt?.toString())
        putNullable("inboundActualOffBlock", inboundActualOffBlock?.toString())
        putNullable("outboundActualOffBlock", outboundActualOffBlock?.toString())
        putNullable("outboundArrivalStand", outboundArrivalStand)
        putNullable("arrivalBridge", arrivalBridge)
        putNullable("originCode", originCode)
        putNullable("destinationCode", destinationCode)
        putNullable("localAirportCode", localAirportCode)
        putNullable("localAirportName", localAirportName)
        put("inboundHasVip", inboundHasVip)
        put("outboundHasVip", outboundHasVip)
    }

    private fun JSONObject.toAssignment() = RosterAssignment(
        aircraftRegistration = getString("aircraftRegistration"),
        aircraftType = nullableString("aircraftType"),
        inboundFlight = nullableString("inboundFlight"),
        origin = nullableString("origin"),
        scheduledArrival = nullableDateTime("scheduledArrival"),
        outboundFlight = nullableString("outboundFlight"),
        destination = nullableString("destination"),
        scheduledDeparture = nullableDateTime("scheduledDeparture"),
        assignees = getString("assignees"),
        estimatedArrival = nullableDateTime("estimatedArrival"),
        actualArrival = nullableDateTime("actualArrival"),
        estimatedDeparture = nullableDateTime("estimatedDeparture"),
        actualDeparture = nullableDateTime("actualDeparture"),
        inboundBoardingGate = nullableString("inboundBoardingGate"),
        inboundDepartureStand = nullableString("inboundDepartureStand"),
        boardingGate = nullableString("boardingGate"),
        departureStand = nullableString("departureStand"),
        arrivalStand = nullableString("arrivalStand") ?: nullableString("arrivalGate"),
        inboundGateClosedObservedAt = nullableDateTime("inboundGateClosedObservedAt"),
        outboundGateClosedObservedAt = nullableDateTime("outboundGateClosedObservedAt"),
        inboundActualOffBlock = nullableDateTime("inboundActualOffBlock"),
        outboundActualOffBlock = nullableDateTime("outboundActualOffBlock"),
        outboundArrivalStand = nullableString("outboundArrivalStand"),
        arrivalBridge = nullableString("arrivalBridge"),
        originCode = nullableString("originCode"),
        destinationCode = nullableString("destinationCode"),
        localAirportCode = nullableString("localAirportCode"),
        localAirportName = nullableString("localAirportName"),
        inboundHasVip = optBoolean("inboundHasVip", false),
        outboundHasVip = optBoolean("outboundHasVip", false),
    )

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.nullableDateTime(key: String): LocalDateTime? =
        nullableString(key)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    companion object {
        private const val FILE_NAME = "air_shift"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_LAST_LIVE_REFRESH = "last_live_refresh"
        private const val KEY_DUTY_PROGRESS_DATE = "duty_progress_date"
        private const val KEY_DUTY_INDEX = "duty_index"
        private const val KEY_ASSIGNMENTS = "assignments"
        private const val KEY_LEGACY_SUPPLEMENT = "roster_supplement"
        private const val KEY_LEGACY_GATEWAY_URL = "gateway_url"
    }
}
