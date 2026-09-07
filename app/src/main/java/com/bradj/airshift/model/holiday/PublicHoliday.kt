package com.bradj.airshift.model.holiday

import java.time.LocalDate

/** 某一天在国家法定节假日安排里的记录；[name] 是所属节日，调休上班日也归到它为之调休的那个节日。 */
data class PublicHoliday(
    val date: LocalDate,
    val name: String,
    val kind: Kind,
) {
    /** 放假，或为了连休而调到周末的上班日。 */
    enum class Kind {
        OFF,
        WORK,
    }

    val isOff: Boolean get() = kind == Kind.OFF
}
