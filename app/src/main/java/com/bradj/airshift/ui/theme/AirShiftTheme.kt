package com.bradj.airshift.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.foundation.isSystemInDarkTheme

// =====================================================================
// 航勤智排 Design Tokens —— 东航品牌基因（VI 对齐版）
// 仅用于 UI 样式，不涉及任何业务逻辑。
//
// 品牌基调：
//   东航红 #C8102E —— 克制使用（主按钮 / 当前任务 / 紧急状态 / 品牌点缀）
//   深藏青 #14284B —— 大标题、页头背景、深色区域
//   暖白 #F7F8FA 页面背景 + 纯白卡片，藏青灰阶文字，不用纯黑
// 如后续提供官方 VI 手册色值，以手册为准并在此统一替换。
// =====================================================================

// ---------- 品牌色 Brand ----------

/** 东航品牌红。仅用于：主行动按钮、当前任务强调、紧急状态、品牌点缀、"出港"标签。 */
val CeaRed = Color(0xFFC8102E)

/** 东航红深色端（按压态 / 深色模式容器）。 */
val CeaRedDeep = Color(0xFFA00D24)

/** 出港标签浅红底。 */
val CeaRedSoft = Color(0xFFFDECEE)

/** 出港标签上的深红文字（AA 对比度）。 */
val OnCeaRedSoft = Color(0xFF9C0B22)

/** 深藏青（取自尾翼蓝）：页头背景、大标题、核心深色文字。 */
val CeaNavy = Color(0xFF14284B)

/** 深藏青渐变亮端：仅用于页头 135° 藏青系单色微渐变。 */
val CeaNavyLight = Color(0xFF1E3A66)

/** 页头 135° 藏青微渐变（左上 → 右下）。全 App 唯一允许的渐变。 */
val CeaNavyGradient: Brush = Brush.linearGradient(
    colors = listOf(CeaNavy, CeaNavyLight),
    start = androidx.compose.ui.geometry.Offset.Zero,
    end = androidx.compose.ui.geometry.Offset.Infinite,
)

// ---------- 中性色 Neutral ----------

/** 页面底色：暖白。 */
val CloudWhite = Color(0xFFF7F8FA)

/** 卡片底色：纯白。 */
val CardWhite = Color(0xFFFFFFFF)

/** 1dp 微边框 / 底部导航顶部细线。 */
val BorderSoft = Color(0xFFECEEF2)

/** 输入框浅灰填充。 */
val FieldFill = Color(0xFFF0F2F5)

/** 藏青灰阶 · 主文字（不用纯黑）。 */
val TextPrimary = Color(0xFF14284B)

/** 藏青灰阶 · 正文。 */
val TextBody = Color(0xFF4A5568)

/** 藏青灰阶 · 说明文字（13sp 起，白底对比度 ≥ 7:1，满足 WCAG AA/AAA）。 */
val TextSecondary = Color(0xFF4A5568)

/** 藏青灰阶 · 弱提示 / 占位。 */
val TextHint = Color(0xFF8A94A6)

/** "--" 占位骨架短横线填充色（浅灰，不直接显示破折号）。 */
val SkeletonGray = Color(0xFFE3E6EB)

/** 卡片顶部 1px 内高光 rgba(255,255,255,.6)。 */
val CardTopHighlight = Color(0x99FFFFFF)

/** 底部导航未选中态：藏青灰阶弱提示色。 */
val NavyGrey = Color(0xFF8A94A6)

// ---------- 语义色 Semantic ----------

/** 进港：藏青蓝系。 */
val InboundBlue = Color(0xFF2B5EA7)
val InboundBlueSoft = Color(0xFFEAF1FB)

/** 正常 / 完成：墨绿。 */
val SuccessGreen = Color(0xFF0F7B5F)

/** 正常 / 完成浅绿底。 */
val SuccessGreenSoft = Color(0xFFE3F2EC)

/** 延误 / 紧急 / "需要留意"：琥珀。 */
val AmberAccent = Color(0xFFD97706)

/** 提示条浅琥珀底。 */
val AmberSoft = Color(0xFFFBF0DC)

/** 琥珀色文字（AA 对比度，用于"变更"等小号提醒文字）。 */
val AmberText = Color(0xFFA85D00)

/** 登机口/机位"变更"提醒文字色（AA）。 */
val ChangeOrange = AmberText

/** VIP 琥珀金强调（保留辨识度）。 */
val VipAmber = Color(0xFFE8A33D)
val VipAmberContainer = Color(0xFFFCEBC8)
val OnVipAmberContainer = Color(0xFF6E4200)

// ---------- 深色模式 Dark（机坪夜间作业；红色不变） ----------

val DarkBackground = Color(0xFF0F1A2E)
val DarkCard = Color(0xFF1A2740)
val DarkFieldFill = Color(0xFF22304A)
val DarkBorder = Color(0xFF2A3A57)
val DarkTextPrimary = Color(0xFFEDF1F7)
val DarkTextSecondary = Color(0xFF9AA7BD)

// ---------- 圆角 Radius ----------

