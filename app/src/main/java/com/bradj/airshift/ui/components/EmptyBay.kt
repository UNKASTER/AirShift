package com.bradj.airshift.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.theme.AirShiftMotion
import com.bradj.airshift.ui.theme.AirShiftRadius
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.AirShiftTokens
import com.bradj.airshift.ui.theme.LocalReduceMotion

/**
 * 空栏位：没有排班 / 今日全部完成 / 未匹配班组。
 * 图标 + 标题 + 一句说明 + 可选的单个操作。
 * [animateEntrance] 为 true 时四行按 40 ms 逐行淡入并上浮 8dp（只给"从有任务过渡到全部完成"这种稀有时刻）；
 * reduce-motion 时只淡入、无延迟。入场期间按钮可点。
 */
@Composable
fun EmptyBay(
    icon: ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    animateEntrance: Boolean = false,
) {
    val c = AirShiftTokens.colors
    val reduceMotion = LocalReduceMotion.current
    var shown by remember { mutableStateOf(!animateEntrance) }
    LaunchedEffect(Unit) { shown = true }
    val risePx = with(LocalDensity.current) { 8.dp.roundToPx() }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = AirShiftSpacing.L, vertical = AirShiftSpacing.XL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.S),
    ) {
        Entrance(shown, order = 0, reduceMotion = reduceMotion, risePx = risePx) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = c.hint)
        }
        Entrance(shown, order = 1, reduceMotion = reduceMotion, risePx = risePx) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink, textAlign = TextAlign.Center)
        }
        Entrance(shown, order = 2, reduceMotion = reduceMotion, risePx = risePx) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = c.inkSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(AirShiftSpacing.S))
            Entrance(shown, order = 3, reduceMotion = reduceMotion, risePx = risePx) {
                val interaction = remember { MutableInteractionSource() }
                Button(
                    onClick = onAction,
                    interactionSource = interaction,
                    modifier = Modifier.fillMaxWidth().height(48.dp).indication(interaction, LocalIndication.current),
                    shape = RoundedCornerShape(AirShiftRadius.Button),
                    colors = ButtonDefaults.buttonColors(containerColor = c.departure, contentColor = Color.White),
                ) {
                    Text(actionText, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

/** 一行的入场：淡入（Content 档）+ 8dp 上浮（Enter 档），第 [order] 行延迟 order × StaggerStep；reduce-motion 时只淡入。 */
@Composable
private fun Entrance(
    visible: Boolean,
    order: Int,
    reduceMotion: Boolean,
    risePx: Int,
    content: @Composable () -> Unit,
) {
    val delay = if (reduceMotion) 0 else order * AirShiftMotion.StaggerStepMs
    val enter: EnterTransition = if (reduceMotion) {
        fadeIn(AirShiftMotion.content(delayMillis = delay))
    } else {
        fadeIn(AirShiftMotion.content(delayMillis = delay)) +
            slideInVertically(AirShiftMotion.enter(delayMillis = delay)) { risePx }
    }
    AnimatedVisibility(visible = visible, enter = enter, exit = ExitTransition.None) {
        content()
    }
}
