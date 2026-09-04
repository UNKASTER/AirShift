package com.bradj.airshift.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradj.airshift.model.LegDirection
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.specialservice.FlightCancellationScope
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.specialservice.ServiceType
import com.bradj.airshift.ui.theme.AirShiftMotion
import com.bradj.airshift.ui.theme.AirShiftRadius
import com.bradj.airshift.ui.theme.AirShiftTokens
import com.bradj.airshift.ui.theme.FlightNumber
import com.bradj.airshift.ui.theme.FlightNumberLarge
import com.bradj.airshift.ui.theme.NumericSmall
import com.bradj.airshift.ui.theme.NumericValue
import com.bradj.airshift.ui.theme.StripTime
import com.bradj.airshift.ui.theme.currentCardShadow
import java.time.LocalDate
import java.time.LocalDateTime

private const val COMPLETED_ALPHA = 0.6f
private val MetaValueStyle = NumericSmall.copy(fontSize = 13.sp)

/**
 * 信息条：一项任务。左侧 6dp 方向夹条，右侧每个航段一行（折叠）或一块（展开）。
 *
 * - [expanded]：展开为 FULL 级别（航班号大字、航线全名、计划/预计、登机口关闭与实际离位、特服与 MUC 明细）；
 * - [emphasized]：当前任务，抬起（阴影、无边线）；
 * - [completed]：已完成，整条变暗，状态灯改为"已完成"；
 * - [onClick]：点击切换展开。
 */
@Composable
fun DutyStrip(
    assignment: RosterAssignment,
    muc: MucContext,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    completed: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val level = if (expanded) DetailLevel.FULL else DetailLevel.SUMMARY
    val legs = remember(assignment, muc, level) { assignment.legUiModels(muc, level) }
    val c = AirShiftTokens.colors
    val shape = RoundedCornerShape(AirShiftRadius.Strip)
    val baseDate = (assignment.scheduledArrival ?: assignment.scheduledDeparture)?.toLocalDate()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("strip_${assignment.stableId}")
            .alpha(if (completed) COMPLETED_ALPHA else 1f)
            .then(if (emphasized) Modifier.currentCardShadow(shape) else Modifier)
            .clip(shape)
            .background(c.strip)
            .then(if (emphasized) Modifier else Modifier.border(1.dp, c.rule, shape))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .animateContentSize(tween(AirShiftMotion.StandardMs, easing = AirShiftMotion.Standard))
            .height(IntrinsicSize.Min),
    ) {
        DirectionHolder(assignment.kind)
        Column(modifier = Modifier.weight(1f)) {
            if (expanded) {
                StripHead(assignment, legs)
            }
            legs.forEachIndexed { index, leg ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = if (expanded) 14.dp else 0.dp),
                        thickness = 1.dp,
                        color = c.rule,
                    )
                }
                if (expanded) {
                    ExpandedLeg(leg, baseDate)
                } else {
                    LegLine(
                        leg = leg,
                        baseDate = baseDate,
                        completed = completed,
                        vipText = if (index == 0) assignment.vipBadgeText() else null,
                        hasSpecialServices = index == 0 && legs.any { it.hasSpecialServices },
                    )
                }
            }
            if (expanded) {
                StripFoot(legs.last())
            }
        }
    }
}

private fun RosterAssignment.vipBadgeText(): String? = when {
    inboundHasVip && outboundHasVip -> "VIP"
    inboundHasVip -> "进港 VIP"
    outboundHasVip -> "出港 VIP"
    else -> null
}

// ---------- 折叠：一航段一行，44dp ----------

