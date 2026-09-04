package com.bradj.airshift.model

/**
 * 航段方向。业务与布局判断一律用枚举；中文文案只在渲染的最后一步取用，
 * 这样改文案不会悄悄改变颜色、图标或小组件的行为。
 */
enum class LegDirection(val label: String, val shortLabel: String) {
    INBOUND("进港", "进"),
    OUTBOUND("出港", "出"),
}
