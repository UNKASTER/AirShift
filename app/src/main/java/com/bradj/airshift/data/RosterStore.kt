package com.bradj.airshift.data

import android.content.Context
import androidx.core.content.edit
import com.bradj.airshift.api.FlightInfo
import com.bradj.airshift.api.FlightLookup
import com.bradj.airshift.api.FlightRefreshScope
import com.bradj.airshift.api.dutyWindowLookups
import com.bradj.airshift.api.refreshIndices
import com.bradj.airshift.api.refreshLookups
import com.bradj.airshift.api.withLiveInfo
import com.bradj.airshift.model.DutyProgressDay
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.dutyWindowIndices
import com.bradj.airshift.model.shift.ObservedShiftGroups
import com.bradj.airshift.model.shift.ShiftBusPlan
import com.bradj.airshift.model.shift.ShiftCalibration
import org.json.JSONArray
import org.json.JSONObject
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

data class RosterSnapshot(
    val assignments: List<RosterAssignment>,
    val generation: Long,
    val manuallyCompletedCount: Int,
)

internal data class DutyCompletion(
    val snapshot: RosterSnapshot,
    val newlyTrackedFlights: Set<FlightLookup>,
)

/**
 * 排班、人工进度与 generation 的本地存储。
 *
 * [clock] 决定“现在”：人工进度按 [DutyProgressDay] 归属执勤日（06:00 切换，而非自然日零点），
 * 测试可注入固定时钟复现跨零点场景。遗留键的清理已移到 [LegacyMigrations]，构造本类不再触发
 * Keystore 访问；API Key 存储按需惰性创建。
 */
