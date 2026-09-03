package com.bradj.airshift.model.shift

import java.time.LocalTime

/** 班车推荐依据：ROSTER 用当天真实排班算出，ESTIMATE 用历史规律推算。 */
enum class ShiftEstimateSource { ROSTER, ESTIMATE }

/** 排班表中某槽位的典型首个任务，用于没有当日排班时推算到位时间。 */
data class ExpectedFirstTask(val minutes: Int, val inbound: Boolean)

data class BusRecommendation(
    val departure: LocalTime,
    /** 到场时间 = 发车 + [ShiftBusPlan.RIDE_MINUTES]。 */
    val arriveAtMinutes: Int,
    /** 到位时间：出港提前 60 分钟、进港提前 10 分钟。 */
    val reportByMinutes: Int,
    /** 到场到到位之间还剩多少分钟；越小越赶。 */
    val spareMinutes: Int,
    val source: ShiftEstimateSource,
    /** true 表示这是交接班相关日才有的 09:00 加班车。 */
    val isExtraHandoverBus: Boolean,
    /** true 表示按用户既定习惯固定乘坐，不是按到位时间算出来的。 */
    val isFixedByRule: Boolean,
)

/**
 * 班车时刻与选车规则。
 *
 * 到位要求由用户确认：出港航班最晚提前 60 分钟到位，进港航班最晚提前 10 分钟到位，
 * 与 [com.bradj.airshift.model.DutyTimeline] 的既有规则一致。
 * 选车 = 满足「发车 + 车程 ≤ 到位时间 − 到位余量」的最晚一班。
 */
object ShiftBusPlan {
    /** 班车车程，用户确认约 5 分钟。 */
    const val RIDE_MINUTES = 5

    const val OUTBOUND_REPORT_LEAD_MINUTES = 60
    const val INBOUND_REPORT_LEAD_MINUTES = 10

    const val DEFAULT_REPORT_MARGIN_MINUTES = 15
    val REPORT_MARGIN_OPTIONS = listOf(0, 15, 30)

    /** 常规班车：最早三班加密，其后自 08:00 起每两小时一班。 */
    val REGULAR_DEPARTURES: List<LocalTime> = buildList {
        add(LocalTime.of(4, 50))
        add(LocalTime.of(5, 25))
        add(LocalTime.of(5, 55))
        for (hour in 8..22 step 2) add(LocalTime.of(hour, 0))
    }

    /** 交接班相关日（本班接班的第 1 天与交班的第 4 天）额外一班。 */
    val EXTRA_HANDOVER_DEPARTURE: LocalTime = LocalTime.of(9, 0)

    fun departures(kind: ShiftDayKind): List<LocalTime> =
        if (hasExtraHandoverBus(kind)) {
            (REGULAR_DEPARTURES + EXTRA_HANDOVER_DEPARTURE).sorted()
        } else {
            REGULAR_DEPARTURES
        }

    fun hasExtraHandoverBus(kind: ShiftDayKind): Boolean =
        kind == ShiftDayKind.WORK_FIRST || kind == ShiftDayKind.HANDOVER

    /**
     * 用户既定习惯的固定班车，不走到位时间推算：
     * 第 1 天早班/夜班坐 09:00 加班车、第 1 天中班坐 12:00；第 2、3 天的中二至中四坐 12:00。
     */
    fun fixedDeparture(kind: ShiftDayKind, slot: ShiftSlot): LocalTime? = when {
        kind == ShiftDayKind.WORK_FIRST && slot.tier == ShiftTier.MID -> LocalTime.of(12, 0)
        kind == ShiftDayKind.WORK_FIRST -> EXTRA_HANDOVER_DEPARTURE
        kind.isFullWorkday && slot.tier == ShiftTier.MID && slot.number >= 2 -> LocalTime.of(12, 0)
        else -> null
    }

