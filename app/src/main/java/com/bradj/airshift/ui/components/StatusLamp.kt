package com.bradj.airshift.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.theme.AirShiftMotion
import com.bradj.airshift.ui.theme.AirShiftRadius
import com.bradj.airshift.ui.theme.AirShiftTokens

private data class LampColors(val container: Color, val content: Color)

@Composable
private fun LampKind.colors(): LampColors {
    val c = AirShiftTokens.colors
    return when (this) {
        LampKind.Neutral -> LampColors(c.neutralSoft, c.inkSecondary)
        LampKind.Ok -> LampColors(c.okSoft, c.ok)
        LampKind.Estimate -> LampColors(c.estimateSoft, c.estimate)
        LampKind.Alert -> LampColors(c.alertSoft, c.alert)
        LampKind.Vip -> LampColors(c.vipSoft, c.vipText)
        LampKind.Arrival -> LampColors(c.arrivalSoft, c.arrivalText)
        LampKind.Departure -> LampColors(c.departureSoft, c.departureText)
    }
}

/**
 * 状态灯：22dp 高的小矩形（4dp 圆角），12sp SemiBold；不是胶囊。
 * [dot] 在文字前加一个 6dp 圆点，用于飞行状态（已起飞 / 晚 15 分 / 已取消）；
 * [icon] 在文字前加一个 14dp 线性图标，用于特服轮椅。
 * 颜色变化 150 ms。
 */
@Composable
fun StatusLamp(
    text: String,
    kind: LampKind,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconContentDescription: String? = null,
) {
    val target = kind.colors()
    val container by animateColorAsState(
        target.container,
        tween(AirShiftMotion.QuickMs, easing = AirShiftMotion.EmphasizedDecelerate),
        label = "lampContainer",
    )
    val content by animateColorAsState(
        target.content,
        tween(AirShiftMotion.QuickMs, easing = AirShiftMotion.EmphasizedDecelerate),
        label = "lampContent",
    )
    Row(
        modifier = modifier
            .height(22.dp)
            .clip(RoundedCornerShape(AirShiftRadius.Lamp))
            .background(container)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Canvas(modifier = Modifier.size(6.dp)) { drawCircle(content) }
            Spacer(Modifier.width(5.dp))
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                modifier = Modifier.size(14.dp),
                tint = content,
            )
            if (text.isNotEmpty()) Spacer(Modifier.width(3.dp))
        }
        if (text.isNotEmpty()) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
