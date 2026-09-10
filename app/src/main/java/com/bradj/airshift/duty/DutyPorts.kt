package com.bradj.airshift.duty

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.bradj.airshift.api.AirportPoint
import com.bradj.airshift.api.FlightRefreshScheduler
import com.bradj.airshift.api.LiveFlightRefresher
import com.bradj.airshift.api.VariFlightClient
import com.bradj.airshift.api.VariFlightLiveRefresher
import com.bradj.airshift.data.RosterRepository
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.location.AirportLocator
import com.bradj.airshift.location.AirportMatch
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.parser.RosterParseResult
import com.bradj.airshift.reminder.ReminderScheduler
import com.bradj.airshift.reminder.ScheduleSummary
import com.bradj.airshift.reminder.ShuttleAlarmClock
import com.bradj.airshift.reminder.ShuttleAlarmReason
import com.bradj.airshift.reminder.ShuttleAlarmSync
import com.bradj.airshift.reminder.ShuttleAlarmWake
import com.bradj.airshift.specialservice.NotificationAccess
import com.bradj.airshift.specialservice.SpecialServiceRepository
import com.bradj.airshift.specialservice.SpecialServiceState
import com.bradj.airshift.widget.DutyWidgetUpdater
import kotlinx.coroutines.flow.StateFlow
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/** 一份可读取的排班来源（图片或 Excel）；由 UI 从 Uri 构造，编排层只负责读取与落库。 */
internal fun interface RosterSource {
    suspend fun read(userName: String): RosterParseResult
}

internal interface ReminderPort {
    fun canScheduleExactAlarms(): Boolean
    fun scheduleAll(assignments: List<RosterAssignment>): ScheduleSummary
    fun cancelAll(assignments: List<RosterAssignment>)
}

/** 班车闹铃：同步（前台 / 后台语义由 [sync] 的 visible 决定）、时钟是否可用、调试用的定时唤醒。 */
internal interface ShuttleAlarmPort {
    fun sync(visible: Boolean, reason: ShuttleAlarmReason, force: Boolean = false, onFinished: () -> Unit = {})
    fun isClockAvailable(): Boolean
    fun scheduleWakeIn(delaySeconds: Long)
}

/** 没接系统时钟时的占位（仪器测试的端口装配）：什么也不做、立即回调。 */
internal object NoShuttleAlarms : ShuttleAlarmPort {
    override fun sync(visible: Boolean, reason: ShuttleAlarmReason, force: Boolean, onFinished: () -> Unit) =
        onFinished()

    override fun isClockAvailable(): Boolean = false
    override fun scheduleWakeIn(delaySeconds: Long) = Unit
}

internal interface SpecialServicePort {
    val state: StateFlow<SpecialServiceState>
    fun onRosterChanged(assignments: List<RosterAssignment>)
}

internal fun interface AirportLocatorPort {
    fun locate(candidates: Collection<AirportPoint>, callback: (Result<AirportMatch>) -> Unit)
}

internal fun interface ApiKeyTester {
    fun test(apiKey: String, flightNumber: String, date: LocalDate, callback: (Result<Unit>) -> Unit)
}

/**
 * [DutyViewModel] 与外界的全部接缝。生产环境由 [AppDutyPorts.create] 组装；
 * JVM 测试用内存假实现替换，因此这里不能出现任何需要 Android 运行时的直接依赖。
 */
internal data class DutyPorts(
    val store: RosterRepository,
    val specialServices: SpecialServicePort,
    val reminders: ReminderPort,
    val flightRefresher: LiveFlightRefresher,
    val airportLocator: AirportLocatorPort,
    val apiKeyTester: ApiKeyTester,
    val configureBackgroundRefresh: (Boolean) -> Unit,
    val notifyWidget: () -> Unit,
    val clearFlightCache: () -> Unit = {},
    /** 非关键路径的失败只记日志（如实测记录未更新）；JVM 测试里缺省不做任何事。 */
    val logWarning: (message: String, error: Throwable) -> Unit = { _, _ -> },
    val isNotificationAccessGranted: () -> Boolean,
    val hasPermission: (String) -> Boolean,
    val refreshClock: () -> Long,
    val clock: Clock,
    val shuttleAlarms: ShuttleAlarmPort = NoShuttleAlarms,
)

internal object AppDutyPorts {
    private const val LOG_TAG = "AirShift"

    fun create(context: Context, clock: Clock = Clock.systemDefaultZone()): DutyPorts {
        val appContext = context.applicationContext
        val specialServices = SpecialServiceRepository.get(appContext)
        return DutyPorts(
            store = RosterStore(appContext, clock),
            specialServices = object : SpecialServicePort {
                override val state: StateFlow<SpecialServiceState> = specialServices.state
                override fun onRosterChanged(assignments: List<RosterAssignment>) =
                    specialServices.onRosterChanged(assignments)
            },
            reminders = object : ReminderPort {
                override fun canScheduleExactAlarms(): Boolean = ReminderScheduler.canScheduleExactAlarms(appContext)
                override fun scheduleAll(assignments: List<RosterAssignment>): ScheduleSummary =
                    ReminderScheduler.scheduleAll(appContext, assignments)
                override fun cancelAll(assignments: List<RosterAssignment>) =
                    ReminderScheduler.cancelAll(appContext, assignments)
            },
            flightRefresher = VariFlightLiveRefresher(appContext, clock),
            airportLocator = AirportLocatorPort { candidates, callback ->
                AirportLocator.locate(appContext, candidates, callback)
            },
            apiKeyTester = ApiKeyTester { apiKey, flightNumber, date, callback ->
                VariFlightClient(apiKey).testConnection(flightNumber, date, callback)
            },
            configureBackgroundRefresh = { FlightRefreshScheduler.configure(appContext, it) },
            notifyWidget = { DutyWidgetUpdater.notifyRosterChanged(appContext) },
            clearFlightCache = VariFlightClient::clearCachedFlights,
            logWarning = { message, error -> Log.w(LOG_TAG, message, error) },
            isNotificationAccessGranted = { NotificationAccess.isGranted(appContext) },
            hasPermission = { permission ->
                ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
            },
            refreshClock = SystemClock::elapsedRealtime,
            clock = clock,
            shuttleAlarms = object : ShuttleAlarmPort {
                override fun sync(
                    visible: Boolean,
                    reason: ShuttleAlarmReason,
                    force: Boolean,
                    onFinished: () -> Unit,
                ) = ShuttleAlarmSync.sync(
                    appContext,
                    if (visible) ShuttleAlarmSync.Launch.VISIBLE else ShuttleAlarmSync.Launch.BACKGROUND,
                    reason,
                    force,
                    onFinished,
                )

                override fun isClockAvailable(): Boolean = ShuttleAlarmClock(appContext).isAvailable()
                override fun scheduleWakeIn(delaySeconds: Long) = ShuttleAlarmWake.schedule(
                    appContext,
                    LocalDateTime.now(clock).plusSeconds(delaySeconds),
                    force = true,
                )
            },
        )
    }
}

internal object DutyPermissions {
    const val NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS
    const val FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
    const val COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION
}
