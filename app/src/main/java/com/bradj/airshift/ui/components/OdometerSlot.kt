package com.bradj.airshift.ui.components

/** 翻牌槽位：数字逐位动画，其他字符（冒号、汉字）静止。 */
internal data class OdometerSlot(val char: Char, val animated: Boolean)

internal fun odometerSlots(text: String): List<OdometerSlot> =
    text.map { OdometerSlot(it, it.isDigit()) }

/**
 * 翻牌方向由整串数值决定，不由单个字符决定：60→59 时"6→5"与"0→9"必须一起向下。
 * 记住上一串有数字的文本，比较数字部分；相等、无数字或首次时沿用上一次方向（初始向上）。
 */
internal class OdometerDirectionTracker {
    private var lastNumeric: Long? = null
    private var rollUp: Boolean = true

    fun update(text: String): Boolean {
        val numeric = text.filter(Char::isDigit).toLongOrNull()
        val previous = lastNumeric
        if (numeric != null) {
            if (previous != null && numeric != previous) rollUp = numeric > previous
            lastNumeric = numeric
        }
        return rollUp
    }
}
