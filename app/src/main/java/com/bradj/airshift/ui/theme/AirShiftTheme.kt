package com.bradj.airshift.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =====================================================================
// 航勤智排 Design Tokens —— 航显板 × 进程单（0.11）
//
// 世界：每页顶部是贯通到状态栏之下的藏青"板面"，其余是冷白"条架"。
// 任务是固定列的信息条，左侧 6dp 夹条给方向；状态是小矩形灯，不是胶囊。
// 只有两种品牌色：东航红只给出港与主操作，进港用藏青蓝；琥珀 = 预计/变更灯，
// 墨绿 = 正常/已起飞灯。深色主题是真正的夜间航显，不是反色。
// =====================================================================

/** 全套语义色。通过 [LocalAirShiftPalette] 提供，随浅/深主题切换。 */
@Immutable
data class AirShiftPalette(
    val isDark: Boolean,
    /** 板面：页顶贯通区、Onboarding 全屏、小组件底。 */
    val board: Color,
    /** 板面上的行线。 */
    val boardRule: Color,
    /** 板面主文字。 */
    val onBoard: Color,
    /** 板面次文字（藏青调的浅色，不用纯灰）。 */
    val onBoardSecondary: Color,
    /** 板面弱文字 / 分隔点。 */
    val onBoardTertiary: Color,
    /** 板面上的警示文字（"应立即到位"）：藏青底上可读的浅红。 */
    val onBoardAlert: Color,
    /** 页面底色（条架）。 */
    val ground: Color,
    /** 信息条底。 */
    val strip: Color,
    /** 条间线 / 输入框边。 */
    val rule: Color,
    /** 更深一级的线（分段选择器边、休息日夹条）。 */
    val ruleStrong: Color,
    /** 主文字。 */
    val ink: Color,
    /** 次文字。 */
    val inkSecondary: Color,
    /** 弱提示 / 缺失值。 */
    val hint: Color,
    /** 出港夹条、主操作（东航红）。 */
    val departure: Color,
    val departureSoft: Color,
    val departureText: Color,
    /** 进港夹条（藏青蓝）。 */
    val arrival: Color,
    val arrivalSoft: Color,
    val arrivalText: Color,
    /** 正常 / 已起飞 / 已完成。 */
    val ok: Color,
    val okSoft: Color,
    /** 预计时间 / 延误 / 变更 / 交接班。 */
    val estimate: Color,
    val estimateSoft: Color,
    /** 取消 / 应立即到位。 */
    val alert: Color,
    val alertSoft: Color,
    /** VIP 灯。 */
    val vipSoft: Color,
    val vipText: Color,
    /** 中性灯底、次按钮底。 */
    val neutralSoft: Color,
    /** 底栏底色。 */
    val nav: Color,
    /** 输入框底。 */
    val field: Color,
)

val LightPalette = AirShiftPalette(
    isDark = false,
    board = Color(0xFF14284B),
    boardRule = Color(0x1AFFFFFF),
    onBoard = Color(0xFFFFFFFF),
    onBoardSecondary = Color(0xFFA9B6CC),
    onBoardTertiary = Color(0xFF6F7F9C),
    onBoardAlert = Color(0xFFFF8A98),
    ground = Color(0xFFF1F3F7),
    strip = Color(0xFFFFFFFF),
    rule = Color(0xFFE4E8EF),
    ruleStrong = Color(0xFFD5DAE2),
    ink = Color(0xFF14284B),
    inkSecondary = Color(0xFF4A5568),
    hint = Color(0xFF8A94A6),
    departure = Color(0xFFC8102E),
    departureSoft = Color(0xFFFDECEE),
    departureText = Color(0xFF9C0B22),
    arrival = Color(0xFF2B5EA7),
    arrivalSoft = Color(0xFFEAF1FB),
    arrivalText = Color(0xFF1D4B8A),
    ok = Color(0xFF0F7B5F),
    okSoft = Color(0x1A0F7B5F),
    estimate = Color(0xFFB45309),
    estimateSoft = Color(0x1AB45309),
    alert = Color(0xFFC8102E),
    alertSoft = Color(0x1AC8102E),
    vipSoft = Color(0xFFFCEBC8),
    vipText = Color(0xFF6E4200),
    neutralSoft = Color(0x0F14284B),
    nav = Color(0xFFFFFFFF),
    field = Color(0xFFF1F3F7),
)

