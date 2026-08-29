package com.bradj.airshift

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bradj.airshift.api.AirportPoint
import com.bradj.airshift.api.FlightInfo
import com.bradj.airshift.api.FlightLookup
import com.bradj.airshift.api.FlightRefreshScheduler
import com.bradj.airshift.api.VariFlightClient
import com.bradj.airshift.api.withLiveInfo
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.location.AirportLocator
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.parser.ExcelRosterReader
import com.bradj.airshift.parser.OcrRosterReader
import com.bradj.airshift.parser.RosterParseResult
import com.bradj.airshift.reminder.ReminderReceiver
import com.bradj.airshift.reminder.ReminderScheduler
import com.bradj.airshift.specialservice.NotificationAccess
import com.bradj.airshift.specialservice.SpecialServiceRepository
import com.bradj.airshift.ui.AirShiftRoot
import com.bradj.airshift.ui.DutySection
import com.bradj.airshift.ui.all.AllDutyScreen
import com.bradj.airshift.ui.current.CurrentDutyScreen
import com.bradj.airshift.ui.onboarding.OnboardingScreen
import com.bradj.airshift.ui.settings.SettingsScreen
import com.bradj.airshift.ui.theme.AirShiftTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val FOREGROUND_REFRESH_INTERVAL_MILLIS = 5 * 60 * 1000L
private const val BUSY_REFRESH_RETRY_MILLIS = 15 * 1000L
private const val XLS_MIME_TYPE = "application/vnd.ms-excel"
private const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

private data class LiveRefreshResult(
    val assignments: List<RosterAssignment>,
    val airports: List<AirportPoint>,
    val errors: List<String>,
    val attemptedCount: Int,
    val refreshedCount: Int,
)

private data class LiveFlightRequest(
    val lookup: FlightLookup,
    val scheduled: LocalDateTime?,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderReceiver.createChannel(this)
        val store = RosterStore(this)
        val specialServiceRepository = SpecialServiceRepository.get(this)
        FlightRefreshScheduler.configure(this, store.hasVariFlightApiKey)
        setContent {
            AirShiftTheme {
                AirShiftApp(
                    context = this,
                    store = store,
                    specialServiceRepository = specialServiceRepository,
                    readImageRoster = { uri, name, callback -> OcrRosterReader.read(this, uri, name, callback) },
                    readExcelRoster = { uri, name, callback -> ExcelRosterReader.read(this, uri, name, callback) },
                    refreshLive = ::refreshLive,
                    locateAirport = { candidates, callback -> AirportLocator.locate(this, candidates, callback) },
                    openExactAlarmSettings = ::openExactAlarmSettings,
                    openNotificationAccessSettings = { NotificationAccess.openSettings(this) },
                )
            }
        }
    }

    private fun refreshLive(
        assignments: List<RosterAssignment>,
        apiKey: String,
        onlyOperationallyRelevant: Boolean,
        callback: (LiveRefreshResult) -> Unit,
    ) {
        val now = LocalDateTime.now()
        val requests = assignments.flatMap { assignment ->
            buildList {
                assignment.inboundFlight?.let { flight ->
                    add(
                        LiveFlightRequest(
                            FlightLookup.of(flight, assignment.scheduledArrival?.toLocalDate() ?: now.toLocalDate()),
                            assignment.scheduledArrival,
                        ),
                    )
                }
                assignment.outboundFlight?.let { flight ->
                    add(
                        LiveFlightRequest(
                            FlightLookup.of(flight, assignment.scheduledDeparture?.toLocalDate() ?: now.toLocalDate()),
                            assignment.scheduledDeparture,
                        ),
                    )
                }
            }
        }.filter { request ->
            if (!onlyOperationallyRelevant) return@filter true
            val scheduled = request.scheduled ?: return@filter false
            Duration.between(now, scheduled).toMinutes() in -60..240
        }.distinctBy(LiveFlightRequest::lookup)
        if (requests.isEmpty()) {
            callback(LiveRefreshResult(assignments, emptyList(), emptyList(), 0, 0))
            return
        }

        val client = VariFlightClient(apiKey)
        val results = mutableMapOf<FlightLookup, FlightInfo>()
        val errors = mutableListOf<String>()
        var remaining = requests.size
        requests.forEach { request ->
            client.fetchFlight(request.lookup.flightNumber, request.lookup.date) { result ->
                val flightNumber = request.lookup.flightNumber
                result.onSuccess { results[request.lookup] = it }
                    .onFailure { errors += "$flightNumber：${it.message ?: "刷新失败"}" }
                remaining--
                if (remaining == 0) {
                    val enriched = assignments.map { assignment ->
                        assignment.withLiveInfo(results, now.toLocalDate())
                    }
                    val airports = results.values
                        .flatMap { listOfNotNull(it.origin, it.destination) }
                        .distinctBy { it.code }
                    callback(
                        LiveRefreshResult(
                            assignments = enriched,
                            airports = airports,
                            errors = errors,
                            attemptedCount = requests.size,
                            refreshedCount = results.size,
                        ),
                    )
                }
            }
        }
    }

    private fun openExactAlarmSettings() {
        startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:$packageName".toUri()
            },
        )
    }
}

