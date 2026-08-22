package com.bradj.airshift.model

data class RosterSupplement(
    val vipInfo: List<String> = emptyList(),
    val earlyShift: List<String> = emptyList(),
    val middleShift: List<String> = emptyList(),
    val lateShift: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = vipInfo.isEmpty() && earlyShift.isEmpty() && middleShift.isEmpty() && lateShift.isEmpty()
}