    /** 典型首个任务；六份实测排班表内各槽位的常见值。 */
    fun expectedFirstTask(kind: ShiftDayKind, slot: ShiftSlot): ExpectedFirstTask? {
        val table = when (kind) {
            ShiftDayKind.WORK_FIRST -> FIRST_TASK_DAY_ONE
            ShiftDayKind.WORK_SECOND, ShiftDayKind.WORK_THIRD -> FIRST_TASK_FULL_DAY
            ShiftDayKind.HANDOVER -> FIRST_TASK_HANDOVER
            ShiftDayKind.REST -> return null
        }
        return table[slot.tier to slot.number]
    }

    /** 典型到位时间：首个任务时间减去出港 60 / 进港 10 分钟。 */
    fun expectedReportByMinutes(kind: ShiftDayKind, slot: ShiftSlot): Int? {
        val task = expectedFirstTask(kind, slot) ?: return null
        return task.minutes - if (task.inbound) INBOUND_REPORT_LEAD_MINUTES else OUTBOUND_REPORT_LEAD_MINUTES
    }

    /** 典型下班时间；交接班日交班后即回家。 */
    fun expectedOffDutyMinutes(kind: ShiftDayKind, slot: ShiftSlot): Int? = when (kind) {
        ShiftDayKind.REST -> null
        ShiftDayKind.HANDOVER -> ShiftClock.of(10, 0)
        else -> OFF_DUTY[slot.tier to slot.number]
    }

    /**
     * 推荐班车。[rosterReportByMinutes] 非空表示当天已导入真实排班，据其首个任务算出的到位时间。
     * 没有任何可乘班车（到位太早）时返回 null。
     */
    fun recommend(
        kind: ShiftDayKind,
        slot: ShiftSlot,
        rosterReportByMinutes: Int? = null,
        marginMinutes: Int = DEFAULT_REPORT_MARGIN_MINUTES,
    ): BusRecommendation? {
        if (kind.isRest) return null
        val source = if (rosterReportByMinutes != null) ShiftEstimateSource.ROSTER else ShiftEstimateSource.ESTIMATE
        val reportBy = rosterReportByMinutes ?: expectedReportByMinutes(kind, slot) ?: return null
        val fixed = fixedDeparture(kind, slot)
        val departure = fixed
            ?: latestDepartureBefore(kind, reportBy - marginMinutes.coerceAtLeast(0))
            ?: return null
        val arriveAt = ShiftClock.of(departure) + RIDE_MINUTES
        return BusRecommendation(
            departure = departure,
            arriveAtMinutes = arriveAt,
            reportByMinutes = reportBy,
            spareMinutes = reportBy - arriveAt,
            source = source,
            isExtraHandoverBus = departure == EXTRA_HANDOVER_DEPARTURE && hasExtraHandoverBus(kind),
            isFixedByRule = fixed != null,
        )
    }

    /** 满足「发车 + 车程 ≤ deadline」的最晚一班。 */
    private fun latestDepartureBefore(kind: ShiftDayKind, deadlineMinutes: Int): LocalTime? =
        departures(kind).lastOrNull { ShiftClock.of(it) + RIDE_MINUTES <= deadlineMinutes }

    // ---------- 实测数据表 ----------
    // 时间与进出港方向都取自 2026-08-24 至 2026-09-01 的六份排班表，方向在样本内完全一致：
    // 清晨上岗的槽位首个任务是出港（到位提前 60 分钟），下午上岗的是过站进港（到位提前 10 分钟）。