@Composable
private fun AirShiftApp(
    context: Context,
    store: RosterStore,
    specialServiceRepository: SpecialServiceRepository,
    readImageRoster: (Uri, String, (Result<RosterParseResult>) -> Unit) -> Unit,
    readExcelRoster: (Uri, String, (Result<RosterParseResult>) -> Unit) -> Unit,
    refreshLive: (List<RosterAssignment>, String, Boolean, (LiveRefreshResult) -> Unit) -> Unit,
    locateAirport: (Collection<AirportPoint>, (Result<com.bradj.airshift.location.AirportMatch>) -> Unit) -> Unit,
    openExactAlarmSettings: () -> Unit,
    openNotificationAccessSettings: () -> Unit,
) {
    var userName by remember { mutableStateOf(store.userName) }
    if (userName == null) {
        OnboardingScreen { name ->
            store.userName = name
            userName = name.trim()
        }
        return
    }

    var assignments by remember { mutableStateOf(store.loadAssignments()) }
    val specialServiceState by specialServiceRepository.state.collectAsStateWithLifecycle()
    var isWorking by remember { mutableStateOf(false) }
    var isLiveRefreshing by remember { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var warnings by remember { mutableStateOf(emptyList<String>()) }
    var currentAirport by rememberSaveable { mutableStateOf<String?>(null) }
    var exactAlarmWarning by remember {
        mutableStateOf(assignments.isNotEmpty() && !ReminderScheduler.canScheduleExactAlarms(context))
    }
    var section by rememberSaveable { mutableStateOf(DutySection.ALL) }
    var dutyIndex by remember { mutableStateOf(store.currentDutyIndex) }
    var locationCandidates by remember { mutableStateOf(emptyList<AirportPoint>()) }
    var hasVariFlightApiKey by remember { mutableStateOf(store.hasVariFlightApiKey) }
    var notificationAccessGranted by remember { mutableStateOf(NotificationAccess.isGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isForeground by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }

    val runtimePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val notificationGranted = grants[Manifest.permission.POST_NOTIFICATIONS]
            ?: (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!notificationGranted) {
            statusMessage = "未允许通知，排班已保存但系统不会弹出提醒"
        }
        if (locationGranted && locationCandidates.isNotEmpty()) {
            locateAirport(locationCandidates) { result ->
                result.onSuccess {
                    currentAirport = it.airport.name ?: "当前机场"
                }.onFailure { statusMessage = it.message }
            }
        } else if (!locationGranted) {
            statusMessage = "未允许定位，可继续查看排班，但无法自动判断当前机场"
        }
    }

    fun scheduleAndSave(updated: List<RosterAssignment>) {
        ReminderScheduler.cancelAll(context, store.loadAssignments())
        assignments = updated
        store.saveAssignments(updated)
        specialServiceRepository.onRosterChanged(updated)
        FlightRefreshScheduler.configure(context, hasVariFlightApiKey)
        val summary = ReminderScheduler.scheduleAll(context, updated)
        exactAlarmWarning = updated.isNotEmpty() && !summary.exactAlarmsAllowed
        statusMessage = "已保存 ${updated.size} 个保障任务，安排 ${summary.scheduledCount} 个提醒"
    }

    fun syncExactAlarmState(currentAssignments: List<RosterAssignment>, rescheduleIfAllowed: Boolean) {
        val exactAllowed = ReminderScheduler.canScheduleExactAlarms(context)
        exactAlarmWarning = currentAssignments.isNotEmpty() && !exactAllowed
        if (rescheduleIfAllowed && exactAllowed && currentAssignments.isNotEmpty()) {
            ReminderScheduler.scheduleAll(context, currentAssignments)
        }
    }

    fun locateWhenReady() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            runtimePermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else if (locationCandidates.isNotEmpty()) {
            locateAirport(locationCandidates) { result ->
                result.onSuccess {
                    currentAirport = it.airport.name ?: "当前机场"
                }.onFailure { statusMessage = it.message }
            }
        }
    }

    fun requestPermissionsAndLocate() {
        val missing = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        if (missing.isNotEmpty()) {
            runtimePermissions.launch(missing.toTypedArray())
        } else {
            locateWhenReady()
        }
    }

    fun finishImport(result: RosterParseResult) {
        val parsed = result.assignments
        warnings = result.warnings
        val apiKey = store.variFlightApiKey
        if (apiKey == null) {
            isWorking = false
            scheduleAndSave(parsed)
            store.resetDutyProgress()
            dutyIndex = 0
            statusMessage = "排班已识别；尚未配置飞常准 API Key，当前按表内计划时间提醒"
            requestPermissionsAndLocate()
            return
        }
        statusMessage = "正在刷新实时航班信息…"
        refreshLive(parsed, apiKey, false) { refresh ->
            if (refresh.refreshedCount > 0) store.lastLiveRefreshEpochMillis = System.currentTimeMillis()
            locationCandidates = refresh.airports
            warnings = result.warnings + refresh.errors
            scheduleAndSave(refresh.assignments)
            store.resetDutyProgress()
            dutyIndex = 0
            isWorking = false
            requestPermissionsAndLocate()
        }
    }

    fun refreshCurrentAssignments(automatic: Boolean = false) {
        if (isWorking) return
        val apiKey = store.variFlightApiKey
        if (apiKey == null) {
            if (!automatic) statusMessage = "请先在设置中填写飞常准 API Key"
            return
        }
        if (assignments.isEmpty()) {
            if (!automatic) statusMessage = "请先导入排班图片或 Excel 文件"
            return
        }
        isWorking = true
        isLiveRefreshing = true
        statusMessage = if (automatic) "正在自动更新实时航班信息…" else "正在刷新实时航班信息…"
        refreshLive(assignments, apiKey, automatic) { refresh ->
            if (refresh.attemptedCount == 0) {
                isWorking = false
                isLiveRefreshing = false
                statusMessage = "当前没有需要实时跟踪的航班"
                return@refreshLive
            }
            if (refresh.refreshedCount > 0) store.lastLiveRefreshEpochMillis = System.currentTimeMillis()
            locationCandidates = refresh.airports
            warnings = refresh.errors
            scheduleAndSave(refresh.assignments)
            isWorking = false
            isLiveRefreshing = false
            statusMessage = when {
                refresh.refreshedCount == 0 -> "实时信息更新失败，继续显示上次数据"
                refresh.errors.isNotEmpty() -> "实时信息已部分更新：${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}"
                automatic -> "实时信息已自动更新：${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}"
                else -> "实时信息已更新：${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))}"
            }
            if (refresh.airports.isNotEmpty()) locateWhenReady()
        }
    }

    fun testVariFlightApiKey(apiKey: String, callback: (Result<Unit>) -> Unit) {
        val now = LocalDate.now()
        val candidate = assignments.asSequence().mapNotNull { assignment ->
            when {
                assignment.inboundFlight != null -> assignment.inboundFlight to
                    (assignment.scheduledArrival?.toLocalDate() ?: now)
                assignment.outboundFlight != null -> assignment.outboundFlight to
                    (assignment.scheduledDeparture?.toLocalDate() ?: now)
                else -> null
            }
        }.firstOrNull()
        if (candidate == null) {
            callback(Result.failure(IllegalStateException("请先导入包含航班的排班后再测试连接")))
            return
        }
        VariFlightClient(apiKey).testConnection(candidate.first, candidate.second, callback)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    val restoredAssignments = store.loadAssignments()
                    assignments = restoredAssignments
                    specialServiceRepository.onRosterChanged(restoredAssignments)
                    notificationAccessGranted = NotificationAccess.isGranted(context)
                    dutyIndex = store.currentDutyIndex
                    syncExactAlarmState(restoredAssignments, rescheduleIfAllowed = true)
                    isForeground = true
                }
                Lifecycle.Event.ON_STOP -> isForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val latestAutomaticRefresh by rememberUpdatedState { refreshCurrentAssignments(automatic = true) }
    LaunchedEffect(isForeground, assignments.isNotEmpty(), hasVariFlightApiKey) {
        if (!isForeground || assignments.isEmpty() || !hasVariFlightApiKey) return@LaunchedEffect
        while (true) {
            if (isWorking) {
                delay(BUSY_REFRESH_RETRY_MILLIS)
                continue
            }
            latestAutomaticRefresh()
            delay(FOREGROUND_REFRESH_INTERVAL_MILLIS)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isWorking = true
        warnings = emptyList()
        statusMessage = "正在识别排班图片…"
        readImageRoster(uri, userName.orEmpty()) { result ->
            result.onSuccess(::finishImport)
                .onFailure {
                    isWorking = false
                    statusMessage = "图片识别失败：${it.message ?: "无法读取图片"}"
                }
        }
    }

    val excelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isWorking = true
        warnings = emptyList()
        statusMessage = "正在解析 Excel 排班…"
        readExcelRoster(uri, userName.orEmpty()) { result ->
            result.onSuccess(::finishImport)
                .onFailure {
                    isWorking = false
                    statusMessage = "Excel 识别失败：${it.message ?: "无法读取文件"}"
                }
        }
    }

    val visibleSpecialServiceRecords = specialServiceState.activeRecords()
    val visibleGateChanges = specialServiceState.activeGateChanges()
    val visibleStandChanges = specialServiceState.activeStandChanges()
    val visibleFlightCancellations = specialServiceState.activeFlightCancellations()

    AirShiftRoot(
        section = section,
        onSectionSelected = { section = it },
    ) { padding ->
        when (section) {
            DutySection.ALL -> AllDutyScreen(
                userName = userName.orEmpty(),
                currentAirport = currentAirport,
                isWorking = isWorking,
                isLiveRefreshing = isLiveRefreshing,
                statusMessage = statusMessage,
                warnings = warnings,
                exactAlarmWarning = exactAlarmWarning,
                assignments = assignments,
                specialServiceRecords = visibleSpecialServiceRecords,
                gateChanges = visibleGateChanges,
                standChanges = visibleStandChanges,
                flightCancellations = visibleFlightCancellations,
                onImportImage = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onImportExcel = { excelPicker.launch(arrayOf(XLS_MIME_TYPE, XLSX_MIME_TYPE)) },
                onRefresh = { refreshCurrentAssignments() },
                onOpenExactAlarmSettings = openExactAlarmSettings,
                modifier = Modifier.padding(padding),
            )
            DutySection.CURRENT -> CurrentDutyScreen(
                assignments = assignments,
                dutyIndex = dutyIndex,
                specialServiceRecords = visibleSpecialServiceRecords,
                gateChanges = visibleGateChanges,
                standChanges = visibleStandChanges,
                flightCancellations = visibleFlightCancellations,
                onDutyComplete = {
                    store.advanceDutyIndex()
                    dutyIndex = store.currentDutyIndex
                },
                onGoToAllDuty = { section = DutySection.ALL },
                modifier = Modifier.padding(padding),
            )
            DutySection.SETTINGS -> SettingsScreen(
                currentName = userName.orEmpty(),
                currentApiKey = store.variFlightApiKey.orEmpty(),
                hasStoredApiKey = hasVariFlightApiKey,
                notificationAccessGranted = notificationAccessGranted,
                lastSuccessfulRecognitionEpochMillis = specialServiceState.lastSuccessfulRecognitionEpochMillis,
                lastProcessingResult = specialServiceState.lastProcessingResult,
                onOpenNotificationAccessSettings = openNotificationAccessSettings,
                onSave = { name, apiKey ->
                    runCatching {
                        store.userName = name
                        if (apiKey.isNotBlank()) store.variFlightApiKey = apiKey
                    }.onSuccess {
                        VariFlightClient.clearCachedFlights()
                        hasVariFlightApiKey = store.hasVariFlightApiKey
                        FlightRefreshScheduler.configure(context, hasVariFlightApiKey)
                        userName = name.trim()
                    }.onFailure { error ->
                        statusMessage = error.message ?: "无法保存设置"
                    }
                },
                onClearApiKey = {
                    store.clearVariFlightApiKey()
                    VariFlightClient.clearCachedFlights()
                    hasVariFlightApiKey = false
                    FlightRefreshScheduler.configure(context, enabled = false)
                    statusMessage = "飞常准 API Key 已清除，后台实时刷新已停止"
                },
                onTestConnection = ::testVariFlightApiKey,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
