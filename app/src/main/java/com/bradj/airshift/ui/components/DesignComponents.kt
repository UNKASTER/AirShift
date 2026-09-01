package com.bradj.airshift.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.theme.AirShiftRadius
import com.bradj.airshift.ui.theme.CardTopHighlight
import com.bradj.airshift.ui.theme.CeaRedSoft
import com.bradj.airshift.ui.theme.InboundBlue
import com.bradj.airshift.ui.theme.InboundBlueSoft
import com.bradj.airshift.ui.theme.OnCeaRedSoft
import com.bradj.airshift.ui.theme.SkeletonGray
import com.bradj.airshift.ui.theme.SuccessGreen
import com.bradj.airshift.ui.theme.TextHint
import com.bradj.airshift.ui.theme.VipAmber
import com.bradj.airshift.ui.theme.listCardShadow

// ---------- 1.5px 线性图标构建器（Lucide 风格，统一线性，禁止填充混用） ----------

fun linearIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(pathData),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.5f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
    ).build()

/** 共享线性图标集。 */
object LinearIcons {
    /** 机号：飞机。 */
    val Plane = linearIcon(
        "Plane",
        "M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.1-1.1.5l-.3.5c-.2.5-.1 1 .3 1.3L9 12l-2 3H4l-1 1 3 2 2 3 1-1v-3l3-2 3.5 5.3c.3.4.8.5 1.3.3l.5-.2c.4-.3.6-.7.5-1.2z",
    )

    /** 登机口：门。 */
    val Gate = linearIcon("Gate", "M6 3h12v18H6z M4 21h16 M14 12h1")

    /** 机位：定位钉。 */
    val Stand = linearIcon(
        "Stand",
        "M21 10c0 7-9 13-9 13S3 17 3 10a9 9 0 0 1 18 0z M12 13a3 3 0 1 0 0-6 3 3 0 0 0 0 6z",
    )

    /** 机型：标签。 */
    val AircraftType = linearIcon(
        "AircraftType",
        "M20.59 13.41 12 22 2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z M7 7h.01",
    )

    /** 导入图片。 */
    val Image = linearIcon(
        "Image",
        "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z M8.5 10a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z M21 15l-5-5L5 21",
    )

    /** 导入 Excel：文本文件。 */
    val File = linearIcon(
        "File",
        "M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z M14 2v6h6 M16 13H8 M16 17H8 M10 9H8",
    )

    /** 留意/警告：三角感叹。 */
    val Alert = linearIcon(
        "Alert",
        "M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z M12 9v4 M12 17h.01",
    )

    /** 时间类 meta：时钟。 */
    val Clock = linearIcon(
        "Clock",
        "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M12 7v5l3 2",
    )

    /** 飞行状态 · 已起飞：起飞小飞机。 */
    val PlaneTakeoff = linearIcon(
        "PlaneTakeoff",
        "M2 22h20 M6.36 17.4 4 17l-2-4 1.1-.55a2 2 0 0 1 1.8 0l.17.1a2 2 0 0 0 1.8 0L8 12 5 6l.9-.45a2 2 0 0 1 2.09.2l4.02 3a2 2 0 0 0 2.1.2l4.19-2.06a2.41 2.41 0 0 1 1.73-.17L21 7a1.4 1.4 0 0 1 .87 1.99l-.38.76c-.23.46-.6.84-1.07 1.08L7.58 17.2a2 2 0 0 1-1.22.18Z",
    )

    /** 飞行状态 · 未起飞：停机小飞机（地面线 + 飞机）。 */
    val PlaneGround = linearIcon(
        "PlaneGround",
        "M2.5 21.5h19 M17.8 18.2 16 10l3.5-3.5C21 5 21.5 3 21 2c-1-.5-3 0-4.5 1.5L12 7 4.8 5.2c-.5-.1-.9.1-1.1.5l-.3.5c-.2.5-.1 1 .3 1.3L9 11l-2 3H4l-1 1 3 2 2 3 1-1v-3l3-2 3.5 5.3c.3.4.8.5 1.3.3l.5-.2c.4-.3.6-.7.5-1.2z",
    )
}

/**
 * 统一卡片：卡片底色随主题 + 1dp 微边框 + 一级列表卡阴影 + 16dp 圆角；
 * 顶部 1px 内高光（浅色 rgba(255,255,255,.6)，深色自动降为 8%）。
 */
