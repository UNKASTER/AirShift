package com.bradj.airshift.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import com.bradj.airshift.ui.theme.AirShiftMotion

/**
 * 翻牌数字：每一位数字变化时上下翻（增大向上、减小向下），280 ms 减速曲线；
 * 非数字字符不动。字体必须带 tabular figures，否则位宽会随数字变化。
 * 无障碍上整体读作一段文字。
 */
@Composable
fun OdometerText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = text },
        verticalAlignment = Alignment.Bottom,
    ) {
        odometerSlots(text).forEachIndexed { index, slot ->
            if (slot.animated) {
                AnimatedContent(
                    targetState = slot.char,
                    modifier = Modifier.clipToBounds(),
                    transitionSpec = {
                        val up = targetState > initialState
                        val spec = tween<androidx.compose.ui.unit.IntOffset>(
                            AirShiftMotion.FlipMs,
                            easing = AirShiftMotion.EmphasizedDecelerate,
                        )
                        val enter = slideInVertically(spec) { if (up) it else -it } +
                            fadeIn(tween(AirShiftMotion.FlipMs))
                        val exit = slideOutVertically(spec) { if (up) -it else it } +
                            fadeOut(tween(AirShiftMotion.FlipMs))
                        (enter togetherWith exit).using(SizeTransform(clip = true))
                    },
                    label = "odometer$index",
                ) { digit ->
                    Text(digit.toString(), style = style, color = color, maxLines = 1, softWrap = false)
                }
            } else {
                Text(slot.char.toString(), style = style, color = color, maxLines = 1, softWrap = false)
            }
        }
    }
}
