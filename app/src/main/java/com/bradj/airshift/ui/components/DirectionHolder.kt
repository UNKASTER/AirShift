package com.bradj.airshift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bradj.airshift.model.AssignmentKind
import com.bradj.airshift.ui.theme.AirShiftTokens

/** 夹条宽度：进程单架上给信息条定向的彩色夹片。 */
val HolderWidth = 6.dp

/** 方向夹条的上下两色：进港蓝 / 出港红 / 过站上蓝下红。 */
@Composable
fun holderColors(kind: AssignmentKind): Pair<Color, Color> {
    val c = AirShiftTokens.colors
    return when (kind) {
        AssignmentKind.ARRIVAL_ONLY -> c.arrival to c.arrival
        AssignmentKind.DEPARTURE_ONLY -> c.departure to c.departure
        AssignmentKind.TURNAROUND -> c.arrival to c.departure
    }
}

/**
 * 沿条的左边缘把方向夹条画满整条高度。用绘制而不是子布局，条在展开 / 折叠动画里逐帧变高时
 * 不需要 `IntrinsicSize` 的二次测量；调用方自行给内容留出 [HolderWidth] 的起始内边距。
 */
fun Modifier.directionHolder(top: Color, bottom: Color = top): Modifier = drawBehind {
    val width = HolderWidth.toPx()
    if (top == bottom) {
        drawRect(top, size = Size(width, size.height))
    } else {
        val half = size.height / 2f
        drawRect(top, size = Size(width, half))
        drawRect(bottom, topLeft = Offset(0f, half), size = Size(width, size.height - half))
    }
}

/** 单色夹条：日历页按日型着色（今天藏青、整班红、交接班琥珀、休息灰）。 */
@Composable
fun HolderBar(color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.width(HolderWidth).fillMaxHeight().background(color))
}
