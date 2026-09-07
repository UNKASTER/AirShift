package com.bradj.airshift.ui.calendar

import com.bradj.airshift.model.shift.ShiftCalendarRow

/** 排班日历列表的一项：月份标题，或一天一条。 */
sealed interface ShiftCalendarItem {
    /** LazyColumn 的 key，与 0.14.1 之前逐项 `item(key)` 的取值一致。 */
    val key: String

    data class Month(val year: Int, val month: Int) : ShiftCalendarItem {
        override val key: String get() = "month-$year-$month"
    }

    data class Day(val row: ShiftCalendarRow) : ShiftCalendarItem {
        override val key: String get() = row.day.date.toString()
    }
}

/**
 * 按月插入标题后的扁平列表。列表要定位到今天，靠的是今天在这个列表里的下标，
 * 所以先把"哪里有月份标题"算成数据，再交给 LazyColumn，而不是在 LazyColumn 里边遍历边插。
 */
fun List<ShiftCalendarRow>.toCalendarItems(): List<ShiftCalendarItem> = buildList {
    var lastMonth: Pair<Int, Int>? = null
    for (row in this@toCalendarItems) {
        val month = row.day.date.year to row.day.date.monthValue
        if (month != lastMonth) {
            lastMonth = month
            add(ShiftCalendarItem.Month(month.first, month.second))
        }
        add(ShiftCalendarItem.Day(row))
    }
}

/** 今天那一条在列表里的下标；列表里没有今天（没匹配到班组时为空）时为 0，从头开始。 */
fun List<ShiftCalendarItem>.todayIndex(): Int =
    indexOfFirst { it is ShiftCalendarItem.Day && it.row.isToday }.coerceAtLeast(0)
