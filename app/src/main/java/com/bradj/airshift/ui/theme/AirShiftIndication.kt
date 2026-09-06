package com.bradj.airshift.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 按下反馈：在 draw 阶段把内容缩放到 [pressedScale]（不改布局、不重组），可再叠一层 [tint] 着色。
 * 按下用 [pressSpec]（即时到位），抬手 / 取消用 [releaseSpec]（松弛回弹）——两段不对称。
 * 整宽的信息条只着色不缩放（`pressedScale = 1f`），避免行的边界在手指下移动。
 */
data class PressIndication(
    val pressedScale: Float,
    val tint: Color,
    val tintAlpha: Float,
    val pressSpec: FiniteAnimationSpec<Float>,
    val releaseSpec: FiniteAnimationSpec<Float>,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PressIndicationNode(interactionSource, this)
}

private class PressIndicationNode(
    private val interactionSource: InteractionSource,
    private val spec: PressIndication,
) : Modifier.Node(), DrawModifierNode {
    /** 1f = 未按下；draw 里读它，值变了只重绘不重组。 */
    private val scale = Animatable(1f)

    /** 0f..1f 着色强度。 */
    private val overlay = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> animateTo(pressed = true)
                    is PressInteraction.Release, is PressInteraction.Cancel -> animateTo(pressed = false)
                    else -> Unit
                }
            }
        }
    }

    private suspend fun animateTo(pressed: Boolean) = coroutineScope {
        val animationSpec = if (pressed) spec.pressSpec else spec.releaseSpec
        launch { scale.animateTo(if (pressed) spec.pressedScale else 1f, animationSpec) }
        launch { overlay.animateTo(if (pressed) 1f else 0f, animationSpec) }
    }

    override fun ContentDrawScope.draw() {
        val s = scale.value
        if (s == 1f) {
            drawContent()
        } else {
            scale(scale = s, pivot = center) { this@draw.drawContent() }
        }
        val alpha = overlay.value * spec.tintAlpha
        if (alpha > 0f && spec.tint.isSpecified) {
            drawRect(color = spec.tint.copy(alpha = alpha))
        }
    }
}

/** 两档预设。都在组合里取（要读主题色与 MotionScheme），用 `remember` 保持实例稳定。 */
object AirShiftIndication {
    /** 按钮 / 小按钮 / 底栏项：缩放 0.97，不着色。 */
    @Composable
    fun button(): PressIndication {
        val press = AirShiftMotion.fastEffects<Float>()
        val release = AirShiftMotion.fastSpatial<Float>()
        return remember(press, release) {
            PressIndication(
                pressedScale = AirShiftMotion.PressedScale,
                tint = Color.Unspecified,
                tintAlpha = 0f,
                pressSpec = press,
                releaseSpec = release,
            )
        }
    }

    /** 整宽信息条：不缩放，主文字色 6% 着色。 */
    @Composable
    fun row(): PressIndication {
        val ink = AirShiftTokens.colors.ink
        val press = AirShiftMotion.fastEffects<Float>()
        val release = AirShiftMotion.fastSpatial<Float>()
        return remember(ink, press, release) {
            PressIndication(
                pressedScale = 1f,
                tint = ink,
                tintAlpha = 0.06f,
                pressSpec = press,
                releaseSpec = release,
            )
        }
    }
}
