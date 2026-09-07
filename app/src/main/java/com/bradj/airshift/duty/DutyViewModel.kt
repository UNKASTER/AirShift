package com.bradj.airshift.duty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bradj.airshift.api.AirportPoint
import com.bradj.airshift.api.FlightLookup
import com.bradj.airshift.api.FlightRefreshScope
import com.bradj.airshift.api.LiveRefreshResult
import com.bradj.airshift.api.dutyWindowLookups
import com.bradj.airshift.api.refreshLookups
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.RosterTracking
import com.bradj.airshift.model.allDutiesComplete
import com.bradj.airshift.model.shift.ManualShiftGroup
import com.bradj.airshift.model.shift.ShiftCalibration
import com.bradj.airshift.model.shift.ShiftTeam
import com.bradj.airshift.parser.RosterParseResult
import com.bradj.airshift.specialservice.SpecialServiceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal const val FOREGROUND_REFRESH_INTERVAL_MILLIS = 5 * 60 * 1000L

/**
 * 四页界面的编排层：导入、实时刷新、人工完成、设置保存与权限跟进。
 *
 * 所有外部依赖来自 [DutyPorts]，因此可以在 JVM 上用假端口测试；Compose 只渲染 [uiState] 并转发用户动作。
 * 长操作在 [viewModelScope] 里执行，ViewModel 被清理后未完成的导入/刷新不会再落库。
 */
internal class DutyViewModel(private val ports: DutyPorts) : ViewModel() {
    private val store get() = ports.store

    private val mutableState = MutableStateFlow(initialState())
    val uiState: StateFlow<DutyUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<DutyUiEvent>(Channel.BUFFERED)
    val events: Flow<DutyUiEvent> = eventChannel.receiveAsFlow()

    val specialServiceState: StateFlow<SpecialServiceState>
        get() = ports.specialServices.state

    private var pendingRefresh: PendingFlightRefresh? = null
    private var nextForegroundRefreshAt = 0L
    private var locationCandidates: List<AirportPoint> = emptyList()

    private data class PendingFlightRefresh(val generation: Long, val lookups: Set<FlightLookup>)

    private fun initialState(): DutyUiState {
        val snapshot = store.loadSnapshot()
        return DutyUiState(
            userName = store.userName,
            assignments = snapshot.assignments,
            rosterGeneration = snapshot.generation,
            manuallyCompletedCount = snapshot.manuallyCompletedCount,
            now = now(),
            exactAlarmWarning = snapshot.assignments.isNotEmpty() && !ports.reminders.canScheduleExactAlarms(),
            hasVariFlightApiKey = store.hasVariFlightApiKey,
            notificationAccessGranted = ports.isNotificationAccessGranted(),
            shiftCalibration = store.shiftCalibration,
            manualShiftTeam = store.manualShiftTeam,
            manualShiftGroup = store.manualShiftGroup,
            shiftReportMarginMinutes = store.shiftReportMarginMinutes,
        )
    }

    private fun now(): LocalDateTime = LocalDateTime.now(ports.clock)

    private inline fun update(transform: DutyUiState.() -> DutyUiState) = mutableState.update(transform)

    // ---------- 生命周期 ----------

    fun saveUserName(name: String) {
        store.userName = name
        update { copy(userName = name.trim()) }
    }

    /** 冷启动、从后台回到前台：重读存储、重排提醒、重新匹配 MUC。 */
    fun onForegrounded() {
        val restored = store.loadSnapshot()
        ports.specialServices.onRosterChanged(restored.assignments)
        val exactAllowed = ports.reminders.canScheduleExactAlarms()
        if (exactAllowed && restored.assignments.isNotEmpty()) ports.reminders.scheduleAll(restored.assignments)
        update {
            copy(
                assignments = restored.assignments,
                rosterGeneration = restored.generation,
                manuallyCompletedCount = restored.manuallyCompletedCount,
                now = now(),
                notificationAccessGranted = ports.isNotificationAccessGranted(),
                exactAlarmWarning = restored.assignments.isNotEmpty() && !exactAllowed,
                isForeground = true,
            )
        }
        drainPendingRefresh()
    }

    fun onBackgrounded() {
        update { copy(isForeground = false) }
    }

    /** 每分钟一次：推进“现在”并跟随其他写入方（Worker、小组件）的人工进度。 */
    fun tick() {
        update { copy(now = now(), manuallyCompletedCount = store.currentDutyIndex) }
    }

