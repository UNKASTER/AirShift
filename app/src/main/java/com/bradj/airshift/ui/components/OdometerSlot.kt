package com.bradj.airshift.ui.components

/** 翻牌槽位：数字逐位动画，其他字符（冒号、汉字）静止。 */
internal data class OdometerSlot(val char: Char, val animated: Boolean)

internal fun odometerSlots(text: String): List<OdometerSlot> =
    text.map { OdometerSlot(it, it.isDigit()) }
