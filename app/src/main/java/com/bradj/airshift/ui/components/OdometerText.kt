package com.bradj.airshift.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.bradj.airshift.ui.theme.AirShiftMotion

/** 槽宽按 (TextStyle, Density) 缓存：同一字号在四页之间切换时不再重复测 0–9 十个字形。只在主线程的组合中访问。 */
private val SlotWidthCache = HashMap<Pair<TextStyle, Density>, Dp>()

/**
 * 翻牌数字：整串数值变大时所有变化的位向上翻，变小时向下翻（220 ms 减速曲线）；旧数字 130 ms 淡出，比新数字快。
 * 非数字字符不动。每个数字槽位固定为 0–9 中最宽一位的宽度，位置不随数字变化。
 * 无障碍上整体读作一段文字。
 */
@Composable
fun OdometerText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val slotWidth = remember(style, density) {
        SlotWidthCache.getOrPut(style to density) {
            with(density) { (0..9).maxOf { measurer.measure(it.toString(), style).size.width }.toDp() }
        }
    }
    // 方向按整串数值算一次，再交给每个槽位；update 对同一 text 是幂等的。
    val tracker = remember { OdometerDirectionTracker() }
    val rollUp = remember(text) { tracker.update(text) }
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = text },
        verticalAlignment = Alignment.Bottom,
    ) {
        odometerSlots(text).forEachIndexed { index, slot ->
            if (slot.animated) {
                Box(modifier = Modifier.width(slotWidth).clipToBounds(), contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = slot.char,
                        transitionSpec = {
                            val enter = slideInVertically(AirShiftMotion.flip()) { if (rollUp) it else -it } +
                                fadeIn(AirShiftMotion.flip())
                            val exit = slideOutVertically(AirShiftMotion.flip()) { if (rollUp) -it else it } +
                                fadeOut(AirShiftMotion.flipExit())
                            enter togetherWith exit
                        },
                        label = "odometer$index",
                    ) { digit ->
                        Text(digit.toString(), style = style, color = color, maxLines = 1, softWrap = false)
                    }
                }
            } else {
                Text(slot.char.toString(), style = style, color = color, maxLines = 1, softWrap = false)
            }
        }
    }
}
