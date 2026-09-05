package com.bradj.airshift.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bradj.airshift.ForegroundFlightRefreshEffect
import com.bradj.airshift.PendingSharedExcelImport
import com.bradj.airshift.SUPPORTED_EXCEL_MIME_TYPES
import com.bradj.airshift.SharedExcelImportQueueViewModel
import com.bradj.airshift.duty.DutyUiEvent
import com.bradj.airshift.duty.DutyUiState
import com.bradj.airshift.duty.DutyViewModel
import com.bradj.airshift.duty.ImportAttempt
import com.bradj.airshift.duty.RosterSource
import com.bradj.airshift.model.shift.ShiftSchedule
import com.bradj.airshift.parser.RosterParseResult
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.GateChangeRecord
import com.bradj.airshift.specialservice.SpecialServiceState
import com.bradj.airshift.specialservice.StandChangeRecord
import com.bradj.airshift.ui.theme.AirShiftMotion
import com.bradj.airshift.ui.all.AllDutyScreen
import com.bradj.airshift.ui.calendar.NextShift
import com.bradj.airshift.ui.calendar.ShiftCalendarScreen
import com.bradj.airshift.ui.current.CurrentDutyScreen
import com.bradj.airshift.ui.onboarding.OnboardingScreen
import com.bradj.airshift.ui.settings.SettingsScreen
import kotlinx.coroutines.delay

private const val DUTY_STATE_TICK_MILLIS = 60 * 1000L

/**
 * 四页装配：渲染 [DutyViewModel.uiState]，把用户动作、生命周期、权限结果与分享队列转发给 ViewModel。
 * 这里不再包含任何业务流程；导入/刷新/完成的规则在 [DutyViewModel]。
 */
