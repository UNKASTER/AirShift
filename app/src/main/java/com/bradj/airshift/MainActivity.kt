package com.bradj.airshift

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bradj.airshift.api.FlightRefreshScheduler
import com.bradj.airshift.data.LegacyMigrations
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.duty.AppDutyPorts
import com.bradj.airshift.duty.DutyViewModel
import com.bradj.airshift.model.allDutiesComplete
import com.bradj.airshift.parser.ExcelRosterReader
import com.bradj.airshift.parser.OcrRosterReader
import com.bradj.airshift.reminder.ReminderReceiver
import com.bradj.airshift.specialservice.NotificationAccess
import com.bradj.airshift.ui.AirShiftApp
import com.bradj.airshift.ui.DutyNavigationViewModel
import com.bradj.airshift.ui.theme.AirShiftTheme

/**
 * Composition root：装配依赖、接收分享 Intent、转发生命周期。
 * 业务编排在 [DutyViewModel]，页面装配在 [AirShiftApp]。
 */
class MainActivity : ComponentActivity() {
    private val sharedExcelImportQueue: SharedExcelImportQueueViewModel by viewModels()
    private val dutyNavigation: DutyNavigationViewModel by viewModels()
    private val dutyViewModel: DutyViewModel by viewModels {
        DutyViewModel.factory(AppDutyPorts.create(applicationContext))
    }

    /**
     * 请求高刷新率。vivo 默认把本应用按 60 Hz 排帧（取证：`mActiveModeId=4`、应用未请求帧率），动画只有一半的帧；
     * 请求后切到 120 Hz（vsync 16.6 → 8.3 ms）。只对本窗口生效，退到后台即恢复系统默认。
     * 只给 `preferredRefreshRate` 提示（取同分辨率下不超过 [MAX_REQUESTED_REFRESH_RATE] 的最高模式的刷新率），
     * 不钉死 `preferredDisplayModeId`——钉死会连面板刷新率的下限一起锁住；两种写法实测都切到 120 Hz。不用 144 Hz。
     */
    private fun requestHighRefreshRate() {
        val current = display.mode
        val target = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .filter { it.refreshRate <= MAX_REQUESTED_REFRESH_RATE }
            .maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply {
            preferredRefreshRate = target.refreshRate
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) sharedExcelImportQueue.enqueue(intent)
        setIntent(Intent(Intent.ACTION_MAIN))
        // 每页顶部都是藏青板面，状态栏图标恒为浅色；导航栏透明，跟随底栏颜色。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        requestHighRefreshRate()
        ReminderReceiver.createChannel(this)
        LegacyMigrations.runOnce(this)
        val store = RosterStore(this)
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
                    viewModel = dutyViewModel,
                    readImageRoster = { uri, name -> OcrRosterReader.read(this, uri, name) },
                    readExcelRoster = { uri, name -> ExcelRosterReader.read(this, uri, name) },
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

    private fun openExactAlarmSettings() {
        startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:$packageName".toUri()
            },
        )
    }

    private companion object {
        /** 请求的刷新率上限：120 Hz 已让动画帧数翻倍，144 Hz 只多耗电。 */
        const val MAX_REQUESTED_REFRESH_RATE = 121f
    }
}
