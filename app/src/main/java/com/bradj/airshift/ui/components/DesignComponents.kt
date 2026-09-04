package com.bradj.airshift.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// ---------- 1.5px 线性图标构建器（统一线性，禁止填充混用） ----------

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
        strokeLineJoin = StrokeJoin.Round,
    ).build()

/** 共享线性图标集。 */
object LinearIcons {
    /** 机号：飞机。 */
    val Plane = linearIcon(
        "Plane",
        "M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.1-1.1.5l-.3.5c-.2.5-.1 1 .3 1.3L9 12l-2 3H4l-1 1 3 2 2 3 1-1v-3l3-2 3.5 5.3c.3.4.8.5 1.3.3l.5-.2c.4-.3.6-.7.5-1.2z",
    )

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

    /** 特服 · 轮椅：头 + 躯干/座面/小腿 + 大轮（ISA 轮椅符号的线性版）。 */
    val Wheelchair = linearIcon(
        "Wheelchair",
        "M11.75 4.25a1.75 1.75 0 1 1-3.5 0 1.75 1.75 0 0 1 3.5 0z " +
            "M10 8v4.5h6l2.5 5.5h2.5 " +
            "M14 16.5a4.5 4.5 0 1 1-9 0 4.5 4.5 0 0 1 9 0z",
    )
}

/** 小圆点（授权状态等指示用）。 */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(color = color, radius = size.minDimension / 2f)
    }
}