    private val FIRST_TASK_FULL_DAY: Map<Pair<ShiftTier, Int>, ExpectedFirstTask> = mapOf(
        (ShiftTier.EARLY to 1) to outbound(7, 20),
        (ShiftTier.EARLY to 2) to outbound(7, 30),
        (ShiftTier.EARLY to 3) to outbound(7, 50),
        (ShiftTier.EARLY to 4) to outbound(7, 50),
        (ShiftTier.MID to 1) to outbound(7, 50),
        // 中二至中四下午才上岗，首个任务是过站航班的进港段。
        (ShiftTier.MID to 2) to inbound(12, 50),
        (ShiftTier.MID to 3) to inbound(12, 50),
        (ShiftTier.MID to 4) to inbound(13, 30),
        (ShiftTier.NIGHT to 1) to outbound(7, 10),
        (ShiftTier.NIGHT to 2) to outbound(7, 10),
        (ShiftTier.NIGHT to 3) to outbound(7, 20),
        (ShiftTier.NIGHT to 4) to outbound(7, 20),
    )

    // 接班日上午由上一班交出，本班从进港航班接手；只有夜班槽位赶上 10:40 的出港。
    private val FIRST_TASK_DAY_ONE: Map<Pair<ShiftTier, Int>, ExpectedFirstTask> = mapOf(
        (ShiftTier.EARLY to 1) to inbound(10, 55),
        (ShiftTier.EARLY to 2) to inbound(11, 0),
        (ShiftTier.EARLY to 3) to inbound(11, 20),
        (ShiftTier.EARLY to 4) to inbound(11, 20),
        (ShiftTier.MID to 1) to inbound(12, 55),
        (ShiftTier.MID to 2) to inbound(12, 50),
        (ShiftTier.MID to 3) to inbound(13, 30),
        (ShiftTier.MID to 4) to inbound(13, 40),
        (ShiftTier.NIGHT to 1) to outbound(10, 40),
        (ShiftTier.NIGHT to 2) to outbound(10, 40),
        (ShiftTier.NIGHT to 3) to outbound(10, 45),
        (ShiftTier.NIGHT to 4) to outbound(10, 45),
    )

    private val FIRST_TASK_HANDOVER: Map<Pair<ShiftTier, Int>, ExpectedFirstTask> = mapOf(
        (ShiftTier.EARLY to 1) to outbound(7, 10),
        (ShiftTier.EARLY to 2) to outbound(7, 20),
        (ShiftTier.EARLY to 3) to outbound(7, 30),
        (ShiftTier.EARLY to 4) to outbound(7, 30),
        (ShiftTier.MID to 1) to outbound(7, 50),
        (ShiftTier.MID to 2) to outbound(7, 50),
        (ShiftTier.MID to 3) to outbound(7, 50),
        (ShiftTier.MID to 4) to outbound(8, 10),
    )

    private val OFF_DUTY: Map<Pair<ShiftTier, Int>, Int> = mapOf(
        (ShiftTier.EARLY to 1) to ShiftClock.of(17, 15),
        (ShiftTier.EARLY to 2) to ShiftClock.of(17, 30),
        (ShiftTier.EARLY to 3) to ShiftClock.of(17, 35),
        (ShiftTier.EARLY to 4) to ShiftClock.of(17, 40),
        (ShiftTier.MID to 1) to ShiftClock.of(20, 0),
        (ShiftTier.MID to 2) to ShiftClock.of(20, 15),
        (ShiftTier.MID to 3) to ShiftClock.of(21, 30),
        (ShiftTier.MID to 4) to ShiftClock.of(21, 45),
        (ShiftTier.NIGHT to 1) to ShiftClock.of(0, 40, nextDay = true),
        (ShiftTier.NIGHT to 2) to ShiftClock.of(1, 0, nextDay = true),
        (ShiftTier.NIGHT to 3) to ShiftClock.of(1, 35, nextDay = true),
        (ShiftTier.NIGHT to 4) to ShiftClock.of(1, 35, nextDay = true),
    )

    private fun outbound(hour: Int, minute: Int) = ExpectedFirstTask(ShiftClock.of(hour, minute), inbound = false)

    private fun inbound(hour: Int, minute: Int) = ExpectedFirstTask(ShiftClock.of(hour, minute), inbound = true)
}
