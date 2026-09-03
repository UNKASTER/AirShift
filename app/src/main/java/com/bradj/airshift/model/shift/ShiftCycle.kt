package com.bradj.airshift.model.shift

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 六天周期内的日型。 */
enum class ShiftDayKind(val label: String) {
    /** 第 1 天：接班日，上午由上一班交出，本班约 10:40 起接手，干到次日凌晨。 */
    WORK_FIRST("上班 · 接班"),

    /** 第 2 天：整班，07:10 起到次日凌晨。 */
    WORK_SECOND("上班"),

    /** 第 3 天：整班，07:10 起到次日凌晨。 */
    WORK_THIRD("上班"),

    /** 第 4 天：只上上午，交班后回家；前一天排到夜班的组不到岗。 */
    HANDOVER("交接班 · 上午"),

    /** 第 5、6 天：休息。 */
    REST("休息"),
    ;

    val isFullWorkday: Boolean
        get() = this == WORK_FIRST || this == WORK_SECOND || this == WORK_THIRD

    val isRest: Boolean get() = this == REST
}

/**
 * 上三休三周期与班组轮转。
 *
 * 规律由 2026-08-24 至 2026-09-01 的六份真实排班表反推，样本内零反例：
 * - 六天一个周期：整班 3 天、交接班上午 1 天、休息 2 天；
 * - 班组序列只在整班工作日左移 3 位，交接班日与休息日不推进；
 * - 按日历日推进无解（4k ≡ 3 mod 10 无整数解），故“仅工作日推进”是唯一自洽解释。
 */
object ShiftCycle {
    /** 用户确认的第一个上班日，即周期第 1 天。 */
    val ANCHOR: LocalDate = LocalDate.of(2026, 8, 23)

    const val CYCLE_LENGTH_DAYS = 6

    /** 每个整班工作日班组序列左移的位数。 */
    const val ROTATION_STEP = 3

    private val KINDS = listOf(
        ShiftDayKind.WORK_FIRST,
        ShiftDayKind.WORK_SECOND,
        ShiftDayKind.WORK_THIRD,
        ShiftDayKind.HANDOVER,
        ShiftDayKind.REST,
        ShiftDayKind.REST,
    )

    /** 日期在六天周期内的序号 0..5；锚点之前的日期同样有效。 */
    fun dayIndexInCycle(date: LocalDate): Int =
        Math.floorMod(ChronoUnit.DAYS.between(ANCHOR, date), CYCLE_LENGTH_DAYS.toLong()).toInt()

    fun dayKind(date: LocalDate): ShiftDayKind = KINDS[dayIndexInCycle(date)]

    /**
     * 整班工作日的全局序号（只数 D1/D2/D3，交接班日与休息日不计）。
     * 锚点当天为 0；非整班工作日返回 null。
     */
    fun workOrdinal(date: LocalDate): Long? {
        val index = dayIndexInCycle(date)
        if (index > 2) return null
        val elapsed = ChronoUnit.DAYS.between(ANCHOR, date)
        val cycleNumber = Math.floorDiv(elapsed, CYCLE_LENGTH_DAYS.toLong())
        return cycleNumber * 3 + index
    }

    /**
     * 班组序列的旋转偏移，已校准到 2026-08-24（workOrdinal = 1）偏移为 0。
     * 非整班工作日返回 null——交接班日的班次继承 [previousFullWorkday]。
     */
    fun rotationOffset(date: LocalDate, tableSize: Int = ShiftGroupTable.DEFAULT.size): Int? {
        if (tableSize <= 0) return null
        val ordinal = workOrdinal(date) ?: return null
        return Math.floorMod((ordinal - 1) * ROTATION_STEP.toLong(), tableSize.toLong()).toInt()
    }

    /** 前一个整班工作日；交接班日据此继承班次与到岗规则。 */
    fun previousFullWorkday(date: LocalDate): LocalDate? {
        for (back in 1..CYCLE_LENGTH_DAYS) {
            val candidate = date.minusDays(back.toLong())
            if (dayKind(candidate).isFullWorkday) return candidate
        }
        return null
    }

    /** 该日期所属周期的第一天（D1）。 */
    fun cycleStart(date: LocalDate): LocalDate = date.minusDays(dayIndexInCycle(date).toLong())
}