    // ---------- 导入 ----------

    fun importImage(source: RosterSource) {
        update { copy(isWorking = true, warnings = emptyList(), statusMessage = "正在识别排班图片…") }
        viewModelScope.launch {
            readRoster(source)
                .onSuccess { finishImport(it, attempt = null) }
                .onFailure { error ->
                    update { copy(isWorking = false, statusMessage = "图片识别失败：${error.message ?: "无法读取图片"}") }
                }
        }
    }

    fun importExcel(
        source: RosterSource,
        attempt: ImportAttempt? = null,
        progressMessage: String = "正在解析 Excel 排班…",
    ) {
        if (uiState.value.isWorking) return
        update { copy(isWorking = true, warnings = emptyList(), statusMessage = progressMessage) }
        viewModelScope.launch {
            readRoster(source)
                .onSuccess { finishImport(it, attempt) }
                .onFailure { error ->
                    attempt?.onFinished?.invoke()
                    update { copy(isWorking = false, statusMessage = "Excel 识别失败：${error.message ?: "无法读取文件"}") }
                }
        }
    }

    fun reportSharedImportError(message: String) {
        update { copy(statusMessage = "Excel 分享导入失败：$message") }
    }

    private suspend fun readRoster(source: RosterSource): Result<RosterParseResult> = try {
        Result.success(source.read(uiState.value.userName.orEmpty()))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun finishImport(result: RosterParseResult, attempt: ImportAttempt?) {
        if (attempt?.isCurrent() == false) {
            update { copy(isWorking = false) }
            return
        }
        update { copy(warnings = result.warnings) }
        // 班次行只出现在整班工作日的表格里；解析不到时不动已保存的校准值。
        // 二组的合成组号按共享成员对齐到上一份校准，设置里手动指定的班组才不会漂到别的组上。
        result.observedShiftGroups
            ?.let { ShiftCalibration(result.rosterDate, it) }
            ?.takeIf { it.isUsable }
            ?.alignedWith(store.shiftCalibration)
            ?.let { calibration ->
                store.shiftCalibration = calibration
                update { copy(shiftCalibration = calibration) }
            }
        val previousAssignments = store.loadSnapshot().assignments
        val importGeneration = store.replaceAssignments(result.assignments)
        pendingRefresh = null
        syncSavedRoster(importGeneration, previousAssignments)
        // The import is durable now. Retrying this event after recreation would reset duty progress.
        attempt?.onFinished?.invoke()
        val apiKey = store.variFlightApiKey
        if (apiKey == null) {
            update { copy(isWorking = false, statusMessage = "排班已识别；尚未配置飞常准 API Key，当前按表内计划时间提醒") }
            requestPermissionsAndLocate()
            return
        }
        val lookups = result.assignments.dutyWindowLookups(0, now())
        if (lookups.isEmpty()) {
            update { copy(isWorking = false, statusMessage = savedWithoutTrackingMessage(result.assignments)) }
            requestPermissionsAndLocate()
            return
        }
        update { copy(statusMessage = "正在刷新实时航班信息…") }
        nextForegroundRefreshAt = ports.refreshClock() + FOREGROUND_REFRESH_INTERVAL_MILLIS
        val refresh = runRefresh(importGeneration, apiKey, lookups, FlightRefreshScope.DUTY_WINDOW)
        val applied = mergeRefresh(refresh, importGeneration)
        if (applied) {
            locationCandidates = refresh.airports
            update { copy(warnings = result.warnings + refresh.errors) }
        }
        update { copy(isWorking = false) }
        if (applied) requestPermissionsAndLocate()
        drainPendingRefresh()
    }

    /** 提前导入的排班要到排班日首个任务前 3 小时才开始自动跟踪（[RosterTracking]），把起点告诉用户。 */
    private fun savedWithoutTrackingMessage(assignments: List<RosterAssignment>): String {
        val start = RosterTracking.startsAt(assignments)?.takeIf { it.isAfter(now()) }
            ?: return "排班已保存，当前没有需要实时跟踪的航班"
        return "排班已保存，${start.format(TRACKING_START_FORMAT)} 起自动跟踪航班动态"
    }

    // ---------- 实时刷新 ----------

    fun refreshCurrentAssignments(automatic: Boolean = false, onlyLookups: Set<FlightLookup>? = null) {
        if (uiState.value.isWorking) return
        val apiKey = store.variFlightApiKey
        if (apiKey == null) {
            if (!automatic) update { copy(statusMessage = "请先在设置中填写飞常准 API Key") }
            return
        }
        val snapshot = store.loadSnapshot()
        if (snapshot.assignments.isEmpty()) {
            if (!automatic) update { copy(statusMessage = "请先导入排班图片或 Excel 文件") }
            return
        }
        val now = now()
        val window = snapshot.assignments.dutyWindowLookups(snapshot.manuallyCompletedCount, now)
        // An exhausted automatic window must not disable an explicit pull-to-refresh.
        val scope = if (!automatic && window.isEmpty()) FlightRefreshScope.ALL_ROSTER else FlightRefreshScope.DUTY_WINDOW
        val available = if (scope == FlightRefreshScope.ALL_ROSTER) {
            snapshot.assignments.refreshLookups(snapshot.manuallyCompletedCount, scope, now)
        } else {
            window
        }
        val lookups = onlyLookups?.let { available.intersect(it) } ?: available
        if (lookups.isEmpty()) {
            if (!automatic) update { copy(statusMessage = "当前没有需要实时跟踪的航班") }
            return
        }
        pendingRefresh = pendingRefresh?.takeIf { it.generation == snapshot.generation }?.let {
            it.copy(lookups = it.lookups - lookups).takeIf { pending -> pending.lookups.isNotEmpty() }
        }
        if (onlyLookups == null) nextForegroundRefreshAt = ports.refreshClock() + FOREGROUND_REFRESH_INTERVAL_MILLIS
        val refreshGeneration = snapshot.generation
        update {
            copy(
                isWorking = true,
                isLiveRefreshing = true,
                statusMessage = when {
                    automatic -> "正在自动更新实时航班信息…"
                    scope == FlightRefreshScope.ALL_ROSTER -> "正在手动更新全部排班航班信息…"
                    else -> "正在刷新实时航班信息…"
                },
            )
        }
        viewModelScope.launch {
            val refresh = runRefresh(refreshGeneration, apiKey, lookups, scope)
            when {
                refresh.attemptedCount == 0 -> update {
                    copy(isWorking = false, isLiveRefreshing = false, statusMessage = "当前没有需要实时跟踪的航班")
                }
                !mergeRefresh(refresh, refreshGeneration, scope) -> update {
                    copy(isWorking = false, isLiveRefreshing = false, statusMessage = "排班已变化，已忽略旧排班的刷新结果")
                }
                else -> {
                    locationCandidates = refresh.airports
                    val clock = now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    update {
                        copy(
                            warnings = refresh.errors,
                            isWorking = false,
                            isLiveRefreshing = false,
                            statusMessage = when {
                                refresh.refreshedCount == 0 -> "实时信息更新失败，继续显示上次数据"
                                refresh.errors.isNotEmpty() -> "实时信息已部分更新：$clock"
                                automatic -> "实时信息已自动更新：$clock"
                                else -> "实时信息已更新：$clock"
                            },
                        )
                    }
                    if (refresh.airports.isNotEmpty()) locateWhenReady()
                }
            }
            drainPendingRefresh()
        }
    }

    private suspend fun runRefresh(
        generation: Long,
        apiKey: String,
        lookups: Set<FlightLookup>,
        scope: FlightRefreshScope,
    ): LiveRefreshResult = try {
        ports.flightRefresher.refresh(generation, apiKey, lookups, scope)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // 批处理本身已逐项捕获；这里只兜底实现层的意外异常，不向 UI 暴露堆栈。
        LiveRefreshResult(
            live = emptyMap(),
            airports = emptyList(),
            errors = listOf("实时航班更新失败"),
            attemptedCount = lookups.size,
            refreshedCount = 0,
            fallbackDate = LocalDate.now(ports.clock),
        )
    }

    private fun mergeRefresh(
        refresh: LiveRefreshResult,
        generation: Long,
        scope: FlightRefreshScope = FlightRefreshScope.DUTY_WINDOW,
    ): Boolean {
        val merged = store.mergeLiveInfoIfGeneration(
            live = refresh.live,
            expectedGeneration = generation,
            fallbackDate = refresh.fallbackDate,
            refreshedAtEpochMillis = System.currentTimeMillis().takeIf { refresh.refreshedCount > 0 },
            scope = scope,
        )
        val current = syncSavedRoster(generation)
        return merged != null && current
    }

    /** 人工完成后只补查新进入两项窗口的航班；忙碌或后台时先排队。 */
    private fun drainPendingRefresh() {
        val pending = pendingRefresh ?: return
        val state = uiState.value
        if (pending.generation != state.rosterGeneration) {
            pendingRefresh = null
            return
        }
        if (state.isWorking || !state.isForeground) return
        pendingRefresh = null
        // If the regular cycle is due too, consume both needs in one full-window request.
        val targets = pending.lookups.takeIf { nextForegroundRefreshAt > ports.refreshClock() }
        refreshCurrentAssignments(automatic = true, onlyLookups = targets)
    }

    /** 供前台刷新循环判断距离下一次周期刷新还有多久。 */
    fun foregroundRefreshDelayMillis(): Long = nextForegroundRefreshAt - ports.refreshClock()

    fun configureBackgroundRefresh(enabled: Boolean) = ports.configureBackgroundRefresh(enabled)

    fun onAutoRefreshStopped() {
        update { copy(statusMessage = "今日执勤已全部完成，自动刷新已停止，仍可在全部执勤页下拉刷新") }
    }

    // ---------- 执勤完成 ----------

    fun completeCurrentDuty() {
        val state = uiState.value
        store.completeCurrentDuty(state.rosterGeneration, state.activeDutyIndex, now())?.let { completion ->
            val waiting = pendingRefresh
                ?.takeIf { it.generation == completion.snapshot.generation }
                ?.lookups
                .orEmpty()
            val currentWindow = completion.snapshot.assignments.dutyWindowLookups(
                completion.snapshot.manuallyCompletedCount,
                now(),
            )
            val pending = (waiting + completion.newlyTrackedFlights).intersect(currentWindow)
            pendingRefresh = PendingFlightRefresh(completion.snapshot.generation, pending).takeIf { pending.isNotEmpty() }
        }
        syncSavedRoster(state.rosterGeneration)
        drainPendingRefresh()
    }

    // ---------- 存储同步 ----------

    /**
     * 在 generation 仍然有效时，把最新存储同步到界面、提醒、MUC、后台刷新与小组件。
     * 读取发生在锁内：Worker 可能在此期间合并了其他航班。
     */
    private fun syncSavedRoster(
        expectedGeneration: Long,
        previousAssignments: List<RosterAssignment>? = null,
    ): Boolean {
        val applied = store.runIfGenerationCurrent(expectedGeneration) {
            val current = store.loadSnapshot()
            val now = now()
            previousAssignments?.let(ports.reminders::cancelAll)
            ports.specialServices.onRosterChanged(current.assignments)
            ports.configureBackgroundRefresh(
                store.hasVariFlightApiKey && current.assignments.isNotEmpty() &&
                    !current.assignments.allDutiesComplete(now, current.manuallyCompletedCount),
            )
            val summary = ports.reminders.scheduleAll(current.assignments)
            update {
                copy(
                    assignments = current.assignments,
                    rosterGeneration = current.generation,
                    manuallyCompletedCount = current.manuallyCompletedCount,
                    now = now,
                    exactAlarmWarning = current.assignments.isNotEmpty() && !summary.exactAlarmsAllowed,
                    statusMessage = "已保存 ${current.assignments.size} 个保障任务，安排 ${summary.scheduledCount} 个提醒",
                )
            }
        }
        if (!applied) {
            val current = store.loadSnapshot()
            update {
                copy(
                    assignments = current.assignments,
                    rosterGeneration = current.generation,
                    manuallyCompletedCount = current.manuallyCompletedCount,
                    now = now(),
                )
            }
            pendingRefresh = null
        }
        if (applied) ports.notifyWidget()
        return applied
    }

    // ---------- 权限与定位 ----------

    private fun requestPermissionsAndLocate() {
        val missing = buildList {
            if (!ports.hasPermission(DutyPermissions.NOTIFICATIONS)) add(DutyPermissions.NOTIFICATIONS)
            if (!ports.hasPermission(DutyPermissions.FINE_LOCATION)) {
                add(DutyPermissions.FINE_LOCATION)
                add(DutyPermissions.COARSE_LOCATION)
            }
        }
        if (missing.isNotEmpty()) {
            eventChannel.trySend(DutyUiEvent.RequestPermissions(missing))
        } else {
            locateWhenReady()
        }
    }

    private fun locateWhenReady() {
        if (!ports.hasPermission(DutyPermissions.FINE_LOCATION)) {
            eventChannel.trySend(
                DutyUiEvent.RequestPermissions(listOf(DutyPermissions.FINE_LOCATION, DutyPermissions.COARSE_LOCATION)),
            )
        } else if (locationCandidates.isNotEmpty()) {
            locate()
        }
    }

    private fun locate() {
        ports.airportLocator.locate(locationCandidates) { result ->
            result
                .onSuccess { match -> update { copy(currentAirport = match.airport.name ?: "当前机场") } }
                .onFailure { error -> update { copy(statusMessage = error.message) } }
        }
    }

    fun onPermissionsResult(grants: Map<String, Boolean>) {
        val notificationGranted = grants[DutyPermissions.NOTIFICATIONS]
            ?: ports.hasPermission(DutyPermissions.NOTIFICATIONS)
        val locationGranted = grants[DutyPermissions.FINE_LOCATION] == true ||
            grants[DutyPermissions.COARSE_LOCATION] == true ||
            ports.hasPermission(DutyPermissions.COARSE_LOCATION)
        if (!notificationGranted) {
            update { copy(statusMessage = "未允许通知，排班已保存但系统不会弹出提醒") }
        }
        if (locationGranted && locationCandidates.isNotEmpty()) {
            locate()
        } else if (!locationGranted) {
            update { copy(statusMessage = "未允许定位，可继续查看排班，但无法自动判断当前机场") }
        }
    }

    // ---------- 设置 ----------

    /** 设置页进入时读一次明文；不放进 UI 状态，避免随每次重组传递。 */
    fun storedApiKey(): String = store.variFlightApiKey.orEmpty()

    fun saveSettings(name: String, apiKey: String) {
        val trimmedName = name.trim()
        runCatching {
            store.userName = trimmedName
            if (apiKey.isNotBlank()) store.variFlightApiKey = apiKey
        }.onSuccess {
            ports.clearFlightCache()
            val hasKey = store.hasVariFlightApiKey
            val state = uiState.value
            ports.configureBackgroundRefresh(
                hasKey && state.assignments.isNotEmpty() &&
                    !state.assignments.allDutiesComplete(now(), state.manuallyCompletedCount),
            )
            update { copy(userName = trimmedName, hasVariFlightApiKey = hasKey) }
        }.onFailure { error ->
            update { copy(statusMessage = error.message ?: "无法保存设置") }
        }
    }

    fun clearApiKey() {
        store.clearVariFlightApiKey()
        ports.clearFlightCache()
        ports.configureBackgroundRefresh(false)
        update { copy(hasVariFlightApiKey = false, statusMessage = "飞常准 API Key 已清除，后台实时刷新已停止") }
    }

    fun testApiKey(apiKey: String, callback: (Result<Unit>) -> Unit) {
        val today = LocalDate.now(ports.clock)
        val candidate = uiState.value.assignments.asSequence().mapNotNull { assignment ->
            when {
                assignment.inboundFlight != null -> assignment.inboundFlight to
                    (assignment.scheduledArrival?.toLocalDate() ?: today)
                assignment.outboundFlight != null -> assignment.outboundFlight to
                    (assignment.scheduledDeparture?.toLocalDate() ?: today)
                else -> null
            }
        }.firstOrNull()
        if (candidate == null) {
            callback(Result.failure(IllegalStateException("请先导入包含航班的排班后再测试连接")))
            return
        }
        ports.apiKeyTester.test(apiKey, candidate.first, candidate.second, callback)
    }

    fun selectShiftGroup(group: ManualShiftGroup?) {
        store.manualShiftGroup = group
        update { copy(manualShiftGroup = group) }
    }

    fun selectShiftTeam(team: ShiftTeam?) {
        store.manualShiftTeam = team
        update { copy(manualShiftTeam = team) }
    }

    fun selectReportMargin(minutes: Int) {
        store.shiftReportMarginMinutes = minutes
        update { copy(shiftReportMarginMinutes = store.shiftReportMarginMinutes) }
    }

    companion object {
        private val TRACKING_START_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")

        fun factory(ports: DutyPorts): ViewModelProvider.Factory = viewModelFactory {
            initializer { DutyViewModel(ports) }
        }
    }
}
