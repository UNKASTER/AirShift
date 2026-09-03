package com.bradj.airshift.model.shift

import java.time.LocalTime

/** 班次层：早班 / 中班 / 夜班，对应排班表右侧“候机早班 / 候机中班 / 候机夜班”三行。 */
enum class ShiftTier(val label: String) {
    EARLY("早"),
    MID("中"),
    NIGHT("晚"),
}

/**
 * 班次槽位，例如 早二 / 中四 / 晚一。
 * 槽位号来自班组在当天旋转后序列中的位置；号越小下班越早。
 */
data class ShiftSlot(val tier: ShiftTier, val number: Int) {
    val label: String
        get() = tier.label + (CHINESE_NUMERALS.getOrNull(number - 1) ?: number.toString())

    private companion object {
        val CHINESE_NUMERALS = listOf("一", "二", "三", "四", "五", "六")
    }
}

/**
 * 每层的槽位数量。排班模板本身是早 1–4 / 中 1–4 / 晚 1–4；
 * 当前 10 个在编班组（7、12 号空缺）填成 3/4/3。
 */
data class ShiftTierSizes(val early: Int, val mid: Int, val night: Int) {
    val total: Int get() = early + mid + night

    /** 把旋转后序列中的位置映射为槽位；越界返回 null。 */
    fun slotAt(position: Int): ShiftSlot? = when {
        position < 0 -> null
        position < early -> ShiftSlot(ShiftTier.EARLY, position + 1)
        position < early + mid -> ShiftSlot(ShiftTier.MID, position - early + 1)
        position < total -> ShiftSlot(ShiftTier.NIGHT, position - early - mid + 1)
        else -> null
    }

    companion object {
        val DEFAULT = ShiftTierSizes(early = 3, mid = 4, night = 3)

        /** 按观测到的三行长度构造；任一行为空时回退默认值。 */
        fun of(early: Int, mid: Int, night: Int): ShiftTierSizes =
            if (early > 0 && mid > 0 && night > 0) ShiftTierSizes(early, mid, night) else DEFAULT
    }
}

/**
 * 可跨零点的执勤时刻，用“当日 00:00 起的分钟数”表示，因此夜班的 01:35 记作 1535。
 * 只服务于排班日历的展示与班车推算，不参与排班存储。
 */
object ShiftClock {
    const val MINUTES_PER_DAY = 24 * 60

    fun of(hour: Int, minute: Int, nextDay: Boolean = false): Int =
        hour * 60 + minute + if (nextDay) MINUTES_PER_DAY else 0

    fun of(time: LocalTime): Int = time.hour * 60 + time.minute

    fun toLocalTime(minutes: Int): LocalTime =
        LocalTime.of(Math.floorMod(minutes, MINUTES_PER_DAY) / 60, Math.floorMod(minutes, MINUTES_PER_DAY) % 60)

    /** 渲染为 `HH:mm`，跨零点时附“次日”。 */
    fun format(minutes: Int): String {
        val text = "%02d:%02d".format(
            Math.floorMod(minutes, MINUTES_PER_DAY) / 60,
            Math.floorMod(minutes, MINUTES_PER_DAY) % 60,
        )
        return if (minutes >= MINUTES_PER_DAY) "次日 $text" else text
    }
}
