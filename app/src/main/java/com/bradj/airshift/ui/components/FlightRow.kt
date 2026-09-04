package com.bradj.airshift.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradj.airshift.model.LegDirection
import com.bradj.airshift.specialservice.FlightCancellationRecord
import com.bradj.airshift.specialservice.FlightCancellationScope
import com.bradj.airshift.specialservice.FlightServiceRecord
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.AmberAccent
import com.bradj.airshift.ui.theme.CeaRedSoft
import com.bradj.airshift.ui.theme.FlightNumber
import com.bradj.airshift.ui.theme.NumericLarge
import com.bradj.airshift.ui.theme.OnCeaRedSoft
import com.bradj.airshift.ui.theme.SuccessGreen
import com.bradj.airshift.ui.theme.TextHint
import java.time.LocalDateTime

/** 航线网格标签容器等宽（以三字宽的「登机口」为准），使两侧数值列各自对齐成竖线。 */
private val RouteMetaLabelWidth = 38.dp

/**
 * 航段区块（FIDS 航显屏质感），卡片三段节奏：
 * 标题行（方向 chip + 30sp Heavy 航班号 + 右侧时间块：实时为主 / 计划为辅）
 * → 航线统一网格（行1 三字码｜行2 中文站名｜行3 登机口｜行4 机位，
 *   左右两列共享行高与基线，箭头在行1中间列与三字码同轴；
 *   两侧标签统一为「登机口」「机位」，左列左对齐、右列右对齐镜像）
 * → 撕线虚线 → meta 区 2×2 等宽网格（登机口关闭｜实际离位／机号｜机型，左对齐，tabular-nums）。
 * 无数据项渲染浅灰骨架短横线，两侧占位规格统一。
 */
@Composable
fun FlightRow(
    direction: LegDirection,
    flight: String,
    fromCode: String?,
    fromName: String?,
    toCode: String?,
    toName: String?,
    planned: LocalDateTime?,
    estimated: LocalDateTime?,
    actual: LocalDateTime?,
    specialServices: List<FlightServiceRecord>,
    flightCancellation: FlightCancellationRecord?,
    details: List<DetailEntry>,
    originDetails: List<DetailEntry> = emptyList(),
    destinationDetails: List<DetailEntry> = emptyList(),
    aircraftRegistration: String? = null,
    aircraftType: String? = null,
    offBlock: LocalDateTime? = null,
) {
    val liveTime = actual ?: estimated
    Column(modifier = Modifier.fillMaxWidth()) {
        // 行1（chip 行）：方向 chip + 飞行状态 chip，左对齐，间距 8dp；无航班数据时状态 chip 不渲染
        Row(verticalAlignment = Alignment.CenterVertically) {
            DirectionTag(direction)
            val departed = actual != null || offBlock != null
            if (departed || planned != null || estimated != null) {
                Spacer(Modifier.width(AirShiftSpacing.S))
                FlightStatusChip(departed = departed)
            }
        }
        Spacer(Modifier.height(10.dp))
        // 行2（主信息行）：航班号 + 实时时间块，基线对齐；
        // 航班号最高优先级：不截断、不省略、不压缩，间距不足时由弹性空隙吸收（最小 24dp）
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                flight,
                style = FlightNumber,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.weight(1f).widthIn(min = AirShiftSpacing.L))
            TimeBlock(live = liveTime, planned = planned)
        }
        Spacer(Modifier.height(20.dp))
        // 航线统一网格：每一行是同一个 Row（1fr | 箭头列 | 1fr），左右严格对位
        RouteGridRow(
            left = { RouteCode(fromCode, alignEnd = false) },
            center = {
                RouteArrow(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.width(44.dp).height(12.dp),
                )
            },
            right = { RouteCode(toCode, alignEnd = true) },
        )
        Spacer(Modifier.height(2.dp))
        RouteGridRow(
            left = { RouteName(fromName, alignEnd = false) },
            right = { RouteName(toName, alignEnd = true) },
        )
        val metaRowCount = maxOf(originDetails.size, destinationDetails.size)
        for (index in 0 until metaRowCount) {
            Spacer(Modifier.height(6.dp))
            RouteGridRow(
                left = {
                    originDetails.getOrNull(index)?.let { entry ->
                        MetaItem(
                            icon = metaIconFor(entry.kind),
                            label = entry.label,
                            value = entry.value,
                            hasChange = entry.hasChange,
                            labelWidth = RouteMetaLabelWidth,
                        )
                    }
                },
                right = {
                    destinationDetails.getOrNull(index)?.let { entry ->
                        MetaItem(
                            icon = metaIconFor(entry.kind),
                            label = entry.label,
                            value = entry.value,
                            hasChange = entry.hasChange,
                            labelWidth = RouteMetaLabelWidth,
                            alignEnd = true,
                        )
                    }
                },
            )
        }
        flightCancellation?.let { cancellation ->
            Spacer(Modifier.height(AirShiftSpacing.S))
            Surface(color = CeaRedSoft, shape = CircleShape) {
                Text(
                    if (cancellation.scope == FlightCancellationScope.TRIP) "MUC：行程已取消" else "MUC：特服已取消",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = OnCeaRedSoft,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (specialServices.isNotEmpty()) {
            Spacer(Modifier.height(AirShiftSpacing.S))
            SpecialServiceDetails(specialServices)
        }
        // 非时间类详情行（如 MUC 变更来源、预计登机开始/关闭）保持紧凑 label-value 行
        val clockEntries = details.filter { it.kind.clockMeta }
        val otherDetails = details.filterNot { it.kind.clockMeta }
        if (otherDetails.isNotEmpty()) {
            Spacer(Modifier.height(AirShiftSpacing.M))
            Column(verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.S)) {
                otherDetails.forEach { detail ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            detail.label,
                            modifier = Modifier.width(88.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (detail.value == "--" || detail.value == "--:--") {
                                SkeletonStub(width = 30.dp, height = 10.dp)
                            } else {
                                Text(
                                    detail.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            if (detail.hasChange) {
                                ChangeIndicator(modifier = Modifier.padding(start = AirShiftSpacing.S))
                            }
                        }
                    }
                }
            }
        }
        // meta 区 2×2 等宽网格：登机口关闭｜实际离位 / 机号｜机型，单元格左对齐
        val metaItems = buildList<@Composable () -> Unit> {
            clockEntries.forEach { entry ->
                add {
                    MetaItem(
                        icon = LinearIcons.Clock,
                        label = entry.label,
                        value = entry.value,
                        hasChange = entry.hasChange,
                    )
                }
            }
            aircraftRegistration?.let { registration ->
                add { MetaItem(icon = LinearIcons.Plane, label = "机号", value = registration) }
            }
            aircraftType?.let { type ->
                add { MetaItem(icon = LinearIcons.AircraftType, label = "机型", value = type) }
            }
        }
        if (metaItems.isNotEmpty()) {
            Spacer(Modifier.height(AirShiftSpacing.M))
            BoardingPassDivider()
            Spacer(Modifier.height(10.dp))
            metaItems.chunked(2).forEachIndexed { rowIndex, rowItems ->
                if (rowIndex > 0) Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        rowItems[0]()
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        rowItems.getOrNull(1)?.invoke()
                    }
                }
            }
        }
    }
}