val DarkPalette = AirShiftPalette(
    isDark = true,
    board = Color(0xFF0B1526),
    boardRule = Color(0x1AFFFFFF),
    onBoard = Color(0xFFEDF1F7),
    onBoardSecondary = Color(0xFF93A3BF),
    onBoardTertiary = Color(0xFF5E6F8C),
    onBoardAlert = Color(0xFFFF8A98),
    ground = Color(0xFF0B1526),
    strip = Color(0xFF122036),
    rule = Color(0xFF1F2F4A),
    ruleStrong = Color(0xFF2A3C5C),
    ink = Color(0xFFEDF1F7),
    inkSecondary = Color(0xFF9AA7BD),
    hint = Color(0xFF6B7A94),
    departure = Color(0xFFC8102E),
    departureSoft = Color(0x24FF6B7A),
    departureText = Color(0xFFFF8A98),
    arrival = Color(0xFF7FA6E6),
    arrivalSoft = Color(0x247FA6E6),
    arrivalText = Color(0xFFA8C4F0),
    ok = Color(0xFF4CC38A),
    okSoft = Color(0x244CC38A),
    estimate = Color(0xFFF5B233),
    estimateSoft = Color(0x24F5B233),
    alert = Color(0xFFFF6B7A),
    alertSoft = Color(0x24FF6B7A),
    vipSoft = Color(0x29F0B24A),
    vipText = Color(0xFFF5CB7A),
    neutralSoft = Color(0x14FFFFFF),
    nav = Color(0xFF0F1B31),
    field = Color(0xFF0B1526),
)

val LocalAirShiftPalette = staticCompositionLocalOf { LightPalette }

/** 主题访问入口：`AirShiftTokens.colors.board`。 */
object AirShiftTokens {
    val colors: AirShiftPalette
        @Composable @ReadOnlyComposable get() = LocalAirShiftPalette.current
}

// ---------- 圆角 ----------

object AirShiftRadius {
    /** 状态灯 / 小标签。 */
    val Lamp: Dp = 4.dp

    /** 输入框 / 小按钮。 */
    val Small: Dp = 8.dp

    /** 信息条。 */
    val Strip: Dp = 10.dp

    /** 主按钮 / 次按钮。 */
    val Button: Dp = 12.dp
}

// ---------- 间距（4dp 网格） ----------

object AirShiftSpacing {
    val XS: Dp = 4.dp
    val S: Dp = 8.dp
    val M: Dp = 16.dp
    val L: Dp = 24.dp
    val XL: Dp = 32.dp
}

// ---------- 阴影（只保留一级：当前条抬起） ----------

private val ShadowNavy = Color(0xFF14284B)

/** 当前条抬起：近似 0 6 20 rgba(20,40,75,.12)。 */
@Stable
fun Modifier.currentCardShadow(shape: Shape): Modifier = shadow(
    elevation = 6.dp,
    shape = shape,
    clip = false,
    ambientColor = ShadowNavy.copy(alpha = 0.12f),
    spotColor = ShadowNavy.copy(alpha = 0.12f),
)

// ---------- 数字字阶（Barlow Semi Condensed，tabular-nums） ----------

private const val TABULAR_NUMS = "tnum"

/** 板面倒计时：68sp Bold。颜色由调用方给。 */
val BoardNumeric = TextStyle(
    fontFamily = AirShiftFonts.Board,
    fontWeight = FontWeight.Bold,
    fontSize = 68.sp,
    lineHeight = 64.sp,
    letterSpacing = (-1.2).sp,
    fontFeatureSettings = TABULAR_NUMS,
)

/** 板头时钟：22sp SemiBold。 */
val BoardClock = TextStyle(
    fontFamily = AirShiftFonts.Board,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 24.sp,
    fontFeatureSettings = TABULAR_NUMS,
)

/** 板面到位时间 / 班车时间：26sp SemiBold。 */
val BoardValue = TextStyle(
    fontFamily = AirShiftFonts.Board,
    fontWeight = FontWeight.SemiBold,
    fontSize = 26.sp,
    lineHeight = 28.sp,
    fontFeatureSettings = TABULAR_NUMS,
)

/** 展开条的航班号与机位号：26sp Bold。 */
val FlightNumberLarge = TextStyle(
    fontFamily = AirShiftFonts.Board,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.2).sp,
    fontFeatureSettings = TABULAR_NUMS,
)

/** 折叠条的航班号：16sp Bold。 */
val FlightNumber = TextStyle(
    fontFamily = AirShiftFonts.Board,
    fontWeight = FontWeight.Bold,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = TABULAR_NUMS,
)

/** 折叠条的时间 / 日历日期：16–18sp SemiBold。 */
val StripTime = TextStyle(
    fontFamily = AirShiftFonts.Board,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = TABULAR_NUMS,
)

/** 展开条里的时间值：17sp SemiBold。 */
val NumericValue = TextStyle(
    fontFamily = AirShiftFonts.Board,
    fontWeight = FontWeight.SemiBold,
    fontSize = 17.sp,
    lineHeight = 20.sp,
    fontFeatureSettings = TABULAR_NUMS,
)

/** 行内小数字（机位、meta 值）：13–15sp SemiBold。 */
val NumericSmall = TextStyle(
    fontFamily = AirShiftFonts.Board,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 18.sp,
    fontFeatureSettings = TABULAR_NUMS,
)

// ---------- Material3 映射 ----------

private fun AirShiftPalette.toColorScheme() = if (isDark) darkScheme() else lightScheme()

