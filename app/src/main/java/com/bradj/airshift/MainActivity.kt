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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bradj.airshift.api.AirportPoint
import com.bradj.airshift.api.FlightInfo
import com.bradj.airshift.api.FlightLookup
import com.bradj.airshift.api.FlightRefreshScheduler
import com.bradj.airshift.api.VariFlightClient
import com.bradj.airshift.api.withLiveInfo
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.location.AirportLocator
import com.bradj.airshift.model.AssignmentKind
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.parser.OcrRosterReader
import com.bradj.airshift.parser.RosterParseResult
import com.bradj.airshift.reminder.ReminderReceiver
import com.bradj.airshift.reminder.ReminderScheduler
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val FOREGROUND_REFRESH_INTERVAL_MILLIS = 5 * 60 * 1000L
private const val BUSY_REFRESH_RETRY_MILLIS = 15 * 1000L

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
        FlightRefreshScheduler.configure(this, store.hasVariFlightApiKey)
        setContent {
            AirShiftTheme {
                AirShiftApp(
                    context = this,
                    store = store,
                    readRoster = { uri, name, callback -> OcrRosterReader.read(this, uri, name, callback) },
                    refreshLive = ::refreshLive,
                    locateAirport = { candidates, callback -> AirportLocator.locate(this, candidates, callback) },
                    openExactAlarmSettings = ::openExactAlarmSettings,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AirShiftApp(
    context: Context,
    store: RosterStore,
    readRoster: (Uri, String, (Result<RosterParseResult>) -> Unit) -> Unit,
    refreshLive: (List<RosterAssignment>, String, Boolean, (LiveRefreshResult) -> Unit) -> Unit,
    locateAirport: (Collection<AirportPoint>, (Result<com.bradj.airshift.location.AirportMatch>) -> Unit) -> Unit,
    openExactAlarmSettings: () -> Unit,
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
    var isWorking by remember { mutableStateOf(false) }
    var isLiveRefreshing by remember { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var warnings by remember { mutableStateOf(emptyList<String>()) }
    var currentAirport by rememberSaveable { mutableStateOf<String?>(null) }
    var exactAlarmWarning by remember {
        mutableStateOf(assignments.isNotEmpty() && !ReminderScheduler.canScheduleExactAlarms(context))
    }
    var showSettings by remember { mutableStateOf(false) }
    var locationCandidates by remember { mutableStateOf(emptyList<AirportPoint>()) }
    var hasVariFlightApiKey by remember { mutableStateOf(store.hasVariFlightApiKey) }
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
            if (!automatic) statusMessage = "请先导入排班图片"
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
        statusMessage = "正在识别排班表…"
        readRoster(uri, userName.orEmpty()) { result ->
            result.onSuccess(::finishImport)
                .onFailure {
                    isWorking = false
                    statusMessage = "识别失败：${it.message ?: "无法读取图片"}"
                }
        }
    }

    if (showSettings) {
        SettingsDialog(
            currentName = userName.orEmpty(),
            currentApiKey = store.variFlightApiKey.orEmpty(),
            hasStoredApiKey = hasVariFlightApiKey,
            onDismiss = { showSettings = false },
            onSave = { name, apiKey ->
                runCatching {
                    store.userName = name
                    if (apiKey.isNotBlank()) store.variFlightApiKey = apiKey
                }.onSuccess {
                    VariFlightClient.clearCachedFlights()
                    hasVariFlightApiKey = store.hasVariFlightApiKey
                    FlightRefreshScheduler.configure(context, hasVariFlightApiKey)
                    userName = name.trim()
                    showSettings = false
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
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        PullToRefreshBox(
            isRefreshing = isLiveRefreshing,
            onRefresh = { refreshCurrentAssignments() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { Header(userName.orEmpty(), currentAirport, onSettings = { showSettings = true }) }
                item {
                    ImportCard(
                        isWorking = isWorking,
                        onImport = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                }
                statusMessage?.let { message ->
                    item {
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (exactAlarmWarning) {
                    item {
                        ActionNotice(
                            text = "系统尚未允许精确闹钟，提醒时间可能略有偏差。",
                            action = "开启精确提醒",
                            onAction = openExactAlarmSettings,
                        )
                    }
                }
                if (warnings.isNotEmpty()) item { WarningCard(warnings) }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("我的保障任务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${assignments.size} 项", color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (assignments.isEmpty()) {
                    item { EmptyRoster() }
                } else {
                    items(assignments, key = { it.stableId }) { assignment -> AssignmentCard(assignment) }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun OnboardingScreen(onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("航勤智排", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("先告诉我你的姓名。之后每次导入排班图，只会提取分配给你的航班。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(20) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("排班表中的姓名") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onSave(name.trim()) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = name.trim().length >= 2,
            ) { Text("保存并开始使用") }
            Spacer(Modifier.height(12.dp))
            Text("姓名仅保存在本机，可稍后在设置中修改。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Header(name: String, airport: String?, onSettings: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("你好，$name", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                airport?.let { "当前位置：$it" } ?: "当前位置：导入并刷新航班后自动识别机场",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onSettings) { Text("设置") }
    }
}

@Composable
private fun ImportCard(
    isWorking: Boolean,
    onImport: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Button(
            onClick = onImport,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            enabled = !isWorking,
        ) {
            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(10.dp))
                Text("处理中")
            } else {
                Text("上传排班图片")
            }
        }
    }
}

@Composable
private fun AssignmentCard(assignment: RosterAssignment) {
    val vipAccent = Color(0xFFF59E0B)
    val vipBadgeText = when {
        assignment.inboundHasVip && assignment.outboundHasVip -> "VIP"
        assignment.inboundHasVip -> "进港 VIP"
        assignment.outboundHasVip -> "出港 VIP"
        else -> null
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (assignment.hasVip) BorderStroke(2.dp, vipAccent) else null,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when (assignment.kind) {
                        AssignmentKind.ARRIVAL_ONLY -> "进港保障"
                        AssignmentKind.DEPARTURE_ONLY -> "出港保障"
                        AssignmentKind.TURNAROUND -> "进港后接续出港"
                    },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                vipBadgeText?.let { label ->
                    Surface(
                        color = Color(0xFFFFE3A3),
                        shape = RoundedCornerShape(7.dp),
                    ) {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            color = Color(0xFF7A4300),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Text(
                buildList {
                    add("机号：${assignment.aircraftRegistration}")
                    assignment.aircraftType?.let { add("机型：$it") }
                }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            assignment.inboundFlight?.let { flight ->
                FlightRow(
                    direction = "进港",
                    flight = flight,
                    fromCode = assignment.originCode,
                    fromName = assignment.origin,
                    toCode = assignment.localAirportCode,
                    toName = assignment.localAirportName,
                    planned = assignment.scheduledArrival,
                    estimated = assignment.estimatedArrival,
                    actual = assignment.actualArrival,
                    details = listOf(
                        "始发登机口：${assignment.inboundBoardingGate ?: "--"}",
                        "登机口关闭：${assignment.inboundGateClosedObservedAt.formatClock()}",
                        "实际离位：${assignment.inboundActualOffBlock.formatClock()}",
                        "到达机位：${assignment.arrivalStand ?: "--"}",
                    ) + listOfNotNull(assignment.arrivalBridge?.let { "机位类型：$it" }),
                )
            }
            if (assignment.inboundFlight != null && assignment.outboundFlight != null) {
                Spacer(Modifier.height(12.dp))
            }
            assignment.outboundFlight?.let { flight ->
                FlightRow(
                    direction = "出港",
                    flight = flight,
                    fromCode = assignment.localAirportCode,
                    fromName = assignment.localAirportName,
                    toCode = assignment.destinationCode,
                    toName = assignment.destination,
                    planned = assignment.scheduledDeparture,
                    estimated = assignment.estimatedDeparture,
                    actual = assignment.actualDeparture,
                    details = listOf(
                        "登机口：${assignment.boardingGate ?: "--"}",
                        "出发机位：${assignment.departureStand ?: "--"}",
                        "登机口关闭：${assignment.outboundGateClosedObservedAt.formatClock()}",
                        "实际离位：${assignment.outboundActualOffBlock.formatClock()}",
                    ),
                )
            }
        }
    }
}

@Composable
private fun FlightRow(
    direction: String,
    flight: String,
    fromCode: String?,
    fromName: String?,
    toCode: String?,
    toName: String?,
    planned: LocalDateTime?,
    estimated: LocalDateTime?,
    actual: LocalDateTime?,
    details: List<String>,
) {
    val liveTime = actual ?: estimated
    val timeColor = if (liveTime == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
    val isInbound = direction == "进港"
    val sectionColor = if (isInbound) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val accentColor = if (isInbound) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = sectionColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${direction}信息", style = MaterialTheme.typography.labelLarge, color = accentColor)
                    Text(flight, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Column(modifier = Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
                    Text(
                        "实时",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        liveTime.formatClock(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = timeColor,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "计划：${planned.formatClock()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AirportRouteLabel(
                    code = fromCode,
                    name = fromName,
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start,
                )
                Text("→", modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                AirportRouteLabel(
                    code = toCode,
                    name = toName,
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                )
            }
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    details.forEach { detail ->
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = accentColor,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AirportRouteLabel(
    code: String?,
    name: String?,
    modifier: Modifier,
    horizontalAlignment: Alignment.Horizontal,
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            code ?: "---",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            name ?: "机场名称待更新",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WarningCard(warnings: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("需要留意", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            warnings.distinct().forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun ActionNotice(text: String, action: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun EmptyRoster() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有排班", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("导入截图后，你的保障任务会显示在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsDialog(
    currentName: String,
    currentApiKey: String,
    hasStoredApiKey: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onClearApiKey: () -> Unit,
    onTestConnection: (String, (Result<Unit>) -> Unit) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    // The plaintext API key must never enter Android's saved-instance-state bundle.
    var apiKey by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var isTesting by remember { mutableStateOf(false) }
    var connectionMessage by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("姓名") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        connectionMessage = null
                    },
                    label = { Text("飞常准 API Key") },
                    supportingText = { Text("由你手动输入，使用 Android Keystore + AES-GCM 加密保存") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                connectionMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = {
                        isTesting = true
                        connectionMessage = null
                        onTestConnection(apiKey.trim()) { result ->
                            isTesting = false
                            connectionMessage = result.fold(
                                onSuccess = { "连接成功" },
                                onFailure = { error -> "连接失败：${error.message ?: "请稍后重试"}" },
                            )
                        }
                    },
                    enabled = apiKey.isNotBlank() && !isTesting,
                ) { Text(if (isTesting) "测试中…" else "测试连接") }
                TextButton(
                    onClick = { onSave(name.trim(), apiKey.trim()) },
                    enabled = name.trim().length >= 2 && !isTesting,
                ) { Text("保存") }
            }
        },
        dismissButton = {
            Row {
                if (hasStoredApiKey || apiKey.isNotBlank()) {
                    TextButton(
                        onClick = {
                            onClearApiKey()
                            apiKey = ""
                            connectionMessage = "API Key 已清除"
                        },
                        enabled = !isTesting,
                    ) { Text("清除 API Key") }
                }
                TextButton(onClick = onDismiss, enabled = !isTesting) { Text("取消") }
            }
        },
    )
}

@Composable
private fun AirShiftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF116B5B),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD6F2E9),
            onPrimaryContainer = Color(0xFF083D34),
            secondary = Color(0xFF48645D),
            secondaryContainer = Color(0xFFD6E8E1),
            tertiary = Color(0xFF765A00),
            tertiaryContainer = Color(0xFFFFE08B),
            background = Color(0xFFF6FAF8),
            surface = Color.White,
            surfaceVariant = Color(0xFFE9EFEC),
        ),
        content = content,
    )
}

private fun LocalDateTime?.formatClock(): String =
    this?.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)) ?: "--:--"