@Composable
private fun LegLine(
    leg: FlightLegUiModel,
    baseDate: LocalDate?,
    completed: Boolean,
    vipText: String?,
    hasSpecialServices: Boolean,
) {
    val c = AirShiftTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            leg.direction.shortLabel,
            modifier = Modifier.width(18.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (leg.direction == LegDirection.INBOUND) c.arrival else c.departureText,
        )
        Spacer(Modifier.width(8.dp))
        TimeText(leg = leg, baseDate = baseDate, style = StripTime, modifier = Modifier.width(52.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            leg.flight,
            modifier = Modifier.width(66.dp),
            style = FlightNumber,
            color = c.ink,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(8.dp))
        RouteCodes(leg = leg, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        InlineStand(leg)
        val status = legStatus(leg, completed)
        if (vipText != null) {
            Spacer(Modifier.width(6.dp))
            StatusLamp(text = vipText, kind = LampKind.Vip)
        }
        if (hasSpecialServices) {
            Spacer(Modifier.width(6.dp))
            StatusLamp(
                text = "",
                kind = LampKind.Neutral,
                icon = LinearIcons.Wheelchair,
                iconContentDescription = "特服",
            )
        }
        if (status != null) {
            Spacer(Modifier.width(8.dp))
            StatusLamp(text = status.text, kind = status.kind, dot = status.dot)
        }
    }
}

/** 折叠行里的航线：进港显示"前站 →"，出港显示"→ 后站"，缺失为 —。 */
@Composable
private fun RouteCodes(leg: FlightLegUiModel, modifier: Modifier = Modifier) {
    val c = AirShiftTokens.colors
    val code = if (leg.direction == LegDirection.INBOUND) leg.fromCode else leg.toCode
    val text = buildAnnotatedString {
        if (leg.direction == LegDirection.OUTBOUND) {
            withStyle(SpanStyle(color = c.hint)) { append("→ ") }
        }
        withStyle(codeSpan(if (code == null) c.hint else c.ink)) { append(code ?: MISSING) }
        if (leg.direction == LegDirection.INBOUND) {
            withStyle(SpanStyle(color = c.hint)) { append(" →") }
        }
    }
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun codeSpan(color: Color) = SpanStyle(
    color = color,
    fontFamily = NumericSmall.fontFamily,
    fontWeight = NumericSmall.fontWeight,
    fontSize = 14.sp,
)

/** 折叠行里的本站机位："机位 355"，缺失为 —。 */
@Composable
private fun InlineStand(leg: FlightLegUiModel) {
    val c = AirShiftTokens.colors
    val stand = leg.localStand()
    Row(verticalAlignment = Alignment.Bottom) {
        Text("机位", style = MaterialTheme.typography.labelSmall, color = c.hint)
        Spacer(Modifier.width(4.dp))
        Text(
            stand?.value ?: MISSING,
            style = NumericSmall,
            color = standColor(stand, c.hint, c.estimate, c.ink),
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun standColor(stand: DetailEntry?, hint: Color, estimate: Color, ink: Color): Color = when {
    stand == null -> hint
    stand.hasChange -> estimate
    else -> ink
}

// ---------- 展开：一航段一块 ----------

@Composable
private fun StripHead(assignment: RosterAssignment, legs: List<FlightLegUiModel>) {
    val c = AirShiftTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(assignment.kind.title(), style = MaterialTheme.typography.labelLarge, color = c.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            legs.flatMap { it.specialServices }.sortedBy { it.serviceType.ordinal }.forEach { record ->
                ServiceLamp(record)
            }
            assignment.vipBadgeText()?.let { StatusLamp(text = it, kind = LampKind.Vip) }
        }
    }
}

@Composable
private fun ExpandedLeg(leg: FlightLegUiModel, baseDate: LocalDate?) {
    val c = AirShiftTokens.colors
    val stand = leg.localStand()
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusLamp(
                    text = leg.direction.label,
                    kind = if (leg.direction == LegDirection.INBOUND) LampKind.Arrival else LampKind.Departure,
                )
                Spacer(Modifier.width(10.dp))
                Text(leg.flight, style = FlightNumberLarge, color = c.ink, maxLines = 1, softWrap = false)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "机位",
                    modifier = Modifier.padding(bottom = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.hint,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stand?.value ?: MISSING,
                    style = FlightNumberLarge,
                    color = standColor(stand, c.hint, c.estimate, c.ink),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        RouteLine(leg)
        TimesRow(leg, baseDate)
        MetaRow(leg)
        leg.details.firstOrNull { it.kind == DetailKind.GATE_CHANGE }?.let { MucLine("登机口变更", it.value) }
        leg.details.firstOrNull { it.kind == DetailKind.STAND_CHANGE_SOURCE }?.let { MucLine("机位变更", it.value) }
        leg.flightCancellation?.let { cancellation ->
            StatusLamp(
                text = if (cancellation.scope == FlightCancellationScope.TRIP) "MUC：行程已取消" else "MUC：特服已取消",
                kind = LampKind.Alert,
                dot = true,
            )
        }
        if (leg.specialServices.isNotEmpty()) {
            ServiceDetails(leg.specialServices)
        }
    }
}

/** 航线全名："PVG 上海浦东 → LHW 兰州中川"，缺失的代码为 —，缺失的名称省略。 */
@Composable
private fun RouteLine(leg: FlightLegUiModel) {
    val c = AirShiftTokens.colors
    val text = buildAnnotatedString {
        appendAirport(leg.fromCode, leg.fromName, c)
        withStyle(SpanStyle(color = c.hint)) { append("  →  ") }
        appendAirport(leg.toCode, leg.toName, c)
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private fun AnnotatedString.Builder.appendAirport(
    code: String?,
    name: String?,
    c: com.bradj.airshift.ui.theme.AirShiftPalette,
) {
    withStyle(codeSpan(if (code == null) c.hint else c.ink)) { append(code ?: MISSING) }
    if (name != null) {
        withStyle(SpanStyle(color = c.inkSecondary)) { append(" $name") }
    }
}

/** 计划 / 预计（或实际）时间 + 状态灯。 */
@Composable
private fun TimesRow(leg: FlightLegUiModel, baseDate: LocalDate?) {
    val c = AirShiftTokens.colors
    val status = legStatus(leg, completed = false)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        leg.planned?.let { planned ->
            LabeledClock(label = "计划", time = planned, baseDate = baseDate, color = c.inkSecondary)
        }
        leg.live?.let { live ->
            LabeledClock(
                label = if (leg.liveKind == LiveKind.ACTUAL) "实际" else "预计",
                time = live,
                baseDate = baseDate,
                color = liveColor(leg),
            )
        }
        if (status != null) {
            StatusLamp(
                text = status.text,
                kind = status.kind,
                dot = status.dot,
                modifier = Modifier.padding(bottom = 1.dp),
            )
        }
    }
}

@Composable
private fun LabeledClock(label: String, time: LocalDateTime, baseDate: LocalDate?, color: Color) {
    val c = AirShiftTokens.colors
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            label,
            modifier = Modifier.padding(bottom = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = c.hint,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            clockWithDayMarker(time, baseDate, c.estimate),
            style = NumericValue,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private val MetaKinds = listOf(
    DetailKind.BOARDING_START,
    DetailKind.BOARDING_END,
    DetailKind.GATE_CLOSED,
    DetailKind.OFF_BLOCK,
)

/** 登机口关闭、实际离位、预计登机开始/关闭、对方机位：有值才显示，没有就整格省略。 */
@Composable
private fun MetaRow(leg: FlightLegUiModel) {
    val c = AirShiftTokens.colors
    val entries = buildList {
        leg.remoteStand()?.let { add("对方机位" to it.value) }
        MetaKinds.forEach { kind ->
            leg.details.firstOrNull { it.kind == kind && it.value.isPresent() }
                ?.let { add(kind.shortLabel() to it.value) }
        }
    }
    if (entries.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        entries.forEachIndexed { index, (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    Text("·", style = MaterialTheme.typography.bodySmall, color = c.hint)
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.bodySmall, color = c.inkSecondary)
                Spacer(Modifier.width(4.dp))
                Text(value, style = MetaValueStyle, color = c.ink, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** MUC 变更行：琥珀值 + 更新时间。 */
@Composable
private fun MucLine(label: String, value: String) {
    val c = AirShiftTokens.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.inkSecondary)
        Spacer(Modifier.width(6.dp))
        Text(value, style = MetaValueStyle, color = c.estimate, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ServiceLamp(record: FlightServiceRecord) {
    val wheelchair = record.serviceType == ServiceType.WHEELCHAIR
    StatusLamp(
        text = record.badgeLabel(),
        kind = LampKind.Neutral,
        icon = if (wheelchair) LinearIcons.Wheelchair else null,
        iconContentDescription = if (wheelchair) "轮椅" else null,
    )
}

/** 特服明细：一行一条记录。轮椅只给等级字母，不显示 WCHR/WCHS/WCHC 全称。 */
@Composable
private fun ServiceDetails(records: List<FlightServiceRecord>) {
    val c = AirShiftTokens.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        records.sortedBy { it.serviceType.ordinal }.forEach { record ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (record.serviceType == ServiceType.WHEELCHAIR) {
                    Icon(
                        imageVector = LinearIcons.Wheelchair,
                        contentDescription = "轮椅",
                        modifier = Modifier.size(12.dp),
                        tint = c.inkSecondary,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                val updated = record.updatedAtEpochMillis.formatEpoch("MM-dd HH:mm")
                Text(
                    "${record.typeLabel()} · ${record.confidence.label()} · 已确认 · 更新 $updated",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.inkSecondary,
                )
            }
        }
    }
}

@Composable
private fun StripFoot(lastLeg: FlightLegUiModel) {
    val c = AirShiftTokens.colors
    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), thickness = 1.dp, color = c.rule)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 7.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("机号", style = MaterialTheme.typography.bodySmall, color = c.hint)
        Spacer(Modifier.width(4.dp))
        Text(lastLeg.aircraftRegistration ?: MISSING, style = MetaValueStyle, color = c.inkSecondary)
        Spacer(Modifier.width(10.dp))
        Text("机型", style = MaterialTheme.typography.bodySmall, color = c.hint)
        Spacer(Modifier.width(4.dp))
        Text(
            lastLeg.aircraftType?.takeIf { it.isPresent() } ?: MISSING,
            style = MetaValueStyle,
            color = c.inkSecondary,
        )
    }
}

// ---------- 共用的小计算 ----------

@Composable
private fun liveColor(leg: FlightLegUiModel): Color {
    val c = AirShiftTokens.colors
    return when {
        leg.liveKind == LiveKind.ACTUAL -> c.ok
        leg.delayMinutes() == null -> c.ink
        leg.delayMinutes().isLate() -> c.estimate
        else -> c.ok
    }
}

/** 时间列：实时优先、计划回退；实际墨绿、晚点琥珀、其他主色；跨到次日带 +1 上标。 */
@Composable
private fun TimeText(
    leg: FlightLegUiModel,
    baseDate: LocalDate?,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val c = AirShiftTokens.colors
    val time = leg.live ?: leg.planned
    if (time == null) {
        Text(MISSING, modifier = modifier, style = style, color = c.hint)
    } else {
        Text(
            clockWithDayMarker(time, baseDate, c.estimate),
            modifier = modifier.widthIn(min = 44.dp),
            style = style,
            color = if (leg.live != null) liveColor(leg) else c.ink,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun clockWithDayMarker(time: LocalDateTime, baseDate: LocalDate?, markerColor: Color) = buildAnnotatedString {
    append(time.formatClock())
    if (baseDate != null && time.toLocalDate().isAfter(baseDate)) {
        withStyle(SpanStyle(color = markerColor, fontSize = 10.sp, baselineShift = BaselineShift.Superscript)) {
            append("+1")
        }
    }
}

