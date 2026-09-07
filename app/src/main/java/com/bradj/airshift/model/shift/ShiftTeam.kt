package com.bradj.airshift.model.shift

import java.time.LocalDate

/**
 * 大组。两大组都上三休三，在同一个六天周期里错开 3 天交替上班：
 * 一组 D1 D2 D3 交 休 休，二组 交 休 休 D1 D2 D3，因此任何一天恰有一个大组在整班。
 *
 * 一组锚点由用户确认；二组锚点由 2026-09-07 的二组表反推——那天一组交接班（只上上午），
 * 二组的表从 10:40 的出港开始并带“候机早班/中班/夜航”三行，正是接班日。
 */
enum class ShiftTeam(val label: String, val anchor: LocalDate) {
    FIRST("一组", LocalDate.of(2026, 8, 23)),
    SECOND("二组", LocalDate.of(2026, 8, 26)),
    ;

    companion object {
        /**
         * 当天处于整班工作日的大组。带班次行的表格只出现在整班日，故一份校准表的日期唯一确定它属于哪个大组。
         */
        fun onFullWorkday(date: LocalDate): ShiftTeam =
            entries.first { ShiftCycle.dayKind(date, it).isFullWorkday }
    }
}
