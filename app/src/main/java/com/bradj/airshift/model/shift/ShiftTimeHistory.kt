package com.bradj.airshift.model.shift

import java.time.LocalDate

/** 末项最晚落在次日 24:00 之前；夜班的次日凌晨航班按 [ShiftClock] 记作 > 1440。 */
private const val MAX_LAST_TASK_MINUTES = 2 * ShiftClock.MINUTES_PER_DAY

/**
 * 内置表与实测记录共用的三档日型：接班日 / 整班（第 2、3 天合并）/ 交接班日。
 * 第 2、3 天的首末任务规律相同（内置表本就只有一张 `FIRST_TASK_FULL_DAY`），合并后每个周期得两个样本，学得更快。
 */
enum class ShiftTimeBucket {
    DAY_ONE,
    FULL_DAY,
    HANDOVER,
    ;

    companion object {
        fun of(kind: ShiftDayKind): ShiftTimeBucket? = when (kind) {
            ShiftDayKind.WORK_FIRST -> DAY_ONE
            ShiftDayKind.WORK_SECOND, ShiftDayKind.WORK_THIRD -> FULL_DAY
            ShiftDayKind.HANDOVER -> HANDOVER
            ShiftDayKind.REST -> null
        }
    }
}

/** 实测记录的聚合键。二组的合成组号会随再导入重排，因此只按大组、日型档与槽位归类，不按组号。 */
data class ShiftTimeKey(val team: ShiftTeam, val bucket: ShiftTimeBucket, val slot: ShiftSlot)

/**
 * 一次导入对某槽位的实测：当天该槽位的首个任务、其进出港方向与最后一项任务。不含任何姓名。
 * 分钟数按排班日 00:00 起算，夜班末项落在次日凌晨时 > 1440。
 */
data class ShiftTimeObservation(
    val date: LocalDate,
    val team: ShiftTeam,
    val kind: ShiftDayKind,
    val slot: ShiftSlot,
    val firstTaskMinutes: Int,
    val inbound: Boolean,
    val lastTaskMinutes: Int,
) {
    /** 休息日没有聚合键。 */
    val key: ShiftTimeKey? get() = ShiftTimeBucket.of(kind)?.let { ShiftTimeKey(team, it, slot) }

    /** 到位时间 = 首个任务 − 出港 70 / 进港 15 分钟；班车只取决于它。 */
    val reportByMinutes: Int get() = firstTaskMinutes - ShiftBusPlan.reportLeadMinutes(inbound)

    /** 首个任务落在当天且到位不早于零点，末项不早于首项且不晚于次日 24:00。 */
    val isPlausible: Boolean
        get() = key != null &&
            firstTaskMinutes < ShiftClock.MINUTES_PER_DAY &&
            reportByMinutes >= 0 &&
            lastTaskMinutes in firstTaskMinutes until MAX_LAST_TASK_MINUTES
}

/** 某槽位由实测得到的估计；交接班档交班固定 10:00，[offDutyMinutes] 恒为 null。 */
data class LearnedSlotTimes(
    val firstTask: ExpectedFirstTask,
    val offDutyMinutes: Int?,
    val sampleCount: Int,
) {
    val reportByMinutes: Int get() = ShiftBusPlan.reportByMinutes(firstTask)
}

/** 某大组各槽位的实测估计表，外加供界面显示的汇总。 */
data class LearnedTimes(
    val entries: Map<Pair<ShiftTimeBucket, ShiftSlot>, LearnedSlotTimes> = emptyMap(),
    /** 该大组有实测记录的日期数（含尚未攒够样本的键）。 */
    val dateCount: Int = 0,
    val latestDate: LocalDate? = null,
) {
    operator fun get(kind: ShiftDayKind, slot: ShiftSlot): LearnedSlotTimes? =
        ShiftTimeBucket.of(kind)?.let { entries[it to slot] }

    val isEmpty: Boolean get() = dateCount == 0

    companion object {
        val NONE = LearnedTimes()
    }
}

/**
 * 本机积累的实测记录。每次导入排班时 [record] 一批，日历用 [learnedTimes] 取聚合结果。
 *
 * 聚合规则：同一键攒够 [MIN_SAMPLES] 次才生效，只看最近 [MAX_DATES_PER_KEY] 个日期；
 * 到位时间取下中位数（偶数取更早者，宁早勿晚），方向取多数（平手算出港，提前量更大），
 * 首个任务由到位时间反推；下班取末项的上中位数。
 */
data class ShiftTimeHistory(val observations: List<ShiftTimeObservation> = emptyList()) {
    val isEmpty: Boolean get() = observations.isEmpty()

    /** 丢弃不合理项；同日期同键的旧记录被替换；每键只留最近 [MAX_DATES_PER_KEY] 个日期；结果按日期排序。 */
    fun record(incoming: List<ShiftTimeObservation>): ShiftTimeHistory {
        val fresh = incoming.filter { it.isPlausible }.associateBy { it.date to it.key }
        if (fresh.isEmpty()) return this
        val merged = observations.filterNot { (it.date to it.key) in fresh } + fresh.values
        val trimmed = merged
            .groupBy { it.key }
            .values
            .flatMap { sameKey ->
                val keptDates = sameKey.map { it.date }.distinct().sortedDescending().take(MAX_DATES_PER_KEY).toSet()
                sameKey.filter { it.date in keptDates }
            }
            .sortedWith(compareBy({ it.date }, { it.team }, { it.kind }, { it.slot.tier }, { it.slot.number }))
        return ShiftTimeHistory(trimmed)
    }

    /** 只汇总 [team] 的记录；样本数少于 [minSamples] 的键不出现在结果里。 */
    fun learnedTimes(team: ShiftTeam, minSamples: Int = MIN_SAMPLES): LearnedTimes {
        val mine = observations.filter { it.team == team }
        if (mine.isEmpty()) return LearnedTimes.NONE
        val entries = mine
            .mapNotNull { observation -> observation.key?.let { it to observation } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size >= minSamples }
            .map { (key, samples) -> (key.bucket to key.slot) to aggregate(key.bucket, samples) }
            .toMap()
        return LearnedTimes(
            entries = entries,
            dateCount = mine.map { it.date }.distinct().size,
            latestDate = mine.maxOf { it.date },
        )
    }

    private fun aggregate(bucket: ShiftTimeBucket, samples: List<ShiftTimeObservation>): LearnedSlotTimes {
        val reportBy = lowerMedian(samples.map { it.reportByMinutes })
        val inbound = samples.count { it.inbound } * 2 > samples.size
        val firstTask = ExpectedFirstTask(reportBy + ShiftBusPlan.reportLeadMinutes(inbound), inbound)
        val offDuty = if (bucket == ShiftTimeBucket.HANDOVER) null else upperMedian(samples.map { it.lastTaskMinutes })
        return LearnedSlotTimes(firstTask, offDuty, samples.size)
    }

    private fun lowerMedian(values: List<Int>): Int = values.sorted()[(values.size - 1) / 2]

    private fun upperMedian(values: List<Int>): Int = values.sorted()[values.size / 2]

    companion object {
        const val MAX_DATES_PER_KEY = 8
        const val MIN_SAMPLES = 3
        val EMPTY = ShiftTimeHistory()
    }
}
