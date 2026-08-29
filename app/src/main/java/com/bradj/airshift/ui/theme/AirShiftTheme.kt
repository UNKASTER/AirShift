package com.bradj.airshift.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 中国东方航空 VI 主色：东航红。 */
val CeaRed = Color(0xFFC8102E)

/** VIP 琥珀色强调（保留辨识度）。 */
val VipAmber = Color(0xFFF59E0B)
val VipAmberContainer = Color(0xFFFFE3A3)
val OnVipAmberContainer = Color(0xFF7A4300)

/** 登机口/机位“变更”最小提醒的橙色。 */
val ChangeOrange = Color(0xFFE8860B)

private val AirShiftColorScheme = lightColorScheme(
    primary = CeaRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF7DCDF),
    onPrimaryContainer = Color(0xFF5C0715),
    secondary = Color(0xFF4A5568),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF1E2836),
    tertiary = Color(0xFF765A00),
    tertiaryContainer = Color(0xFFFFE08B),
    onTertiaryContainer = Color(0xFF3F2E00),
    background = Color(0xFFF5F6F8),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9ECEF),
    onSurfaceVariant = Color(0xFF5B6470),
)

private val AirShiftTypography = Typography()

@Composable
fun AirShiftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AirShiftColorScheme,
        typography = AirShiftTypography,
        content = content,
    )
}
