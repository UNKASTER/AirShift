package com.bradj.airshift.data

import android.content.Context
import androidx.core.content.edit
import com.bradj.airshift.model.RosterAssignment
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

class RosterStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var userName: String?
        get() = preferences.getString(KEY_USER_NAME, null)?.takeIf { it.isNotBlank() }
        set(value) {
            preferences.edit { putString(KEY_USER_NAME, value?.trim()) }
        }

    var gatewayBaseUrl: String?
        get() = preferences.getString(KEY_GATEWAY_URL, null)?.trim()?.trimEnd('/')
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://10.") }
        set(value) {
            preferences.edit { putString(KEY_GATEWAY_URL, value?.trim()?.trimEnd('/')) }
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
        putNullable("arrivalGate", arrivalGate)
        putNullable("arrivalBridge", arrivalBridge)
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
        arrivalGate = nullableString("arrivalGate"),
        arrivalBridge = nullableString("arrivalBridge"),
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
        private const val KEY_GATEWAY_URL = "gateway_url"
        private const val KEY_ASSIGNMENTS = "assignments"
    }
}