@Composable
fun QuietCard(
    modifier: Modifier = Modifier,
    vip: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val dark = isSystemInDarkTheme()
    Card(
        modifier = modifier.listCardShadow(shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (vip) {
            androidx.compose.foundation.BorderStroke(1.5.dp, VipAmber)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().drawBehind {
                val highlight = if (dark) Color.White.copy(alpha = 0.08f) else CardTopHighlight
                val y = 0.5.dp.toPx()
                val inset = AirShiftRadius.Card.toPx()
                drawLine(
                    color = highlight,
                    start = Offset(inset, y),
                    end = Offset(size.width - inset, y),
                    strokeWidth = 1.dp.toPx(),
                )
            },
        ) {
            Column { content() }
        }
    }
}

/** 进出港方向 chip：全圆角胶囊；进港 蓝字浅蓝底 / 出港 红字浅红底。 */
@Composable
fun DirectionTag(direction: String, modifier: Modifier = Modifier) {
    val isInbound = direction == "进港"
    Surface(
        modifier = modifier,
        color = if (isInbound) InboundBlueSoft else CeaRedSoft,
        shape = CircleShape,
    ) {
        Text(
            direction,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            color = if (isInbound) InboundBlue else OnCeaRedSoft,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 飞行状态 chip（与进出港 chip 同高同圆角，12sp Semibold）：
 * 未起飞：浅灰底 + 深灰字 + 停机小飞机；已起飞：10% 墨绿底 + 墨绿字 + 起飞小飞机。
 */
@Composable
fun FlightStatusChip(departed: Boolean, modifier: Modifier = Modifier) {
    val background = if (departed) SuccessGreen.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant
    val foreground = if (departed) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier,
        color = background,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (departed) LinearIcons.PlaneTakeoff else LinearIcons.PlaneGround,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = foreground,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (departed) "已起飞" else "未起飞",
                color = foreground,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 航线细线箭头：1dp 圆头细线 + 小箭头，连接起讫机场三字码（FIDS 质感）。 */
@Composable
fun RouteArrow(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.dp.toPx()
        val y = size.height / 2f
        val headLength = 6.dp.toPx()
        val headHalf = 3.5.dp.toPx()
        val endX = size.width
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(endX - headLength, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        val head = Path().apply {
            moveTo(endX, y)
            lineTo(endX - headLength, y - headHalf)
            moveTo(endX, y)
            lineTo(endX - headLength, y + headHalf)
        }
        drawPath(head, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}

/** 登机牌撕线：1dp 虚线分隔（接续航班两段、卡片 meta 行之上）。 */
@Composable
fun BoardingPassDivider(modifier: Modifier = Modifier, color: Color? = null) {
    val lineColor = color ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = lineColor,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
        )
    }
}

/** "--" 占位骨架：浅灰短横线，不直接显示破折号。深色模式下自动切换为半透明白。 */
@Composable
fun SkeletonStub(
    modifier: Modifier = Modifier,
    width: Dp = 32.dp,
    height: Dp = 10.dp,
) {
    val color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.14f) else SkeletonGray
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(color),
    )
}

/**
 * meta 行单项：13dp 线性图标 + 灰色小 label + 深色 Semibold value（tabular-nums）；
 * value 为 "--" 时渲染骨架短横线；hasChange 附"变更"提醒；
 * labelWidth 非空时 label 容器固定等宽（用于航线网格使数值列对齐）。
 */
@Composable
fun MetaItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hasChange: Boolean = false,
    labelWidth: Dp? = null,
    alignEnd: Boolean = false,
) {
    Row(
        modifier = if (alignEnd) modifier.fillMaxWidth() else modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = TextHint,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            modifier = if (labelWidth != null) Modifier.width(labelWidth) else Modifier,
            style = MaterialTheme.typography.labelSmall,
            color = TextHint,
        )
        Spacer(Modifier.width(3.dp))
        if (value == "--" || value == "--:--") {
            SkeletonStub(width = 26.dp, height = 9.dp)
        } else {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        if (hasChange) {
            ChangeIndicator(modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/**
 * 头部燕子尾翼弧线装饰：低透明度（5–10%）同心弧线，极简线条，不喧宾夺主。
 */
@Composable
fun RouteArcsDecoration(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 1.dp.toPx())
        val base = size.minDimension
        val center = Offset(size.width * 0.92f, size.height * 0.08f)
        listOf(0.55f, 0.8f, 1.05f).forEach { factor ->
            drawArc(
                color = color,
                startAngle = 90f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(center.x - base * factor, center.y - base * factor),
                size = androidx.compose.ui.geometry.Size(base * factor * 2f, base * factor * 2f),
                style = stroke,
            )
        }
    }
}

/** 卡片左侧 4dp 强调竖条（配合 IntrinsicSize.Min 的 Row 使用）。 */
@Composable
fun AccentBar(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(4.dp)
            .fillMaxHeight()
            .background(color),
    )
}

/** 小圆点（授权状态等指示用）。 */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(color = color, radius = size.minDimension / 2f)
    }
}