@Composable
internal fun AirShiftApp(
    viewModel: DutyViewModel,
    readImageRoster: suspend (Uri, String) -> RosterParseResult,
    readExcelRoster: suspend (Uri, String) -> RosterParseResult,
    openExactAlarmSettings: () -> Unit,
    openNotificationAccessSettings: () -> Unit,
    pendingSharedExcelImport: PendingSharedExcelImport?,
    sharedExcelImportQueue: SharedExcelImportQueueViewModel,
    dutyNavigation: DutyNavigationViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val userName = state.userName
    if (userName == null) {
        OnboardingScreen(now = state.now) { name -> viewModel.saveUserName(name) }
        return
    }

    val specialServiceState by viewModel.specialServiceState.collectAsStateWithLifecycle()
    val section by dutyNavigation.section.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val runtimePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants -> viewModel.onPermissionsResult(grants) }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DutyUiEvent.RequestPermissions -> runtimePermissions.launch(event.permissions.toTypedArray())
            }
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onForegrounded()
                Lifecycle.Event.ON_STOP -> viewModel.onBackgrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.isForeground, viewModel) {
        if (!state.isForeground) return@LaunchedEffect
        while (true) {
            viewModel.tick()
            delay(DUTY_STATE_TICK_MILLIS)
        }
    }

    // 新导入必须重启已退出的循环；普通实时字段变化不能触发立即再次请求。
    ForegroundFlightRefreshEffect(
        active = state.isForeground && state.assignments.isNotEmpty() && state.hasVariFlightApiKey,
        rosterGeneration = state.rosterGeneration,
        dutiesComplete = !state.autoRefreshEligible,
        isWorking = state.isWorking,
        onConfigure = viewModel::configureBackgroundRefresh,
        onRefresh = { viewModel.refreshCurrentAssignments(automatic = true) },
        onStopped = viewModel::onAutoRefreshStopped,
        refreshDelayMillis = viewModel::foregroundRefreshDelayMillis,
    )

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importImage(RosterSource { name -> readImageRoster(uri, name) })
    }
    val excelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importExcel(RosterSource { name -> readExcelRoster(uri, name) })
    }

    LaunchedEffect(pendingSharedExcelImport?.id, userName, state.isWorking) {
        val pending = pendingSharedExcelImport ?: return@LaunchedEffect
        if (state.isWorking) return@LaunchedEffect
        when (pending) {
            is PendingSharedExcelImport.File -> {
                val token = sharedExcelImportQueue.beginAttempt(pending.id) ?: return@LaunchedEffect
                viewModel.importExcel(
                    source = RosterSource { name -> readExcelRoster(pending.uri, name) },
                    attempt = ImportAttempt(
                        isCurrent = { sharedExcelImportQueue.isCurrentAttempt(pending.id, token) },
                        onFinished = { sharedExcelImportQueue.consume(pending.id, token) },
                    ),
                    progressMessage = "正在解析分享的 Excel 排班…",
                )
            }
            is PendingSharedExcelImport.Error -> {
                viewModel.reportSharedImportError(pending.message)
                sharedExcelImportQueue.consume(pending.id)
            }
        }
    }

    // 排班日历：班组表与轮转相位随最近一次 Excel 班次行校正；解析不到时保持内置表。
    val shiftSchedule = remember(state.shiftCalibration) { ShiftSchedule(state.shiftCalibration) }
    val autoShiftGroupId = remember(shiftSchedule, userName) { shiftSchedule.findGroupIdForName(userName) }
    val shiftGroupId = autoShiftGroupId ?: state.manualShiftGroupId
    // 当前执勤全部完成后，板面显示下一次到岗；只在日历相关状态变化时重算。
    val today = state.now.toLocalDate()
    val margin = state.shiftReportMarginMinutes
    val nextShiftText = remember(shiftSchedule, shiftGroupId, state.assignments, margin, today) {
        NextShift.text(shiftSchedule, shiftGroupId, state.assignments, margin, today)
    }

    // 只在 MUC 状态或分钟 tick 变化时重新过滤：每次重组都产生新 List 会让全部任务卡跟着重组。
    val visibleSpecialServiceRecords = remember(specialServiceState, state.now) { specialServiceState.activeRecords() }
    val visibleGateChanges = remember(specialServiceState, state.now) { specialServiceState.activeGateChanges() }
    val visibleStandChanges = remember(specialServiceState, state.now) { specialServiceState.activeStandChanges() }
    val visibleFlightCancellations = remember(specialServiceState, state.now) { specialServiceState.activeFlightCancellations() }

    AirShiftRoot(
        section = section,
        onSectionSelected = dutyNavigation::selectSection,
    ) { padding ->
        // 分区切换用 shared-axis：新页按底栏标签的左右方向从 16dp 位移滑入并淡入（Enter 档，同曲线），旧页 Exit 档淡出。
        val sectionOffsetPx = with(LocalDensity.current) { AirShiftMotion.SectionOffset.roundToPx() }
        AnimatedContent(
            targetState = section,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                val offset = if (forward) sectionOffsetPx else -sectionOffsetPx
                val enter = fadeIn(AirShiftMotion.enter()) + slideInHorizontally(AirShiftMotion.enter()) { offset }
                // 旧页的位移沿用 Enter 档（0.11.1 行为）；切页方案的下一批会把退场改为只淡出。
                val exit = fadeOut(AirShiftMotion.exit()) + slideOutHorizontally(AirShiftMotion.enter()) { -offset }
                enter togetherWith exit
            },
            label = "section",
        ) { target ->
            SectionContent(
                section = target,
                padding = padding,
                state = state,
                userName = userName,
                specialServiceState = specialServiceState,
                shiftSchedule = shiftSchedule,
                shiftGroupId = shiftGroupId,
                autoShiftGroupId = autoShiftGroupId,
                nextShiftText = nextShiftText,
                visibleSpecialServiceRecords = visibleSpecialServiceRecords,
                visibleGateChanges = visibleGateChanges,
                visibleStandChanges = visibleStandChanges,
                visibleFlightCancellations = visibleFlightCancellations,
                viewModel = viewModel,
                dutyNavigation = dutyNavigation,
                openExactAlarmSettings = openExactAlarmSettings,
                openNotificationAccessSettings = openNotificationAccessSettings,
                onImportImage = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onImportExcel = { excelPicker.launch(SUPPORTED_EXCEL_MIME_TYPES.toTypedArray()) },
            )
        }
    }
}