class RosterStore(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val applicationContext = context.applicationContext
    private val variFlightApiKeyStore by lazy { VariFlightApiKeyStore(applicationContext) }

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

    /** 只看是否存有密文，不解密；Keystore 瞬时故障不会让后台刷新被误判为“没有 Key”而取消。 */
    val hasVariFlightApiKey: Boolean
        get() = variFlightApiKeyStore.hasValue

    fun clearVariFlightApiKey() {
        variFlightApiKeyStore.clear()
    }

    /**
     * 排班日历：到位余量（分钟）。到位要求本身是硬规定，余量是用户自己给班车留的富余。
     */
    var shiftReportMarginMinutes: Int
        get() = preferences.getInt(KEY_SHIFT_REPORT_MARGIN, ShiftBusPlan.DEFAULT_REPORT_MARGIN_MINUTES)
        set(value) {
            preferences.edit { putInt(KEY_SHIFT_REPORT_MARGIN, value.coerceIn(0, MAX_SHIFT_REPORT_MARGIN)) }
        }

    /** 排班日历：手动指定班组，仅在姓名匹配不到任何班组时作为兜底。 */
    var manualShiftGroupId: Int?
        get() = preferences.getInt(KEY_SHIFT_MANUAL_GROUP, 0).takeIf { it > 0 }
        set(value) {
            preferences.edit {
                if (value == null) remove(KEY_SHIFT_MANUAL_GROUP) else putInt(KEY_SHIFT_MANUAL_GROUP, value)
            }
        }

    /**
     * 排班日历：最近一次从 Excel“候机早班/中班/夜班”行读到的真实分组。
     * 用于校正内置班组表与轮转相位；解析不到时保持原值，内置表继续生效。
     */
    var shiftCalibration: ShiftCalibration?
        get() = preferences.getString(KEY_SHIFT_CALIBRATION, null)?.let(::decodeShiftCalibration)
        set(value) {
            preferences.edit {
                if (value == null) {
                    remove(KEY_SHIFT_CALIBRATION)
                } else {
                    putString(KEY_SHIFT_CALIBRATION, encodeShiftCalibration(value))
                }
            }
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
        get() = synchronized(rosterLock) { readDutyIndex(dutyDay()) }

    val rosterGeneration: Long
        get() = synchronized(rosterLock) { preferences.getLong(KEY_ROSTER_GENERATION, 0L) }

    fun setCurrentDutyIndex(index: Int) {
        synchronized(rosterLock) {
            preferences.edit {
                putString(KEY_DUTY_PROGRESS_DATE, dutyDay().toString())
                putInt(KEY_DUTY_INDEX, index.coerceIn(0, loadAssignments().size))
            }
        }
    }

    /**
     * Completes exactly the duty shown by the caller. The generation and index guards make a
     * repeated tap from stale UI a no-op instead of completing the following duty.
     */
    internal fun completeCurrentDuty(
        expectedGeneration: Long,
        expectedDutyIndex: Int,
        now: LocalDateTime = LocalDateTime.now(clock),
    ): DutyCompletion? = synchronized(rosterLock) {
        if (preferences.getLong(KEY_ROSTER_GENERATION, 0L) != expectedGeneration) return@synchronized null
        val before = loadSnapshot()
        val currentIndex = before.assignments
            .dutyWindowIndices(before.manuallyCompletedCount, now)
            .firstOrNull()
        if (currentIndex != expectedDutyIndex) return@synchronized null

        val oldWindow = before.assignments.dutyWindowLookups(before.manuallyCompletedCount, now)
        setCurrentDutyIndex(currentIndex + 1)
        val after = loadSnapshot()
        val newWindow = after.assignments.dutyWindowLookups(after.manuallyCompletedCount, now)
        DutyCompletion(
            snapshot = after,
            newlyTrackedFlights = newWindow - oldWindow,
        )
    }

    fun loadSnapshot(): RosterSnapshot = synchronized(rosterLock) {
        RosterSnapshot(
            assignments = loadAssignments(),
            generation = preferences.getLong(KEY_ROSTER_GENERATION, 0L),
            manuallyCompletedCount = readDutyIndex(dutyDay()),
        )
    }

    fun replaceAssignments(assignments: List<RosterAssignment>): Long {
        val encoded = encodeAssignments(assignments)
        return synchronized(rosterLock) {
            val generation = Math.addExact(preferences.getLong(KEY_ROSTER_GENERATION, 0L), 1L)
            preferences.edit {
                putString(KEY_ASSIGNMENTS, encoded)
                putString(KEY_DUTY_PROGRESS_DATE, dutyDay().toString())
                putInt(KEY_DUTY_INDEX, 0)
                putLong(KEY_ROSTER_GENERATION, generation)
            }
            generation
        }
    }

    fun saveAssignments(assignments: List<RosterAssignment>) {
        val encoded = encodeAssignments(assignments)
        synchronized(rosterLock) {
            preferences.edit { putString(KEY_ASSIGNMENTS, encoded) }
        }
    }

    fun saveAssignmentsIfGeneration(
        assignments: List<RosterAssignment>,
        expectedGeneration: Long,
        refreshedAtEpochMillis: Long? = null,
    ): Boolean {
        val encoded = encodeAssignments(assignments)
        return synchronized(rosterLock) {
            if (preferences.getLong(KEY_ROSTER_GENERATION, 0L) != expectedGeneration) return@synchronized false
            preferences.edit {
                putString(KEY_ASSIGNMENTS, encoded)
                refreshedAtEpochMillis?.let { putLong(KEY_LAST_LIVE_REFRESH, it) }
            }
            true
        }
    }

    internal fun mergeLiveInfoIfGeneration(
        live: Map<FlightLookup, List<FlightInfo>>,
        expectedGeneration: Long,
        fallbackDate: LocalDate,
        refreshedAtEpochMillis: Long? = null,
        scope: FlightRefreshScope = FlightRefreshScope.DUTY_WINDOW,
    ): RosterSnapshot? = synchronized(rosterLock) {
        val current = loadSnapshot()
        if (current.generation != expectedGeneration) return@synchronized null
        val now = LocalDateTime.now(clock)
        val targetIndices = current.assignments.refreshIndices(current.manuallyCompletedCount, scope, now).toSet()
        val allowedLookups = current.assignments.refreshLookups(current.manuallyCompletedCount, scope, now)
        val relevantLive = live.filterKeys { it in allowedLookups }
        if (relevantLive.isEmpty()) return@synchronized current

        // Refreshes may overlap a duty completion or another refresh; merge into the latest roster.
        val updated = current.assignments.mapIndexed { index, assignment ->
            if (index in targetIndices) assignment.withLiveInfo(relevantLive, fallbackDate) else assignment
        }
        preferences.edit {
            putString(KEY_ASSIGNMENTS, encodeAssignments(updated))
            refreshedAtEpochMillis?.let { putLong(KEY_LAST_LIVE_REFRESH, it) }
        }
        current.copy(assignments = updated)
    }

    // Keep short follow-up effects ordered with imports; never perform network work in this block.
    internal fun runIfGenerationCurrent(expectedGeneration: Long, action: () -> Unit): Boolean =
        synchronized(rosterLock) {
            if (preferences.getLong(KEY_ROSTER_GENERATION, 0L) != expectedGeneration) return@synchronized false
            action()
            true
        }

    /** 当前执勤日：06:00 之前仍属前一天，夜班跨零点后的人工完成不会被清零。 */
    private fun dutyDay(): LocalDate = DutyProgressDay.of(LocalDateTime.now(clock))

    private fun readDutyIndex(dutyDay: LocalDate): Int {
        val progressDate = preferences.getString(KEY_DUTY_PROGRESS_DATE, null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (progressDate != dutyDay) return 0
        return preferences.getInt(KEY_DUTY_INDEX, 0).coerceAtLeast(0)
    }

    private fun encodeAssignments(assignments: List<RosterAssignment>): String {
        val array = JSONArray()
        assignments.forEach { assignment -> array.put(assignment.toJson()) }
        return array.toString()
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

    private fun encodeShiftCalibration(calibration: ShiftCalibration): String =
        JSONObject().apply {
            put("date", calibration.date.toString())
            put("early", JSONArray(calibration.observed.early))
            put("mid", JSONArray(calibration.observed.mid))
            put("night", JSONArray(calibration.observed.night))
            put(
                "members",
                JSONObject().apply {
                    calibration.observed.members.forEach { (id, names) -> put(id.toString(), JSONArray(names)) }
                },
            )
        }.toString()

    private fun decodeShiftCalibration(raw: String): ShiftCalibration? = runCatching {
        val json = JSONObject(raw)
        val members = json.optJSONObject("members")
        ShiftCalibration(
            date = LocalDate.parse(json.getString("date")),
            observed = ObservedShiftGroups(
                early = json.getJSONArray("early").toIntList(),
                mid = json.getJSONArray("mid").toIntList(),
                night = json.getJSONArray("night").toIntList(),
                members = members?.keys()?.asSequence()?.mapNotNull { key ->
                    val id = key.toIntOrNull() ?: return@mapNotNull null
                    id to members.getJSONArray(key).toStringList()
                }?.toMap().orEmpty(),
            ),
        )
    }.getOrNull()

    private fun JSONArray.toIntList(): List<Int> = List(length()) { getInt(it) }

    private fun JSONArray.toStringList(): List<String> = List(length()) { getString(it) }

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
        private val rosterLock = Any()
        internal const val PREFERENCES_NAME = "air_shift"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_LAST_LIVE_REFRESH = "last_live_refresh"
        private const val KEY_DUTY_PROGRESS_DATE = "duty_progress_date"
        private const val KEY_DUTY_INDEX = "duty_index"
        private const val KEY_ROSTER_GENERATION = "roster_generation"
        private const val KEY_ASSIGNMENTS = "assignments"
        private const val KEY_SHIFT_REPORT_MARGIN = "shift_report_margin_minutes"
        private const val KEY_SHIFT_MANUAL_GROUP = "shift_manual_group_id"
        private const val KEY_SHIFT_CALIBRATION = "shift_group_calibration"
        private const val MAX_SHIFT_REPORT_MARGIN = 120
    }
}
