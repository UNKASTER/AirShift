package com.bradj.airshift.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.theme.CardBorder
import com.bradj.airshift.ui.theme.CardWhite
import com.bradj.airshift.ui.theme.CeaRedSoft
import com.bradj.airshift.ui.theme.InboundBlue
import com.bradj.airshift.ui.theme.InboundBlueSoft
import com.bradj.airshift.ui.theme.OnCeaRedSoft
import com.bradj.airshift.ui.theme.softShadow

/**
 * 统一白卡：纯白底 + 1dp 微边框 + 柔和弥散阴影，大卡 20dp 圆角。
 */
@Composable
fun QuietCard(
    modifier: Modifier = Modifier,
    vip: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.softShadow(MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = if (vip) {
            androidx.compose.foundation.BorderStroke(1.5.dp, com.bradj.airshift.ui.theme.VipAmber)
        } else {
            CardBorder
        },
        content = content,
    )
}

/** 进出港方向小标签：进港 蓝字浅蓝底 / 出港 红字浅红底，12dp 圆角。 */
@Composable
fun DirectionTag(direction: String, modifier: Modifier = Modifier) {
    val isInbound = direction == "进港"
    Surface(
        modifier = modifier,
        color = if (isInbound) InboundBlueSoft else CeaRedSoft,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            direction,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = if (isInbound) InboundBlue else OnCeaRedSoft,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 航线细线箭头：1dp 圆头细线 + 小箭头，连接起讫机场。 */
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

/**
 * 头部航线弧线装饰：低透明度（5–10%）同心弧线，极简线条，不喧宾夺主。
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