/**
 * 航线网格行：左 1fr 左对齐 + 中间箭头列 + 右 1fr 右对齐。
 * 每行共享同一行高与基线，左右两列逐行严格对位。
 */
@Composable
private fun RouteGridRow(
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
    center: (@Composable () -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { left() }
        Box(modifier = Modifier.width(52.dp), contentAlignment = Alignment.Center) { center?.invoke() }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { right() }
    }
}

/** meta 行图标映射：按条目类型选择线性图标。 */
private fun metaIconFor(kind: DetailKind) = when (kind) {
    DetailKind.GATE -> LinearIcons.Gate
    else -> LinearIcons.Stand
}

/**
 * 时间块（右对齐，竖排两行，计划在上 / 实时在下）：
 * 上 = 「计划 HH:mm」12sp 灰 #8A94A6；下 = 实时时间 28sp Heavy 大字
 * （延误判定按计划与实时的分钟差：15 分钟～12 小时为延误，琥珀 #D97706；
 * 正点/提前/跨日不可比统一墨绿 #0F7B5F，无计划可对比时藏青中性）。
 * 主信息行 flex-end 底边对齐：时间块底边与航班号底边对齐，数字无下伸部，
 * 底边对齐即视觉基线对齐。无实时数据时计划顶替大字位、小字行隐藏。
 */
@Composable
private fun TimeBlock(live: LocalDateTime?, planned: LocalDateTime?) {
    val neutralColor = MaterialTheme.colorScheme.onSurface
    Column(horizontalAlignment = Alignment.End) {
        if (live != null) {
            if (planned != null) {
                Text(
                    "计划 ${planned.formatClock()}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = TextHint,
                )
            }
            val liveColor = when {
                planned == null -> neutralColor
                else -> {
                    // 跨午夜航班的计划/实时可能分属两天，直接比较完整日期会被日期差污染；
                    // 只把 15 分钟～12 小时的正差判为延误，≥12 小时视为跨日数据不可比。
                    val delayMinutes = java.time.Duration.between(planned, live).toMinutes()
                    if (delayMinutes in 16 until 720) AmberAccent else SuccessGreen
                }
            }
            Text(
                live.formatClock(),
                style = NumericLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = liveColor,
            )
        } else if (planned != null) {
            Text(
                planned.formatClock(),
                style = NumericLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = neutralColor,
            )
        } else {
            SkeletonStub(width = 56.dp, height = 22.dp)
        }
    }
}

/** 航线端点三字码：20sp Heavy 等宽大写。 */
@Composable
private fun RouteCode(code: String?, alignEnd: Boolean) {
    if (code == null) {
        SkeletonStub(width = 44.dp, height = 16.dp)
    } else {
        Text(
            code,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = "tnum",
                letterSpacing = 1.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            maxLines = 1,
        )
    }
}

/** 航线端点中文名：13sp 灰小字，无数据时骨架占位（两侧规格统一）。 */
@Composable
private fun RouteName(name: String?, alignEnd: Boolean) {
    if (name == null) {
        SkeletonStub(width = 36.dp, height = 9.dp)
    } else {
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = TextHint,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 特服详情：徽章 + 每条记录的类型/置信度/确认状态/更新时间。 */
@Composable
fun SpecialServiceDetails(records: List<FlightServiceRecord>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AirShiftSpacing.S),
        verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.S),
    ) {
        records.sortedBy { it.serviceType.ordinal }.forEach { record ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = CircleShape,
            ) {
                Text(
                    record.badgeLabel(),
                    modifier = Modifier.padding(horizontal = AirShiftSpacing.S, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
    Spacer(Modifier.height(AirShiftSpacing.S))
    records.sortedBy { it.serviceType.ordinal }.forEach { record ->
        Text(
            "${record.typeLabel()} · ${record.confidence.label()} · 已确认 · 更新 ${record.updatedAtEpochMillis.formatEpoch("MM-dd HH:mm")}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