object AirShiftRadius {
    /** 标签 chip / 状态胶囊：全圆角。 */
    val Chip: Dp = 999.dp

    /** 小标签 / 输入框。 */
    val Tag: Dp = 12.dp

    /** 按钮。 */
    val Button: Dp = 14.dp

    /** 卡片。 */
    val Card: Dp = 16.dp
}

// ---------- 间距 Spacing（4dp 基准网格） ----------

object AirShiftSpacing {
    val XS: Dp = 4.dp
    val S: Dp = 8.dp
    val M: Dp = 16.dp
    val L: Dp = 24.dp
    val XL: Dp = 32.dp
}

// ---------- 阴影 Elevation（藏青底色调，三级） ----------

private val ShadowNavy = Color(0xFF14284B)

/** 一级 · 列表卡：近似 0 1 3 rgba(20,40,75,.06)。 */
@Stable
fun Modifier.listCardShadow(shape: Shape): Modifier = shadow(
    elevation = 2.dp,
    shape = shape,
    clip = false,
    ambientColor = ShadowNavy.copy(alpha = 0.06f),
    spotColor = ShadowNavy.copy(alpha = 0.06f),
)

/** 二级 · 当前任务卡：近似 0 4 16 rgba(20,40,75,.10)。 */
@Stable
fun Modifier.currentCardShadow(shape: Shape): Modifier = shadow(
    elevation = 6.dp,
    shape = shape,
    clip = false,
    ambientColor = ShadowNavy.copy(alpha = 0.10f),
    spotColor = ShadowNavy.copy(alpha = 0.10f),
)

/** 三级 · 倒计时 hero 与浮动按钮：近似 0 8 24 rgba(20,40,75,.16)。 */
@Stable
fun Modifier.heroShadow(shape: Shape): Modifier = shadow(
    elevation = 12.dp,
    shape = shape,
    clip = false,
    ambientColor = ShadowNavy.copy(alpha = 0.16f),
    spotColor = ShadowNavy.copy(alpha = 0.16f),
)

/** 兼容别名：一级列表卡阴影。 */
@Stable
fun Modifier.softShadow(shape: Shape): Modifier = listCardShadow(shape)

/** 白卡统一边框。 */
val CardBorder: BorderStroke
    @Composable get() = BorderStroke(1.dp, BorderSoft)

// ---------- 动效 Motion ----------

object AirShiftMotion {
    /** 状态变化统一时长（ms）。 */
    const val StateChangeMs: Int = 200

    /** CSS ease-out 等价曲线 cubic-bezier(0, 0, .58, 1)。 */
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
}

// ---------- 数字字阶（等宽 tabular-nums，Heavy 粗体） ----------

private val TabularNums = "tnum"

/** 倒计时 hero：60sp Heavy tabular-nums（规范区间 56–64）。颜色由调用方按常态/紧急态覆写。 */
val NumericHero = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 60.sp,
    lineHeight = 64.sp,
    fontFeatureSettings = TabularNums,
    color = CeaRed,
)

/** 航班号：30sp Heavy tabular-nums（规范区间 28–34），超大粗体一瞥即读。 */
val FlightNumber = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 30.sp,
    lineHeight = 36.sp,
    fontFeatureSettings = TabularNums,
    color = TextPrimary,
)

/** 大号数字：到位时间、计划时间（右侧大号对齐）。 */
val NumericLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    fontFeatureSettings = TabularNums,
    color = TextPrimary,
)

/** 中号数字：航段实时时间。 */
val NumericMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    lineHeight = 30.sp,
    fontFeatureSettings = TabularNums,
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

/** 深色模式：背景 #0F1A2E、卡片 #1A2740，红色不变。 */
private val AirShiftDarkColorScheme = darkColorScheme(
    primary = CeaRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C1422),
    onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = Color(0xFF8FB0E8),
    onSecondary = Color(0xFF0F1A2E),
    secondaryContainer = Color(0xFF1E3252),
    onSecondaryContainer = Color(0xFFD5E2F8),
    tertiary = Color(0xFFE8B34B),
    tertiaryContainer = Color(0xFF4A3600),
    onTertiaryContainer = Color(0xFFFBE7AE),
    background = DarkBackground,
    surface = DarkCard,
    surfaceVariant = DarkFieldFill,
    onSurface = DarkTextPrimary,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorder,
)

/**
 * 品牌字阶：
 * 页面标题 20sp Semibold；正文 15–16sp；辅助信息 12–13sp。
 * Heavy / Semibold / Regular 三档拉开对比，禁止全文 Regular。
 */
private val AirShiftTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp, fontFeatureSettings = TabularNums),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

/** 圆角体系：chip 999、按钮 14、卡片 16、小标签 12。 */
private val AirShiftShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(AirShiftRadius.Tag),
    medium = RoundedCornerShape(AirShiftRadius.Card),
    large = RoundedCornerShape(AirShiftRadius.Card),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AirShiftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AirShiftDarkColorScheme else AirShiftColorScheme,
        typography = AirShiftTypography,
        shapes = AirShiftShapes,
        content = content,
    )
}
