package com.bradj.airshift.duty

import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.allDutiesComplete
import com.bradj.airshift.model.dutyWindowIndices
import com.bradj.airshift.model.shift.ManualShiftGroup
import com.bradj.airshift.model.shift.ShiftBusPlan
import com.bradj.airshift.model.shift.ShiftCalibration
import com.bradj.airshift.model.shift.ShiftTeam
import java.time.LocalDateTime

/** 四页界面共用的编排状态；页面只读取，不直接修改。 */
internal data class DutyUiState(
    val userName: String?,
    val assignments: List<RosterAssignment>,
    val rosterGeneration: Long,
    val manuallyCompletedCount: Int,
    val now: LocalDateTime,
    val isForeground: Boolean = false,
    val isWorking: Boolean = false,
    val isLiveRefreshing: Boolean = false,
    val statusMessage: String? = null,
    val warnings: List<String> = emptyList(),
    val currentAirport: String? = null,
    val exactAlarmWarning: Boolean = false,
    val hasVariFlightApiKey: Boolean = false,
    val notificationAccessGranted: Boolean = false,
    val shiftCalibration: ShiftCalibration? = null,
    /** 手动大组，只在没有校准表时生效；手动班组只对指定时所在的大组有效。 */
    val manualShiftTeam: ShiftTeam? = null,
    val manualShiftGroup: ManualShiftGroup? = null,
    val shiftReportMarginMinutes: Int = ShiftBusPlan.DEFAULT_REPORT_MARGIN_MINUTES,
) {
    /** 当前未完成执勤的下标；全部完成时等于 assignments.size。 */
    val activeDutyIndex: Int
        get() = assignments.dutyWindowIndices(manuallyCompletedCount, now).firstOrNull() ?: assignments.size

    /** 非空且尚有未完成执勤时才值得前后台自动刷新。 */
    val autoRefreshEligible: Boolean
        get() = assignments.isNotEmpty() && !assignments.allDutiesComplete(now, manuallyCompletedCount)
}

/** 编排层需要 UI 执行的一次性动作。 */
internal sealed interface DutyUiEvent {
    data class RequestPermissions(val permissions: List<String>) : DutyUiEvent
}

/** 分享队列里的一次导入尝试：只有仍是队首且 token 未过期时才允许落库并消费事件。 */
internal class ImportAttempt(
    val isCurrent: () -> Boolean,
    val onFinished: () -> Unit,
)
