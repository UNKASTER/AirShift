package com.bradj.airshift

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.bradj.airshift.api.FlightRefreshScope
import com.bradj.airshift.api.dutyWindowLookups
import com.bradj.airshift.api.refreshFlightBatch
import com.bradj.airshift.api.refreshLookups
import com.bradj.airshift.api.VariFlightClient
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.location.AirportLocator
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.allDutiesComplete
import com.bradj.airshift.model.dutyWindowIndices
import com.bradj.airshift.parser.ExcelRosterReader
import com.bradj.airshift.parser.OcrRosterReader
import com.bradj.airshift.parser.RosterParseResult
import com.bradj.airshift.reminder.ReminderReceiver
import com.bradj.airshift.reminder.ReminderScheduler
import com.bradj.airshift.specialservice.NotificationAccess
import com.bradj.airshift.specialservice.SpecialServiceRepository
import com.bradj.airshift.ui.AirShiftRoot
import com.bradj.airshift.ui.DutyNavigationViewModel
import com.bradj.airshift.ui.DutySection
import com.bradj.airshift.ui.all.AllDutyScreen
import com.bradj.airshift.ui.current.CurrentDutyScreen
import com.bradj.airshift.ui.onboarding.OnboardingScreen
import com.bradj.airshift.ui.settings.SettingsScreen
import com.bradj.airshift.ui.theme.AirShiftTheme
import com.bradj.airshift.widget.DutyWidgetUpdater
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors

private const val DUTY_STATE_TICK_MILLIS = 60 * 1000L
private const val FOREGROUND_REFRESH_INTERVAL_MILLIS = 5 * 60 * 1000L

internal data class LiveRefreshResult(
    val live: Map<FlightLookup, List<FlightInfo>>,
    val airports: List<AirportPoint>,
    val errors: List<String>,
    val attemptedCount: Int,
    val refreshedCount: Int,
    val fallbackDate: LocalDate = LocalDate.now(),
)

private data class PendingFlightRefresh(
    val generation: Long,
    val lookups: Set<FlightLookup>,
)

