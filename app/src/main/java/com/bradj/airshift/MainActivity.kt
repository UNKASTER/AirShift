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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.bradj.airshift.api.AirportPoint
import com.bradj.airshift.api.FlightGatewayClient
import com.bradj.airshift.api.FlightInfo
import com.bradj.airshift.api.FlightRefreshScheduler
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.location.AirportLocator
import com.bradj.airshift.model.AssignmentKind
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.parser.OcrRosterReader
import com.bradj.airshift.parser.RosterParseResult
import com.bradj.airshift.reminder.ReminderReceiver
import com.bradj.airshift.reminder.ReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderReceiver.createChannel(this)
        val store = RosterStore(this)
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
        gatewayBaseUrl: String,
        callback: (List<RosterAssignment>, List<AirportPoint>, List<String>) -> Unit,
    ) {
        val requests = assignments.flatMap { assignment ->
            buildList {
                assignment.inboundFlight?.let { add(it to assignment.scheduledArrival?.toLocalDate()) }
                assignment.outboundFlight?.let { add(it to assignment.scheduledDeparture?.toLocalDate()) }
            }
        }.distinctBy { it.first to it.second }
        if (requests.isEmpty()) {
            callback(assignments, emptyList(), emptyList())
            return
        }

        val client = FlightGatewayClient(gatewayBaseUrl)
        val results = mutableMapOf<String, FlightInfo>()
        val errors = mutableListOf<String>()
        var remaining = requests.size
        requests.forEach { (flightNumber, date) ->
            client.fetchFlight(flightNumber, date ?: LocalDate.now()) { result ->
                result.onSuccess { results[flightNumber] = it }
                    .onFailure { errors += "$flightNumber：${it.message ?: "刷新失败"}" }
                remaining--
                if (remaining == 0) {
                    val enriched = assignments.map { assignment ->
                        val inbound = assignment.inboundFlight?.let(results::get)
                        val outbound = assignment.outboundFlight?.let(results::get)
                        assignment.copy(
                            origin = inbound?.origin?.name ?: assignment.origin,
                            destination = outbound?.destination?.name ?: assignment.destination,
                            estimatedArrival = inbound?.estimatedArrival,
                            actualArrival = inbound?.actualArrival,
                            estimatedDeparture = outbound?.estimatedDeparture,
                            actualDeparture = outbound?.actualDeparture,
                            arrivalGate = inbound?.arrivalGate,
                            arrivalBridge = inbound?.arrivalBridge,
                        )
                    }
                    val airports = results.values
                        .flatMap { listOfNotNull(it.origin, it.destination) }
                        .distinctBy { it.code }
                    callback(enriched, airports, errors)
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
    readRoster: (Uri, String, (Result<RosterParseResult>) -> Unit) -> Unit,
    refreshLive: (List<RosterAssignment>, String, (List<RosterAssignment>, List<AirportPoint>, List<String>) -> Unit) -> Unit,
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
    var isWorking by rememberSaveable { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var warnings by remember { mutableStateOf(emptyList<String>()) }
    var currentAirport by rememberSaveable { mutableStateOf<String?>(null) }
    var exactAlarmWarning by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var locationCandidates by remember { mutableStateOf(emptyList<AirportPoint>()) }

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
                    currentAirport = "${it.airport.name}（${it.airport.code}，约 ${"%.1f".format(it.distanceKm)} km）"
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
        FlightRefreshScheduler.configure(context, store.gatewayBaseUrl != null)
        val summary = ReminderScheduler.scheduleAll(context, updated)
        exactAlarmWarning = !summary.exactAlarmsAllowed
        statusMessage = "已保存 ${updated.size} 个保障任务，安排 ${summary.scheduledCount} 个提醒"
    }

    fun locateWhenReady() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            runtimePermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else if (locationCandidates.isNotEmpty()) {
            locateAirport(locationCandidates) { result ->
                result.onSuccess {
                    currentAirport = "${it.airport.name}（${it.airport.code}，约 ${"%.1f".format(it.distanceKm)} km）"
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

    fun finishImport(parsed: List<RosterAssignment>, parseWarnings: List<String>) {
        warnings = parseWarnings
        val gateway = store.gatewayBaseUrl
        if (gateway == null) {
            isWorking = false
            scheduleAndSave(parsed)
            statusMessage = "排班已识别；实时航班服务尚未配置，当前按表内计划时间提醒"
            requestPermissionsAndLocate()
            return
        }
        statusMessage = "正在刷新实时航班信息…"
        refreshLive(parsed, gateway) { enriched, airports, apiErrors ->
            locationCandidates = airports
            warnings = parseWarnings + apiErrors
            scheduleAndSave(enriched)
            isWorking = false
            requestPermissionsAndLocate()
        }
    }

    fun refreshCurrentAssignments() {
        val gateway = store.gatewayBaseUrl
        if (gateway == null) {
            statusMessage = "请先在设置中填写实时航班网关地址"
            return
        }
        if (assignments.isEmpty()) {
            statusMessage = "请先导入排班图片"
            return
        }
        isWorking = true
        statusMessage = "正在刷新实时航班信息…"
        refreshLive(assignments, gateway) { enriched, airports, apiErrors ->
            locationCandidates = airports
            warnings = apiErrors
            scheduleAndSave(enriched)
            isWorking = false
            locateWhenReady()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isWorking = true
        warnings = emptyList()
        statusMessage = "正在识别排班表…"
        readRoster(uri, userName.orEmpty()) { result ->
            result.onSuccess { parsed -> finishImport(parsed.assignments, parsed.warnings) }
                .onFailure {
                    isWorking = false
                    statusMessage = "识别失败：${it.message ?: "无法读取图片"}"
                }
        }
    }

    if (showSettings) {
        SettingsDialog(
            currentName = userName.orEmpty(),
            currentGateway = store.gatewayBaseUrl.orEmpty(),
            onDismiss = { showSettings = false },
            onSave = { name, gateway ->
                store.userName = name
                store.gatewayBaseUrl = gateway.takeIf { it.isNotBlank() }
                FlightRefreshScheduler.configure(context, store.gatewayBaseUrl != null)
                userName = name.trim()
                showSettings = false
            },
        )
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Header(userName.orEmpty(), currentAirport, onSettings = { showSettings = true }) }
            item {
                ImportCard(
                    isWorking = isWorking,
                    statusMessage = statusMessage,
                    canRefresh = assignments.isNotEmpty() && store.gatewayBaseUrl != null,
                    onImport = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onRefresh = ::refreshCurrentAssignments,
                )
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
    statusMessage: String?,
    canRefresh: Boolean,
    onImport: () -> Unit,
    onRefresh: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("导入今日排班", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("选择固定模板的完整排班截图，系统会自动识别姓名、航班与保障时间。")
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onImport,
                    enabled = !isWorking,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.size(10.dp))
                        Text("处理中")
                    } else {
                        Text("选择排班图片")
                    }
                }
                TextButton(onClick = onRefresh, enabled = canRefresh && !isWorking) { Text("刷新实时信息") }
            }
            statusMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun AssignmentCard(assignment: RosterAssignment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        when (assignment.kind) {
                            AssignmentKind.ARRIVAL_ONLY -> "进港保障"
                            AssignmentKind.DEPARTURE_ONLY -> "出港保障"
                            AssignmentKind.TURNAROUND -> "进港后接续出港"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        listOfNotNull(assignment.aircraftRegistration, assignment.aircraftType).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                assignment.arrivalGate?.let { Badge("到达 $it") }
            }
            Spacer(Modifier.height(14.dp))
            assignment.inboundFlight?.let {
                FlightRow("进港", it, "${assignment.origin.orEmpty()} → 本场", assignment.scheduledArrival, assignment.estimatedArrival, assignment.actualArrival)
            }
            if (assignment.inboundFlight != null && assignment.outboundFlight != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
            assignment.outboundFlight?.let {
                FlightRow("出港", it, "本场 → ${assignment.destination.orEmpty()}", assignment.scheduledDeparture, assignment.estimatedDeparture, assignment.actualDeparture)
            }
            assignment.arrivalBridge?.let {
                Spacer(Modifier.height(10.dp))
                Text("机位类型：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FlightRow(
    direction: String,
    flight: String,
    route: String,
    planned: LocalDateTime?,
    estimated: LocalDateTime?,
    actual: LocalDateTime?,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text("$direction  $flight", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(route, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("计划 ${planned.formatTime()}")
            estimated?.let { Text("预计 ${it.formatTime()}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium) }
            actual?.let { Text("实际 ${it.formatTime()}", color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
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
    currentGateway: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    var gateway by rememberSaveable { mutableStateOf(currentGateway) }
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
                    value = gateway,
                    onValueChange = { gateway = it.trim() },
                    label = { Text("实时航班网关地址") },
                    placeholder = { Text("https://…") },
                    supportingText = { Text("API 密钥只保留在服务端，不写入手机应用") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), gateway.trim()) }, enabled = name.trim().length >= 2) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
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

private fun LocalDateTime?.formatTime(): String =
    this?.format(DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)) ?: "--:--"