private fun AirShiftPalette.darkScheme() = darkColorScheme(
    primary = departure,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C1422),
    onPrimaryContainer = departureText,
    secondary = arrival,
    onSecondary = ground,
    secondaryContainer = Color(0xFF1E3252),
    onSecondaryContainer = arrivalText,
    tertiary = estimate,
    onTertiary = ground,
    tertiaryContainer = Color(0xFF4A3600),
    onTertiaryContainer = Color(0xFFFBE7AE),
    error = alert,
    onError = ground,
    errorContainer = Color(0xFF4A1A22),
    onErrorContainer = departureText,
    background = ground,
    onBackground = ink,
    surface = strip,
    onSurface = ink,
    surfaceVariant = Color(0xFF1A2A45),
    onSurfaceVariant = inkSecondary,
    surfaceContainerLowest = ground,
    surfaceContainerLow = strip,
    surfaceContainer = strip,
    surfaceContainerHigh = Color(0xFF1A2A45),
    surfaceContainerHighest = Color(0xFF223454),
    outline = ruleStrong,
    outlineVariant = rule,
    inverseSurface = Color(0xFFEDF1F7),
    inverseOnSurface = Color(0xFF14284B),
    scrim = Color.Black,
)

private fun AirShiftPalette.lightScheme() = lightColorScheme(
    primary = departure,
    onPrimary = Color.White,
    primaryContainer = departureSoft,
    onPrimaryContainer = departureText,
    secondary = arrival,
    onSecondary = Color.White,
    secondaryContainer = arrivalSoft,
    onSecondaryContainer = arrivalText,
    tertiary = estimate,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBE7AE),
    onTertiaryContainer = Color(0xFF3F2E00),
    error = alert,
    onError = Color.White,
    errorContainer = departureSoft,
    onErrorContainer = departureText,
    background = ground,
    onBackground = ink,
    surface = strip,
    onSurface = ink,
    surfaceVariant = field,
    onSurfaceVariant = inkSecondary,
    surfaceContainerLowest = strip,
    surfaceContainerLow = Color(0xFFF7F8FB),
    surfaceContainer = ground,
    surfaceContainerHigh = Color(0xFFE9ECF2),
    surfaceContainerHighest = Color(0xFFE1E5ED),
    outline = ruleStrong,
    outlineVariant = rule,
    inverseSurface = board,
    inverseOnSurface = Color.White,
    scrim = Color.Black,
)

/**
 * 字阶（sp）：11 / 12 / 13 / 15 / 17 / 20 / 26 / 34 / 68。
 * 所有槽位挂 Barlow，汉字回落系统字体；数字槽位见上方 Board 系列。
 */
private fun textStyle(weight: FontWeight, size: Int, lineHeight: Int, letterSpacing: Float = 0f) = TextStyle(
    fontFamily = AirShiftFonts.Text,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

private val AirShiftTypography = Typography(
    displayLarge = BoardNumeric,
    displayMedium = textStyle(FontWeight.Bold, 44, 48),
    displaySmall = textStyle(FontWeight.Bold, 34, 40, letterSpacing = -0.25f),
    headlineLarge = textStyle(FontWeight.Bold, 30, 36),
    headlineMedium = textStyle(FontWeight.Bold, 26, 32, letterSpacing = -0.2f),
    headlineSmall = textStyle(FontWeight.Bold, 22, 28),
    titleLarge = textStyle(FontWeight.SemiBold, 20, 26),
    titleMedium = textStyle(FontWeight.SemiBold, 17, 22),
    titleSmall = textStyle(FontWeight.SemiBold, 15, 20),
    bodyLarge = textStyle(FontWeight.Normal, 15, 22),
    bodyMedium = textStyle(FontWeight.Normal, 13, 19),
    bodySmall = textStyle(FontWeight.Normal, 12, 17),
    labelLarge = textStyle(FontWeight.SemiBold, 13, 18),
    labelMedium = textStyle(FontWeight.SemiBold, 12, 16),
    labelSmall = textStyle(FontWeight.Medium, 11, 15, letterSpacing = 0.2f),
)

/** 圆角体系：灯 4、输入框/小按钮 8、信息条 10、按钮 12。 */
private val AirShiftShapes = Shapes(
    extraSmall = RoundedCornerShape(AirShiftRadius.Lamp),
    small = RoundedCornerShape(AirShiftRadius.Small),
    medium = RoundedCornerShape(AirShiftRadius.Strip),
    large = RoundedCornerShape(AirShiftRadius.Button),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun AirShiftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val reduceMotion = rememberReduceMotion()
    CompositionLocalProvider(
        LocalAirShiftPalette provides palette,
        LocalReduceMotion provides reduceMotion,
    ) {
        // material3 1.4.0 的 MotionScheme / MaterialTheme.motionScheme 是 internal：应用自己的弹簧值在
        // AirShiftMotion 里镜像 M3 standard，这里不传 motionScheme（M3 组件用它内部的同一组值）。
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = AirShiftTypography,
            shapes = AirShiftShapes,
            content = content,
        )
    }
}
