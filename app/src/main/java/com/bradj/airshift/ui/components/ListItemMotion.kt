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
// Lint 要求返回 Modifier 的工厂是 Modifier 的扩展；这里故意做成 LazyItemScope 的扩展，
// 让八个调用点写 `animateListItem()` 而不是 `Modifier.animateListItem(this)`，与框架自带的
// `LazyItemScope.animateItem` 用法一致。这是可读性换来的唯一一处 @Suppress。
@Suppress("ModifierFactoryExtensionFunction")
fun LazyItemScope.animateListItem(): Modifier = Modifier.animateItem(
    fadeInSpec = AirShiftMotion.content(),
    placementSpec = AirShiftMotion.defaultSpatial(IntOffset.VisibilityThreshold),
    fadeOutSpec = AirShiftMotion.exit(),
)
