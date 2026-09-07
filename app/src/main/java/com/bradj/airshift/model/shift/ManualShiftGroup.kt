package com.bradj.airshift.model.shift

/**
 * 设置里手动指定的班组，只在姓名匹配不到任何班组时兜底。
 * 连同指定时所在的大组一起记下：一组的组号是排班表上的真实编号，二组的是校准时给的合成序号，
 * 跨大组没有意义，大组切换后手动指定就不再参与判定。
 */
data class ManualShiftGroup(val team: ShiftTeam, val id: Int)
