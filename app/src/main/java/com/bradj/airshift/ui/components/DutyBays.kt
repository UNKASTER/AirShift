package com.bradj.airshift.ui.components

import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.isDutyComplete
import com.bradj.airshift.model.nextIncompleteDutyIndex
import java.time.LocalDateTime

/**
 * 全部执勤页的三个栏位：当前 / 接下来 / 已完成，元素都是排班列表的下标。
 *
 * - 当前：人工前缀之后第一项未自动完成的任务（与当前执勤页的窗口起点一致）；
 * - 已完成：当前之前的全部（人工完成 + 自动完成），以及当前之后已经自动完成的（如无时间信息、已有实际时间）；
 * - 接下来：当前之后仍未完成的任务，按排班顺序。
 */
data class DutyBays(
    val current: Int?,
    val upcoming: List<Int>,
    val completed: List<Int>,
)

fun List<RosterAssignment>.splitIntoBays(manuallyCompletedCount: Int, now: LocalDateTime): DutyBays {
    val current = nextIncompleteDutyIndex(manuallyCompletedCount, now).takeIf { it < size }
    val completed = mutableListOf<Int>()
    val upcoming = mutableListOf<Int>()
    indices.forEach { index ->
        when {
            index == current -> Unit
            current == null || index < current || this[index].isDutyComplete(now) -> completed += index
            else -> upcoming += index
        }
    }
    return DutyBays(current = current, upcoming = upcoming, completed = completed)
}
