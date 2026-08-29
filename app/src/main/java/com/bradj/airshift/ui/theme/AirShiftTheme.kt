package com.bradj.airshift.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =====================================================================
// 航勤智排 Design Tokens —— 「安静的效率感」+ 东航品牌基因
// 仅用于 UI 样式，不涉及任何业务逻辑。
// =====================================================================

// ---------- 色板 Color ----------

/** 东航品牌红。仅用于：品牌标识点缀、主行动按钮、当前任务强调、"出港"标签。 */
val CeaRed = Color(0xFFC8102E)

/** 东航红深色端（渐变/按压态）。 */
val CeaRedDeep = Color(0xFFA00D24)

/** 出港标签浅红底。 */
val CeaRedSoft = Color(0xFFFDECEE)

/** 出港标签上的深红文字（AA 对比度）。 */
val OnCeaRedSoft = Color(0xFF9C0B22)

/** 品牌渐变：深红 → 东航红，细腻斜向，仅小面积点缀使用。 */
val CeaRedGradient: Brush = Brush.linearGradient(listOf(CeaRedDeep, CeaRed))

/** 藏青（取自东航飞燕 logo 蓝）：大标题与核心深色文字。 */
val CeaNavy = Color(0xFF1B2A4A)

/** 进港标签蓝 / 浅蓝底。 */
val InboundBlue = Color(0xFF2B5EA7)
val InboundBlueSoft = Color(0xFFEAF1FB)

/** 页面底色：云白。 */
val CloudWhite = Color(0xFFF5F6F8)

/** 卡片底色：纯白。 */
val CardWhite = Color(0xFFFFFFFF)

/** 1dp 微边框。 */
val BorderSoft = Color(0xFFECEEF2)

/** 输入框浅灰填充。 */
val FieldFill = Color(0xFFF0F2F5)

/** 主文字（深色）。 */
val TextPrimary = Color(0xFF1B2A4A)

/** 正文次深色。 */
val TextBody = Color(0xFF3A4356)

/** 说明文字（13sp 起，白底对比度 ≥ 4.5:1，满足 WCAG AA）。 */
val TextSecondary = Color(0xFF5F6673)

/** 弱提示 / 占位"--"（仅用于大号数字或非关键装饰）。 */
val TextHint = Color(0xFF8A8F99)

/** 提示竖条琥珀色（装饰性色条/圆点）。 */
val AmberAccent = Color(0xFFE8860B)

/** 琥珀色文字（AA 对比度，用于"变更"等小号提醒文字）。 */
val AmberText = Color(0xFFA85D00)

/** "已授权"绿色（AA 对比度）。 */
val SuccessGreen = Color(0xFF1B7F4C)

/** 底部导航未选中态：藏青灰。 */
val NavyGrey = Color(0xFF5A6B87)

/** VIP 琥珀金强调（保留辨识度）。 */
val VipAmber = Color(0xFFE8A33D)
val VipAmberContainer = Color(0xFFFCEBC8)
val OnVipAmberContainer = Color(0xFF6E4200)

/** 登机口/机位"变更"提醒文字色（AA）。 */
val ChangeOrange = AmberText

// ---------- 圆角 Radius ----------

object AirShiftRadius {
    /** 小标签 / 输入框。 */
    val Tag: Dp = 12.dp

    /** 大卡片。 */
    val Card: Dp = 20.dp
}

// ---------- 间距 Spacing（全部为 8 的倍数） ----------

object AirShiftSpacing {
    val S: Dp = 8.dp
    val M: Dp = 16.dp
    val L: Dp = 24.dp
    val XL: Dp = 32.dp
}

// ---------- 阴影 Elevation ----------

/**
 * 柔和弥散阴影：近似 0 4dp 24dp rgba(27,42,74,0.06)。
 * 用藏青低透明度 ambient/spot 叠加，避免灰色死影。
 */
@Stable
fun Modifier.softShadow(shape: Shape): Modifier = shadow(
    elevation = 6.dp,
    shape = shape,
    clip = false,
    ambientColor = CeaNavy.copy(alpha = 0.04f),
    spotColor = CeaNavy.copy(alpha = 0.08f),
)

/** 白卡统一边框。 */
val CardBorder: BorderStroke
    @Composable get() = BorderStroke(1.dp, BorderSoft)

// ---------- 数字字阶（等宽 tabular-nums，粗体） ----------

/** 超大核心数字：倒计时（34–40sp Bold tabular-nums）。 */
val NumericHero = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 36.sp,
    lineHeight = 42.sp,
    fontFeatureSettings = "tnum",
    color = CeaRed,
)

/** 大号数字：到位时间、实时时间。 */
val NumericLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    fontFeatureSettings = "tnum",
    color = TextPrimary,
)

/** 中号数字：航段实时时间。 */
val NumericMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 30.sp,
    fontFeatureSettings = "tnum",
    color = TextPrimary,
)

/** 数字单位（小时/分钟），跟随大数字后排。 */
val NumericUnit = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    color = TextPrimary,
)

// ---------- Material3 映射 ----------

private val AirShiftColorScheme = lightColorScheme(
    primary = CeaRed,
    onPrimary = Color.White,
    primaryContainer = CeaRedSoft,
    onPrimaryContainer = OnCeaRedSoft,
    secondary = InboundBlue,
    onSecondary = Color.White,
    secondaryContainer = InboundBlueSoft,
    onSecondaryContainer = Color(0xFF1D3F73),
    tertiary = Color(0xFF8A6100),
    tertiaryContainer = Color(0xFFFBE7AE),
    onTertiaryContainer = Color(0xFF3F2E00),
    background = CloudWhite,
    surface = CardWhite,
    surfaceVariant = FieldFill,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFFD5DAE2),
    outlineVariant = BorderSoft,
)

/** 品牌字阶：航班号 22sp 粗体；说明文字 12–13sp；标题收紧字距。 */
private val AirShiftTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

/** 圆角体系：小标签 12dp、大卡 20dp、按钮全圆角。 */
private val AirShiftShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(AirShiftRadius.Tag),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(AirShiftRadius.Card),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AirShiftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AirShiftColorScheme,
        typography = AirShiftTypography,
        shapes = AirShiftShapes,
        content = content,
    )
}
