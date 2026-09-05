package com.bradj.airshift.ui.components

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.bradj.airshift.ui.theme.AirShiftMotion

/**
 * 列表项的进出场与位移：条、通知条、栏位标题、空态共用一套规格。
 * 新增 / 移除的项按 Content / Exit 档淡入淡出；位置变化（被展开的条挤开、完成后移栏、通知条出现时让位）
 * 用 default spatial 弹簧，key 不变的项从当前位置接续，不回跳。
 */
// LazyItemScope 是 Modifier.animateItem 本身要求的接收者，这里不可能改写成 Modifier 的扩展函数。
@Suppress("ModifierFactoryExtensionFunction")
fun LazyItemScope.animateListItem(): Modifier = Modifier.animateItem(
    fadeInSpec = AirShiftMotion.content(),
    placementSpec = AirShiftMotion.defaultSpatial(IntOffset.VisibilityThreshold),
    fadeOutSpec = AirShiftMotion.exit(),
)
