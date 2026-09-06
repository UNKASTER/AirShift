package com.bradj.airshift.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.components.linearIcon
import com.bradj.airshift.ui.theme.AirShiftMotion
import com.bradj.airshift.ui.theme.AirShiftTokens

enum class DutySection {
    ALL,
    CALENDAR,
    CURRENT,
    SETTINGS,
}

// ---------- 1.5px 线性图标（统一线性，禁止填充/线性混用） ----------

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

/** 排班日历：日历。 */
private val IconShiftCalendar = linearIcon(
    "ShiftCalendar",
    "M8 2v4 M16 2v4 M3 10h18 M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
)

/** 设置：滑杆。 */
private val IconSettings = linearIcon(
    "Settings",
    "M21 4h-7 M10 4H3 M21 12h-9 M8 12H3 M21 20h-5 M12 20H3 M14 2v4 M8 10v4 M16 18v4",
)

private data class NavDestination(
    val section: DutySection,
    val icon: ImageVector,
    val label: String,
    val testTag: String,
)

private val Destinations = listOf(
    NavDestination(DutySection.ALL, IconDutyList, "全部执勤", "nav_all"),
    NavDestination(DutySection.CALENDAR, IconShiftCalendar, "排班日历", "nav_calendar"),
    NavDestination(DutySection.CURRENT, IconDutyCurrent, "当前执勤", "nav_current"),
    NavDestination(DutySection.SETTINGS, IconSettings, "设置", "nav_settings"),
)

/**
 * 根布局：Scaffold + 四等分底栏。
 * 页面自己处理状态栏 inset（板面贯通到状态栏之下），所以 Scaffold 不消费任何 inset；
 * 底栏消费导航栏 inset。
 */
@Composable
fun AirShiftRoot(
    section: DutySection,
    onSectionSelected: (DutySection) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = AirShiftTokens.colors.ground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            DutyNavigationBar(
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

/** 底栏红灯：20×3dp。 */
private val LampWidth = 20.dp
private val LampHeight = 3.dp

/**
 * 底栏：四个等宽目的地，顶部 1dp 线；一枚 20×3dp 东航红"灯"在四个标签间横移到选中项上方，
 * 图标与文字用 effects 弹簧变藏青。灯的位移与页面的横向滑入用同一支弹簧，读成一个动作。
 */
@Composable
private fun DutyNavigationBar(
    section: DutySection,
    onSectionSelected: (DutySection) -> Unit,
) {
    val c = AirShiftTokens.colors
    Column(modifier = Modifier.fillMaxWidth().background(c.nav).navigationBarsPadding()) {
        HorizontalDivider(thickness = 1.dp, color = c.rule)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(64.dp)) {
            val itemWidth = maxWidth / Destinations.size
            val index = Destinations.indexOfFirst { it.section == section }.coerceAtLeast(0)
            val lampX by animateDpAsState(
                targetValue = itemWidth * index + (itemWidth - LampWidth) / 2,
                animationSpec = AirShiftMotion.fastSpatial(Dp.VisibilityThreshold),
                label = "navLamp",
            )
            Row(modifier = Modifier.fillMaxSize()) {
                Destinations.forEach { destination ->
                    NavigationItem(
                        destination = destination,
                        selected = section == destination.section,
                        onClick = { onSectionSelected(destination.section) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(lampX.roundToPx(), 0) }
                    .width(LampWidth)
                    .height(LampHeight)
                    .background(c.departure, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)),
            )
        }
    }
}

@Composable
private fun NavigationItem(
    destination: NavDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AirShiftTokens.colors
    val tint by animateColorAsState(
        targetValue = if (selected) c.ink else c.hint,
        animationSpec = AirShiftMotion.defaultEffects(),
        label = "navTint",
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .testTag(destination.testTag)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = tint,
            )
            Spacer(Modifier.height(4.dp))
            val labelStyle = MaterialTheme.typography.labelSmall
            Text(
                destination.label,
                color = tint,
                style = if (selected) labelStyle.copy(fontWeight = FontWeight.SemiBold) else labelStyle,
                maxLines = 1,
            )
        }
    }
}
