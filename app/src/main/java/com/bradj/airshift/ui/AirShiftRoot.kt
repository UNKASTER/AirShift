package com.bradj.airshift.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradj.airshift.ui.components.linearIcon
import com.bradj.airshift.ui.theme.AirShiftMotion
import com.bradj.airshift.ui.theme.BorderSoft
import com.bradj.airshift.ui.theme.CeaRed
import com.bradj.airshift.ui.theme.NavyGrey
import com.bradj.airshift.ui.theme.heroShadow

enum class DutySection {
    ALL,
    CURRENT,
    SETTINGS,
}

// ---------- 1.5px 线性图标（Lucide 风格自绘，禁止填充/线性混用） ----------
// 构建器统一放在 ui.components.linearIcon 复用。

/** 全部执勤：列表。 */
private val IconDutyList = linearIcon(
    "DutyList",
    "M8.5 6h12 M8.5 12h12 M8.5 18h12 M3.25 6h1.5 M3.25 12h1.5 M3.25 18h1.5",
)

/** 当前执勤：纸飞机（任务进发）。 */
private val IconDutyCurrent = linearIcon(
    "DutyCurrent",
    "M22 2L11 13 M22 2L15 22L11 13L2 9Z",
)

/** 设置：滑杆。 */
private val IconSettings = linearIcon(
    "Settings",
    "M21 4h-7 M10 4H3 M21 12h-9 M8 12H3 M21 20h-5 M12 20H3 M14 2v4 M8 10v4 M16 18v4",
)

/**
 * 根布局：Scaffold + 品牌底部导航。
 * 白底 + 顶部 1px 浅灰线；1.5px 线性图标；激活态东航红；
 * 中央「当前执勤」为浮动圆形红按钮（三级阴影），突出核心入口。
 * 状态颜色切换统一 200ms ease-out。
 */
@Composable
fun AirShiftRoot(
    section: DutySection,
    onSectionSelected: (DutySection) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            DutyBottomBar(
                section = section,
                onSectionSelected = onSectionSelected,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(padding)
        }
    }
}

@Composable
private fun DutyBottomBar(
    section: DutySection,
    onSectionSelected: (DutySection) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // 白条本体：白底 + 顶部 1px 浅灰线
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                HorizontalDivider(thickness = 1.dp, color = BorderSoft)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BottomBarItem(
                        modifier = Modifier.weight(1f),
                        icon = IconDutyList,
                        label = "全部执勤",
                        selected = section == DutySection.ALL,
                        onClick = { onSectionSelected(DutySection.ALL) },
                    )
                    // 中央浮动按钮占位
                    Spacer(modifier = Modifier.width(88.dp))
                    BottomBarItem(
                        modifier = Modifier.weight(1f),
                        icon = IconSettings,
                        label = "设置",
                        selected = section == DutySection.SETTINGS,
                        onClick = { onSectionSelected(DutySection.SETTINGS) },
                    )
                }
            }
        }
        // 中央「当前执勤」浮动圆形按钮：红底白图标 + 三级阴影；
        // 选中时外圈光环（2px 白描边 + 外扩 4px 的 20% 红环）并放大 1.05 倍
        val currentSelected = section == DutySection.CURRENT
        val fabScale by animateFloatAsState(
            targetValue = if (currentSelected) 1.05f else 1f,
            animationSpec = tween(AirShiftMotion.StateChangeMs, easing = AirShiftMotion.EaseOut),
            label = "fabScale",
        )
        val haloAlpha by animateFloatAsState(
            targetValue = if (currentSelected) 1f else 0f,
            animationSpec = tween(AirShiftMotion.StateChangeMs, easing = AirShiftMotion.EaseOut),
            label = "fabHalo",
        )
        Column(
            modifier = Modifier.align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(68.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (haloAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .drawBehind {
                                val buttonRadius = 28.dp.toPx() * fabScale
                                // 外扩 4px 的 20% 透明度红环
                                drawCircle(
                                    color = CeaRed.copy(alpha = 0.2f * haloAlpha),
                                    radius = buttonRadius + 4.dp.toPx(),
                                    style = Stroke(width = 4.dp.toPx()),
                                )
                                // 2px 白色描边
                                drawCircle(
                                    color = Color.White.copy(alpha = haloAlpha),
                                    radius = buttonRadius + 1.dp.toPx(),
                                    style = Stroke(width = 2.dp.toPx()),
                                )
                            },
                    )
                }
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer { scaleX = fabScale; scaleY = fabScale }
                        .heroShadow(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = 28.dp),
                            role = Role.Tab,
                            onClick = { onSectionSelected(DutySection.CURRENT) },
                        ),
                    shape = CircleShape,
                    color = CeaRed,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = IconDutyCurrent,
                            contentDescription = "当前执勤",
                            modifier = Modifier.size(24.dp),
                            tint = Color.White,
                        )
                    }
                }
            }
            val labelColor by bottomItemColor(selected = currentSelected)
            Text(
                "当前执勤",
                color = labelColor,
                fontSize = 10.sp,
                fontWeight = if (currentSelected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun bottomItemColor(selected: Boolean) = animateColorAsState(
    targetValue = if (selected) CeaRed else NavyGrey,
    animationSpec = tween(
        durationMillis = AirShiftMotion.StateChangeMs,
        easing = AirShiftMotion.EaseOut,
    ),
    label = "bottomItemColor",
)

@Composable
private fun BottomBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by bottomItemColor(selected)
    val pillAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(AirShiftMotion.StateChangeMs, easing = AirShiftMotion.EaseOut),
        label = "navPill",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // 激活 pill：全圆角浅红底（东航红 11%），高 32dp，200ms ease-out 淡入淡出
        Box(
            modifier = Modifier.height(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (pillAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(32.dp)
                        .graphicsLayer { alpha = pillAlpha }
                        .background(CeaRed.copy(alpha = 0.11f), CircleShape),
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = tint,
            )
        }
        Text(
            label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}