@Composable
private fun SectionContent(
    section: DutySection,
    padding: PaddingValues,
    state: DutyUiState,
    userName: String,
    specialServiceState: SpecialServiceState,
    shiftSchedule: ShiftSchedule,
    shiftGroupId: Int?,
    autoShiftGroupId: Int?,
    nextShiftText: String?,
    visibleSpecialServiceRecords: List<FlightServiceRecord>,
    visibleGateChanges: List<GateChangeRecord>,
    visibleStandChanges: List<StandChangeRecord>,
    visibleFlightCancellations: List<FlightCancellationRecord>,
    viewModel: DutyViewModel,
    dutyNavigation: DutyNavigationViewModel,
    openExactAlarmSettings: () -> Unit,
    openNotificationAccessSettings: () -> Unit,
    onImportImage: () -> Unit,
    onImportExcel: () -> Unit,
) {
        when (section) {
            DutySection.ALL -> AllDutyScreen(
                currentAirport = state.currentAirport,
                now = state.now,
                isWorking = state.isWorking,
                isLiveRefreshing = state.isLiveRefreshing,
                statusMessage = state.statusMessage,
                warnings = state.warnings,
                exactAlarmWarning = state.exactAlarmWarning,
                assignments = state.assignments,
                manuallyCompletedCount = state.manuallyCompletedCount,
                specialServiceRecords = visibleSpecialServiceRecords,
                gateChanges = visibleGateChanges,
                standChanges = visibleStandChanges,
                flightCancellations = visibleFlightCancellations,
                onImportImage = onImportImage,
                onImportExcel = onImportExcel,
                onRefresh = { viewModel.refreshCurrentAssignments() },
                onOpenExactAlarmSettings = openExactAlarmSettings,
                modifier = Modifier.padding(padding),
            )
            DutySection.CURRENT -> CurrentDutyScreen(
                assignments = state.assignments,
                dutyIndex = state.activeDutyIndex,
                now = state.now,
                specialServiceRecords = visibleSpecialServiceRecords,
                gateChanges = visibleGateChanges,
                standChanges = visibleStandChanges,
                flightCancellations = visibleFlightCancellations,
                onDutyComplete = viewModel::completeCurrentDuty,
                onGoToAllDuty = { dutyNavigation.selectSection(DutySection.ALL) },
                modifier = Modifier.padding(padding),
                nextShiftText = nextShiftText,
            )
            DutySection.CALENDAR -> ShiftCalendarScreen(
                userName = userName,
                schedule = shiftSchedule,
                groupId = shiftGroupId,
                assignments = state.assignments,
                reportMarginMinutes = state.shiftReportMarginMinutes,
                now = state.now,
                onGoToSettings = { dutyNavigation.selectSection(DutySection.SETTINGS) },
                modifier = Modifier.padding(padding),
            )
            DutySection.SETTINGS -> SettingsScreen(
                currentName = userName,
                // 只在进入设置页或 Key 变化时解密一次；重组不再触发 Keystore。
                currentApiKey = remember(state.hasVariFlightApiKey) { viewModel.storedApiKey() },
                hasStoredApiKey = state.hasVariFlightApiKey,
                notificationAccessGranted = state.notificationAccessGranted,
                lastSuccessfulRecognitionEpochMillis = specialServiceState.lastSuccessfulRecognitionEpochMillis,
                lastProcessingResult = specialServiceState.lastProcessingResult,
                shiftGroupId = shiftGroupId,
                shiftGroupAutoDetected = autoShiftGroupId != null,
                shiftGroupOptions = shiftSchedule.table.cycleOrder.sorted(),
                shiftReportMarginMinutes = state.shiftReportMarginMinutes,
                now = state.now,
                onShiftGroupSelected = viewModel::selectShiftGroup,
                onShiftReportMarginSelected = viewModel::selectReportMargin,
                onOpenNotificationAccessSettings = openNotificationAccessSettings,
                onSave = viewModel::saveSettings,
                onClearApiKey = viewModel::clearApiKey,
                onTestConnection = viewModel::testApiKey,
                modifier = Modifier.padding(padding),
            )
        }
}