class MainActivity : ComponentActivity() {
    private val sharedExcelImportQueue: SharedExcelImportQueueViewModel by viewModels()
    private val dutyNavigation: DutyNavigationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) sharedExcelImportQueue.enqueue(intent)
        setIntent(Intent(Intent.ACTION_MAIN))
        enableEdgeToEdge()
        ReminderReceiver.createChannel(this)
        val store = RosterStore(this)
        val specialServiceRepository = SpecialServiceRepository.get(this)
        val roster = store.loadSnapshot()
        FlightRefreshScheduler.configure(
            this,
            store.hasVariFlightApiKey && roster.assignments.isNotEmpty() &&
                !roster.assignments.allDutiesComplete(manuallyCompletedCount = roster.manuallyCompletedCount),
        )
        setContent {
            val pendingSharedExcelImports by sharedExcelImportQueue.pending.collectAsStateWithLifecycle()
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
                    pendingSharedExcelImport = pendingSharedExcelImports.firstOrNull(),
                    sharedExcelImportQueue = sharedExcelImportQueue,
                    dutyNavigation = dutyNavigation,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedExcelImportQueue.enqueue(intent)
        setIntent(Intent(Intent.ACTION_MAIN))
    }

    override fun onStart() {
        super.onStart()
        dutyNavigation.onActivityForegrounded()
    }

    override fun onStop() {
        super.onStop()
        dutyNavigation.onActivityBackgrounded(isChangingConfigurations)
    }

    private fun refreshLive(
        generation: Long,
        apiKey: String,
        lookups: Set<FlightLookup>,
        scope: FlightRefreshScope,
        callback: (LiveRefreshResult) -> Unit,
    ) {
        val fallbackDate = LocalDate.now()
        flightRefreshExecutor.execute {
            val store = RosterStore(this)
            val client = VariFlightClient(apiKey)
            val isCurrent: (FlightLookup) -> Boolean = { lookup ->
                val current = store.loadSnapshot()
                !isDestroyed && current.generation == generation && store.variFlightApiKey == apiKey &&
                    lookup in current.assignments.refreshLookups(current.manuallyCompletedCount, scope)
            }
            val batch = refreshFlightBatch(
                targets = lookups,
                isCurrent = isCurrent,
                fetch = { lookup -> client.fetchFlightBlocking(lookup) { isCurrent(lookup) } },
            )
            val result = LiveRefreshResult(
                live = batch.live,
                airports = batch.live.values.flatten()
                    .flatMap { listOfNotNull(it.origin, it.destination) }
                    .distinctBy { it.code },
                errors = batch.errors,
                attemptedCount = batch.attemptedCount,
                refreshedCount = batch.live.size,
                fallbackDate = fallbackDate,
            )
            Handler(Looper.getMainLooper()).post {
                callback(result)
            }
        }
    }

    private companion object {
        val flightRefreshExecutor = Executors.newSingleThreadExecutor()
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
internal fun AirShiftApp(
    context: Context,
    store: RosterStore,
    specialServiceRepository: SpecialServiceRepository,
    readImageRoster: (Uri, String, (Result<RosterParseResult>) -> Unit) -> Unit,
    readExcelRoster: (Uri, String, (Result<RosterParseResult>) -> Unit) -> Unit,
    refreshLive: (Long, String, Set<FlightLookup>, FlightRefreshScope, (LiveRefreshResult) -> Unit) -> Unit,
    locateAirport: (Collection<AirportPoint>, (Result<com.bradj.airshift.location.AirportMatch>) -> Unit) -> Unit,
    openExactAlarmSettings: () -> Unit,
    openNotificationAccessSettings: () -> Unit,
    pendingSharedExcelImport: PendingSharedExcelImport?,
    sharedExcelImportQueue: SharedExcelImportQueueViewModel,
    dutyNavigation: DutyNavigationViewModel,
    configureRefresh: (Boolean) -> Unit = { FlightRefreshScheduler.configure(context, it) },
    refreshClock: () -> Long = SystemClock::elapsedRealtime,
) {
    var userName by remember { mutableStateOf(store.userName) }
    if (userName == null) {
        OnboardingScreen { name ->
            store.userName = name
            userName = name.trim()
        }
        return
    }

    val initialRoster = remember { store.loadSnapshot() }
    var assignments by remember { mutableStateOf(initialRoster.assignments) }
    var rosterGeneration by remember { mutableLongStateOf(initialRoster.generation) }
    var pendingRefresh by remember { mutableStateOf<PendingFlightRefresh?>(null) }
    var nextForegroundRefreshAt by remember { mutableLongStateOf(0L) }
    val specialServiceState by specialServiceRepository.state.collectAsStateWithLifecycle()
    var isWorking by remember { mutableStateOf(false) }
    var isLiveRefreshing by remember { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var warnings by remember { mutableStateOf(emptyList<String>()) }
    var currentAirport by rememberSaveable { mutableStateOf<String?>(null) }
    var exactAlarmWarning by remember {
        mutableStateOf(assignments.isNotEmpty() && !ReminderScheduler.canScheduleExactAlarms(context))
    }
    val section by dutyNavigation.section.collectAsStateWithLifecycle()
    var dutyIndex by remember { mutableIntStateOf(initialRoster.manuallyCompletedCount) }
    var dutyNow by remember { mutableStateOf(LocalDateTime.now()) }
    var locationCandidates by remember { mutableStateOf(emptyList<AirportPoint>()) }
    var hasVariFlightApiKey by remember { mutableStateOf(store.hasVariFlightApiKey) }
    var notificationAccessGranted by remember { mutableStateOf(NotificationAccess.isGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val operationOwner = remember { AtomicBoolean(true) }
    DisposableEffect(operationOwner) {
        onDispose { operationOwner.set(false) }
    }
    fun isOperationOwnerActive(): Boolean =
        operationOwner.get() && lifecycleOwner.lifecycle.currentState != Lifecycle.State.DESTROYED

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

    fun syncSavedRoster(
        expectedGeneration: Long,
        previousAssignments: List<RosterAssignment>? = null,
    ): Boolean {
        val applied = store.runIfGenerationCurrent(expectedGeneration) {
            // Read after acquiring the lock: the worker may have merged another flight meanwhile.
            val current = store.loadSnapshot()
            assignments = current.assignments
            rosterGeneration = current.generation
            dutyIndex = current.manuallyCompletedCount
            dutyNow = LocalDateTime.now()
            previousAssignments?.let { ReminderScheduler.cancelAll(context, it) }
            specialServiceRepository.onRosterChanged(assignments)
            configureRefresh(hasVariFlightApiKey && assignments.isNotEmpty() &&
                !assignments.allDutiesComplete(dutyNow, dutyIndex))
            val summary = ReminderScheduler.scheduleAll(context, assignments)
            exactAlarmWarning = assignments.isNotEmpty() && !summary.exactAlarmsAllowed
            statusMessage = "已保存 ${assignments.size} 个保障任务，安排 ${summary.scheduledCount} 个提醒"
        }
        if (!applied) {
            val current = store.loadSnapshot()
            assignments = current.assignments
            rosterGeneration = current.generation
            dutyIndex = current.manuallyCompletedCount
            dutyNow = LocalDateTime.now()
            pendingRefresh = null
        }
        if (applied) DutyWidgetUpdater.notifyRosterChanged(context)
        return applied
    }

    fun mergeRefresh(
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

    fun finishImport(
        result: RosterParseResult,
        isCurrentAttempt: () -> Boolean = { true },
        onFinished: () -> Unit = {},
    ) {
        if (!isOperationOwnerActive() || !isCurrentAttempt()) return
        val parsed = result.assignments
        warnings = result.warnings
        val previousAssignments = store.loadAssignments()
        val importGeneration = store.replaceAssignments(parsed)
        pendingRefresh = null
        syncSavedRoster(importGeneration, previousAssignments)
        // The import is durable now. Retrying this event after recreation would reset duty progress.
        onFinished()
        val apiKey = store.variFlightApiKey
        if (apiKey == null) {
            isWorking = false
            statusMessage = "排班已识别；尚未配置飞常准 API Key，当前按表内计划时间提醒"
            requestPermissionsAndLocate()
            return
        }
        val lookups = parsed.dutyWindowLookups(0)
        if (lookups.isEmpty()) {
            isWorking = false
            statusMessage = "排班已保存，当前没有需要实时跟踪的航班"
            requestPermissionsAndLocate()
            return
        }
        statusMessage = "正在刷新实时航班信息…"
        nextForegroundRefreshAt = refreshClock() + FOREGROUND_REFRESH_INTERVAL_MILLIS
        refreshLive(importGeneration, apiKey, lookups, FlightRefreshScope.DUTY_WINDOW) { refresh ->
            if (!isOperationOwnerActive()) return@refreshLive
            val applied = mergeRefresh(refresh, importGeneration)
            if (applied) {
                locationCandidates = refresh.airports
                warnings = result.warnings + refresh.errors
            }
            isWorking = false
            if (applied) requestPermissionsAndLocate()
        }
    }

    fun importExcel(
        uri: Uri,
        progressMessage: String,
        sharedImport: PendingSharedExcelImport.File? = null,
    ) {
        if (isWorking) return
        val attemptToken = sharedImport?.let { sharedExcelImportQueue.beginAttempt(it.id) ?: return }
        val isCurrentAttempt = {
            isOperationOwnerActive() &&
                (sharedImport == null || sharedExcelImportQueue.isCurrentAttempt(sharedImport.id, checkNotNull(attemptToken)))
        }
        val finishSharedImport = {
            if (sharedImport != null) sharedExcelImportQueue.consume(sharedImport.id, checkNotNull(attemptToken))
            Unit
        }
        isWorking = true
        warnings = emptyList()
        statusMessage = progressMessage
        readExcelRoster(uri, userName.orEmpty()) { result ->
            if (!isCurrentAttempt()) return@readExcelRoster
            result.onSuccess { finishImport(it, isCurrentAttempt, finishSharedImport) }
                .onFailure {
                    finishSharedImport()
                    isWorking = false
                    statusMessage = "Excel 识别失败：${it.message ?: "无法读取文件"}"
                }
        }
    }

    fun refreshCurrentAssignments(automatic: Boolean = false, onlyLookups: Set<FlightLookup>? = null) {
        if (isWorking) return
        val apiKey = store.variFlightApiKey
        if (apiKey == null) {
            if (!automatic) statusMessage = "请先在设置中填写飞常准 API Key"
            return
        }
        val snapshot = store.loadSnapshot()
        if (snapshot.assignments.isEmpty()) {
            if (!automatic) statusMessage = "请先导入排班图片或 Excel 文件"
            return
        }
        val window = snapshot.assignments.dutyWindowLookups(snapshot.manuallyCompletedCount)
        // An exhausted automatic window must not disable an explicit pull-to-refresh.
        val scope = if (!automatic && window.isEmpty()) FlightRefreshScope.ALL_ROSTER else FlightRefreshScope.DUTY_WINDOW
        val available = if (scope == FlightRefreshScope.ALL_ROSTER) {
            snapshot.assignments.refreshLookups(snapshot.manuallyCompletedCount, scope)
        } else window
        val lookups = onlyLookups?.let { available.intersect(it) } ?: available
        if (lookups.isEmpty()) {
            if (!automatic) statusMessage = "当前没有需要实时跟踪的航班"
            return
        }
        pendingRefresh = pendingRefresh?.takeIf { it.generation == snapshot.generation }?.let {
            it.copy(lookups = it.lookups - lookups).takeIf { pending -> pending.lookups.isNotEmpty() }
        }
        isWorking = true
        isLiveRefreshing = true
        if (onlyLookups == null) nextForegroundRefreshAt = refreshClock() + FOREGROUND_REFRESH_INTERVAL_MILLIS
        val refreshGeneration = snapshot.generation
        statusMessage = when {
            automatic -> "正在自动更新实时航班信息…"
            scope == FlightRefreshScope.ALL_ROSTER -> "正在手动更新全部排班航班信息…"
            else -> "正在刷新实时航班信息…"
        }
        refreshLive(refreshGeneration, apiKey, lookups, scope) { refresh ->
            if (!isOperationOwnerActive()) return@refreshLive
            if (refresh.attemptedCount == 0) {
                isWorking = false
                isLiveRefreshing = false
                statusMessage = "当前没有需要实时跟踪的航班"
                return@refreshLive
            }
            if (!mergeRefresh(refresh, refreshGeneration, scope)) {
                isWorking = false
                isLiveRefreshing = false
                statusMessage = "排班已变化，已忽略旧排班的刷新结果"
                return@refreshLive
            }
            locationCandidates = refresh.airports
            warnings = refresh.errors
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
                    val restored = store.loadSnapshot()
                    assignments = restored.assignments
                    rosterGeneration = restored.generation
                    specialServiceRepository.onRosterChanged(restored.assignments)
                    notificationAccessGranted = NotificationAccess.isGranted(context)
                    dutyIndex = restored.manuallyCompletedCount
                    dutyNow = LocalDateTime.now()
                    syncExactAlarmState(restored.assignments, rescheduleIfAllowed = true)
                    isForeground = true
                }
                Lifecycle.Event.ON_STOP -> isForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isForeground) {
        if (!isForeground) return@LaunchedEffect
        while (true) {
            dutyNow = LocalDateTime.now()
            dutyIndex = store.currentDutyIndex
            delay(DUTY_STATE_TICK_MILLIS)
        }
    }

    LaunchedEffect(isWorking, isForeground, pendingRefresh, rosterGeneration) {
        val pending = pendingRefresh ?: return@LaunchedEffect
        if (pending.generation != rosterGeneration) {
            pendingRefresh = null
            return@LaunchedEffect
        }
        if (isWorking || !isForeground) return@LaunchedEffect
        pendingRefresh = null
        // If the regular cycle is due too, consume both needs in one full-window request.
        val targets = pending.lookups.takeIf { nextForegroundRefreshAt > refreshClock() }
        refreshCurrentAssignments(automatic = true, onlyLookups = targets)
    }

    val activeDutyIndex = assignments.dutyWindowIndices(dutyIndex, dutyNow).firstOrNull() ?: assignments.size
    val autoRefreshEligible = assignments.isNotEmpty() && !assignments.allDutiesComplete(dutyNow, dutyIndex)
    // 新导入必须重启已退出的循环；普通实时字段变化不能触发立即再次请求。
    ForegroundFlightRefreshEffect(
        active = isForeground && assignments.isNotEmpty() && hasVariFlightApiKey,
        rosterGeneration = rosterGeneration,
        dutiesComplete = !autoRefreshEligible,
        isWorking = isWorking,
        onConfigure = configureRefresh,
        onRefresh = { refreshCurrentAssignments(automatic = true) },
        onStopped = {
            statusMessage = "今日执勤已全部完成，自动刷新已停止，仍可在全部执勤页下拉刷新"
        },
        refreshDelayMillis = { nextForegroundRefreshAt - refreshClock() },
    )

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isWorking = true
        warnings = emptyList()
        statusMessage = "正在识别排班图片…"
        readImageRoster(uri, userName.orEmpty()) { result ->
            if (!isOperationOwnerActive()) return@readImageRoster
            result.onSuccess { finishImport(it) }
                .onFailure {
                    isWorking = false
                    statusMessage = "图片识别失败：${it.message ?: "无法读取图片"}"
                }
        }
    }

    val excelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importExcel(uri, "正在解析 Excel 排班…")
    }

    LaunchedEffect(pendingSharedExcelImport?.id, userName, isWorking) {
        val pending = pendingSharedExcelImport ?: return@LaunchedEffect
        if (isWorking) return@LaunchedEffect
        when (pending) {
            is PendingSharedExcelImport.File -> importExcel(
                pending.uri,
                "正在解析分享的 Excel 排班…",
                pending,
            )
            is PendingSharedExcelImport.Error -> {
                statusMessage = "Excel 分享导入失败：${pending.message}"
                sharedExcelImportQueue.consume(pending.id)
            }
        }
    }

    val visibleSpecialServiceRecords = specialServiceState.activeRecords()
    val visibleGateChanges = specialServiceState.activeGateChanges()
    val visibleStandChanges = specialServiceState.activeStandChanges()
    val visibleFlightCancellations = specialServiceState.activeFlightCancellations()

    AirShiftRoot(
        section = section,
        onSectionSelected = dutyNavigation::selectSection,
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
                onImportExcel = { excelPicker.launch(SUPPORTED_EXCEL_MIME_TYPES.toTypedArray()) },
                onRefresh = { refreshCurrentAssignments() },
                onOpenExactAlarmSettings = openExactAlarmSettings,
                modifier = Modifier.padding(padding),
            )
            DutySection.CURRENT -> CurrentDutyScreen(
                assignments = assignments,
                dutyIndex = activeDutyIndex,
                now = dutyNow,
                specialServiceRecords = visibleSpecialServiceRecords,
                gateChanges = visibleGateChanges,
                standChanges = visibleStandChanges,
                flightCancellations = visibleFlightCancellations,
                onDutyComplete = {
                    store.completeCurrentDuty(rosterGeneration, activeDutyIndex)?.let { completion ->
                        val waiting = pendingRefresh
                            ?.takeIf { it.generation == completion.snapshot.generation }
                            ?.lookups
                            .orEmpty()
                        val currentWindow = completion.snapshot.assignments.dutyWindowLookups(
                            completion.snapshot.manuallyCompletedCount,
                        )
                        val pending = (waiting + completion.newlyTrackedFlights).intersect(currentWindow)
                        pendingRefresh = PendingFlightRefresh(completion.snapshot.generation, pending)
                            .takeIf { pending.isNotEmpty() }
                    }
                    syncSavedRoster(rosterGeneration)
                },
                onGoToAllDuty = { dutyNavigation.selectSection(DutySection.ALL) },
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
                        configureRefresh(
                            hasVariFlightApiKey && assignments.isNotEmpty() &&
                                !assignments.allDutiesComplete(manuallyCompletedCount = dutyIndex),
                        )
                        userName = name.trim()
                    }.onFailure { error ->
                        statusMessage = error.message ?: "无法保存设置"
                    }
                },
                onClearApiKey = {
                    store.clearVariFlightApiKey()
                    VariFlightClient.clearCachedFlights()
                    hasVariFlightApiKey = false
                    configureRefresh(false)
                    statusMessage = "飞常准 API Key 已清除，后台实时刷新已停止"
                },
                onTestConnection = ::testVariFlightApiKey,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
