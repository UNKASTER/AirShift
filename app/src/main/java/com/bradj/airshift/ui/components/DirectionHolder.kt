package com.bradj.airshift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bradj.airshift.model.AssignmentKind
import com.bradj.airshift.ui.theme.AirShiftTokens

/** 夹条宽度：进程单架上给信息条定向的彩色夹片。 */
val HolderWidth = 6.dp

/**
 * 方向夹条：进港蓝 / 出港红 / 过站上蓝下红。放在 `Row(IntrinsicSize.Min)` 里撑满条高。
 */
@Composable
fun DirectionHolder(kind: AssignmentKind, modifier: Modifier = Modifier) {
    val c = AirShiftTokens.colors
    when (kind) {
        AssignmentKind.ARRIVAL_ONLY -> HolderBar(c.arrival, modifier)
        AssignmentKind.DEPARTURE_ONLY -> HolderBar(c.departure, modifier)
        AssignmentKind.TURNAROUND -> Column(modifier = modifier.width(HolderWidth).fillMaxHeight()) {
            Box(Modifier.width(HolderWidth).weight(1f).background(c.arrival))
            Box(Modifier.width(HolderWidth).weight(1f).background(c.departure))
        }
    }
}

/** 单色夹条：日历页按日型着色（今天藏青、整班红、交接班琥珀、休息灰）。 */
@Composable
fun HolderBar(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.width(HolderWidth).fillMaxHeight().background(color))
}
